package com.wenwen.config;
import com.wenwen.ai.parse.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration
public class AiParseGatewayConfiguration {
    @Bean public AiGatewaySettings aiGatewaySettings(Environment environment){return new AiGatewaySettings(environment);}
    @Bean(destroyMethod="close") public AiParseGateway aiParseGateway(AiGatewaySettings settings){return new AiParseGateway(settings);}
}
