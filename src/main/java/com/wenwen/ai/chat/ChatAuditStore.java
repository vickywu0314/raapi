package com.wenwen.ai.chat;
import com.wenwen.mapper.AiChatAuditMapper;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
public final class ChatAuditStore {
    private final AiChatAuditMapper mapper;private final TransactionTemplate write;
    public ChatAuditStore(AiChatAuditMapper mapper,PlatformTransactionManager tx){this.mapper=mapper;write=new TransactionTemplate(tx);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    public void append(ChatAuditEvent event){write.execute(status->{if(mapper.insert(event)!=1)throw new IllegalStateException("问答审计未保存");return null;});}
}
