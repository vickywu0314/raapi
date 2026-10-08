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
    public static final class Content {public final Map<String,Object> payload;public final long expires;public final String hash,fingerprint;Content(Map<String,Object> p,long e,String h,String s){payload=p;expires=e;hash=h;fingerprint=s;}}
    public Content read(long owner,AiExportQuery query,Instant now){
        Content content;
        if("COHORT".equals(query.source)){AnalysisRun run=analyses.find(query.referenceId);if(run==null||run.getOwnerDoctorId()!=owner)throw missing();expiry(query.source,run.getExpiresAtMs(),now);content=new Content(new AnalysisResultCodec().decode(run),run.getExpiresAtMs(),run.getPayloadSha256(),run.getScopeFingerprint());}
        else {ComparisonRun run=comparisons.find(query.referenceId);if(run==null||run.getOwnerDoctorId()!=owner)throw missing();expiry(query.source,run.getExpiresAtMs(),now);content=new Content(new ComparisonResultCodec().decode(run),run.getExpiresAtMs(),run.getPayloadSha256(),run.getScopeFingerprint());}
        requireScope(owner,content);return content;
    }
    public void requireScope(long owner,Content c){try{scope.require(owner,c.fingerprint);}catch(CohortException e){if("SCOPE_CHANGED".equals(e.getCode()))throw new CohortException(409,"SCOPE_CHANGED","导出范围已变更，请重新分析");throw e;}}
    public static void expiry(String source,long expires,Instant now){if(now.toEpochMilli()>=expires)throw new CohortException(410,"COHORT".equals(source)?"ANALYSIS_EXPIRED":"COMPARE_EXPIRED","导出引用已到期，请重新分析");}
    private static CohortException missing(){return new CohortException(404,"RESOURCE_NOT_FOUND","导出引用不存在");}
}
