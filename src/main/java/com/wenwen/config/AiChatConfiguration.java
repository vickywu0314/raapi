package com.wenwen.config;
import com.wenwen.ai.chat.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration @Import(com.wenwen.controller.ra.AiChatController.class)
public class AiChatConfiguration {
    @Bean public ChatSettings chatSettings(Environment env){return new ChatSettings(env);}
    @Bean(destroyMethod="close") public ChatGateway chatGateway(ChatSettings settings){return new ChatGateway(settings);}
    @Bean public ChatAuditStore chatAuditStore(com.wenwen.mapper.AiChatAuditMapper mapper,org.springframework.transaction.PlatformTransactionManager tx){return new ChatAuditStore(mapper,tx);}
    @Bean public ChatService chatService(com.wenwen.ai.source.CohortSourceAdapter source,java.time.Clock clock,com.wenwen.ai.treatment.DrugDictionary dict,ChatSettings settings,ChatGateway gateway,ChatAuditStore audit){return new ChatService(source,clock,dict,settings,gateway,audit);}
}
