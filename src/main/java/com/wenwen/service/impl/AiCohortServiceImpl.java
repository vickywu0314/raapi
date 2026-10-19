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
    public AiCohortVo analyze(TrustedDoctor doctor,CohortQuery query,String traceId) {
        Instant started=clock.instant();settings.validateAt(started.toEpochMilli());
        CohortResultComputer.Computed computed=results.compute(doctor,query,traceId,started,null,0,10,0);
        results.create(computed.session);return computed.result;
    }
    public com.wenwen.vo.AiCohortPatientsVo page(TrustedDoctor doctor,com.wenwen.ai.query.CohortPageQuery query,String traceId,Instant requestedAt) {
        com.wenwen.ai.result.AnalysisCursor.Token cursor=cursors.verify(query.getAnalysisId(),query.getCursor());
        if(cursor.owner!=doctor.getId())throw new com.wenwen.ai.query.CohortException(404,"RESOURCE_NOT_FOUND","分析结果不存在");
        if(requestedAt.toEpochMilli()>=cursor.expires)throw com.wenwen.ai.result.AnalysisQueryCodec.expired("分析结果已到期，请重新分析");
        com.wenwen.ai.result.AnalysisRun run=results.require(doctor.getId(),cursor.id,requestedAt);
        if(run.getExpiresAtMs()!=cursor.expires)throw com.wenwen.ai.result.AnalysisSettings.unavailable();
        CohortResultComputer.Computed computed=results.compute(doctor,new com.wenwen.ai.result.AnalysisQueryCodec().decode(run),traceId,requestedAt,run,cursor.offset,20,0);
        Map<String,Object> meta=new LinkedHashMap<>(computed.result.getMeta());meta.put("displayReadCompletedAt",computed.result.getMeta().get("readCompletedAt"));
        return new com.wenwen.vo.AiCohortPatientsVo(run.getId(),computed.result.getPatients(),Collections.unmodifiableMap(meta));
    }
}
