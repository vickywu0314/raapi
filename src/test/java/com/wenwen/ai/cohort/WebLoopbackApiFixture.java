package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.result.*;
import com.wenwen.ai.scope.*;
import com.wenwen.ai.source.CohortSourceAdapter;
import com.wenwen.ai.treatment.*;
import com.wenwen.controller.ra.AiCohortController;
import com.wenwen.controller.ra.PrivateCohortController;
import com.wenwen.controller.ra.ComparisonController;
import com.wenwen.mapper.*;
import com.wenwen.service.impl.AiCohortServiceImpl;
import com.wenwen.service.impl.PrivateCohortServiceImpl;
import com.wenwen.service.impl.ComparisonServiceImpl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.DataSource;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import org.springframework.core.io.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** 测试专用真实 TCP / Servlet / SQL 仪器；不扫描或加载生产配置。 */
public final class WebLoopbackApiFixture implements AutoCloseable {
    static final String[] TABLES = {"patient_basic_info", "patient_relation_doctor", "patient_follow_up_history", "patient_comorbidity", "ra_ai_analysis_run"};
    static final String[] PRIVATE_TABLES={"ra_private_cohort_definition","ra_private_cohort_command"};
    static final String COMPARISON_TABLE="ra_ai_compare_run";
    static final ObjectMapper JSON = new ObjectMapper();
    final List<String> ownedTables = new ArrayList<>();
    DriverManagerDataSource raw;
    ObservedDataSource observed;
    AnnotationConfigWebApplicationContext application;
    Tomcat tomcat;
    boolean closed;
    int preparationConnections;
    String scenario="default";
    final ScenarioClock scenarioClock=new ScenarioClock();
    final java.util.concurrent.atomic.AtomicLong scenarioDoctor=new java.util.concurrent.atomic.AtomicLong(101);
    long controlSeq;
    /** Clock与Principal仅供本fixture owned stdin，跨Servlet线程原子可见。 */
    static final class ScenarioClock extends Clock {
        final java.util.concurrent.atomic.AtomicReference<Instant> now;
        final ZoneId zone;
        ScenarioClock(){this(new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-07T02:00:00Z")),ZoneOffset.UTC);}
        private ScenarioClock(java.util.concurrent.atomic.AtomicReference<Instant> now,ZoneId zone){this.now=now;this.zone=zone;}
        public ZoneId getZone(){return zone;}
        public Clock withZone(ZoneId zone){return new ScenarioClock(now,zone);}
        public Instant instant(){return now.get();}
        void advanceTtl(){now.updateAndGet(instant->instant.plusMillis(900000));}
    }
    synchronized boolean control(String line) {
        String[] parts=line.split(" ",-1);Long sequence=null;
        if(parts.length==3 && parts[0].equals("CONTROL") && parts[1].matches("[1-9][0-9]{0,15}"))sequence=Long.parseLong(parts[1]);
        boolean accepted=false;
        if(sequence!=null && (scenario.equals("p01b")||scenario.equals("p03")||scenario.equals("p04")) && sequence==controlSeq+1 && Arrays.asList("IDENTITY_101","IDENTITY_202","IDENTITY_ANONYMOUS","ADVANCE_TTL").contains(parts[2])) {
            if(parts[2].equals("ADVANCE_TTL"))scenarioClock.advanceTtl();
            else scenarioDoctor.set(parts[2].equals("IDENTITY_101")?101:parts[2].equals("IDENTITY_202")?202:0);
            controlSeq=sequence;accepted=true;
        }
        event("control-ack","seq",sequence,"command",parts.length==3?parts[2]:null,"accepted",accepted);
        return accepted;
    }
    TrustedDoctor scenarioPrincipal() {
        long id=scenarioDoctor.get();if(id==0)throw new CohortException(401,"UNAUTHENTICATED","请先登录");
        return new TrustedDoctor(id);
    }

    static synchronized void event(String type, Object... fields) {
        Map<String,Object> data = new LinkedHashMap<>(); data.put("type",type);
        for (int i=0;i<fields.length;i+=2) data.put((String)fields[i],fields[i+1]);
        try { System.out.println("RA_WEB_FIXTURE_EVENT " + JSON.writeValueAsString(data)); System.out.flush(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    static void requireDatabase(String database, String isolation) {
        if (!"ra_synthetic_test".equals(database)) throw new IllegalStateException("拒绝非专用合成库");
        if (!"REPEATABLE-READ".equals(isolation)) throw new IllegalStateException("拒绝非REPEATABLE-READ");
    }
    void connect() {
        String url=System.getenv("RA_TEST_MYSQL_URL"),user=System.getenv("RA_TEST_MYSQL_USER"),password=System.getenv("RA_TEST_MYSQL_PASSWORD");
        CohortHttpFixture.guard(url,user,password);
        event("guard-passed");
        raw = new DriverManagerDataSource(url,user,password); raw.setDriverClassName("com.mysql.cj.jdbc.Driver");
        event("datasource-created");
    }
    Connection preparationConnection() throws SQLException { preparationConnections++; return raw.getConnection(); }
    void verifyDatabase(Connection c) throws SQLException {
        try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT DATABASE(), @@transaction_isolation, VERSION()")) {
            if(!rs.next())throw new IllegalStateException("核库未返回行");
            requireDatabase(rs.getString(1),rs.getString(2));
            event("database-verified","database",rs.getString(1),"isolation",rs.getString(2),"version",rs.getString(3),"jdbc",c.getMetaData().getDriverVersion());
        }
    }
    void schema() throws Exception {
        try (Connection c=preparationConnection(); Statement s=c.createStatement()) {
            verifyDatabase(c);
            int i=0;
            for (String sql:resource("schema.sql").split(";")) if (!sql.trim().isEmpty()) {
                s.execute(sql); ownedTables.add(TABLES[i++]);
                event("table-owned","table",ownedTables.get(ownedTables.size()-1));
            }
            for (String sql:resource(seedForScenario(scenario)).split(";")) if (!sql.trim().isEmpty()) s.execute(sql);
            if(scenario.equals("p03")||scenario.equals("p04"))createPrivateTables(s);
            if(scenario.equals("p04"))createComparisonTables(s);
            event("schema-ready","tables",ownedTables.size(),"preparationConnections",preparationConnections);
        }
    }
    static boolean allowsHttpPath(String scenario,String path) {
        return Arrays.asList("/api/ra/ai/cohort","/api/ra/ai/cohortPatients").contains(path)
            || (("p03".equals(scenario)||"p04".equals(scenario))&&Arrays.asList("/api/ra/cohort/save","/api/ra/cohort/list","/api/ra/cohort/open","/api/ra/cohort/rename","/api/ra/cohort/delete").contains(path))
            || ("p04".equals(scenario)&&Arrays.asList("/api/ra/ai/compare","/api/ra/ai/compareResult").contains(path));
    }
    static String seedForScenario(String scenario) {
        if("default".equals(scenario))return "seed.sql";
        if("p01b".equals(scenario)||"p03".equals(scenario))return "seed-p01b.sql";
        if("p02".equals(scenario))return "seed-p02.sql";
        if("p04".equals(scenario))return "seed-p04.sql";
        throw new IllegalArgumentException("未知合成场景");
    }
    static String resource(String name) throws IOException {
        try(InputStream in=WebLoopbackApiFixture.class.getResourceAsStream("/ai-cohort/web/"+name)) {
            if(in==null) throw new FileNotFoundException(name);
            return new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        }
    }
    static List<String> privateStatements() throws IOException {
        try(InputStream in=WebLoopbackApiFixture.class.getResourceAsStream("/sql/20261008_private_cohort_definition.sql")) {
            if(in==null)throw new FileNotFoundException("private-cohort-ddl");
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            List<String> statements=new ArrayList<>();for(String sql:ddl.split(";"))if(!sql.trim().isEmpty())statements.add(sql);
            if(statements.size()!=PRIVATE_TABLES.length)throw new IllegalStateException("私有DDL声明不符");
            for(int i=0;i<PRIVATE_TABLES.length;i++)if(!statements.get(i).contains("CREATE TABLE "+PRIVATE_TABLES[i]+" ("))throw new IllegalStateException("私有DDL表顺序不符");
            return statements;
        }
    }
    void createPrivateTables(Statement statement) throws SQLException,IOException {
        List<String> statements=privateStatements();
        for(int i=0;i<statements.size();i++) {
            statement.execute(statements.get(i));ownedTables.add(PRIVATE_TABLES[i]);
            event("table-owned","table",PRIVATE_TABLES[i]);
        }
    }
    static List<String> comparisonStatements() throws IOException {
        try(InputStream in=WebLoopbackApiFixture.class.getResourceAsStream("/sql/20261008_ai_compare_run.sql")) {
            if(in==null)throw new FileNotFoundException("comparison-ddl");
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            List<String> statements=new ArrayList<>();for(String sql:ddl.split(";"))if(!sql.trim().isEmpty())statements.add(sql);
            if(statements.size()!=1||!statements.get(0).contains("CREATE TABLE "+COMPARISON_TABLE+" ("))throw new IllegalStateException("比较DDL声明不符");
            return statements;
        }
    }
    void createComparisonTables(Statement statement) throws SQLException,IOException {
        statement.execute(comparisonStatements().get(0));ownedTables.add(COMPARISON_TABLE);
        event("table-owned","table",COMPARISON_TABLE);
    }
    @Configuration @EnableWebMvc
    @Import({AiCohortController.class,AiCohortServiceImpl.class,CohortSourceAdapter.class,AnalysisResultStore.class,AnalysisPageReader.class})
    public static class Infrastructure {
        @Bean public PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(ds);
            factory.setMapperLocations(new Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml")});
            return factory.getObject();
        }
        @Bean public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory f) { return new SqlSessionTemplate(f); }
        @Bean public AiCohortSourceMapper sourceMapper(SqlSessionTemplate t) { return t.getMapper(AiCohortSourceMapper.class); }
        @Bean public AiAnalysisRunMapper runMapper(SqlSessionTemplate t) { return t.getMapper(AiAnalysisRunMapper.class); }
        @Bean public Clock clock() { return Clock.fixed(Instant.parse("2026-10-07T02:00:00Z"),ZoneOffset.UTC); }
        @Bean public DrugDictionary dictionary() { return new DevelopmentDrugDictionary(); }
        @Bean public AiFeatureAvailability availability(org.springframework.core.env.Environment e) { return new AiFeatureAvailability(e.getProperty("ra.ai.api.enabled"),e.getProperty("ra.export.xlsx.enabled")); }
        @Bean public AnalysisSettings settings(org.springframework.core.env.Environment e) { return new AnalysisSettings(e.getProperty("ra.ai.analysis.result-ttl-seconds"),e.getProperty("ra.ai.analysis.result-max-bytes"),e.getProperty("ra.ai.analysis.cursor-key-base64")); }
        @Bean public AnalysisCursor cursor(AnalysisSettings s) { return new AnalysisCursor(s); }
    }
    @Configuration @EnableWebMvc
    @Import({AiCohortController.class,AiCohortServiceImpl.class,CohortSourceAdapter.class,AnalysisResultStore.class,AnalysisPageReader.class,PrivateCohortController.class,PrivateCohortServiceImpl.class,DefinitionStore.class})
    public static class PrivateInfrastructure extends Infrastructure {
        @Override @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
            factory.setMapperLocations(new Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/PrivateCohortMapper.xml")});
            return factory.getObject();
        }
        @Bean public PrivateCohortMapper privateMapper(SqlSessionTemplate template){return template.getMapper(PrivateCohortMapper.class);}
    }
    @Configuration @EnableWebMvc
    @Import({ComparisonController.class,ComparisonServiceImpl.class,ComparisonResultStore.class,ComparisonScopeReader.class})
    public static class ComparisonInfrastructure extends PrivateInfrastructure {
        @Override @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
            factory.setMapperLocations(new Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/PrivateCohortMapper.xml"),new ClassPathResource("mybatis/AiComparisonRunMapper.xml")});
            return factory.getObject();
        }
        @Bean public AiComparisonRunMapper comparisonMapper(SqlSessionTemplate template){return template.getMapper(AiComparisonRunMapper.class);}
    }
    int start(String identity, boolean sqlFailure, File base) throws Exception {
        observed=new ObservedDataSource(raw); observed.failSecondSql=sqlFailure;
        application=new AnnotationConfigWebApplicationContext();
        MutablePropertySources sources=application.getEnvironment().getPropertySources();
        // Servlet property sources are unpopulated here; remove defaults rather than consulting the host.
        for(String name:new String[]{"systemProperties","systemEnvironment","servletContextInitParams","servletConfigInitParams","jndiProperties"}) sources.remove(name);
        Map<String,Object> properties=new LinkedHashMap<>();
        properties.put("ra.ai.api.enabled","true");properties.put("ra.export.xlsx.enabled","false");
        properties.put("ra.ai.gateway.enabled","false");properties.put("ra.ai.gateway.outbound-approved","false");
        properties.put("ra.ai.analysis.result-ttl-seconds","900");properties.put("ra.ai.analysis.result-max-bytes","8388608");
        properties.put("ra.ai.analysis.cursor-key-base64","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.put("ra.serology.rf-uln","20");properties.put("ra.serology.ccp-uln","25");
        sources.addFirst(new MapPropertySource("synthetic-only",properties));
        application.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("observedDataSource",observed));
        final PrincipalProvider principal=(scenario.equals("p01b")||scenario.equals("p03")||scenario.equals("p04"))?this::scenarioPrincipal:("synthetic".equals(identity) ? () -> new TrustedDoctor(101) : () -> {throw new CohortException(401,"UNAUTHENTICATED","请先登录");});
        application.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("principalProvider",principal));
        if((scenario.equals("p01b")||scenario.equals("p03")||scenario.equals("p04"))) {
            scenarioDoctor.set(identity.equals("synthetic")?101:0);
            application.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("clock",scenarioClock));
        }
        application.register(scenario.equals("p04")?ComparisonInfrastructure.class:scenario.equals("p03")?PrivateInfrastructure.class:Infrastructure.class);
        tomcat=new Tomcat();tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.getConnector().setProperty("address","127.0.0.1");tomcat.getConnector().setPort(0);
        Context context=tomcat.addContext("",base.getAbsolutePath());
        context.setParentClassLoader(WebLoopbackApiFixture.class.getClassLoader());
        DispatcherServlet dispatcher=new DispatcherServlet(application);
        HttpServlet servlet=new HttpServlet(){
            protected synchronized void service(HttpServletRequest request,HttpServletResponse response) throws ServletException,IOException {
                if(!allowsHttpPath(scenario,request.getRequestURI())) {response.sendError(404);return;}
                try {dispatcher.service(request,response);}
                finally {event("http-observation","borrowed",observed.borrowed,"returned",observed.returned,"active",observed.active.get(),"mysqlFailures",observed.mysqlFailures,"mysqlError",observed.lastMysqlError,"sqlState",observed.lastSqlState,"status",response.getStatus());}
            }
            public void init(ServletConfig config) throws ServletException {super.init(config);dispatcher.init(config);}
            public void destroy(){dispatcher.destroy();super.destroy();}
        };
        Tomcat.addServlet(context,"cohort",servlet).setLoadOnStartup(1);context.addServletMappingDecoded("/*","cohort");
        tomcat.start();
        if(!application.isActive())throw new IllegalStateException("DispatcherServlet context未启动");
        List<String> names=new ArrayList<>();for(org.springframework.core.env.PropertySource<?> source:sources)names.add(source.getName());
        if(sources.contains("systemProperties")||sources.contains("systemEnvironment"))throw new IllegalStateException("环境隔离失败");
        for(String name:application.getBeanDefinitionNames())if(name.toLowerCase(Locale.ROOT).contains("gateway"))throw new IllegalStateException("不应装配gateway");
        event("context-isolated","propertySources",names,"gatewayBeans",0,"identity",identity);
        return tomcat.getConnector().getLocalPort();
    }
    @Override public synchronized void close() throws Exception {
        if(closed)return;closed=true;Exception first=null;
        if(tomcat!=null)try{tomcat.stop();}catch(Exception e){first=e;}
        if(application!=null)try{application.close();}catch(Exception e){if(first==null)first=e;}
        if(raw!=null)for(int i=ownedTables.size()-1;i>=0;i--)try(Connection c=preparationConnection();Statement s=c.createStatement()){
            s.execute("DROP TABLE "+ownedTables.get(i));event("table-released","table",ownedTables.get(i));
        }catch(Exception e){if(first==null)first=e;}
        if(tomcat!=null)try{tomcat.destroy();}catch(Exception e){if(first==null)first=e;}
        event("closed","active",observed==null?0:observed.active.get(),"preparationConnections",preparationConnections,"clean",first==null);
        if(first!=null)throw first;
    }
    void schemaConflictCheck() throws Exception {
        // Only this check creates the sentinel. The fixture must never acquire it after CREATE fails.
        try(Connection c=preparationConnection();Statement s=c.createStatement()){
            verifyDatabase(c);
            s.execute("CREATE TABLE patient_relation_doctor (sentinel INT PRIMARY KEY) ENGINE=InnoDB");
            s.execute("INSERT INTO patient_relation_doctor VALUES (8675309)");
        }
        boolean conflict=false;
        try {schema();}catch(SQLException e){conflict=e.getErrorCode()==1050;event("create-conflict","mysqlError",e.getErrorCode());}
        close();
        try(Connection c=preparationConnection();Statement s=c.createStatement()){
            try {
                if(!conflict)throw new AssertionError("未到达真实CREATE冲突");
                try(ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patient_basic_info'")){rs.next();if(rs.getInt(1)!=0)throw new AssertionError("本次首表未自动清理");}
                try(ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patient_relation_doctor'")){rs.next();if(rs.getInt(1)!=1)throw new AssertionError("预存哨兵表被误清；fallback前真实SQL观察");}
                try(ResultSet rs=s.executeQuery("SELECT sentinel FROM patient_relation_doctor")){if(!rs.next()||rs.getInt(1)!=8675309||rs.next())throw new AssertionError("预存哨兵未保留");}
                event("schema-ownership-observed","beforeFallback",true,"ownedTableAbsent",true,"sentinel",8675309);
            } finally {s.execute("DROP TABLE IF EXISTS patient_relation_doctor");}
        }
    }
    void privateSchemaConflictCheck() throws Exception {
        try(Connection c=preparationConnection();Statement statement=c.createStatement()){
            verifyDatabase(c);statement.execute("CREATE TABLE ra_private_cohort_command (sentinel INT PRIMARY KEY) ENGINE=InnoDB");statement.execute("INSERT INTO ra_private_cohort_command VALUES (8675309)");
        }
        boolean conflict=false;
        try{schema();}catch(SQLException e){conflict=e.getErrorCode()==1050;event("create-conflict","mysqlError",e.getErrorCode());}
        close();
        try(Connection c=preparationConnection();Statement statement=c.createStatement()){
            try{
                if(!conflict)throw new AssertionError("未到达私有第二表真实CREATE冲突");
                try(ResultSet rs=statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'ra_private_cohort_command'")){rs.next();if(rs.getInt(1)!=0)throw new AssertionError("本次owned表未自动清理");}
                try(ResultSet rs=statement.executeQuery("SELECT sentinel FROM ra_private_cohort_command")){if(!rs.next()||rs.getInt(1)!=8675309||rs.next())throw new AssertionError("私有预存哨兵未保留");}
                event("private-schema-ownership-observed","beforeFallback",true,"ownedTableAbsent",true,"sentinel",8675309);
            }finally{statement.execute("DROP TABLE IF EXISTS ra_private_cohort_command");}
        }
    }
    void comparisonSchemaConflictCheck() throws Exception {
        try(Connection c=preparationConnection();Statement statement=c.createStatement()){
            verifyDatabase(c);statement.execute("CREATE TABLE ra_ai_compare_run (sentinel INT PRIMARY KEY) ENGINE=InnoDB");statement.execute("INSERT INTO ra_ai_compare_run VALUES (8675309)");
        }
        boolean conflict=false;
        try{schema();}catch(SQLException e){conflict=e.getErrorCode()==1050;event("create-conflict","mysqlError",e.getErrorCode());}
        close();
        try(Connection c=preparationConnection();Statement statement=c.createStatement()){
            try{
                if(!conflict)throw new AssertionError("未到达比较表真实CREATE冲突");
                try(ResultSet rs=statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'ra_ai_compare_run'")){rs.next();if(rs.getInt(1)!=0)throw new AssertionError("本次owned表未自动清理");}
                try(ResultSet rs=statement.executeQuery("SELECT sentinel FROM ra_ai_compare_run")){if(!rs.next()||rs.getInt(1)!=8675309||rs.next())throw new AssertionError("比较预存哨兵未保留");}
                event("comparison-schema-ownership-observed","beforeFallback",true,"ownedTableAbsent",true,"sentinel",8675309);
            }finally{statement.execute("DROP TABLE IF EXISTS ra_ai_compare_run");}
        }
    }
    public static void main(String[] args) throws Exception {
        String identity="synthetic",mode="normal",scenario="default";boolean failure=false;
        for(String arg:args){if(arg.startsWith("--identity="))identity=arg.substring(11);else if(arg.equals("--sql-failure"))failure=true;else if(arg.startsWith("--mode="))mode=arg.substring(7);else if(arg.startsWith("--scenario="))scenario=arg.substring(11);else throw new IllegalArgumentException("未知参数");}
        if(!Arrays.asList("synthetic","anonymous").contains(identity))throw new IllegalArgumentException("未知身份");
        seedForScenario(scenario);
        final WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario=scenario;
        Thread hook=new Thread(()->{try{fixture.close();}catch(Exception e){event("cleanup-error","class",e.getClass().getName());}},"web-fixture-close");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            fixture.connect();
            if(mode.equals("guard-only"))return;
            if(mode.equals("schema-conflict-check")){if(scenario.equals("p04"))fixture.comparisonSchemaConflictCheck();else if(scenario.equals("p03"))fixture.privateSchemaConflictCheck();else fixture.schemaConflictCheck();return;}
            fixture.schema();
            if(mode.equals("partial-start-fail"))throw new IllegalStateException("受控部分装配失败");
            String base=System.getenv("RA_WEB_FIXTURE_DIR");if(base==null)throw new IllegalStateException("缺少本次Tomcat目录");
            int port=fixture.start(identity,failure,new File(base));
            Map<String,Object> ready=new LinkedHashMap<>();ready.put("host","127.0.0.1");ready.put("port",port);ready.put("fixtureVersion","p00b-v1");ready.put("identity",identity.equals("synthetic")?"synthetic-doctor-101":"anonymous");
            boolean held=mode.equals("startup-never-ready");
            if(held)event("held-not-ready","port",port);
            else {System.out.println("RA_WEB_FIXTURE_READY "+JSON.writeValueAsString(ready));System.out.flush();}
            try(BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){
                String line;while((line=input.readLine())!=null){if(line.equals("STOP")&&!mode.equals("ignore-stop"))break;else if(line.startsWith("CONTROL "))fixture.control(line);}
                if(mode.equals("ignore-stop"))new java.util.concurrent.CountDownLatch(1).await();
            }
            if(held){System.out.println("RA_WEB_FIXTURE_READY "+JSON.writeValueAsString(ready));System.out.flush();}

        }catch(Throwable e){event("failure","class",e.getClass().getName());throw e;}
        finally {try{fixture.close();}finally{Runtime.getRuntime().removeShutdownHook(hook);}}
    }
}
