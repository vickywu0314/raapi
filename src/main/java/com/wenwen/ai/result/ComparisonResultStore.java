package com.wenwen.ai.result;
import com.wenwen.mapper.AiComparisonRunMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
@Component public final class ComparisonResultStore {
    private final AiComparisonRunMapper mapper;
    private final TransactionTemplate write,read;
    public ComparisonResultStore(AiComparisonRunMapper mapper,PlatformTransactionManager transactions) {
        this.mapper=mapper;write=new TransactionTemplate(transactions);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read=new TransactionTemplate(transactions);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);read.setReadOnly(true);
    }
    public void create(ComparisonRun run) {write.execute(status->{mapper.deleteExpired(run.getCreatedAtMs());if(mapper.insert(run)!=1)throw new IllegalStateException("结果未保存");return null;});}
    public ComparisonRun find(String id){return read.execute(status->mapper.find(id));}
}
