package com.wenwen.config;

import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.scope.PrincipalProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCohortConfiguration {
    @Bean public com.wenwen.ai.result.AnalysisSettings cohortAnalysisSettings(
        @org.springframework.beans.factory.annotation.Value("${ra.ai.analysis.result-ttl-seconds:900}") String ttl,
        @org.springframework.beans.factory.annotation.Value("${ra.ai.analysis.result-max-bytes:8388608}") String max,
        @org.springframework.beans.factory.annotation.Value("${ra.ai.analysis.cursor-key-base64:}") String key){return new com.wenwen.ai.result.AnalysisSettings(ttl,max,key);}
    @Bean public com.wenwen.ai.result.AnalysisCursor cohortAnalysisCursor(com.wenwen.ai.result.AnalysisSettings settings){return new com.wenwen.ai.result.AnalysisCursor(settings);}
    @Bean
    @ConditionalOnMissingBean(com.wenwen.ai.treatment.DrugDictionary.class)
    public com.wenwen.ai.treatment.DrugDictionary cohortDrugDictionary() { return new com.wenwen.ai.treatment.DevelopmentDrugDictionary(); }
    @Bean
    @ConditionalOnMissingBean(PrincipalProvider.class)
    public PrincipalProvider cohortPrincipalProvider() {
        return () -> { throw new CohortException(401, "UNAUTHENTICATED", "请先登录"); };
    }
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock cohortClock() { return Clock.systemUTC(); }
}
