package com.wenwen.service.impl;

import com.wenwen.mapper.*;
import com.wenwen.service.VisitService;
import com.wenwen.vo.*;
import java.io.InputStream;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.*;
import org.mybatis.spring.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;

/** 真实Spring事务/Mapper/数据库；测试写入及独立writer不经过被观察DataSource。 */
abstract class VisitUpdateFixture {
    static final String BQPG="{\"unknown\":{\"flag\":true,\"n\":3,\"s\":\"UPPER\",\"nil\":null,\"nested\":{\"b\":2,\"a\":1}},\"result\":{\"ytgjs\":4,\"zzgjs\":1,\"crpScore\":\"8.88\",\"esrScore\":7.77,\"hqaScore\":2},\"ztScoreByPatient\":50,\"hqaScore\":2}";
    static final String LAB="{\"cfydb\":9,\"xc\":25,\"other\":{\"b\":false,\"nil\":null}}";
    AnnotationConfigApplicationContext context;
    DriverManagerDataSource raw;
    ObservedDataSource observed;
    SqlObserver sqlObserver;
    VisitService service;
    PatientMapper patientMapper;
    int writerCommits;
    final List<String> ownedTables=new ArrayList<>();

    static void guard(String url,String user,String password) {
        if(url==null || user==null || user.isEmpty() || password==null || password.isEmpty() || !url.startsWith("jdbc:mysql://")) throw new IllegalStateException("缺少显式合成MySQL环境");
        URI u=URI.create(url.substring(5));
        if(!("127.0.0.1".equals(u.getHost()) || "localhost".equals(u.getHost())) || !"/ra_synthetic_test".equals(u.getPath()) || u.getUserInfo()!=null) throw new IllegalStateException("仅允许loopback/ra_synthetic_test");
    }
    @Configuration @EnableTransactionManagement
    @Import({VisitServiceImpl.class,AuditLogServiceImpl.class})
    static class Infrastructure {
        @Bean public DriverManagerDataSource rawDataSource() {
            String url=System.getenv("RA_TEST_MYSQL_URL"),u=System.getenv("RA_TEST_MYSQL_USER"),p=System.getenv("RA_TEST_MYSQL_PASSWORD"); guard(url,u,p);
            DriverManagerDataSource ds=new DriverManagerDataSource(url,u,p);ds.setDriverClassName("com.mysql.cj.jdbc.Driver");return ds;
        }
        @Bean @Primary public ObservedDataSource dataSource(DriverManagerDataSource raw) {return new ObservedDataSource(raw);}
        @Bean public PlatformTransactionManager transactionManager(DataSource ds) {return new DataSourceTransactionManager(ds);}
        @Bean public SqlObserver sqlObserver() {return new SqlObserver();}
        @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds,SqlObserver observer) throws Exception {
            SqlSessionFactoryBean f=new SqlSessionFactoryBean();f.setDataSource(ds); f.setPlugins(new Interceptor[]{observer});
            f.setMapperLocations(new org.springframework.core.io.Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/VisitMapper.xml"),new ClassPathResource("mybatis/AuditLogMapper.xml"),new ClassPathResource("mybatis/PatientMapper.xml")});return f.getObject();
        }
        @Bean public VisitMapper visitMapper(SqlSessionFactory f) {return new SqlSessionTemplate(f).getMapper(VisitMapper.class);}
        @Bean public AuditLogMapper auditLogMapper(SqlSessionFactory f) {return new SqlSessionTemplate(f).getMapper(AuditLogMapper.class);}
        @Bean public PatientMapper patientMapper(SqlSessionFactory f) {return new SqlSessionTemplate(f).getMapper(PatientMapper.class);}
    }
    @Intercepts({@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}),@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class})})
    static final class SqlObserver implements Interceptor {
        Runnable afterVisitRead; int visitUpdates,auditAttempts,auditFailures,readBarriers; final List<String> completed=new ArrayList<>();
        public Object intercept(Invocation call) throws Throwable {
            String id=((MappedStatement)call.getArgs()[0]).getId();
            if(id.endsWith("AuditLogMapper.insert")) auditAttempts++;
            Object result;
            try {result=call.proceed();} catch(Throwable e) {if(id.endsWith("AuditLogMapper.insert"))auditFailures++;throw e;}
            completed.add(id);
            if(id.endsWith("VisitMapper.updateVisit"))visitUpdates++;
            if(id.endsWith("VisitMapper.getVisit") && afterVisitRead!=null) {Runnable action=afterVisitRead;afterVisitRead=null;readBarriers++;action.run();}
            return result;
        }
        public Object plugin(Object o) {return Plugin.wrap(o,this);}
        public void setProperties(Properties p) {}
        void reset(){afterVisitRead=null;visitUpdates=auditAttempts=auditFailures=readBarriers=0;completed.clear();}
    }
    static final class ObservedDataSource extends AbstractDataSource {
        final DataSource delegate; int borrowed,returned,active; final List<Boolean> updateAutoCommit=new ArrayList<>();
        ObservedDataSource(DataSource d){delegate=d;}
        public Connection getConnection() throws SQLException {return observe(delegate.getConnection());}
        public Connection getConnection(String u,String p) throws SQLException {return observe(delegate.getConnection(u,p));}
        Connection observe(Connection c) {
            borrowed++;active++;
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},new InvocationHandler(){
                boolean closed;
                public Object invoke(Object p,Method m,Object[] a)throws Throwable {
                    try {
                        if("close".equals(m.getName())){if(!closed){c.close();closed=true;returned++;active--;}return null;}
                        if("prepareStatement".equals(m.getName()) && ((String)a[0]).trim().startsWith("UPDATE patient_follow_up_history")) updateAutoCommit.add(c.getAutoCommit());
                        return m.invoke(c,a);
                    }catch(InvocationTargetException e){throw e.getCause();}
                }
            });
        }
        void reset(){assertEquals(0,active);borrowed=returned=0;updateAutoCommit.clear();}
    }
    @BeforeEach void setup() throws Exception {
        context=new AnnotationConfigApplicationContext(Infrastructure.class);raw=context.getBean(DriverManagerDataSource.class);observed=context.getBean(ObservedDataSource.class);sqlObserver=context.getBean(SqlObserver.class);service=context.getBean(VisitService.class);patientMapper=context.getBean(PatientMapper.class);
        try(Connection c=raw.getConnection();Statement s=c.createStatement()) {
            try(ResultSet r=s.executeQuery("SELECT DATABASE(),VERSION()")){assertTrue(r.next());assertEquals("ra_synthetic_test",r.getString(1));System.out.println("P02a MySQL "+r.getString(2)+"; JDBC "+c.getMetaData().getDriverVersion());}
            String[] names={"patient_basic_info","patient_relation_doctor","patient_follow_up_history","user","patient_audit_log"}; int i=0;
            try(InputStream in=getClass().getResourceAsStream("/ai-cohort/p02a/schema.sql")) {String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();for(String q:ddl.split(";"))if(!q.trim().isEmpty()){s.execute(q);ownedTables.add(names[i++]);}}
            try(ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")){int n=0;while(r.next()){assertEquals("InnoDB",r.getString(1));n++;}assertEquals(5,n);}
            s.execute("INSERT INTO patient_basic_info VALUES (1,1,'2026-10-01','2026-10-01')");
            s.execute("INSERT INTO patient_relation_doctor VALUES (1,101,0,1,'2026-10-01','2026-10-01')");
            s.execute("INSERT INTO `user` VALUES (101,'synthetic-doctor')");
        }
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("INSERT INTO patient_follow_up_history VALUES (10,1,101,0,NULL,'2026-10-01 10:00:00.123456','2026-09-30 20:00:00.654321',?,?,?,?,?,?,?)")) {
            String[] data={"{\"followDate\":\"2026-10-01\",\"u\":null}",LAB,BQPG,"{\"record\":\"synthetic\"}"," {\"unknown\":true} ","unparseable",null};for(int i=0;i<data.length;i++)s.setString(i+1,data[i]);s.executeUpdate();
        }
        reset();
    }
    @AfterEach void teardown() throws Exception {
        if(raw!=null)for(int i=ownedTables.size()-1;i>=0;i--)sql("DROP TABLE `"+ownedTables.get(i)+"`");
        if(context!=null)context.close();
    }
    void reset(){observed.reset();sqlObserver.reset();writerCommits=0;}
    VisitUpdateRequest request(String column,String key,Object value) {
        VisitUpdateRequest r=new VisitUpdateRequest();r.setDoctorId(101L);r.setVisitId(10L);r.setVersion(service.getEditForm(101L,10L).getVersion());
        if(column!=null)r.setModules(Collections.singletonMap(column,VisitEditorInvalidationTest.fields(key,value)));reset();return r;
    }
    void released(){assertEquals(0,observed.active);assertEquals(observed.borrowed,observed.returned);assertEquals(1,observed.borrowed);}
    void sql(String q)throws SQLException {try(Connection c=raw.getConnection();Statement s=c.createStatement()){assertTrue(c.getAutoCommit());s.execute(q);}}
    void rawModule(String column,String value)throws SQLException {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET "+column+"=? WHERE id=10")){assertTrue(c.getAutoCommit());s.setString(1,value);assertEquals(1,s.executeUpdate());}}
    Map<String,Object> row()throws SQLException {try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM patient_follow_up_history WHERE id=10")){if(!r.next())return null;Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnName(i),r.getObject(i));return row;}}
    String textQuery(String q)throws SQLException {try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){assertTrue(r.next());return r.getString(1);}}
    int auditCount()throws SQLException {try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM patient_audit_log")){r.next();return r.getInt(1);}}
    void writer(Runnable action) {
        ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> f=pool.submit(action);
        try{f.get(5,TimeUnit.SECONDS);writerCommits++;}catch(Exception e){throw new AssertionError("独立writer未有界提交",e);}finally{pool.shutdownNow();try{assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    }
    void writerSql(String q){writer(()->{try{sql(q);}catch(SQLException e){throw new AssertionError(e);}});}
    void writerModule(String c,String v){writer(()->{try{rawModule(c,v);}catch(SQLException e){throw new AssertionError(e);}});}
}
