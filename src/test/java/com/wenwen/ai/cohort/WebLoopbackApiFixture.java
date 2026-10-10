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
    static final String[] APPLICATION_TABLES={"export_application","ra_ai_export_request"};
    static final String APPLICATION_REASON_CANARY="ordinary-application-canary";
    static final String SMTP_FROM="fixture-from@notification.invalid",SMTP_TO="fixture-admin@notification.invalid";
    static final ObjectMapper JSON = new ObjectMapper();
    final List<String> ownedTables = new ArrayList<>();
    DriverManagerDataSource raw;
    ObservedDataSource observed;
    AnnotationConfigWebApplicationContext application;
    Tomcat tomcat;
    boolean closed;
    int preparationConnections;
    String scenario="default";
    AiGatewayLoopbackPeer parsePeer;
    AiGatewayLoopbackPeer.Exchange parsePeerPlan;
    boolean peerPlanDiscarded;
    NotificationSmtpPeer applicationPeer;
    static boolean applicationScenario(String scenario){return Arrays.asList("p06-application-replay","p06-application-retry","p06-application-unknown").contains(scenario);}
    boolean applicationScenario(){return applicationScenario(scenario);}
    static NotificationSmtpPeer.Mode applicationPeerMode(String scenario){
        if("p06-application-replay".equals(scenario))return NotificationSmtpPeer.Mode.SUCCESS;
        if("p06-application-retry".equals(scenario))return NotificationSmtpPeer.Mode.RCPT_REJECT;
        if("p06-application-unknown".equals(scenario))return NotificationSmtpPeer.Mode.DATA_SILENT;
        throw new IllegalArgumentException("未知申请场景");
    }
    boolean parseScenario(){return Arrays.asList("p05-rule","p05-safety","p05-apply-rule","p05-apply-safety").contains(scenario);}
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
        if(sequence!=null&&(scenario.equals("p05-safety")||scenario.equals("p05-apply-safety"))&&sequence==controlSeq+1&&parts[2].equals("PARSE_PEER_ASSERT")) {
            if(parsePeer==null||parsePeerPlan==null||parsePeer.requests.get()!=0||parsePeerPlan.received.getCount()!=1||parsePeer.failure.get()!=null)throw new AssertionError("解析安全门未保护已排计划peer");
            event("parse-peer-observed","requests",0,"received",1,"failure",null,"beforeDiscard",true);controlSeq=sequence;accepted=true;
        }
        if(sequence!=null && (scenario.equals("p01b")||scenario.equals("p03")||scenario.equals("p04")) && sequence==controlSeq+1 && Arrays.asList("IDENTITY_101","IDENTITY_202","IDENTITY_ANONYMOUS","ADVANCE_TTL").contains(parts[2])) {
            if(parts[2].equals("ADVANCE_TTL"))scenarioClock.advanceTtl();
            else scenarioDoctor.set(parts[2].equals("IDENTITY_101")?101:parts[2].equals("IDENTITY_202")?202:0);
            controlSeq=sequence;accepted=true;
        }
        if(sequence!=null&&applicationScenario()&&applicationPeer!=null&&sequence==controlSeq+1) {
            if(parts[2].equals("SMTP_SUCCESS")&&scenario.equals("p06-application-retry")){applicationPeer.mode=NotificationSmtpPeer.Mode.SUCCESS;controlSeq=sequence;accepted=true;}
            else if(parts[2].equals("SMTP_ASSERT")){assertApplicationPeer();controlSeq=sequence;accepted=true;}
        }
        event("control-ack","seq",sequence,"command",parts.length==3?parts[2]:null,"accepted",accepted);
        return accepted;
    }
    static Map<String,Object> applicationSmtpProperties(int port) {
        if(port<1||port>65535)throw new IllegalArgumentException("无效合成SMTP端口");
        Map<String,Object> properties=new LinkedHashMap<>();
        properties.put("ra.export.smtp.enabled","true");properties.put("ra.export.smtp.host","127.0.0.1");
        properties.put("ra.export.smtp.port",String.valueOf(port));properties.put("ra.export.smtp.mode","LOOPBACK_PLAIN");
        properties.put("ra.export.smtp.from",SMTP_FROM);properties.put("ra.export.apply-mail-to",SMTP_TO);
        properties.put("ra.export.smtp.username","");properties.put("ra.export.smtp.password","");return properties;
    }
    /** 只读现有peer；解码正文仅在内存内检查，事件永不包含正文/原协议文本。 */
    void assertApplicationPeer() {
        NotificationSmtpPeer peer=applicationPeer;
        if(peer==null||peer.failure!=null||peer.authCommands.get()!=0)throw new AssertionError("合成SMTP安全观察失败");
        try {
            boolean peerEof=peer.connections.get()==0||peer.eof.await(1,java.util.concurrent.TimeUnit.SECONDS);
            if(!peerEof)throw new AssertionError("SMTP连接未退出；fallback前观察");
            synchronized(peer.commands){for(String command:peer.commands){
                if(command.startsWith("MAIL FROM")&&!command.equals("MAIL FROM:<"+SMTP_FROM+">"))throw new AssertionError("SMTP发件地址越界");
                if(command.startsWith("RCPT TO")&&!command.equals("RCPT TO:<"+SMTP_TO+">"))throw new AssertionError("SMTP收件地址越界");
            }}
            synchronized(peer.messages){for(byte[] bytes:peer.messages){
                javax.mail.internet.MimeMessage message=new javax.mail.internet.MimeMessage(javax.mail.Session.getInstance(new Properties()),new ByteArrayInputStream(bytes));
                if(!message.isMimeType("text/plain")||message.getFrom()==null||message.getFrom().length!=1||!SMTP_FROM.equals(((javax.mail.internet.InternetAddress)message.getFrom()[0]).getAddress())||message.getAllRecipients()==null||message.getAllRecipients().length!=1||!SMTP_TO.equals(((javax.mail.internet.InternetAddress)message.getAllRecipients()[0]).getAddress()))throw new AssertionError("SMTP信封或模板越界");
                Object content=message.getContent();if(!(content instanceof String))throw new AssertionError("SMTP正文非文本");
                assertApplicationBody((String)content);
            }}
            event("application-peer-observed","connections",peer.connections.get(),"dataCommands",peer.dataCommands.get(),"accepted",peer.accepted.get(),"authCommands",peer.authCommands.get(),"messages",peer.messages.size(),"failure",null,"beforeFallback",true,"peerEof",peerEof,"reasonCanaryAbsent",true,"patientCanaryAbsent",true,"addressesSafe",true);
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("SMTP观察被中断",e);}
        catch(IOException|javax.mail.MessagingException e){throw new AssertionError("SMTP安全模板无法读取",e);}
    }
    static void assertApplicationBody(String body) {
        if(body.contains(APPLICATION_REASON_CANARY))throw new AssertionError("申请说明进入SMTP正文");
        for(int id=101;id<=137;id++)if(body.contains("合成文件患者"+id)||body.contains("EXPORT-"+id))throw new AssertionError("患者canary进入SMTP正文");
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
            if(scenario.equals("p03")||scenario.equals("p04")||scenario.equals("p06-comparison"))createPrivateTables(s);
            if(scenario.equals("p04")||scenario.equals("p06-comparison"))createComparisonTables(s);
            if(parseScenario())createParseAuditTable(s);
            if(scenario.equals("p06-cohort")||scenario.equals("p06-comparison")||applicationScenario())createExportAuditTable(s);
            if(applicationScenario())createApplicationTables(s);
            event("schema-ready","tables",ownedTables.size(),"preparationConnections",preparationConnections);
        }
    }
    static boolean allowsHttpPath(String scenario,String path) {
        return Arrays.asList("/api/ra/ai/cohort","/api/ra/ai/cohortPatients").contains(path)
            || (("p03".equals(scenario)||"p04".equals(scenario))&&Arrays.asList("/api/ra/cohort/save","/api/ra/cohort/list","/api/ra/cohort/open","/api/ra/cohort/rename","/api/ra/cohort/delete").contains(path))
            || (Arrays.asList("p05-rule","p05-safety","p05-apply-rule","p05-apply-safety").contains(scenario)&&"/api/ra/ai/parse".equals(path))
            || (Arrays.asList("p05-rule","p05-safety","p05-apply-rule","p05-apply-safety").contains(scenario)&&"/api/ra/ai/applyParse".equals(path))
            || ("p06-cohort".equals(scenario)&&"/api/ra/export/xlsx".equals(path))
            || (applicationScenario(scenario)&&Arrays.asList("/api/ra/export/xlsx","/api/ra/export/applyAnalysis","/api/ra/export/retryNotification").contains(path))
            || ("p06-comparison".equals(scenario)&&Arrays.asList("/api/ra/cohort/save","/api/ra/cohort/list","/api/ra/ai/compare","/api/ra/ai/compareResult","/api/ra/export/xlsx").contains(path))
            || ("p04".equals(scenario)&&Arrays.asList("/api/ra/ai/compare","/api/ra/ai/compareResult").contains(path));
    }
    static String seedForScenario(String scenario) {
        if("default".equals(scenario))return "seed.sql";
        if("p01b".equals(scenario)||"p03".equals(scenario)||"p05-rule".equals(scenario)||"p05-safety".equals(scenario))return "seed-p01b.sql";
        if("p05-apply-rule".equals(scenario)||"p05-apply-safety".equals(scenario))return "seed-p05.sql";
        if("p02".equals(scenario))return "seed-p02.sql";
        if("p06-cohort".equals(scenario)||applicationScenario(scenario))return "seed-p06.sql";
        if("p04".equals(scenario)||"p06-comparison".equals(scenario))return "seed-p04.sql";
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
    static String parseAuditStatement() throws IOException {
        try(InputStream in=WebLoopbackApiFixture.class.getResourceAsStream("/sql/20261008_ai_parse_audit.sql")) {
            if(in==null)throw new FileNotFoundException("parse-audit-ddl");
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            List<String> sql=new ArrayList<>();for(String item:ddl.split(";"))if(!item.trim().isEmpty())sql.add(item);
            if(sql.size()!=1||!sql.get(0).contains("CREATE TABLE ra_ai_parse_audit ("))throw new IllegalStateException("解析审计DDL声明不符");return sql.get(0);
        }
    }
    void createParseAuditTable(Statement statement) throws SQLException,IOException {
        statement.execute(parseAuditStatement());ownedTables.add("ra_ai_parse_audit");event("table-owned","table","ra_ai_parse_audit");
    }
    static String exportAuditStatement() throws IOException {
        try(InputStream in=WebLoopbackApiFixture.class.getResourceAsStream("/sql/20261008_ai_export_audit.sql")) {
            if(in==null)throw new FileNotFoundException("export-audit-ddl");
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            List<String> sql=new ArrayList<>();for(String item:ddl.split(";"))if(!item.trim().isEmpty())sql.add(item);
            if(sql.size()!=1||!sql.get(0).contains("CREATE TABLE ra_ai_export_audit ("))throw new IllegalStateException("导出审计DDL声明不符");return sql.get(0);
        }
    }
    void createExportAuditTable(Statement statement) throws SQLException,IOException {
        statement.execute(exportAuditStatement());ownedTables.add("ra_ai_export_audit");event("table-owned","table","ra_ai_export_audit");
    }
    static List<String> applicationStatements() throws IOException {
        List<String> statements=new ArrayList<>();
        String[] files={"20261006_export_application.sql","20261008_ai_export_request.sql"};
        for(int i=0;i<files.length;i++)try(InputStream in=WebLoopbackApiFixture.class.getResourceAsStream("/sql/"+files[i])) {
            if(in==null)throw new FileNotFoundException(files[i]);
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            List<String> items=new ArrayList<>();for(String sql:ddl.split(";"))if(!sql.trim().isEmpty())items.add(sql);
            if(items.size()!=1)throw new IllegalStateException("申请DDL声明不符");
            // 仅test-only严格CREATE；拒绝预存表，只有execute成功才登记owned。
            String sql=items.get(0);
            if(i==0)sql=sql.replace("CREATE TABLE IF NOT EXISTS `export_application` (","CREATE TABLE `export_application` (");
            String expected="CREATE TABLE "+(i==0?"`export_application`":"ra_ai_export_request")+" (";
            if(!sql.contains(expected)||sql.contains("IF NOT EXISTS"))throw new IllegalStateException("申请DDL表或严格CREATE不符");
            statements.add(sql);
        }
        return statements;
    }
    void createApplicationTables(Statement statement) throws SQLException,IOException {
        List<String> statements=applicationStatements();
        for(int i=0;i<statements.size();i++){statement.execute(statements.get(i));ownedTables.add(APPLICATION_TABLES[i]);event("table-owned","table",APPLICATION_TABLES[i]);}
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
    @Configuration @EnableWebMvc
    @Import({com.wenwen.controller.ra.AiParseController.class,com.wenwen.service.impl.AiParseServiceImpl.class,com.wenwen.ai.parse.ParseAuditStore.class,com.wenwen.config.AiParseGatewayConfiguration.class})
    public static class ParseInfrastructure extends Infrastructure {
        @Override @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
            factory.setMapperLocations(new Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/AiParseAuditMapper.xml")});return factory.getObject();
        }
        @Bean public AiParseAuditMapper parseAuditMapper(SqlSessionTemplate template){return template.getMapper(AiParseAuditMapper.class);}
    }
    @Configuration @EnableWebMvc
    @Import({com.wenwen.controller.ra.AiExportController.class,com.wenwen.service.impl.AiExportServiceImpl.class,com.wenwen.ai.export.ExportContentReader.class,com.wenwen.ai.export.ExportFieldPolicy.class,com.wenwen.ai.export.OrdinaryXlsxWriter.class,com.wenwen.ai.export.ExportAuditStore.class,ComparisonResultStore.class,ComparisonScopeReader.class})
    public static class ExportInfrastructure extends Infrastructure {
        @Override @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
            factory.setMapperLocations(new Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/AiComparisonRunMapper.xml"),new ClassPathResource("mybatis/AiExportAuditMapper.xml")});return factory.getObject();
        }
        @Bean public AiComparisonRunMapper comparisonMapper(SqlSessionTemplate template){return template.getMapper(AiComparisonRunMapper.class);}
        @Bean public AiExportAuditMapper exportAuditMapper(SqlSessionTemplate template){return template.getMapper(AiExportAuditMapper.class);}
    }
    @Configuration @EnableWebMvc
    @Import({com.wenwen.controller.ra.AiExportController.class,com.wenwen.service.impl.AiExportServiceImpl.class,com.wenwen.ai.export.ExportContentReader.class,com.wenwen.ai.export.ExportFieldPolicy.class,com.wenwen.ai.export.OrdinaryXlsxWriter.class,com.wenwen.ai.export.ExportAuditStore.class})
    public static class ComparisonExportInfrastructure extends ComparisonInfrastructure {
        @Override @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
            factory.setMapperLocations(new Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/PrivateCohortMapper.xml"),new ClassPathResource("mybatis/AiComparisonRunMapper.xml"),new ClassPathResource("mybatis/AiExportAuditMapper.xml")});return factory.getObject();
        }
        @Bean public AiExportAuditMapper exportAuditMapper(SqlSessionTemplate template){return template.getMapper(AiExportAuditMapper.class);}
    }
    @Configuration @EnableWebMvc
    @Import({com.wenwen.controller.ra.AiExportApplicationController.class,com.wenwen.service.impl.AiExportApplicationServiceImpl.class,com.wenwen.ai.export.ExportApplicationStore.class,com.wenwen.config.ExportNotificationConfiguration.class})
    public static class ApplicationInfrastructure extends ExportInfrastructure {
        @Override @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
            // 默认测试DEBUG不得打印旧insertApplication的自由reason绑定参数。
            org.apache.ibatis.session.Configuration configuration=new org.apache.ibatis.session.Configuration();
            configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);factory.setConfiguration(configuration);
            factory.setMapperLocations(new Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/AiComparisonRunMapper.xml"),new ClassPathResource("mybatis/AiExportAuditMapper.xml"),new ClassPathResource("mybatis/ExportMapper.xml"),new ClassPathResource("mybatis/AiExportApplicationMapper.xml")});return factory.getObject();
        }
        @Bean public AiExportApplicationMapper applicationMapper(SqlSessionTemplate template){return template.getMapper(AiExportApplicationMapper.class);}
        @Bean public ExportMapper legacyExportMapper(SqlSessionTemplate template){return template.getMapper(ExportMapper.class);}
    }
    int start(String identity, boolean sqlFailure, File base) throws Exception {
        observed=new ObservedDataSource(raw); observed.failSecondSql=sqlFailure;
        application=new AnnotationConfigWebApplicationContext();
        MutablePropertySources sources=application.getEnvironment().getPropertySources();
        // Servlet property sources are unpopulated here; remove defaults rather than consulting the host.
        for(String name:new String[]{"systemProperties","systemEnvironment","servletContextInitParams","servletConfigInitParams","jndiProperties"}) sources.remove(name);
        Map<String,Object> properties=new LinkedHashMap<>();
        properties.put("ra.ai.api.enabled","true");properties.put("ra.export.xlsx.enabled",(scenario.equals("p06-cohort")||scenario.equals("p06-comparison")||applicationScenario())?"true":"false");
        properties.put("ra.ai.gateway.enabled","false");properties.put("ra.ai.gateway.outbound-approved","false");
        properties.put("ra.ai.analysis.result-ttl-seconds","900");properties.put("ra.ai.analysis.result-max-bytes","8388608");
        properties.put("ra.ai.analysis.cursor-key-base64","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.put("ra.serology.rf-uln","20");properties.put("ra.serology.ccp-uln","25");
        if((scenario.equals("p05-safety")||scenario.equals("p05-apply-safety"))) {
            parsePeer=new AiGatewayLoopbackPeer();
            Map<String,Object> inner=new LinkedHashMap<>();inner.put("filters",Collections.singletonMap("sex","F"));inner.put("matched",Collections.singletonList(com.wenwen.vo.AiCohortVo.object("field","sex","value","F")));inner.put("unrecognized",Collections.emptyList());inner.put("status","OK");inner.put("notice","已生成候选条件，尚未应用。");
            String content=JSON.writeValueAsString(inner);
            parsePeerPlan=parsePeer.json(JSON.writeValueAsString(com.wenwen.vo.AiCohortVo.object("id","p05b-synthetic-response-1","object","chat.completion","created",0,"model","p05b-synthetic-model","choices",Collections.singletonList(com.wenwen.vo.AiCohortVo.object("index",0,"finish_reason","stop","message",com.wenwen.vo.AiCohortVo.object("role","assistant","content",content),"logprobs",null)),"usage",com.wenwen.vo.AiCohortVo.object("prompt_tokens",1,"completion_tokens",1,"total_tokens",2))));
            properties.put("ra.ai.gateway.enabled","true");properties.put("ra.ai.gateway.outbound-approved","true");properties.put("ra.ai.gateway.synthetic-loopback","true");properties.put("ra.ai.gateway.endpoint",parsePeer.endpoint());properties.put("ra.ai.gateway.model","p05b-synthetic-model");properties.put("ra.ai.gateway.api-key","p05b-loopback-key");event("parse-peer-planned","received",parsePeerPlan.received.getCount());
        }
        if(applicationScenario()){applicationPeer=new NotificationSmtpPeer(applicationPeerMode(scenario));properties.putAll(applicationSmtpProperties(applicationPeer.port()));event("application-peer-planned","mode",applicationPeer.mode.name(),"host","127.0.0.1","smtpMode","LOOPBACK_PLAIN","auth",false,"recipientDomain","notification.invalid");}
        sources.addFirst(new MapPropertySource("synthetic-only",properties));
        application.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("observedDataSource",observed));
        final PrincipalProvider principal=(scenario.equals("p01b")||scenario.equals("p03")||scenario.equals("p04"))?this::scenarioPrincipal:("synthetic".equals(identity) ? () -> new TrustedDoctor(101) : () -> {throw new CohortException(401,"UNAUTHENTICATED","请先登录");});
        application.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("principalProvider",principal));
        if((scenario.equals("p01b")||scenario.equals("p03")||scenario.equals("p04"))) {
            scenarioDoctor.set(identity.equals("synthetic")?101:0);
            application.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("clock",scenarioClock));
        }
        application.register(applicationScenario()?ApplicationInfrastructure.class:scenario.equals("p06-comparison")?ComparisonExportInfrastructure.class:scenario.equals("p06-cohort")?ExportInfrastructure.class:parseScenario()?ParseInfrastructure.class:scenario.equals("p04")?ComparisonInfrastructure.class:scenario.equals("p03")?PrivateInfrastructure.class:Infrastructure.class);
        tomcat=new Tomcat();tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.getConnector().setProperty("address","127.0.0.1");tomcat.getConnector().setPort(0);
        Context context=tomcat.addContext("",base.getAbsolutePath());
        context.setParentClassLoader(WebLoopbackApiFixture.class.getClassLoader());
        DispatcherServlet dispatcher=new DispatcherServlet(application);
        HttpServlet servlet=new HttpServlet(){
            protected synchronized void service(HttpServletRequest request,HttpServletResponse response) throws ServletException,IOException {
                if(!allowsHttpPath(scenario,request.getRequestURI())) {response.sendError(404);return;}
                int selectsBefore=observed.selects,analysisInsertsBefore=observed.inserts;
                try {dispatcher.service(request,response);}
                finally {if(parseScenario()&&request.getRequestURI().equals("/api/ra/ai/parse"))event("parse-http-observation","selectDelta",observed.selects-selectsBefore,"analysisInsertDelta",observed.inserts-analysisInsertsBefore,"active",observed.active.get());if(parseScenario()&&request.getRequestURI().equals("/api/ra/ai/applyParse"))event("apply-http-observation","selectDelta",observed.selects-selectsBefore,"analysisInsertDelta",observed.inserts-analysisInsertsBefore,"active",observed.active.get());event("http-observation","borrowed",observed.borrowed,"returned",observed.returned,"active",observed.active.get(),"mysqlFailures",observed.mysqlFailures,"mysqlError",observed.lastMysqlError,"sqlState",observed.lastSqlState,"status",response.getStatus());}
            }
            public void init(ServletConfig config) throws ServletException {super.init(config);dispatcher.init(config);}
            public void destroy(){dispatcher.destroy();super.destroy();}
        };
        Tomcat.addServlet(context,"cohort",servlet).setLoadOnStartup(1);context.addServletMappingDecoded("/*","cohort");
        tomcat.start();
        if(!application.isActive())throw new IllegalStateException("DispatcherServlet context未启动");
        List<String> names=new ArrayList<>();for(org.springframework.core.env.PropertySource<?> source:sources)names.add(source.getName());
        if(sources.contains("systemProperties")||sources.contains("systemEnvironment"))throw new IllegalStateException("环境隔离失败");
        int gatewayBeans=0;
        for(String name:application.getBeanDefinitionNames())if(name.toLowerCase(Locale.ROOT).contains("gateway")){if(!parseScenario())throw new IllegalStateException("不应装配gateway");gatewayBeans++;}
        if(parseScenario()&&(application.getBeansOfType(com.wenwen.ai.parse.AiParseGateway.class).size()!=1||application.getBeansOfType(com.wenwen.ai.parse.AiGatewaySettings.class).size()!=1||application.getBeansOfType(com.wenwen.ai.parse.ParseAuditStore.class).size()!=1))throw new IllegalStateException("解析最小装配不符");
        event("context-isolated","propertySources",names,"gatewayBeans",gatewayBeans,"identity",identity);
        return tomcat.getConnector().getLocalPort();
    }
    @Override public synchronized void close() throws Exception {
        if(closed)return;closed=true;Exception first=null;
        if(tomcat!=null)try{tomcat.stop();}catch(Exception e){first=e;}
        if(application!=null)try{application.close();}catch(Exception e){if(first==null)first=e;}
        if(parseScenario()&&application!=null)event("parse-context-closed","active",application.isActive());
        if(applicationScenario()&&application!=null)event("application-context-closed","active",application.isActive());
        if(applicationPeer!=null)try{applicationPeer.close();event("application-peer-closed","connections",applicationPeer.connections.get(),"dataCommands",applicationPeer.dataCommands.get(),"accepted",applicationPeer.accepted.get(),"authCommands",applicationPeer.authCommands.get(),"messages",applicationPeer.messages.size(),"failure",applicationPeer.failure==null?null:"SYNTHETIC_PEER_FAILED","peerEof",applicationPeer.eof.getCount()==0);}catch(Exception e){if(first==null)first=e;}
        if(parsePeer!=null) {
            try{if(parsePeerPlan!=null&&!peerPlanDiscarded){parsePeer.discardUnused(parsePeerPlan);peerPlanDiscarded=true;}}
            catch(AssertionError e){if(first==null)first=new IllegalStateException("合成解析peer计划已被消费",e);}
            try{parsePeer.close();event("parse-peer-closed","requests",parsePeer.requests.get(),"failure",parsePeer.failure.get()==null?null:"SYNTHETIC_PEER_FAILED","unusedDiscarded",peerPlanDiscarded);}
            catch(Exception e){if(first==null)first=e;}
        }
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
