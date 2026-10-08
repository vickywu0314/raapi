package com.wenwen.ai.export;
import com.wenwen.mapper.AiExportAuditMapper;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** 每个真实事实一次REQUIRES_NEW提交，不跨POI/网络持连接。 */
@Component public final class ExportAuditStore {
    private final AiExportAuditMapper mapper;private final TransactionTemplate transaction;
    public ExportAuditStore(AiExportAuditMapper m,PlatformTransactionManager t){mapper=m;transaction=new TransactionTemplate(t);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    private void change(java.util.function.IntSupplier action){transaction.execute(status->{if(action.getAsInt()!=1)throw new IllegalStateException("导出审计未保存");return null;});}
    public void insertPreparing(ExportAuditRecord record){change(()->mapper.insert(record));}
    public void insertNeedApply(ExportAuditRecord record){change(()->mapper.insert(record));}
    public void markGenerationFailed(String id,long owner,long now,String code){if(!Arrays.asList("RESULT_TOO_LARGE","SERVICE_UNAVAILABLE","CANCELLED").contains(code))throw new IllegalArgumentException("生成原因无效");Map<String,Object> e=event(id,owner,now);e.put("errorCode",code);change(()->mapper.markGenerationFailed(e));}
    public void markReady(String id,long owner,long now,int rows,int bytes){Map<String,Object> e=event(id,owner,now);e.put("fileRows",rows);e.put("fileBytes",bytes);change(()->mapper.markReady(e));}
    public void startTransfer(String id,long owner,long now){change(()->mapper.startTransfer(event(id,owner,now)));}
    public void rejectTransfer(String id,long owner,long now,String error){if(!Arrays.asList("SCOPE_CHANGED","ANALYSIS_EXPIRED","COMPARE_EXPIRED","SERVICE_UNAVAILABLE").contains(error))throw new IllegalArgumentException("拒绝原因无效");Map<String,Object> e=event(id,owner,now);e.put("errorCode",error);change(()->mapper.rejectTransfer(e));}
    public void finishWriteFailed(String id,long owner,long now){change(()->mapper.finishWriteFailed(event(id,owner,now)));}
    public void finishWritten(String id,long owner,long now){change(()->mapper.finishWritten(event(id,owner,now)));}
    private static Map<String,Object> event(String id,long owner,long now){Map<String,Object> e=new HashMap<>();e.put("id",id);e.put("actorDoctorId",owner);e.put("now",now);return e;}
}
