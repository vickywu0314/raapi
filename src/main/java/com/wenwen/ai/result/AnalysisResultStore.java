package com.wenwen.ai.result;
import com.wenwen.mapper.AiAnalysisRunMapper;
import com.wenwen.ai.analysis.CohortResultComputer;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.source.CohortSourceAdapter;
import com.wenwen.ai.treatment.DrugDictionary;
import java.time.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** analysisId解析为小查询会话；分析、分页、导出沿同一实时计算路径。 */
@Component public final class AnalysisResultStore {
    private final AiAnalysisRunMapper mapper;private final CohortResultComputer computer;
    private final TransactionTemplate write,read;
    public AnalysisResultStore(AiAnalysisRunMapper mapper,PlatformTransactionManager transactions,CohortSourceAdapter source,Clock clock,DrugDictionary dictionary,AnalysisSettings settings,AnalysisCursor cursors) {
        this.mapper=mapper;computer=new CohortResultComputer(source,clock,dictionary,settings,cursors);
        write=new TransactionTemplate(transactions);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read=new TransactionTemplate(transactions);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);read.setReadOnly(true);
    }
    public void create(AnalysisRun run) {new AnalysisQueryCodec().decode(run);write.execute(status->{mapper.deleteExpired(run.getCreatedAtMs());if(mapper.insert(run)!=1)throw new IllegalStateException("查询会话未保存");return null;});}
    public AnalysisRun find(String id){return read.execute(status->mapper.find(id));}
    public AnalysisRun require(long owner,String id,Instant now){
        AnalysisRun run=find(id);if(run==null)throw AnalysisQueryCodec.expired("分析引用已失效，请重新分析");
        if(run.getOwnerDoctorId()!=owner)throw new CohortException(404,"RESOURCE_NOT_FOUND","分析结果不存在");
        if(now.toEpochMilli()>=run.getExpiresAtMs())throw AnalysisQueryCodec.expired("分析结果已到期，请重新分析");
        new AnalysisQueryCodec().decode(run);return run;
    }
    public CohortResultComputer.Computed compute(TrustedDoctor doctor,CohortQuery query,String trace,Instant started,AnalysisRun prior,int offset,int limit,int maxRows){return computer.compute(doctor,query,trace,started,prior,offset,limit,maxRows);}
    public CohortResultComputer.Computed export(long owner,String id,Instant now,int maxRows){AnalysisRun run=require(owner,id,now);return compute(new TrustedDoctor(owner),new AnalysisQueryCodec().decode(run),java.util.UUID.randomUUID().toString(),now,run,0,Integer.MAX_VALUE,maxRows);}
}
