package com.wenwen.ai.cohort;

import java.math.BigDecimal;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.mybatis.spring.*;
import org.springframework.core.io.ClassPathResource;
import javax.sql.DataSource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.wenwen.config.AiCohortConfiguration;
import com.wenwen.controller.ra.AiCohortController;
import com.wenwen.service.impl.AiCohortServiceImpl;
import com.wenwen.ai.source.CohortSourceAdapter;
import com.wenwen.mapper.AiCohortSourceMapper;
import java.util.*;
import java.sql.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 导出独立装配；全部源患者均为本测试合成数据。 */
abstract class ExportApplicationHttpFixture extends AiExportHttpFixture {
    ExportApplicationObservedDataSource applicationObserved;
    @Configuration @EnableWebMvc
    @Import({
        AiCohortConfiguration.class, AiCohortController.class, AiCohortServiceImpl.class, CohortSourceAdapter.class, com.wenwen.ai.result.AnalysisResultStore.class,com.wenwen.ai.result.AnalysisPageReader.class, com.wenwen.controller.ra.PrivateCohortController.class, com.wenwen.service.impl.PrivateCohortServiceImpl.class, com.wenwen.ai.cohort.DefinitionStore.class,com.wenwen.controller.ra.ComparisonController.class,com.wenwen.service.impl.ComparisonServiceImpl.class,com.wenwen.ai.result.ComparisonResultStore.class,com.wenwen.ai.result.ComparisonScopeReader.class, com.wenwen.controller.ra.AiExportController.class, com.wenwen.service.impl.AiExportServiceImpl.class, com.wenwen.ai.export.ExportContentReader.class, com.wenwen.ai.export.ExportFieldPolicy.class, com.wenwen.ai.export.OrdinaryXlsxWriter.class, com.wenwen.ai.export.ExportAuditStore.class, com.wenwen.service.impl.ExportServiceImpl.class, com.wenwen.controller.ra.ExportController.class, com.wenwen.controller.ra.AiExportApplicationController.class,com.wenwen.service.impl.AiExportApplicationServiceImpl.class,com.wenwen.ai.export.ExportApplicationStore.class,com.wenwen.config.ExportNotificationConfiguration.class
    })
    static class ApplicationInfrastructure {
        @Bean public com.wenwen.mapper.ExportMapper legacyExportMapper(org.apache.ibatis.session.SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.ExportMapper.class);}
        @Bean public com.wenwen.mapper.VisitMapper legacyVisitMapper(){return unused(com.wenwen.mapper.VisitMapper.class);}
        @Bean public com.wenwen.service.PatientService legacyPatientService(){return unused(com.wenwen.service.PatientService.class);}
        private static <T> T unused(Class<T> type){return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{if(method.getDeclaringClass()==Object.class){if("toString".equals(method.getName()))return "synthetic-unused-legacy-dependency";if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);if("equals".equals(method.getName()))return proxy==args[0];}throw new AssertionError("新xlsx不得调用旧业务依赖："+method.getName());}));}
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
        @Bean public PrivateCohortObservedDataSource privateObserved(ObservedDataSource observed){
            return new PrivateCohortObservedDataSource(observed);
        }
        @Bean public ComparisonObservedDataSource comparisonObserved(PrivateCohortObservedDataSource priv){return new ComparisonObservedDataSource(priv);}
        @Bean public ExportObservedDataSource exportObserved(ComparisonObservedDataSource comparison){return new ExportObservedDataSource(comparison);}
        @Bean @Primary public ExportApplicationObservedDataSource applicationObserved(ExportObservedDataSource d){return new ExportApplicationObservedDataSource(d);}
        @Bean public com.wenwen.mapper.AiExportApplicationMapper applicationMapper(org.apache.ibatis.session.SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(com.wenwen.mapper.AiExportApplicationMapper.class);}
        @Bean public com.wenwen.mapper.AiExportAuditMapper exportMapper(org.apache.ibatis.session.SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiExportAuditMapper.class);}
        @Bean @Primary public ObservedWriter observedWriter(){return new ObservedWriter();}
        @Bean public PlatformTransactionManager transactions(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.Resource[] {
                new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/PatientMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/PrivateCohortMapper.xml"),new ClassPathResource("mybatis/AiComparisonRunMapper.xml"),new ClassPathResource("mybatis/AiExportAuditMapper.xml"),new ClassPathResource("mybatis/ExportMapper.xml"),new ClassPathResource("mybatis/AiExportApplicationMapper.xml")
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
        @Bean public com.wenwen.mapper.AiComparisonRunMapper comparisonMapper(org.apache.ibatis.session.SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiComparisonRunMapper.class);}
        @Bean @Primary public CountingDictionary comparisonDictionary(){return new CountingDictionary();}
        @Bean @Primary public MutableClock testClock() {
            return new MutableClock();
        }
    }
    void buildContext(boolean trusted) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        if(syntheticCursorKey!=null)context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-analysis",Collections.singletonMap("ra.ai.analysis.cursor-key-base64",syntheticCursorKey)));
        context.register(ApplicationInfrastructure.class);
        if (trusted) context.register(TrustedIdentity.class);
        beforeRefresh();
        context.refresh();
        bodyRead = new CountingBodyFilter();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        raw = context.getBean(DriverManagerDataSource.class);
        clock = context.getBean(MutableClock.class);
        observed = context.getBean(ObservedDataSource.class);
        privateObserved=context.getBean(PrivateCohortObservedDataSource.class);comparisonObserved=context.getBean(ComparisonObservedDataSource.class);exportObserved=context.getBean(ExportObservedDataSource.class);applicationObserved=context.getBean(ExportApplicationObservedDataSource.class);
    }
    @Override @org.junit.jupiter.api.BeforeEach void setup()throws Exception {super.setup();for(String file:Arrays.asList("20261006_export_application.sql","20261008_ai_export_request.sql")){try(Connection c=raw.getConnection();Statement st=c.createStatement();java.io.InputStream in=getClass().getResourceAsStream("/sql/"+file)){String ddl=new Scanner(in,"UTF-8").useDelimiter("\\A").next();for(String statement:ddl.split(";"))if(!statement.trim().isEmpty())st.execute(statement);}}ownedTables.add("export_application");ownedTables.add("ra_ai_export_request");}
    byte[] analysisPayload(String id)throws Exception{try(Connection c=raw.getConnection();PreparedStatement q=c.prepareStatement("SELECT query_json FROM ra_ai_analysis_query_session WHERE id=?")){q.setString(1,id);try(ResultSet r=q.executeQuery()){org.junit.jupiter.api.Assertions.assertTrue(r.next());return r.getBytes(1);}}}
    @Override void exited(){super.exited();org.junit.jupiter.api.Assertions.assertEquals(0,applicationObserved.active.get());org.junit.jupiter.api.Assertions.assertEquals(applicationObserved.borrowed.get(),applicationObserved.returned.get());}
    @Override void resetOwners(){super.resetOwners();applicationObserved.reset();}
}
