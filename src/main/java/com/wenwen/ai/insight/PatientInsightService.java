package com.wenwen.ai.insight;

import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.source.*;
import com.wenwen.ai.treatment.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.wenwen.vo.AiCohortVo.object;

/** 只投影一次本人 RA 快照中的目标患者；没有结果持久化。 */
@Service
public final class PatientInsightService {
    private final CohortSourceAdapter source;
    private final Clock clock;
    private final DrugDictionary dictionary;
    public PatientInsightService(CohortSourceAdapter source,Clock clock,DrugDictionary dictionary){this.source=source;this.clock=clock;this.dictionary=dictionary;}
    public Map<String,Object> insight(TrustedDoctor doctor,PatientDetailQuery query,String trace){
        Instant started=clock.instant();LocalDate asOf=started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        DrugDictionary.Snapshot view=dictionary.snapshot();SourceBatch batch=source.read(doctor.getId(),asOf);
        Map<String,Object> result=new LinkedHashMap<>(project(batch,query.getPatientId(),asOf,view));
        result.put("meta",object("studyCode","RA","asOfDate",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),"traceId",trace,
                "policyVersions",batch.withFmSource(object("insight","dev-insight-v01","crp","dev-crp-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04",
                    "treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary",view.version))));return result;
    }
    public static Map<String,Object> project(SourceBatch batch,long id,LocalDate asOf,DrugDictionary.Snapshot view){
        if(!batch.getPatientIds().contains(id))throw new CohortException(404,"NOT_FOUND","患者不存在或无权访问");
        PatientDisplay display=batch.getDisplay().get(id);com.wenwen.ai.clinical.PatientClinical clinical=batch.getClinical().get(id);
        List<Map<String,Object>> visits=new ArrayList<>();
        List<ScoreVisit> target=new ArrayList<>();for(ScoreVisit visit:batch.getVisits())if(visit.getPatientId()==id)target.add(visit);
        target.sort(Comparator.comparing(ScoreVisit::getObservedAt,Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(ScoreVisit::getId));
        for(ScoreVisit visit:target){
            visits.add(measurement(visit,asOf));
        }
        TreatmentTimeline timeline=TreatmentTimeline.build(target,asOf,view);List<Map<String,Object>> history=new ArrayList<>();
        for(TreatmentEpisode episode:timeline.episodes){Map<String,Object> summary=new LinkedHashMap<>(episode.summary(asOf));summary.remove("schemeDurationMonths");history.add(summary);}
        Map<String,Map<String,Object>> names=new LinkedHashMap<>();
        for(ScoreVisit visit:target)for(MedicationObservation.Row row:visit.getMedication().rows){DrugDictionary.Drug drug=view.identify(row.name);if(drug!=null&&drug.recognized())names.putIfAbsent(drug.id,object("id",drug.id,"name",drug.name,"category",drug.category));}
        return object("patient",object("patientId",Long.toString(id),"name",display.getName(),"studyNo",display.getStudyNo(),"sex",clinical.getSex(),"age",clinical.getAge(),
                "diseaseDurationYears",clinical.getDiseaseDurationYears(),"sero",clinical.getSero(),"fm",clinical.getFm(),"as",clinical.getAs(),"clinicalProvenance",clinical.getProvenance()),
            "visits",visits,"treatment",object("current",timeline.current().summary(asOf),"history",history,"drugNames",new ArrayList<>(names.values())));

    }
    public static Map<String,Object> measurement(ScoreVisit visit,LocalDate asOf){
            Map<String,Object> metrics=new LinkedHashMap<>();
            metrics.put("das28Crp",object("value",visit.getScore(),"unit",null,"sourceVisitId",Long.toString(visit.getId()),"sourceField","bqpg.result.crpScore",
                "observedAt",visit.getObservedAt()==null?null:visit.getObservedAt().toString(),"quality",visit.getQuality(),"missingReason",visit.getScore()==null?"MISSING_OR_INVALID_CRP":null));
            for(String key:Arrays.asList("tjc","sjc","gh","haq","crp","pain")){
                Map<String,Object> original=visit.getEvaluation().get(key),projected=new LinkedHashMap<>();
                for(String field:Arrays.asList("value","unit","sourceVisitId","sourceField","observedAt","quality","missingReason"))projected.put(field,original.get(field));
                metrics.put(key,projected);
            }
            LocalDate date=visit.getObservedAt();return object("visitId",Long.toString(visit.getId()),"observedAt",date==null?null:date.toString(),
                "temporalStatus",date==null?"UNDATED":date.isAfter(asOf)?"FUTURE":"OBSERVED","metrics",metrics);
    }
}
