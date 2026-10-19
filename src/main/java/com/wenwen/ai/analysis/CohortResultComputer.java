package com.wenwen.ai.analysis;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.source.*;
import com.wenwen.ai.clinical.VisitMatcher;
import com.wenwen.util.Das28Util;
import com.wenwen.vo.AiCohortVo;
import java.time.*;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;
/** 请求私有的实时计算。全候选轻量派生/筛选/排序，只有本页或导出才投影来源明细。 */
public final class CohortResultComputer {
    private final CohortSourceAdapter source;private final Clock clock;private final com.wenwen.ai.treatment.DrugDictionary dictionary;private final com.wenwen.ai.result.AnalysisSettings settings;private final com.wenwen.ai.result.AnalysisCursor cursors;
    public CohortResultComputer(CohortSourceAdapter source,Clock clock,com.wenwen.ai.treatment.DrugDictionary dictionary,com.wenwen.ai.result.AnalysisSettings settings,com.wenwen.ai.result.AnalysisCursor cursors){this.source=source;this.clock=clock;this.dictionary=dictionary;this.settings=settings;this.cursors=cursors;}
    public static final class Computed {
        public final AiCohortVo result;public final List<Map<String,Object>> rows;public final CohortQuery query;public final com.wenwen.ai.result.AnalysisRun session;public final int aggregationPasses;
        Computed(AiCohortVo v,List<Map<String,Object>> r,CohortQuery q,com.wenwen.ai.result.AnalysisRun s){this(v,r,q,s,1);}
        Computed(AiCohortVo v,List<Map<String,Object>> r,CohortQuery q,com.wenwen.ai.result.AnalysisRun s,int passes){result=v;rows=Collections.unmodifiableList(r);query=q;session=s;aggregationPasses=passes;}
        public Map<String,Object> exportPayload(){return new com.wenwen.ai.result.AnalysisResultCodec().project(result,rows,query);}
    }
    public Computed compute(TrustedDoctor doctor, CohortQuery query, String traceId, Instant started, com.wenwen.ai.result.AnalysisRun prior, int offset, int limit, int maxRows) {
        if(prior==null) settings.validateAt(started.toEpochMilli());
        LocalDate asOf = prior==null?started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate():LocalDate.parse(prior.getAsOfDate());
        com.wenwen.ai.treatment.DrugDictionary.Snapshot drugView=dictionary.snapshot();
        SourceBatch batch = source.read(doctor.getId(),asOf);
        Map<String,Object> policies=batch.withFmSource(object("statistics",com.wenwen.ai.statistics.StatisticalPolicy.VERSION,"descriptive",DescriptiveAnalysis.VERSION,"qc",com.wenwen.ai.qc.MissingDataPolicy.VERSION,"crp","dev-crp-v04","now","dev-now-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04","treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary",drugView.version));
        String policyFingerprint=com.wenwen.ai.result.AnalysisValues.sha256((com.wenwen.ai.result.AnalysisQueryCodec.policyFingerprint(policies)+"\n"+batch.getClinicalRulesFingerprint()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String fingerprint=com.wenwen.ai.result.AnalysisValues.scope(doctor.getId(),batch.getPatientIds());
        if(prior!=null) {
            if(!fingerprint.equals(prior.getScopeFingerprint()))throw new com.wenwen.ai.query.CohortException(409,"SCOPE_CHANGED","分析范围已变更，请重新分析");
            if(!policyFingerprint.equals(prior.getPolicyFingerprint()))throw com.wenwen.ai.result.AnalysisQueryCodec.expired("分析规则已变更，请重新分析");
        }
        CohortComputation.Derivation derivation=CohortComputation.derive(batch,query.getAt(),asOf,drugView);
        CohortComputation.Selection cohortSelection=CohortComputation.select(derivation,query);
        if(maxRows>0 && cohortSelection.members.size()>maxRows)throw new com.wenwen.ai.query.CohortException(413,"RESULT_TOO_LARGE","导出行数超出限制");
        List<DerivedPatient> ordered=new ArrayList<>(cohortSelection.members);
        ordered.sort((a,b)->{java.math.BigDecimal x=a.evaluation==null?null:a.evaluation.getScore(),y=b.evaluation==null?null:b.evaluation.getScore();int c=x==null?(y==null?0:1):y==null?-1:y.compareTo(x);return c!=0?c:Long.compare(a.id,b.id);});
        boolean pageOnly=prior!=null && limit!=Integer.MAX_VALUE;
        long end=(long)offset+limit;
        Map<Long,List<ScoreVisit>> visitsByPatient=derivation.visitsByPatient;
        Map<Long,Set<String>> observedQuality=derivation.observedQuality;
        Map<Long,DerivedPatient> universe=derivation.universe;
        List<DerivedPatient> cohort=new ArrayList<>();
        String[] levels = {"remission","low","moderate","high"};
        Map<String,Integer> counts = new LinkedHashMap<>(), baseCounts=new LinkedHashMap<>();
        for (String level : levels) { counts.put(level,0); baseCounts.put(level,0); }
        List<Map<String,Object>> items = new ArrayList<>();
        int n = 0, eval = 0, baseN=0, unknownTxN=0;
        Integer effectiveIdsN = cohortSelection.effectiveIdsN;
        for (DerivedPatient derived : ordered) {
            long id=derived.id;
            com.wenwen.ai.qc.MissingDataStatus qc=derived.qc;
            com.wenwen.ai.clinical.PatientClinical clinical=derived.clinical;
            List<ScoreVisit> medicationVisits=visitsByPatient.getOrDefault(id,Collections.emptyList());
            com.wenwen.ai.treatment.TreatmentEpisode treatment=derived.treatment;
            ScoreVisit now=derived.now,six=derived.six,baseline=derived.baseline,score=derived.evaluation;
            String level = score == null ? null : Das28Util.activity(score.getScore());
            n++;
            if(!pageOnly) {cohort.add(derived);
            if("UNKNOWN".equals(treatment.state) || "CONFLICT".equals(treatment.state)) unknownTxN++;
            if (level != null) { counts.put(level,counts.get(level)+1); eval++; }
            if(baseline!=null) { String baseLevel=Das28Util.activity(baseline.getScore()); baseCounts.put(baseLevel,baseCounts.get(baseLevel)+1); baseN++; }
            }
            if(n>offset && n<=end) {
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
        if(pageOnly)for(int i=0;i<items.size();i++) {
            Map<String,Object> live=items.get(i),safe=new LinkedHashMap<>(com.wenwen.ai.result.AnalysisResultCodec.projectRow(live));
            safe.put("name",live.get("name"));safe.put("studyNo",live.get("studyNo"));items.set(i,Collections.unmodifiableMap(safe));
        }
        if(pageOnly) {
            Instant computed=clock.instant();Map<String,Object> empty=Collections.emptyMap();
            Map<String,Object> patients=object("total",n,"items",items,"returnedCount",items.size(),"nextCursor",end<n?cursors.issue(prior.getId(),doctor.getId(),(int)end,prior.getExpiresAtMs()):null,"hasMore",end<n);
            AiCohortVo page=new AiCohortVo(prior.getId(),n,batch.getPatientIds().size(),query.getIds()==null?null:query.getIds().size(),effectiveIdsN,empty,patients,metadata(query,asOf,started,batch,computed,prior.getExpiresAtMs(),policies,traceId),empty,empty,empty,empty,empty);
            return new Computed(page,items,query,prior,0);
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
        Instant computed=clock.instant();long created=prior==null?computed.toEpochMilli():prior.getCreatedAtMs(),expires=prior==null?Math.addExact(created,settings.ttlMillis()):prior.getExpiresAtMs();
        String analysisId=prior==null?UUID.randomUUID().toString():prior.getId();
        AiCohortVo result=new AiCohortVo(analysisId,n,batch.getPatientIds().size(),query.getIds() == null ? null : query.getIds().size(),effectiveIdsN,
            object("current",distribution,"base",baseDistribution,"baseN",baseN,"baseUnknownN",n-baseN,"evalN",eval,"unknownN",n-eval,"unknownTxN",unknownTxN),object("total",n,"items",items,"returnedCount",items.size(),"nextCursor",end<n?cursors.issue(analysisId,doctor.getId(),(int)end,expires):null,"hasMore",end<n),
            metadata(query,asOf,started,batch,computed,expires,policies,traceId), object("targetRate",target.get("value"),"ageMedian",age.get("value"),"durationMedian",duration.get("value"),"cohortRate",share.get("value"),"femaleRate",female.get("value"),"seroRate",sero.get("value"),"completeRate",complete.get("value"),"incompleteCount",completeness.get("incompleteCount"),"qcUnknownN",completeness.get("qcUnknownN"),"evaluable",eval),object("targetRate",target.get("meta"),"ageMedian",age.get("meta"),"durationMedian",duration.get("meta"),"cohortRate",share.get("meta"),"femaleRate",female.get("meta"),"seroRate",sero.get("meta"),"completeRate",complete.get("meta")),StatisticalAnalysis.byTreatment(cohort,universe.values()),DescriptiveAnalysis.fm(cohort),DescriptiveAnalysis.lines(cohort));
        com.wenwen.ai.result.AnalysisRun run=new com.wenwen.ai.result.AnalysisRun();
        run.setId(analysisId);run.setOwnerDoctorId(doctor.getId());run.setScopeFingerprint(com.wenwen.ai.result.AnalysisValues.scope(doctor.getId(),batch.getPatientIds()));run.setSortKey(com.wenwen.ai.result.AnalysisValues.SORT);run.setCreatedAtMs(created);run.setExpiresAtMs(expires);
        run.setPayloadVersion(2);run.setAsOfDate(asOf.toString());run.setQueryJson(new com.wenwen.ai.result.AnalysisQueryCodec().encode(query));run.setPolicyFingerprint(policyFingerprint);
        return new Computed(result,items,query,run);
    }
    private static Map<String,Object> metadata(CohortQuery query,LocalDate asOf,Instant started,SourceBatch batch,Instant computed,long expires,Map<String,Object> policies,String traceId) {
        return object("at",query.getAt(),"asOfDate",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),
                "computedAt",computed.toString(),"expiresAt",Instant.ofEpochMilli(expires).toString(),"patientProjection","LIVE_SOURCE_V04","policyVersions",policies,"traceId",traceId,
                "supportedFilters",Arrays.asList("studyCode","at","act","ids","sex","age","sero","cm","tx","data"),"completion","P03_RESULT","statisticalDisclosure","探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系");
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
