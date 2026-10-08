package com.wenwen.ai.parse;
import com.wenwen.mapper.AiParseAuditMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
@Component public final class ParseAuditStore {
    private final AiParseAuditMapper mapper;private final TransactionTemplate write;
    public ParseAuditStore(AiParseAuditMapper mapper,PlatformTransactionManager transactions){this.mapper=mapper;write=new TransactionTemplate(transactions);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    public void append(ParseAuditEvent event){write.execute(status->{if(mapper.insert(event)!=1)throw new IllegalStateException("审计未保存");return null;});}
}
