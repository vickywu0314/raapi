package com.wenwen.service.impl;

import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.source.*;
import com.wenwen.ai.clinical.VisitMatcher;
import com.wenwen.service.AiCohortService;
import com.wenwen.util.Das28Util;
import com.wenwen.vo.AiCohortVo;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.wenwen.vo.AiCohortVo.object;

@Service
public class AiCohortServiceImpl implements AiCohortService {
    private final CohortSourceAdapter source;
    private final Clock clock;
    private final com.wenwen.ai.treatment.DrugDictionary dictionary;
    public AiCohortServiceImpl(CohortSourceAdapter source, Clock clock, com.wenwen.ai.treatment.DrugDictionary dictionary) { this.source = source; this.clock = clock; this.dictionary=dictionary; }
    public AiCohortVo analyze(TrustedDoctor doctor, CohortQuery query, String traceId) {
        Instant started = clock.instant();
        LocalDate asOf = started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        com.wenwen.ai.treatment.DrugDictionary.Snapshot drugView=dictionary.snapshot();
        SourceBatch batch = source.read(doctor.getId(),asOf);
        Map<Long,List<ScoreVisit>> visitsByPatient=new HashMap<>();
        Map<Long,Set<String>> observedQuality = new HashMap<>();
        for (ScoreVisit visit : batch.getVisits()) {
            visitsByPatient.computeIfAbsent(visit.getPatientId(),key -> new ArrayList<>()).add(visit);
            observedQuality.computeIfAbsent(visit.getPatientId(),key -> new LinkedHashSet<>()).addAll(visit.getQuality());
        }
        String[] levels = {"remission","low","moderate","high"};
        Map<String,Integer> counts = new LinkedHashMap<>(), baseCounts=new LinkedHashMap<>();
        for (String level : levels) { counts.put(level,0); baseCounts.put(level,0); }
        List<Map<String,Object>> items = new ArrayList<>();
        int n = 0, eval = 0, baseN=0, unknownTxN=0;
        Integer effectiveIdsN = query.getIds() == null ? null : 0;
        for (long id : batch.getPatientIds()) {
            if (query.getIds() != null) {
                if (!query.getIds().contains(id)) continue;
                effectiveIdsN++;
            }
            com.wenwen.ai.qc.MissingDataStatus qc=batch.getQc().get(id);
            if (!qc.matches(query.getData())) continue;
            com.wenwen.ai.clinical.PatientClinical clinical=batch.getClinical().get(id);
            if (query.getSex()!=null && !query.getSex().equals(clinical.getSex())) continue;
            if (query.getSero()!=null && !"TRUE".equals(clinical.getSero())) continue;
            if (query.getCm()!=null && !("FM".equals(query.getCm()) && "TRUE".equals(clinical.getFm()))
                    && !("AS".equals(query.getCm()) && "TRUE".equals(clinical.getAs()))
                    && !("none".equals(query.getCm()) && "FALSE".equals(clinical.getFm()) && "FALSE".equals(clinical.getAs()))) continue;
            if (query.getAge()!=null) {
                String[] bounds=query.getAge().split("-",-1);
                int min=Integer.parseInt(bounds[0]), max=bounds[1].isEmpty()?120:Integer.parseInt(bounds[1]);
                if (clinical.getAge()==null || clinical.getAge()<min || clinical.getAge()>max) continue;
            }
            List<ScoreVisit> medicationVisits=visitsByPatient.getOrDefault(id,Collections.emptyList());
            com.wenwen.ai.treatment.TreatmentEpisode treatment=com.wenwen.ai.treatment.TreatmentTimeline.build(medicationVisits,asOf,drugView).current();
            if(!treatment.matches(query.getTx())) continue;
            ScoreVisit now=VisitMatcher.now(medicationVisits,id,asOf);
            ScoreVisit six=VisitMatcher.sixMonth(medicationVisits,id,asOf,treatment);
            ScoreVisit baseline=VisitMatcher.baseline(medicationVisits,id,asOf,treatment);
            ScoreVisit score = "6m".equals(query.getAt()) ? six : now;
            if("6m".equals(query.getAt()) && score==null) continue;
            String level = score == null ? null : Das28Util.activity(score.getScore());
            if (query.getAct() != null) {
                if (score == null) continue;
                boolean target = score.getScore().compareTo(new java.math.BigDecimal("2.7")) <= 0;
                if (!(query.getAct().equals(level) || ("target".equals(query.getAct()) && target)
                        || ("mod-high".equals(query.getAct()) && !target))) continue;
            }
            n++;
            if("UNKNOWN".equals(treatment.state) || "CONFLICT".equals(treatment.state)) unknownTxN++;
            if (level != null) { counts.put(level,counts.get(level)+1); eval++; }
            if(baseline!=null) { String baseLevel=Das28Util.activity(baseline.getScore()); baseCounts.put(baseLevel,baseCounts.get(baseLevel)+1); baseN++; }
            if (items.size() < 10) {
                Map<String,Object> provenance = provenance(score,observedQuality.getOrDefault(id,Collections.emptySet()),"NO_VALID_CRP");
                Map<String,Object> baselineProvenance=provenance(baseline,Collections.emptySet(),VisitMatcher.reliable(treatment)?"NO_VALID_BASELINE":"NO_RELIABLE_START");
                ScoreVisit latestCrp=VisitMatcher.latest(medicationVisits,id,asOf,visit -> visit.getEvaluation().get("crp").get("value")!=null);
                Map<String,Object> selection=object("at",query.getAt(),"episodeKey",treatment.key,
                    "baselineVisitId",visitId(baseline),"baselineDate",visitDate(baseline),"evalVisitId",visitId(score),"evalDate",visitDate(score),
                    "nowVisitId",visitId(now),"nowDate",visitDate(now),"target6mDate",VisitMatcher.reliable(treatment)?treatment.start.plusMonths(6).toString():null,
                    "eligible6m",six!=null,"quality",VisitMatcher.beforeScheme(score,treatment)?Collections.singletonList("EVAL_BEFORE_SCHEME"):Collections.emptyList(),
                    "missingReason",score==null?("6m".equals(query.getAt())?"NO_ELIGIBLE_6M":"NO_VALID_CRP"):null,
                    "deltaMissingReason",VisitMatcher.deltaMissingReason(baseline,treatment.key,score,treatment.key,treatment));
                items.add(object("qc",qc.asMap(),"patientId",Long.toString(id),"das28At",score == null ? null : score.getScore(),"das28Base",baseline==null?null:baseline.getScore(),"das28Current",now==null?null:now.getScore(),
                    "deltaDas28",VisitMatcher.delta(baseline,treatment.key,score,treatment.key,treatment),"baselineProvenance",baselineProvenance,"selection",selection,
                    "crpAt",score==null?com.wenwen.ai.clinical.EvaluationPolicy.missingCrp("NO_EVALUATION"):score.getEvaluation().get("crp"),
                    "crpCurrent",latestCrp==null?com.wenwen.ai.clinical.EvaluationPolicy.missingCrp("NO_VALID_CRP_LAB"):latestCrp.getEvaluation().get("crp"),"activity",level,"scoreProvenance",provenance,"clinicalProvenance",clinical.getProvenance(),"evaluation",score==null?com.wenwen.ai.clinical.EvaluationPolicy.missing():score.getEvaluation(),
                    "treatment",treatment.summary(asOf),"clinical",object("sex",clinical.getSex(),"age",clinical.getAge(),"diseaseDurationYears",clinical.getDiseaseDurationYears(),"sero",clinical.getSero(),"fm",clinical.getFm(),"as",clinical.getAs())));
            }
        }
        List<Map<String,Object>> distribution = new ArrayList<>(), baseDistribution=new ArrayList<>();
        for (String level : levels) { distribution.add(object("level",level,"label",Das28Util.label(level),"count",counts.get(level))); baseDistribution.add(object("level",level,"label",Das28Util.label(level),"count",baseCounts.get(level))); }
        return new AiCohortVo(n,batch.getPatientIds().size(),query.getIds() == null ? null : query.getIds().size(),effectiveIdsN,
            object("current",distribution,"base",baseDistribution,"baseN",baseN,"baseUnknownN",n-baseN,"evalN",eval,"unknownN",n-eval,"unknownTxN",unknownTxN),object("total",n,"items",items),
            object("at",query.getAt(),"asOfDate",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),
                "computedAt",clock.instant().toString(),"policyVersions",object("qc",com.wenwen.ai.qc.MissingDataPolicy.VERSION,"crp","dev-crp-v04","now","dev-now-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04","treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary",drugView.version),"traceId",traceId,
                "supportedFilters",Arrays.asList("studyCode","at","act","ids","sex","age","sero","cm","tx","data"),"completion","P02_QC"));
    }
    private static String visitId(ScoreVisit visit) { return visit==null?null:Long.toString(visit.getId()); }
    private static String visitDate(ScoreVisit visit) { return visit==null?null:visit.getObservedAt().toString(); }
    private static Map<String,Object> provenance(ScoreVisit score,Collection<String> quality,String missing) {
        return score==null ? object("source",null,"sourceVisitId",null,"sourceField",null,"observedAt",null,"raw",null,
            "quality",new ArrayList<>(quality),"missingReason",missing)
            : object("source","LEGACY_STORED","sourceVisitId",visitId(score),"sourceField","bqpg.result.crpScore",
                "observedAt",visitDate(score),"raw",score.getRaw(),"quality",score.getQuality());
    }
}
