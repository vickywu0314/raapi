package com.wenwen.config;

import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.scope.PrincipalProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCohortConfiguration {
    @Bean
    @ConditionalOnMissingBean(PrincipalProvider.class)
    public PrincipalProvider cohortPrincipalProvider() {
        return () -> { throw new CohortException(401, "UNAUTHENTICATED", "请先登录"); };
    }
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock cohortClock() { return Clock.systemUTC(); }
}
