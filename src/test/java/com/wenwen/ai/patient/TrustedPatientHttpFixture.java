package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.config.AiCohortConfiguration;
import java.io.IOException;
import java.net.URI;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Real MVC/MySQL instrumentation; credentials never printed. */
public abstract class TrustedPatientHttpFixture {
    protected AnnotationConfigWebApplicationContext context;
    protected MockMvc http;
    protected boolean apiEnabled=true;
    protected DriverManagerDataSource raw;
    protected ObservedSource observed;
    protected BodyCounter body;
    protected final ObjectMapper json = new ObjectMapper();
    @Configuration @EnableWebMvc @Import({AiCohortConfiguration.class,com.wenwen.service.impl.PatientServiceImpl.class,com.wenwen.ai.qc.QcSnapshotReader.class,com.wenwen.service.impl.AuditLogServiceImpl.class,com.wenwen.service.impl.VisitServiceImpl.class})
    public static class Infrastructure {
        @Bean public DriverManagerDataSource rawSource() {
            String url=System.getenv("RA_TEST_MYSQL_URL"), user=System.getenv("RA_TEST_MYSQL_USER"), password=System.getenv("RA_TEST_MYSQL_PASSWORD");
            if(url==null||user==null||password==null)throw new IllegalStateException("Explicit synthetic database required");
            URI uri=URI.create(url.substring(5));
            if(!"127.0.0.1".equals(uri.getHost())||uri.getPort()!=43306||!"/ra_synthetic_test".equals(uri.getPath()))throw new IllegalStateException("Only guarded loopback synthetic database allowed");
            DriverManagerDataSource ds=new DriverManagerDataSource(url,user,password); ds.setDriverClassName("com.mysql.cj.jdbc.Driver");return ds;
        }
        @Bean @Primary public ObservedSource dataSource(DriverManagerDataSource raw){return new ObservedSource(raw);}
    }
    @Configuration public static class PatientInfrastructure {
        @Bean public org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource ds)throws Exception{
            org.mybatis.spring.SqlSessionFactoryBean f=new org.mybatis.spring.SqlSessionFactoryBean();f.setDataSource(ds);
            f.setMapperLocations(new org.springframework.core.io.Resource[]{new org.springframework.core.io.ClassPathResource("mybatis/ProjectMapper.xml"),new org.springframework.core.io.ClassPathResource("mybatis/PatientMapper.xml"),new org.springframework.core.io.ClassPathResource("mybatis/AuditLogMapper.xml"),new org.springframework.core.io.ClassPathResource("mybatis/VisitMapper.xml")});return f.getObject();}
        @Bean public org.mybatis.spring.SqlSessionTemplate template(org.apache.ibatis.session.SqlSessionFactory f){return new org.mybatis.spring.SqlSessionTemplate(f);}
        @Bean public com.wenwen.mapper.PatientMapper patients(org.mybatis.spring.SqlSessionTemplate t){return t.getMapper(com.wenwen.mapper.PatientMapper.class);}
        @Bean public com.wenwen.mapper.ProjectMapper projects(org.mybatis.spring.SqlSessionTemplate t){return t.getMapper(com.wenwen.mapper.ProjectMapper.class);}
        @Bean public com.wenwen.mapper.VisitMapper visits(org.mybatis.spring.SqlSessionTemplate t){return t.getMapper(com.wenwen.mapper.VisitMapper.class);}
        @Bean public com.wenwen.mapper.AuditLogMapper audit(org.mybatis.spring.SqlSessionTemplate t){return t.getMapper(com.wenwen.mapper.AuditLogMapper.class);}
    }
    protected final java.util.List<String> ownedTables=new java.util.ArrayList<>();
    public static String resource(String name)throws Exception{try(java.io.InputStream in=TrustedPatientHttpFixture.class.getResourceAsStream("/ai-analysis-real-data/p00/"+name)){return new java.util.Scanner(in,"UTF-8").useDelimiter("\\A").next();}}
    protected void sql(String statement)throws Exception{try(Connection c=raw.getConnection();Statement s=c.createStatement()){s.execute(statement);}}
    protected org.springframework.mock.web.MockHttpSession session(long id){org.springframework.mock.web.MockHttpSession session=new org.springframework.mock.web.MockHttpSession();session.setAttribute(com.wenwen.ai.scope.AuthenticatedDoctorSession.ATTRIBUTE,new com.wenwen.ai.scope.AuthenticatedDoctorSession(new com.wenwen.ai.scope.TrustedDoctor(id)));return session;}
    public static class ObservedSource extends AbstractDataSource {
        final DataSource delegate; public int borrowed,selects;
        ObservedSource(DataSource delegate){this.delegate=delegate;}
        public Connection getConnection() throws SQLException {borrowed++;return observe(delegate.getConnection());}
        public Connection getConnection(String user,String password)throws SQLException{borrowed++;return observe(delegate.getConnection(user,password));}
        private Connection observe(Connection connection){return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{try{if("prepareStatement".equals(m.getName())&&((String)a[0]).trim().startsWith("SELECT"))selects++;return m.invoke(connection,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});}
    }
    public static class BodyCounter implements Filter {
        public int bytes;
        public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
            bytes=0;chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest)request){public ServletInputStream getInputStream()throws IOException{ServletInputStream in=super.getInputStream();return new ServletInputStream(){public int read()throws IOException{int n=in.read();if(n>=0)bytes++;return n;}public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0)bytes+=n;return n;}public boolean isFinished(){return in.isFinished();}public boolean isReady(){return in.isReady();}public void setReadListener(ReadListener l){in.setReadListener(l);}};}},response);
        }
    }
    @BeforeEach public void setup() throws Exception {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-flags",java.util.Collections.singletonMap("ra.ai.api.enabled",String.valueOf(apiEnabled))));context.register(Infrastructure.class,PatientInfrastructure.class);
        // Baseline lacks this route; reflection keeps the first assertion executable.
        try{context.register(Class.forName("com.wenwen.controller.ra.TrustedPatientController"));}catch(ClassNotFoundException absent){}
        try{context.register(Class.forName("com.wenwen.service.impl.TrustedPatientServiceImpl"));}catch(ClassNotFoundException absent){}
        context.refresh();raw=context.getBean(DriverManagerDataSource.class);observed=context.getBean(ObservedSource.class);body=new BodyCounter();http=MockMvcBuilders.webAppContextSetup(context).addFilters(body).build();
        try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT DATABASE()")){org.junit.jupiter.api.Assertions.assertTrue(r.next());org.junit.jupiter.api.Assertions.assertEquals("ra_synthetic_test",r.getString(1));}
    }
    protected void seed()throws Exception{String[] tables={"patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity","`user`","patient_audit_log"};int i=0;for(String ddl:resource("schema.sql").split(";"))if(!ddl.trim().isEmpty()){sql(ddl);ownedTables.add(tables[i++]);}for(String row:resource("seed.sql").split(";"))if(!row.trim().isEmpty())sql(row);}
    @AfterEach public void close() throws Exception {try{for(int i=ownedTables.size()-1;i>=0;i--)sql("DROP TABLE "+ownedTables.get(i));}finally{if(context!=null)context.close();}}
}
