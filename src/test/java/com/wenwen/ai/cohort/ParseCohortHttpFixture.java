package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.scope.*;
import com.wenwen.ai.source.CohortSourceAdapter;
import com.wenwen.mapper.AiCohortSourceMapper;
import org.mybatis.spring.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import com.wenwen.config.AiCohortConfiguration;
import com.wenwen.controller.ra.AiCohortController;
import com.wenwen.service.impl.AiCohortServiceImpl;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;


abstract class ParseCohortHttpFixture extends DescriptiveHttpFixture {
    ParseAuditObservedDataSource auditObserved;
    @Configuration @EnableWebMvc
    @Import({AiCohortConfiguration.class, AiCohortController.class, AiCohortServiceImpl.class, CohortSourceAdapter.class, com.wenwen.ai.result.AnalysisResultStore.class,com.wenwen.ai.result.AnalysisPageReader.class, com.wenwen.controller.ra.AiParseController.class, com.wenwen.service.impl.AiParseServiceImpl.class, com.wenwen.ai.parse.ParseAuditStore.class})
    static class ParseInfrastructure {
        @Bean public DriverManagerDataSource dataSource() {
            String url = System.getenv("RA_TEST_MYSQL_URL"), user = System.getenv("RA_TEST_MYSQL_USER"), password = System.getenv("RA_TEST_MYSQL_PASSWORD");
            guard(url, user, password);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver"); return ds;
        }
        @Bean public ObservedDataSource observedDataSource(DriverManagerDataSource raw) { return new ObservedDataSource(raw); }
        @Bean @Primary public ParseAuditObservedDataSource auditObserved(ObservedDataSource observed){return new ParseAuditObservedDataSource(observed);}
        @Bean public com.wenwen.mapper.AiParseAuditMapper auditMapper(org.apache.ibatis.session.SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiParseAuditMapper.class);}
        @Bean public PlatformTransactionManager transactions(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.Resource[] {new ClassPathResource("mybatis/AiParseAuditMapper.xml"),new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/PatientMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml")});
            return factory.getObject();
        }
        @Bean public AiCohortSourceMapper sourceMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(AiCohortSourceMapper.class);
        }
        @Bean public com.wenwen.mapper.AiAnalysisRunMapper runMapper(org.apache.ibatis.session.SqlSessionFactory factory) { return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiAnalysisRunMapper.class); }
        @Bean @Primary public MutableClock testClock() { return new MutableClock(); }
    }
    void buildContext(boolean trusted) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        if(syntheticCursorKey!=null)context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-analysis",Collections.singletonMap("ra.ai.analysis.cursor-key-base64",syntheticCursorKey)));
        context.register(ParseInfrastructure.class);
        if (trusted) context.register(TrustedIdentity.class);
        beforeRefresh();
        context.refresh();
        bodyRead = new CountingBodyFilter();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        raw = context.getBean(DriverManagerDataSource.class);
        clock = context.getBean(MutableClock.class);
        observed = context.getBean(ObservedDataSource.class);
    }
    @BeforeEach void setupParse() throws Exception {
        try(InputStream in=getClass().getResourceAsStream("/sql/20261008_ai_parse_audit.sql")) {sql(new Scanner(in, "UTF-8").useDelimiter("\\A").next().trim());ownedTables.add("ra_ai_parse_audit");}
        sixPatients();auditObserved=context.getBean(ParseAuditObservedDataSource.class);
    }
}
