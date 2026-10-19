package com.wenwen.service.impl;
import com.wenwen.ai.analysis.*;
import com.wenwen.ai.query.*;
import com.wenwen.ai.result.*;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.source.*;
import com.wenwen.ai.treatment.DrugDictionary;
import com.wenwen.service.ComparisonService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.wenwen.vo.AiCohortVo.object;
@Service public final class ComparisonServiceImpl implements ComparisonService {
    private final CohortSourceAdapter source;
    private final Clock clock;
    private final DrugDictionary dictionary;
    private final AnalysisSettings settings;
    private final ComparisonResultStore results;
    private final com.wenwen.ai.cohort.DefinitionStore definitions;
    private final ComparisonScopeReader scopes;
    @org.springframework.beans.factory.annotation.Value("${ra.ai.comparison.result-max-bytes:8388608}") private String comparisonMaxBytes="8388608";
    private int resultLimit(){try{int n=Integer.parseInt(comparisonMaxBytes);if(n<=0||n>16777215)throw new IllegalArgumentException();return n;}catch(RuntimeException e){throw AnalysisSettings.unavailable();}}
    private final ComparisonResultCodec codec=new ComparisonResultCodec();
    public ComparisonServiceImpl(CohortSourceAdapter source,Clock clock,DrugDictionary dictionary,AnalysisSettings settings,ComparisonResultStore results,com.wenwen.ai.cohort.DefinitionStore definitions,ComparisonScopeReader scopes){this.source=source;this.clock=clock;this.dictionary=dictionary;this.settings=settings;this.results=results;this.definitions=definitions;this.scopes=scopes;}
    public Map<String,Object> compare(TrustedDoctor doctor,ComparisonQuery query,String traceId){
        int resultMax=resultLimit();Instant started=clock.instant();ResolvedSide aSide=resolve(doctor,query.a),bSide=resolve(doctor,query.b);if(!aSide.filters.getAt().equals(bSide.filters.getAt()))throw new CohortException(400,"INVALID_REQUEST","比较时点须相同");settings.validateAt(started.toEpochMilli());LocalDate asOf=started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();DrugDictionary.Snapshot drugView=dictionary.snapshot();SourceBatch batch=source.read(doctor.getId(),asOf);
        CohortComputation.Derivation derivation=CohortComputation.derive(batch,aSide.filters.getAt(),asOf,drugView);CohortComputation.Selection a=CohortComputation.select(derivation,aSide.filters),b=CohortComputation.select(derivation,bSide.filters);
        Set<Long> intersection=new HashSet<>();for(DerivedPatient p:a.members)intersection.add(p.id);Set<Long> right=new HashSet<>();for(DerivedPatient p:b.members)right.add(p.id);intersection.retainAll(right);
        List<DerivedPatient> left=new ArrayList<>(a.members),rightMembers=new ArrayList<>(b.members);if(query.excludeOverlap){left.removeIf(p->intersection.contains(p.id));rightMembers.removeIf(p->intersection.contains(p.id));}
        List<Map<String,Object>> rows=ComparisonAnalysis.rows(left,rightMembers,intersection.size(),query.excludeOverlap);
        Map<String,Object> coverageA=ComparisonAnalysis.coverage(left),coverageB=ComparisonAnalysis.coverage(rightMembers);
        Instant computed=clock.instant();long created=computed.toEpochMilli(),expires=Math.addExact(created,settings.ttlMillis());String id=UUID.randomUUID().toString();
        Map<String,Object> payload=object("studyTotal",derivation.universe.size(),"a",side(aSide,a,left),"b",side(bSide,b,rightMembers),"overlapN",intersection.size(),"excludeOverlap",query.excludeOverlap,"rows",rows,"meta",object("at",aSide.filters.getAt(),"asOfDate",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),"computedAt",computed.toString(),"expiresAt",Instant.ofEpochMilli(expires).toString(),"traceId",traceId,"policyVersions",batch.withFmSource(object("statistics",com.wenwen.ai.statistics.StatisticalPolicy.VERSION,"descriptive",DescriptiveAnalysis.VERSION,"qc",com.wenwen.ai.qc.MissingDataPolicy.VERSION,"crp","dev-crp-v04","now","dev-now-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04","treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary",drugView.version,"comparison",ComparisonAnalysis.VERSION)),"sourceDateCoverage",object("a",coverageA,"b",coverageB),"completion","P04_COMPARE","statisticalDisclosure","探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系"));
        byte[] bytes=codec.encode(payload);if(bytes.length>resultMax)throw new CohortException(413,"RESULT_TOO_LARGE","比较结果超出保留容量");ComparisonRun run=new ComparisonRun();run.setId(id);run.setOwnerDoctorId(doctor.getId());run.setScopeFingerprint(AnalysisValues.scope(doctor.getId(),batch.getPatientIds()));run.setPayloadVersion(1);run.setCreatedAtMs(created);run.setExpiresAtMs(expires);run.setPayload(bytes);run.setPayloadSha256(AnalysisValues.sha256(bytes));results.create(run);
        Map<String,Object> response=new LinkedHashMap<>();response.put("compareResultId",id);response.putAll(payload);return response;
    }
    @SuppressWarnings("unchecked") public Map<String,Object> read(TrustedDoctor doctor,String id,String traceId,Instant requestedAt){
        ComparisonRun run=results.find(id);if(run==null||run.getOwnerDoctorId()!=doctor.getId())throw new CohortException(404,"RESOURCE_NOT_FOUND","比较结果不存在");
        if(requestedAt.toEpochMilli()>=run.getExpiresAtMs())throw new CohortException(410,"COMPARE_EXPIRED","比较结果已到期");
        Map<String,Object> payload=codec.decode(run);scopes.require(doctor.getId(),run.getScopeFingerprint());
        Map<String,Object> original=(Map<String,Object>)payload.get("meta"),meta=new LinkedHashMap<>(original);meta.put("comparisonTraceId",original.get("traceId"));meta.put("traceId",traceId);
        Map<String,Object> response=new LinkedHashMap<>();response.put("compareResultId",run.getId());response.putAll(payload);response.put("meta",meta);return response;
    }
    private static final class ResolvedSide {
        final String source,cohortId;final Long version;final CohortQuery filters;
        ResolvedSide(String source,String id,Long version,CohortQuery filters){this.source=source;cohortId=id;this.version=version;this.filters=filters;}
    }
    private ResolvedSide resolve(TrustedDoctor doctor,ComparisonQuery.Side side){
        if("FILTERS".equals(side.source))return new ResolvedSide(side.source,null,null,side.filters);
        com.wenwen.ai.cohort.PrivateCohortDefinition d=definitions.open(doctor.getId(),side.cohortId);
        return new ResolvedSide("SAVED",d.getId(),d.getVersion(),com.wenwen.ai.cohort.PrivateCohortCodec.decode(d));
    }
    private static Map<String,Object> side(ResolvedSide side,CohortComputation.Selection selection,List<DerivedPatient> members){return object("source",side.source,"filters",side.filters.normalizedFilters(),"cohortId",side.cohortId,"cohortVersion",side.version,"nBeforeExclusion",selection.members.size(),"n",members.size(),"evalN",evalN(members),"submittedUniqueIdsN",selection.submittedUniqueIdsN,"effectiveIdsN",selection.effectiveIdsN);}
    private static int evalN(List<DerivedPatient> patients){int n=0;for(DerivedPatient p:patients)if(p.evaluation!=null)n++;return n;}
}
