package com.wenwen.service.impl;

import com.wenwen.ai.analysis.*;
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
    private final com.wenwen.ai.result.AnalysisResultStore results;
    private final com.wenwen.ai.result.AnalysisSettings settings;
    private final com.wenwen.ai.result.AnalysisCursor cursors;
    private final com.wenwen.ai.result.AnalysisPageReader pages;
    private final com.wenwen.ai.result.AnalysisResultCodec codec=new com.wenwen.ai.result.AnalysisResultCodec();
    public AiCohortServiceImpl(CohortSourceAdapter source, Clock clock, com.wenwen.ai.treatment.DrugDictionary dictionary,com.wenwen.ai.result.AnalysisResultStore results,com.wenwen.ai.result.AnalysisSettings settings,com.wenwen.ai.result.AnalysisCursor cursors,com.wenwen.ai.result.AnalysisPageReader pages) { this.source = source; this.clock = clock; this.dictionary=dictionary;this.results=results;this.settings=settings;this.cursors=cursors;this.pages=pages; }
    public Map<String,Object> resolveEntry(TrustedDoctor doctor,com.wenwen.ai.query.SimilarEntryQuery query,String traceId,Instant started) {
        LocalDate asOf=started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        SourceBatch batch=source.read(doctor.getId(),asOf);
        if(!batch.getPatientIds().contains(query.getIndexPatientId()))throw new com.wenwen.ai.query.CohortException(404,"RESOURCE_NOT_FOUND","索引患者不存在");
        com.wenwen.ai.clinical.PatientClinical clinical=Objects.requireNonNull(batch.getClinical().get(query.getIndexPatientId()));
        List<String> missing=new ArrayList<>();
        if(clinical.getSex()==null)missing.add("sex");if(clinical.getAge()==null)missing.add("age");if("UNKNOWN".equals(clinical.getSero()))missing.add("sero");
        Map<String,Object> meta=object("asOf",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),"traceId",traceId,"policyVersion","dev-similar-v01");
        if(clinical.getAge()!=null&&(clinical.getAge()<0||clinical.getAge()>120))
            return object("source","SIMILAR","filters",null,"missingBasis",missing,"status","UNSUPPORTED_BASIS","canApply",false,"reason","AGE_OUT_OF_SUPPORTED_RANGE","meta",meta);
        if(clinical.getSex()==null&&clinical.getAge()==null&&!"TRUE".equals(clinical.getSero()))
            return object("source","SIMILAR","filters",null,"missingBasis",missing,"status","NO_BASIS","canApply",false,"reason",null,"meta",meta);
        Map<String,Object> generated=object("sex",clinical.getSex(),"age",clinical.getAge()==null?null:Math.max(0,clinical.getAge()-5)+"-"+Math.min(120,clinical.getAge()+5),"sero","TRUE".equals(clinical.getSero())?"1":null);
        Map<String,Object> filters;
        try {
            byte[] input=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(object("filters",generated));
            filters=CohortQuery.read(new java.io.ByteArrayInputStream(input),doctor).normalizedFilters();
        } catch(java.io.IOException e) { throw new IllegalStateException("相似条件无法规范化",e); }
        return object("source","SIMILAR","filters",filters,"missingBasis",missing,"status",missing.isEmpty()?"READY":"PARTIAL_BASIS","canApply",true,"reason",null,
            "meta",meta);
    }
    public AiCohortVo analyze(TrustedDoctor doctor, CohortQuery query, String traceId) {
        Instant started = clock.instant();
        settings.validateAt(started.toEpochMilli());
        LocalDate asOf = started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        com.wenwen.ai.treatment.DrugDictionary.Snapshot drugView=dictionary.snapshot();
        SourceBatch batch = source.read(doctor.getId(),asOf);
        Map<Long,List<ScoreVisit>> visitsByPatient=new HashMap<>();
        Map<Long,Set<String>> observedQuality = new HashMap<>();
        for (ScoreVisit visit : batch.getVisits()) {
            visitsByPatient.computeIfAbsent(visit.getPatientId(),key -> new ArrayList<>()).add(visit);
            observedQuality.computeIfAbsent(visit.getPatientId(),key -> new LinkedHashSet<>()).addAll(visit.getQuality());
        }
        Map<Long,DerivedPatient> universe=new LinkedHashMap<>();
        for(long id:batch.getPatientIds()) {
            List<ScoreVisit> records=visitsByPatient.getOrDefault(id,Collections.emptyList());
            com.wenwen.ai.treatment.TreatmentEpisode tx=com.wenwen.ai.treatment.TreatmentTimeline.build(records,asOf,drugView).current();
            ScoreVisit now=VisitMatcher.now(records,id,asOf),six=VisitMatcher.sixMonth(records,id,asOf,tx),base=VisitMatcher.baseline(records,id,asOf,tx);
            universe.put(id,new DerivedPatient(id,batch.getClinical().get(id),batch.getQc().get(id),tx,now,six,base,"6m".equals(query.getAt())?six:now));
        }
        List<DerivedPatient> cohort=new ArrayList<>();
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
            DerivedPatient derived=universe.get(id);
            com.wenwen.ai.qc.MissingDataStatus qc=derived.qc;
            if (!qc.matches(query.getData())) continue;
            com.wenwen.ai.clinical.PatientClinical clinical=derived.clinical;
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
            com.wenwen.ai.treatment.TreatmentEpisode treatment=derived.treatment;
            if(!treatment.matches(query.getTx())) continue;
            ScoreVisit now=derived.now,six=derived.six,baseline=derived.baseline,score=derived.evaluation;
            if("6m".equals(query.getAt()) && score==null) continue;
            String level = score == null ? null : Das28Util.activity(score.getScore());
            if (query.getAct() != null) {
                if (score == null) continue;
                boolean target = score.getScore().compareTo(new java.math.BigDecimal("2.7")) <= 0;
                if (!(query.getAct().equals(level) || ("target".equals(query.getAct()) && target)
                        || ("mod-high".equals(query.getAct()) && !target))) continue;
            }
            n++; cohort.add(derived);
            if("UNKNOWN".equals(treatment.state) || "CONFLICT".equals(treatment.state)) unknownTxN++;
            if (level != null) { counts.put(level,counts.get(level)+1); eval++; }
            if(baseline!=null) { String baseLevel=Das28Util.activity(baseline.getScore()); baseCounts.put(baseLevel,baseCounts.get(baseLevel)+1); baseN++; }
            {
                Map<String,Object> provenance = provenance(score,observedQuality.getOrDefault(id,Collections.emptySet()),"NO_VALID_CRP");
                Map<String,Object> baselineProvenance=provenance(baseline,Collections.emptySet(),VisitMatcher.reliable(treatment)?"NO_VALID_BASELINE":"NO_RELIABLE_START");
                ScoreVisit latestCrp=VisitMatcher.latest(medicationVisits,id,asOf,visit -> visit.getEvaluation().get("crp").get("value")!=null);
                Map<String,Object> selection=object("at",query.getAt(),"episodeKey",treatment.key,
                    "baselineVisitId",visitId(baseline),"baselineDate",visitDate(baseline),"evalVisitId",visitId(score),"evalDate",visitDate(score),
                    "nowVisitId",visitId(now),"nowDate",visitDate(now),"target6mDate",VisitMatcher.reliable(treatment)?treatment.start.plusMonths(6).toString():null,
                    "eligible6m",six!=null,"quality",VisitMatcher.beforeScheme(score,treatment)?Collections.singletonList("EVAL_BEFORE_SCHEME"):Collections.emptyList(),
                    "missingReason",score==null?("6m".equals(query.getAt())?"NO_ELIGIBLE_6M":"NO_VALID_CRP"):null,
                    "deltaMissingReason",VisitMatcher.deltaMissingReason(baseline,treatment.key,score,treatment.key,treatment));
                PatientDisplay display=Objects.requireNonNull(batch.getDisplay().get(id));
                items.add(object("name",display.getName(),"studyNo",display.getStudyNo(),"qc",qc.asMap(),"patientId",Long.toString(id),"das28At",score == null ? null : score.getScore(),"das28Base",baseline==null?null:baseline.getScore(),"das28Current",now==null?null:now.getScore(),
                    "deltaDas28",VisitMatcher.delta(baseline,treatment.key,score,treatment.key,treatment),"baselineProvenance",baselineProvenance,"selection",selection,
                    "crpAt",score==null?com.wenwen.ai.clinical.EvaluationPolicy.missingCrp("NO_EVALUATION"):score.getEvaluation().get("crp"),
                    "crpCurrent",latestCrp==null?com.wenwen.ai.clinical.EvaluationPolicy.missingCrp("NO_VALID_CRP_LAB"):latestCrp.getEvaluation().get("crp"),"activity",level,"scoreProvenance",provenance,"clinicalProvenance",clinical.getProvenance(),"evaluation",score==null?com.wenwen.ai.clinical.EvaluationPolicy.missing():score.getEvaluation(),
                    "treatment",treatment.summary(asOf),"clinical",object("sex",clinical.getSex(),"age",clinical.getAge(),"diseaseDurationYears",clinical.getDiseaseDurationYears(),"sero",clinical.getSero(),"fm",clinical.getFm(),"as",clinical.getAs())));
            }
        }
        List<Map<String,Object>> distribution = new ArrayList<>(), baseDistribution=new ArrayList<>();
        for (String level : levels) {
            Map<String,Object> currentRow=new LinkedHashMap<>(DescriptiveAnalysis.bucket("level",level,counts.get(level),eval)); currentRow.put("label",Das28Util.label(level));
            Map<String,Object> baseRow=new LinkedHashMap<>(DescriptiveAnalysis.bucket("level",level,baseCounts.get(level),baseN)); baseRow.put("label",Das28Util.label(level));
            distribution.add(Collections.unmodifiableMap(currentRow));baseDistribution.add(Collections.unmodifiableMap(baseRow));
        }
        Map<String,Object> target=DescriptiveAnalysis.target(cohort);
        Map<String,Object> age=DescriptiveAnalysis.median(cohort,true),duration=DescriptiveAnalysis.median(cohort,false);
        Map<String,Object> share=DescriptiveAnalysis.cohortShare(n,universe.size()),female=DescriptiveAnalysis.clinicalRatio(cohort,true),sero=DescriptiveAnalysis.clinicalRatio(cohort,false);
        Map<String,Object> completeness=DescriptiveAnalysis.completeness(cohort);
        Map<?,?> complete=(Map<?,?>)completeness.get("ratio");
        items.sort(com.wenwen.ai.result.AnalysisValues.ORDER);
        Instant computed=clock.instant();long created=computed.toEpochMilli(),expires=Math.addExact(created,settings.ttlMillis());
        String analysisId=UUID.randomUUID().toString();
        AiCohortVo result=new AiCohortVo(analysisId,n,batch.getPatientIds().size(),query.getIds() == null ? null : query.getIds().size(),effectiveIdsN,
            object("current",distribution,"base",baseDistribution,"baseN",baseN,"baseUnknownN",n-baseN,"evalN",eval,"unknownN",n-eval,"unknownTxN",unknownTxN),object("total",n,"items",new ArrayList<>(items.subList(0,Math.min(10,items.size()))),"returnedCount",Math.min(10,n),"nextCursor",n>10?cursors.issue(analysisId,doctor.getId(),10,expires):null,"hasMore",n>10),
            object("at",query.getAt(),"asOfDate",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),
                "computedAt",computed.toString(),"expiresAt",Instant.ofEpochMilli(expires).toString(),"patientProjection","LIVE_SOURCE_V04","policyVersions",object("statistics",com.wenwen.ai.statistics.StatisticalPolicy.VERSION,"descriptive",DescriptiveAnalysis.VERSION,"qc",com.wenwen.ai.qc.MissingDataPolicy.VERSION,"crp","dev-crp-v04","now","dev-now-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04","treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary",drugView.version),"traceId",traceId,
                "supportedFilters",Arrays.asList("studyCode","at","act","ids","sex","age","sero","cm","tx","data"),"completion","P03_RESULT","statisticalDisclosure","探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系"), object("targetRate",target.get("value"),"ageMedian",age.get("value"),"durationMedian",duration.get("value"),"cohortRate",share.get("value"),"femaleRate",female.get("value"),"seroRate",sero.get("value"),"completeRate",complete.get("value"),"incompleteCount",completeness.get("incompleteCount"),"qcUnknownN",completeness.get("qcUnknownN"),"evaluable",eval),object("targetRate",target.get("meta"),"ageMedian",age.get("meta"),"durationMedian",duration.get("meta"),"cohortRate",share.get("meta"),"femaleRate",female.get("meta"),"seroRate",sero.get("meta"),"completeRate",complete.get("meta")),StatisticalAnalysis.byTreatment(cohort,universe.values()),DescriptiveAnalysis.fm(cohort),DescriptiveAnalysis.lines(cohort));
        com.wenwen.ai.result.AnalysisRun run=new com.wenwen.ai.result.AnalysisRun();
        run.setId(analysisId);run.setOwnerDoctorId(doctor.getId());run.setScopeFingerprint(com.wenwen.ai.result.AnalysisValues.scope(doctor.getId(),batch.getPatientIds()));run.setSortKey(com.wenwen.ai.result.AnalysisValues.SORT);run.setPayloadVersion(1);run.setCreatedAtMs(created);run.setExpiresAtMs(expires);
        byte[] payload=codec.encode(result,items,query);if(payload.length>settings.maxBytes())throw new com.wenwen.ai.query.CohortException(413,"RESULT_TOO_LARGE","分析结果超出保留容量");run.setPayload(payload);run.setPayloadSha256(com.wenwen.ai.result.AnalysisValues.sha256(payload));results.create(run);
        return result;
    }
    @SuppressWarnings("unchecked") public com.wenwen.vo.AiCohortPatientsVo page(TrustedDoctor doctor,com.wenwen.ai.query.CohortPageQuery query,String traceId,Instant requestedAt) {
        long now=requestedAt.toEpochMilli();
        com.wenwen.ai.result.AnalysisCursor.Token cursor=cursors.verify(query.getAnalysisId(),query.getCursor());
        if(cursor.owner!=doctor.getId())throw new com.wenwen.ai.query.CohortException(404,"RESOURCE_NOT_FOUND","分析结果不存在");
        if(now>=cursor.expires)throw new com.wenwen.ai.query.CohortException(410,"ANALYSIS_EXPIRED","分析结果已到期");
        com.wenwen.ai.result.AnalysisRun run=results.find(cursor.id);
        if(run==null||run.getOwnerDoctorId()!=doctor.getId())throw new com.wenwen.ai.query.CohortException(404,"RESOURCE_NOT_FOUND","分析结果不存在");
        if(now>=run.getExpiresAtMs())throw new com.wenwen.ai.query.CohortException(410,"ANALYSIS_EXPIRED","分析结果已到期");
        if(run.getExpiresAtMs()!=cursor.expires)throw com.wenwen.ai.result.AnalysisSettings.unavailable();
        Map<String,Object> payload=codec.decode(run);List<Map<String,Object>> rows=(List<Map<String,Object>>)payload.get("rows");
        if(cursor.offset>=rows.size())throw com.wenwen.ai.result.AnalysisCursor.invalid();
        int end=Math.min(cursor.offset+20,rows.size());List<Long> ids=new ArrayList<>();for(Map<String,Object> row:rows.subList(cursor.offset,end))ids.add(Long.parseLong((String)row.get("patientId")));
        Map<Long,PatientDisplayRow> displays=pages.read(doctor.getId(),run.getScopeFingerprint(),ids);
        Instant displayCompleted=clock.instant();List<Map<String,Object>> items=new ArrayList<>();
        for(Map<String,Object> row:rows.subList(cursor.offset,end)){Map<String,Object> item=new LinkedHashMap<>(row);PatientDisplayRow display=displays.get(Long.parseLong((String)row.get("patientId")));item.put("name",display.getName());item.put("studyNo",display.getStudyNo());items.add(Collections.unmodifiableMap(item));}
        Map<String,Object> original=(Map<String,Object>)payload.get("meta");Map<String,Object> meta=new LinkedHashMap<>();
        for(String k:Arrays.asList("at","asOfDate","readStartedAt","readCompletedAt","computedAt","policyVersions","expiresAt"))meta.put(k,original.get(k));
        meta.put("traceId",traceId);meta.put("analysisTraceId",original.get("traceId"));meta.put("displayReadCompletedAt",displayCompleted.toString());meta.put("completion","P03_RESULT");meta.put("patientProjection","RETAINED_NUMERIC_V1");
        return new com.wenwen.vo.AiCohortPatientsVo(run.getId(),object("total",rows.size(),"items",items,"returnedCount",items.size(),"nextCursor",end<rows.size()?cursors.issue(run.getId(),doctor.getId(),end,run.getExpiresAtMs()):null,"hasMore",end<rows.size()),Collections.unmodifiableMap(meta));
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
