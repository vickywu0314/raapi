package com.wenwen.ai.result;
import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.TrustedDoctor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
/** 只接受本服务规范查询；不在会话保存患者结果、患者快照或原始JSON。 */
public final class AnalysisQueryCodec {
    private static final int MAX_QUERY_BYTES=128*1024;
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public String encode(CohortQuery query){try{String value=JSON.writeValueAsString(query.normalizedFilters());if(value.getBytes(StandardCharsets.UTF_8).length>MAX_QUERY_BYTES)throw AnalysisSettings.unavailable();return value;}catch(IOException e){throw AnalysisSettings.unavailable();}}
    public CohortQuery decode(AnalysisRun run){
        try {
            if(run==null||run.getPayloadVersion()!=2)throw expired("分析格式已更新，请重新分析");
            if(!AnalysisCursor.canonicalId(run.getId())||!AnalysisValues.SORT.equals(run.getSortKey())||run.getOwnerDoctorId()<=0||run.getExpiresAtMs()<=run.getCreatedAtMs()||run.getScopeFingerprint()==null||!run.getScopeFingerprint().matches("[0-9a-f]{64}")||run.getPolicyFingerprint()==null||!run.getPolicyFingerprint().matches("[0-9a-f]{64}"))throw AnalysisSettings.unavailable();
            if(run.getAsOfDate()==null||!LocalDate.parse(run.getAsOfDate()).toString().equals(run.getAsOfDate()))throw AnalysisSettings.unavailable();
            String value=run.getQueryJson();if(value==null||value.getBytes(StandardCharsets.UTF_8).length>MAX_QUERY_BYTES)throw AnalysisSettings.unavailable();
            JsonNode filters=JSON.readTree(value);if(filters==null||!filters.isObject())throw AnalysisSettings.unavailable();
            CohortQuery q=CohortQuery.read(new ByteArrayInputStream(("{\"filters\":"+value+"}").getBytes(StandardCharsets.UTF_8)),new TrustedDoctor(run.getOwnerDoctorId()));
            if(!encode(q).equals(value))throw AnalysisSettings.unavailable();return q;
        }catch(CohortException e){if(e.getStatus()==410)throw e;throw AnalysisSettings.unavailable();}catch(IOException|RuntimeException e){throw AnalysisSettings.unavailable();}
    }
    public static String policyFingerprint(Map<String,Object> policies){
        // 版本须为安全、受控标识；query hash不充当导出内容hash。
        for(Object value:policies.values())if(!(value instanceof String)||((String)value).isEmpty()||((String)value).length()>200||!((String)value).matches("[\\p{L}\\p{N}:._\\-]+"))throw AnalysisSettings.unavailable();
        try{return AnalysisValues.sha256(JSON.writeValueAsBytes(policies));}catch(IOException e){throw AnalysisSettings.unavailable();}
    }
    public static CohortException expired(String message){return new CohortException(410,"ANALYSIS_EXPIRED",message);}
}
