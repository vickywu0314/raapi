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
/** 独立装配，旧测试继续使用旧fixture。 */
abstract class PrivateCohortHttpFixture extends CohortHttpFixture {
    PrivateCohortObservedDataSource privateObserved;
    @Configuration @EnableWebMvc
    @Import({
        AiCohortConfiguration.class, AiCohortController.class, AiCohortServiceImpl.class, CohortSourceAdapter.class, com.wenwen.ai.result.AnalysisResultStore.class,com.wenwen.ai.result.AnalysisPageReader.class, com.wenwen.controller.ra.PrivateCohortController.class, com.wenwen.service.impl.PrivateCohortServiceImpl.class, com.wenwen.ai.cohort.DefinitionStore.class
    })
    static class PrivateInfrastructure {
        @Bean public DriverManagerDataSource dataSource() {
            String url = System.getenv("RA_TEST_MYSQL_URL"), user = System.getenv("RA_TEST_MYSQL_USER"), password = System.getenv("RA_TEST_MYSQL_PASSWORD");
            guard(url, user, password);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return ds;
        }
        @Bean public ObservedDataSource observedDataSource(DriverManagerDataSource raw) {
            return new ObservedDataSource(raw);
        }
        @Bean @Primary public PrivateCohortObservedDataSource privateObserved(ObservedDataSource observed){
            return new PrivateCohortObservedDataSource(observed);
        }
        @Bean public PlatformTransactionManager transactions(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.Resource[] {
                new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/PatientMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/PrivateCohortMapper.xml")
            });
            return factory.getObject();
        }
        @Bean public AiCohortSourceMapper sourceMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(AiCohortSourceMapper.class);
        }
        @Bean public com.wenwen.mapper.AiAnalysisRunMapper runMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiAnalysisRunMapper.class);
        }
        @Bean public com.wenwen.mapper.PrivateCohortMapper privateMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.PrivateCohortMapper.class);
        }
        @Bean @Primary public MutableClock testClock() {
            return new MutableClock();
        }
    }
    void buildContext(boolean trusted) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        if(syntheticCursorKey!=null)context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-analysis",Collections.singletonMap("ra.ai.analysis.cursor-key-base64",syntheticCursorKey)));
        context.register(PrivateInfrastructure.class);
        if (trusted) context.register(TrustedIdentity.class);
        beforeRefresh();
        context.refresh();
        bodyRead = new CountingBodyFilter();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        raw = context.getBean(DriverManagerDataSource.class);
        clock = context.getBean(MutableClock.class);
        observed = context.getBean(ObservedDataSource.class);
        privateObserved=context.getBean(PrivateCohortObservedDataSource.class);
    }
    @Override @BeforeEach void setup() throws Exception {
        super.setup();
        sql("UPDATE patient_basic_info SET name='synthetic-private-name-CANARY',card_no='110101198610070011' WHERE id=1");
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":2.295},\"privateCaseCanary\":\"synthetic-private-case-CANARY\"}' WHERE id=11");
        try(Connection c=raw.getConnection();
        Statement s=c.createStatement();
        InputStream in=getClass().getResourceAsStream("/sql/20261008_private_cohort_definition.sql")) {
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            for(String sql:ddl.split(";"))if(!sql.trim().isEmpty())s.execute(sql);
            ownedTables.add("ra_private_cohort_definition");
            ownedTables.add("ra_private_cohort_command");
        }
    }
}
