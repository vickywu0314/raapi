package com.wenwen.ai.export;
import com.wenwen.ai.result.*;
import com.wenwen.ai.query.CohortException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
/** 同一次保留run供内容、摘要及最后有效期核验消费。 */
@Component public final class ExportContentReader {
    private final AnalysisResultStore analyses;private final ComparisonResultStore comparisons;private final ComparisonScopeReader scope;
    public ExportContentReader(AnalysisResultStore a,ComparisonResultStore b,ComparisonScopeReader s){analyses=a;comparisons=b;scope=s;}
    @org.springframework.beans.factory.annotation.Value("${ra.export.xlsx.max-rows:100000}") private int maxRows=100000;
    @org.springframework.beans.factory.annotation.Value("${ra.export.xlsx.max-cells:1000000}") private int maxCells=1000000;
    public static final class Content {public final Map<String,Object> payload;public final long expires;public final String hash,fingerprint;Content(Map<String,Object> p,long e,String h,String s){payload=p;expires=e;hash=h;fingerprint=s;}}
    public Content read(long owner,AiExportQuery query,Instant now){
        Content content;
        if("COHORT".equals(query.source)){
            if(maxRows<=0||maxRows>1000000||maxCells<=0||maxCells>10000000)throw AnalysisSettings.unavailable();
            // 预留有界metadata/header预算，在全量来源行投影之前限制候选数。
            int permitted=Math.min(maxRows,(maxCells-256)/Math.max(1,query.requested.size())-1);
            if(permitted<=0)throw new CohortException(413,"RESULT_TOO_LARGE","导出单元格数超出限制");
            com.wenwen.ai.analysis.CohortResultComputer.Computed computed=analyses.export(owner,query.referenceId,now,permitted);
            Map<String,Object> payload=computed.exportPayload();
            content=new Content(payload,computed.session.getExpiresAtMs(),contentHash(payload),computed.session.getScopeFingerprint());
        }
        else {ComparisonRun run=comparisons.find(query.referenceId);if(run==null||run.getOwnerDoctorId()!=owner)throw missing();expiry(query.source,run.getExpiresAtMs(),now);content=new Content(new ComparisonResultCodec().decode(run),run.getExpiresAtMs(),run.getPayloadSha256(),run.getScopeFingerprint());}
        requireScope(owner,content);return content;
    }
    /** 流式摘要针对本次实际输出的安全投影内容，不复制巨型byte[]，也不使用查询hash。 */
    public static String contentHash(Map<String,Object> payload){
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(java.security.DigestOutputStream output=new java.security.DigestOutputStream(new java.io.OutputStream(){public void write(int b){}public void write(byte[] b,int off,int len){}},digest)) {
                new com.fasterxml.jackson.databind.ObjectMapper().writeValue(output,payload);
            }
            StringBuilder value=new StringBuilder();for(byte b:digest.digest())value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString();
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw AnalysisSettings.unavailable();}
    }
    public void requireScope(long owner,Content c){try{scope.require(owner,c.fingerprint);}catch(CohortException e){if("SCOPE_CHANGED".equals(e.getCode()))throw new CohortException(409,"SCOPE_CHANGED","导出范围已变更，请重新分析");throw e;}}
    public static void expiry(String source,long expires,Instant now){if(now.toEpochMilli()>=expires)throw new CohortException(410,"COHORT".equals(source)?"ANALYSIS_EXPIRED":"COMPARE_EXPIRED","导出引用已到期，请重新分析");}
    private static CohortException missing(){return new CohortException(404,"RESOURCE_NOT_FOUND","导出引用不存在");}
}
