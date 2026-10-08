package com.wenwen.ai.cohort;
import com.wenwen.mapper.PrivateCohortMapper;
import com.wenwen.ai.query.PrivateCohortQuery;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** 私有定义的短事务owner。 */
@Component public final class DefinitionStore {
    private final PrivateCohortMapper mapper;
    private final Clock clock;
    private final TransactionTemplate write,read;
    public DefinitionStore(PrivateCohortMapper mapper,PlatformTransactionManager transactions,Clock clock){
        this.mapper=mapper;
        this.clock=clock;
        write=new TransactionTemplate(transactions);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read=new TransactionTemplate(transactions);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);
    }
    public java.util.Map<String,Object> list(long owner,long page,int size){
        org.springframework.transaction.support.TransactionCallback<java.util.Map<String,Object>> view=status->{
            long total=mapper.count(owner);
            java.util.List<java.util.Map<String,Object>> items=new java.util.ArrayList<>();
            for(PrivateCohortDefinition d:mapper.list(owner,(page-1)*size,size))items.add(item(d));
            return com.wenwen.vo.AiCohortVo.object("items",items,"total",total,"page",page,"size",size);
        };
        return read.execute(view);
    }
    public static java.util.Map<String,Object> item(PrivateCohortDefinition d){
        return com.wenwen.vo.AiCohortVo.object("cohortId",d.getId(),"name",d.getName(),"note",d.getNote(),"filters",PrivateCohortCodec.decode(d).normalizedFilters(),"version",d.getVersion(),"createdAt",java.time.Instant.ofEpochMilli(d.getCreatedAtMs()).toString(),"updatedAt",java.time.Instant.ofEpochMilli(d.getUpdatedAtMs()).toString(),"currentN",null);
    }
    public PrivateCohortDefinition open(long owner,String id){
        return read.execute(status->{
            PrivateCohortDefinition d=mapper.find(owner,id);
            if(d==null||d.getDeletedAtMs()!=null)throw new com.wenwen.ai.query.CohortException(404,"RESOURCE_NOT_FOUND","队列不存在");
            PrivateCohortCodec.decode(d);
            return d;
        });
    }
    public PrivateCohortReceipt rename(long owner,PrivateCohortQuery q,String trace){
        return executeWrite(owner,q,status->{
            PrivateCohortDefinition d=mapper.find(owner,q.cohortId);
            if(d==null||d.getDeletedAtMs()!=null)throw new com.wenwen.ai.query.CohortException(404,"RESOURCE_NOT_FOUND","队列不存在");
            PrivateCohortCodec.decode(d);
            if(d.getVersion()!=q.version)throw conflict("VERSION_CONFLICT");
            if(d.getVersion()==Long.MAX_VALUE)throw new IllegalStateException("版本溢出");
            if(q.namePresent)d.setName(q.name);
            if(q.notePresent)d.setNote(q.note);
            d.setVersion(d.getVersion()+1);
            d.setUpdatedAtMs(clock.instant().toEpochMilli());
            if(mapper.rename(d,q.version)!=1)throw conflict("VERSION_CONFLICT");
            PrivateCohortReceipt r=receipt(owner,q,trace,d.getId(),d.getVersion(),d.getUpdatedAtMs());
            if(mapper.insertReceipt(r)!=1)throw new IllegalStateException("回执未保存");
            return r;
        });
    }
    public PrivateCohortReceipt delete(long owner,PrivateCohortQuery q,String trace){
        return executeWrite(owner,q,status->{
            PrivateCohortDefinition d=mapper.findForUpdate(owner,q.cohortId);
            if(d==null)throw new com.wenwen.ai.query.CohortException(404,"RESOURCE_NOT_FOUND","队列不存在");
            PrivateCohortCodec.decode(d);
            long now=clock.instant().toEpochMilli();
            if(d.getDeletedAtMs()==null){
                if(d.getVersion()!=q.version)throw conflict("VERSION_CONFLICT");
                if(d.getVersion()==Long.MAX_VALUE)throw new IllegalStateException("版本溢出");
                d.setVersion(d.getVersion()+1);
                d.setUpdatedAtMs(now);
                d.setDeletedAtMs(now);
                if(mapper.delete(d,q.version)!=1)throw conflict("VERSION_CONFLICT");
            }PrivateCohortReceipt r=receipt(owner,q,trace,d.getId(),d.getVersion(),now);
            if(mapper.insertReceipt(r)!=1)throw new IllegalStateException("回执未保存");
            return r;
        });
    }
    private PrivateCohortReceipt executeWrite(long owner,PrivateCohortQuery q,org.springframework.transaction.support.TransactionCallback<PrivateCohortReceipt> callback){
        String hash=PrivateCohortCodec.commandHash(q);
        org.springframework.transaction.support.TransactionCallback<PrivateCohortReceipt> command=status->{
            PrivateCohortReceipt existing=mapper.findReceipt(owner,q.action,q.requestId);
            return replay(existing==null?callback.doInTransaction(status):existing,hash);
        };
        try{
            return write.execute(command);
        }
        catch(org.springframework.dao.DuplicateKeyException e){
            PrivateCohortReceipt winner=read.execute(status->mapper.findReceipt(owner,q.action,q.requestId));
            if(winner!=null)return replay(winner,hash);
            for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException&&((java.sql.SQLException)cause).getErrorCode()==1062&&cause.getMessage().contains("private_active_name"))throw conflict("NAME_CONFLICT");
            throw e;
        }
        catch(com.wenwen.ai.query.CohortException e){
            if(!"VERSION_CONFLICT".equals(e.getCode())&&!"NAME_CONFLICT".equals(e.getCode()))throw e;
            PrivateCohortReceipt winner=read.execute(status->mapper.findReceipt(owner,q.action,q.requestId));
            if(winner!=null)return replay(winner,hash);
            throw e;
        }
    }
    private static PrivateCohortReceipt replay(PrivateCohortReceipt r,String hash){
        if(r.getProtocolVersion()!=1||r.getResultVersion()<1||("SAVE".equals(r.getAction())&&r.getResultVersion()!=1)||(("RENAME".equals(r.getAction())||"DELETE".equals(r.getAction()))&&r.getResultVersion()<2)||r.getOwnerDoctorId()<1||!validUuid(r.getCohortId())||!validUuid(r.getTraceId())||!validUuid(r.getRequestId())||r.getRequestSha256()==null||!r.getRequestSha256().matches("[0-9a-f]{64}")||!java.util.Arrays.asList("SAVE","RENAME","DELETE").contains(r.getAction()))throw new com.wenwen.ai.query.CohortException(503,"SERVICE_UNAVAILABLE","回执暂不可用");
        if(!hash.equals(r.getRequestSha256()))throw conflict("IDEMPOTENCY_CONFLICT");
        return r;
    }
    private static boolean validUuid(String s){
        return s!=null&&s.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")&&java.util.UUID.fromString(s).toString().equals(s);
    }
    private static com.wenwen.ai.query.CohortException conflict(String code){
        return new com.wenwen.ai.query.CohortException(409,code,"队列命令冲突");
    }
    private static PrivateCohortReceipt receipt(long owner,PrivateCohortQuery q,String trace,String id,long version,long now){
        PrivateCohortReceipt r=new PrivateCohortReceipt();
        r.setOwnerDoctorId(owner);
        r.setAction(q.action);
        r.setRequestId(q.requestId);
        r.setProtocolVersion(1);
        r.setCohortId(id);
        r.setResultVersion(version);
        r.setRequestSha256(PrivateCohortCodec.commandHash(q));
        r.setTraceId(trace);
        r.setCreatedAtMs(now);
        return r;
    }
    public PrivateCohortReceipt save(long owner,PrivateCohortQuery q,String trace){
        return executeWrite(owner,q,status->{
            long now=clock.instant().toEpochMilli();
            PrivateCohortDefinition d=new PrivateCohortDefinition();
            d.setId(UUID.randomUUID().toString());
            d.setOwnerDoctorId(owner);
            d.setStudyCode("RA");
            d.setName(q.name);
            d.setNote(q.note);
            d.setVersion(1);
            d.setFiltersVersion(1);
            d.setFiltersUtf8(PrivateCohortCodec.encode(q.filters.normalizedFilters()));
            d.setFiltersSha256(PrivateCohortCodec.sha(d.getFiltersUtf8()));
            d.setCreatedAtMs(now);
            d.setUpdatedAtMs(now);
            if(mapper.insertDefinition(d)!=1)throw new IllegalStateException("定义未保存");
            PrivateCohortReceipt r=receipt(owner,q,trace,d.getId(),1,now);
            if(mapper.insertReceipt(r)!=1)throw new IllegalStateException("回执未保存");
            return r;
        });
    }
}
