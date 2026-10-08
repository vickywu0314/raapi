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
abstract class AiExportHttpFixture extends ComparisonHttpFixture {
    ExportObservedDataSource exportObserved;
    @Configuration @EnableWebMvc
    @Import({
        AiCohortConfiguration.class, AiCohortController.class, AiCohortServiceImpl.class, CohortSourceAdapter.class, com.wenwen.ai.result.AnalysisResultStore.class,com.wenwen.ai.result.AnalysisPageReader.class, com.wenwen.controller.ra.PrivateCohortController.class, com.wenwen.service.impl.PrivateCohortServiceImpl.class, com.wenwen.ai.cohort.DefinitionStore.class,com.wenwen.controller.ra.ComparisonController.class,com.wenwen.service.impl.ComparisonServiceImpl.class,com.wenwen.ai.result.ComparisonResultStore.class,com.wenwen.ai.result.ComparisonScopeReader.class, com.wenwen.controller.ra.AiExportController.class, com.wenwen.service.impl.AiExportServiceImpl.class, com.wenwen.ai.export.ExportContentReader.class, com.wenwen.ai.export.ExportFieldPolicy.class, com.wenwen.ai.export.OrdinaryXlsxWriter.class, com.wenwen.ai.export.ExportAuditStore.class, com.wenwen.service.impl.ExportServiceImpl.class, com.wenwen.controller.ra.ExportController.class
    })
    static class ExportInfrastructure {
        @Bean public com.wenwen.mapper.ExportMapper legacyExportMapper(){return unused(com.wenwen.mapper.ExportMapper.class);}
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
        @Bean @Primary public ExportObservedDataSource exportObserved(ComparisonObservedDataSource comparison){return new ExportObservedDataSource(comparison);}
        @Bean public com.wenwen.mapper.AiExportAuditMapper exportMapper(org.apache.ibatis.session.SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiExportAuditMapper.class);}
        @Bean @Primary public ObservedWriter observedWriter(){return new ObservedWriter();}
        @Bean public PlatformTransactionManager transactions(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.Resource[] {
                new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/PatientMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/PrivateCohortMapper.xml"),new ClassPathResource("mybatis/AiComparisonRunMapper.xml"),new ClassPathResource("mybatis/AiExportAuditMapper.xml")
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
        context.register(ExportInfrastructure.class);
        if (trusted) context.register(TrustedIdentity.class);
        beforeRefresh();
        context.refresh();
        bodyRead = new CountingBodyFilter();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        raw = context.getBean(DriverManagerDataSource.class);
        clock = context.getBean(MutableClock.class);
        observed = context.getBean(ObservedDataSource.class);
        privateObserved=context.getBean(PrivateCohortObservedDataSource.class);comparisonObserved=context.getBean(ComparisonObservedDataSource.class);exportObserved=context.getBean(ExportObservedDataSource.class);
    }
    @Override @org.junit.jupiter.api.BeforeEach void setup() throws Exception {super.setup();try(Connection c=raw.getConnection();Statement st=c.createStatement();java.io.InputStream in=getClass().getResourceAsStream("/sql/20261008_ai_export_audit.sql")){String ddl=new Scanner(in,java.nio.charset.StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();for(String statement:ddl.split(";"))if(!statement.trim().isEmpty())st.execute(statement);ownedTables.add("ra_ai_export_audit");}}
    @Override void exited(){super.exited();org.junit.jupiter.api.Assertions.assertEquals(0,exportObserved.active.get());org.junit.jupiter.api.Assertions.assertEquals(exportObserved.borrowed.get(),exportObserved.returned.get());org.junit.jupiter.api.Assertions.assertEquals(0,context.getBean(ObservedWriter.class).workbooks);}
    @Override void resetOwners(){super.resetOwners();exportObserved.reset();}

    static class ObservedWriter extends com.wenwen.ai.export.OrdinaryXlsxWriter {
        interface Cut {void after(File file)throws Exception;}
        Cut afterGenerated;int completed,active,workbooks,workbooksClosed;File last;
        @Override protected org.apache.poi.xssf.usermodel.XSSFWorkbook createWorkbook(){org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(){@Override public void close()throws java.io.IOException{super.close();workbooks--;workbooksClosed++;}};workbooks++;return book;}
        @Override public File write(com.wenwen.ai.export.AiExportQuery q,com.wenwen.ai.export.ExportContentReader.Content c,com.wenwen.ai.export.ExportFieldPolicy.Window w,Map<String,Object> metadata,int max)throws java.io.IOException{active++;try{File f=super.write(q,c,w,metadata,max);last=f;completed++;org.junit.jupiter.api.Assertions.assertEquals(0,workbooks,"真实Workbook先close，再发布生成事件");if(afterGenerated!=null){Cut cut=afterGenerated;afterGenerated=null;try{cut.after(f);}catch(java.io.IOException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new java.io.IOException(e);}}return f;}finally{active--;}}
    }
    static final class OutputCut implements javax.servlet.Filter {
        final int prefix;final boolean runtime,flushFailure;final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();int writes,afterFailure;boolean failed,closed;javax.servlet.http.HttpServletResponse response;
        OutputCut(int prefix,boolean runtime,boolean flush){this.prefix=prefix;this.runtime=runtime;flushFailure=flush;}
        public void doFilter(javax.servlet.ServletRequest request,javax.servlet.ServletResponse original,javax.servlet.FilterChain chain)throws java.io.IOException,javax.servlet.ServletException{
            response=(javax.servlet.http.HttpServletResponse)original;javax.servlet.ServletOutputStream stream=new javax.servlet.ServletOutputStream(){
                public boolean isReady(){return true;}public void setWriteListener(javax.servlet.WriteListener listener){throw new UnsupportedOperationException();}
                public void write(int b)throws java.io.IOException{write(new byte[]{(byte)b},0,1);}
                public void write(byte[] b,int offset,int count)throws java.io.IOException{writes++;if(failed){afterFailure++;throw new java.io.IOException("synthetic second output");}int allowed=prefix<0?count:Math.min(count,Math.max(0,prefix-bytes.size()));bytes.write(b,offset,allowed);if(allowed<count){failed=true;if(runtime)throw new IllegalStateException("synthetic container runtime output failure");throw new java.io.IOException("synthetic container output failure");}}
                public void flush()throws java.io.IOException{if(flushFailure){failed=true;throw new java.io.IOException("synthetic container flush failure");}}
                public void close(){closed=true;}
            };chain.doFilter(request,new javax.servlet.http.HttpServletResponseWrapper(response){@Override public javax.servlet.ServletOutputStream getOutputStream(){return stream;}});
        }
    }
    void exportPopulation37() throws Exception {
        sql("DELETE FROM patient_follow_up_history");sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_comorbidity");sql("DELETE FROM patient_basic_info");
        for(int i=0;i<37;i++) {
            int id=101+i;
            try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("INSERT INTO patient_basic_info(id,name,gender,card_no,study_no) VALUES(?,?,?,?,?)")) {
                s.setInt(1,id);s.setString(2,"synthetic-export-name-CANARY");s.setInt(3,1);s.setString(4,"110101198610070011");s.setString(5,"=synthetic-study-CANARY");s.executeUpdate();
            }
            sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,"+id+",0)");
            visit(id*10,id,0,"{\"result\":{\"crpScore\":"+BigDecimal.valueOf(37-i,1).toPlainString()+"},\"secret\":\"synthetic-export-case-CANARY\"}","2026-10-07",null,"{\"cfydb\":"+(10+i)+"}");
        }
        resetOwners();
    }
    MvcResult xlsx(String body) throws Exception {return http.perform(post("/api/ra/export/xlsx").contentType("application/json").content(body)).andReturn();}
}
