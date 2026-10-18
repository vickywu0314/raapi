package com.wenwen.testsupport;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.io.InputStream;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import com.wenwen.ai.result.AnalysisRun;
import com.wenwen.mapper.AiAnalysisRunMapper;

/** 所有写入只在显式loopback合成库，真实5.7语法、容量与失败原子性回归。 */
class Mysql57CompatibilityTest {
    Connection connection;
    final List<String> tables = new ArrayList<>();
    DriverManagerDataSource source;
    @BeforeEach void connect() throws Exception {
        String url=System.getenv("RA_TEST_MYSQL_URL"), user=System.getenv("RA_TEST_MYSQL_USER"), password=System.getenv("RA_TEST_MYSQL_PASSWORD");
        if(url==null||user==null||password==null)throw new IllegalStateException("需要显式合成MySQL");
        java.net.URI uri=java.net.URI.create(url.substring(5));
        if(!url.startsWith("jdbc:mysql://")||!("127.0.0.1".equals(uri.getHost())||"localhost".equals(uri.getHost()))||uri.getUserInfo()!=null||!"/ra_synthetic_test".equals(uri.getPath()))throw new IllegalStateException("仅允许loopback合成库");
        source=new DriverManagerDataSource(url,user,password);source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        connection=source.getConnection();Mysql57Contract.verify(connection);
    }
    @AfterEach void close() throws Exception {
        if(connection!=null){execute("ROLLBACK");connection.setAutoCommit(true);try{for(int i=tables.size()-1;i>=0;i--)execute("DROP TABLE `"+tables.get(i)+"`");}finally{connection.close();}}
    }
    void execute(String sql)throws SQLException{try(Statement s=connection.createStatement()){s.execute(sql);}}
    String text(String sql)throws SQLException{try(Statement s=connection.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    void create(String name,String ddl)throws SQLException{execute(ddl);tables.add(name);}
    String resource(String path)throws Exception{try(InputStream in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return new Scanner(in,"UTF-8").useDelimiter("\\A").next();}}
    void script(String path)throws Exception{for(String sql:resource(path).replaceAll("(?m)^--.*$","").split(";"))if(!sql.trim().isEmpty())execute(sql);}
    void patients()throws Exception{create("patient_basic_info","CREATE TABLE patient_basic_info(id BIGINT PRIMARY KEY,create_date DATETIME,createDate DATETIME,study_no VARCHAR(32),UNIQUE KEY uk_study_no(study_no)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}

    @Test void wrongEngineAndVersionAreRejectedBeforeFixtureWrites(){
        for(String version:Arrays.asList("8.4.11","8.0.43","5.6.51","5.7.43","5.7.440","5.7.44-MariaDB"))assertThrows(IllegalStateException.class,()->Mysql57Contract.requireVersion("MySQL",version));
        assertThrows(IllegalStateException.class,()->Mysql57Contract.requireVersion("H2","5.7.44"));
        Mysql57Contract.requireVersion("MySQL","5.7.44");Mysql57Contract.requireVersion("MySQL","5.7.44-log");
    }
    @Test void syntheticPortsStayExplicitAndReadonlyOcrBoundaryFailsLoud() {
        for(int port:Arrays.asList(43306,43357))Mysql57Contract.requireSyntheticUrl("jdbc:mysql://127.0.0.1:"+port+"/ra_synthetic_test");
        for(String url:Arrays.asList("jdbc:mysql://127.0.0.1/ra_synthetic_test","jdbc:mysql://127.0.0.1:43357/other","jdbc:mysql://user@127.0.0.1:43357/ra_synthetic_test","jdbc:mysql://example.invalid:43357/ra_synthetic_test","jdbc:postgresql://127.0.0.1:43357/ra_synthetic_test"))assertThrows(IllegalStateException.class,()->Mysql57Contract.requireSyntheticUrl(url));
        com.wenwen.ocr.OcrService unread=UnreadOcrBoundary.create();
        assertThrows(AssertionError.class,()->unread.preflightCreate(Collections.emptyList(),101L,1L));
        assertThrows(AssertionError.class,()->unread.linkImages(Collections.emptyList(),101L,1L,1L));
        assertThrows(AssertionError.class,()->unread.canView("unused",101L));
    }
    static int[] escapeCandidates() throws Exception {
        java.lang.reflect.Field field=com.wenwen.service.impl.PatientServiceImpl.class.getDeclaredField("LIKE_ESCAPE_CANDIDATES");field.setAccessible(true);return ((int[])field.get(null)).clone();
    }
    @Test void everyFixedWhitelistEscapeBindsUnder57() throws Exception {
        int[] candidates=escapeCandidates();assertEquals(6492,candidates.length);
        for(int value:candidates)assertTrue((value>=33&&value<=126&&value!='%'&&value!='_')||(value>=0xE000&&value<=0xF8FF));
        try(PreparedStatement statement=connection.prepareStatement("SELECT CONVERT(? USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE CONVERT(? USING utf8mb4) COLLATE utf8mb4_unicode_ci ESCAPE ?")) {
            for(int value:candidates){String escape=new String(Character.toChars(value));statement.setString(1,"%_");statement.setString(2,escape+"%"+escape+"_");statement.setString(3,escape);try(ResultSet result=statement.executeQuery()){assertTrue(result.next());assertEquals(1,result.getInt(1),"escape U+"+Integer.toHexString(value));}}
        }
    }
    @Test void whitelistExhaustionFailsExplicitlyWithoutDroppingTheKeyword() throws Exception {
        String exhausted=java.util.Arrays.stream(escapeCandidates()).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
        java.lang.reflect.Method method=com.wenwen.service.impl.PatientServiceImpl.class.getDeclaredMethod("likeEscape",String.class);method.setAccessible(true);
        java.lang.reflect.InvocationTargetException failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,exhausted));
        assertTrue(failure.getCause() instanceof IllegalArgumentException);assertTrue(failure.getCause().getMessage().contains("全部可用转义字符"));
    }
    @Test void historicalMysql8StatementsReallyAreUnavailable()throws Exception{
        assertThrows(SQLException.class,()->execute("SELECT ROW_NUMBER() OVER (ORDER BY 1)"));
        assertThrows(SQLException.class,()->execute("SELECT * FROM JSON_TABLE('[1]', '$[*]' COLUMNS(n INT PATH '$')) t"));
        assertThrows(SQLException.class,()->execute("WITH t AS (SELECT 1 n) SELECT n FROM t"));
    }
    @Test void studyNumbersUseNumericIdDatesNullFallbackAndPreserveExistingValues()throws Exception{
        patients();execute("INSERT INTO patient_basic_info VALUES (90,'2026-01-02 15:00:00',NULL,NULL),(3,NULL,'2026-01-02 08:00:00',NULL),(11,'2026-01-02',NULL,NULL),(-2,'2026-01-03','2026-01-02',NULL),(500,NULL,NULL,NULL),(600,'2026-01-02',NULL,'EXISTING'),(700,NULL,NULL,'')");
        script("/sql/20261018_backfill_study_no_mysql57.sql");
        assertEquals("RA-20260102-00001",text("SELECT study_no FROM patient_basic_info WHERE id=3"));
        assertEquals("RA-20260102-00002",text("SELECT study_no FROM patient_basic_info WHERE id=11"));
        assertEquals("RA-20260102-00003",text("SELECT study_no FROM patient_basic_info WHERE id=90"));
        assertEquals("RA-20260103-00001",text("SELECT study_no FROM patient_basic_info WHERE id=-2"));
        assertEquals(text("SELECT CONCAT('RA-',DATE_FORMAT(CURRENT_DATE(),'%Y%m%d'),'-00001')"),text("SELECT study_no FROM patient_basic_info WHERE id=500"));
        assertEquals("EXISTING",text("SELECT study_no FROM patient_basic_info WHERE id=600"));assertEquals("",text("SELECT study_no FROM patient_basic_info WHERE id=700"));
        script("/sql/20261018_backfill_study_no_mysql57.sql");assertEquals("0",text("SELECT COUNT(*) FROM patient_basic_info WHERE study_no IS NULL"));
    }
    @Test void studyNumberConflictRollsBackAllRowsAndCanRetryFromStart()throws Exception{
        patients();execute("INSERT INTO patient_basic_info VALUES (1,'2026-01-02',NULL,NULL),(2,'2026-01-02',NULL,NULL),(99,'2026-01-02',NULL,'RA-20260102-00002')");
        SQLException rejection=null;
        try{script("/sql/20261018_backfill_study_no_mysql57.sql");}catch(SQLException expected){rejection=expected;}
        // 在任何显式ROLLBACK/cleanup前观察单条UPDATE失败的原子性，避免测试自己修复部分写入。
        assertEquals("2",text("SELECT COUNT(*) FROM patient_basic_info WHERE study_no IS NULL"));
        assertEquals("RA-20260102-00002",text("SELECT study_no FROM patient_basic_info WHERE id=99"));
        assertNotNull(rejection,"编号冲突必须明确拒绝");execute("ROLLBACK");
        execute("DELETE FROM patient_basic_info WHERE id=99");script("/sql/20261018_backfill_study_no_mysql57.sql");
        assertEquals("RA-20260102-00001",text("SELECT study_no FROM patient_basic_info WHERE id=1"));assertEquals("RA-20260102-00002",text("SELECT study_no FROM patient_basic_info WHERE id=2"));
    }
    @Test void fullUtf8mb4AliasUniquenessUsesDynamicRowsWithoutPrefixTruncation()throws Exception{
        script("/sql/20261012_ocr_lab_alias.sql");tables.add("ocr_lab_alias");
        String prefix=String.join("",Collections.nCopies(199,"\uD83D\uDE00"));
        try(PreparedStatement insert=connection.prepareStatement("INSERT INTO ocr_lab_alias(alias,field) VALUES (?,?)")){
            for(String last:Arrays.asList("A","B")){insert.setString(1,prefix+last);insert.setString(2,"cfydb");assertEquals(1,insert.executeUpdate());}
            insert.setString(1,prefix+"A");assertThrows(SQLException.class,insert::executeUpdate);
        }
        assertEquals("2",text("SELECT COUNT(*) FROM ocr_lab_alias"));
        assertEquals("Dynamic",text("SELECT ROW_FORMAT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ocr_lab_alias'"));
        assertNull(text("SELECT SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ocr_lab_alias' AND INDEX_NAME='uk_alias'"));
    }
    @Test void retainedMapperRoundTripsEightMiBBeyondMysql57DefaultPacket()throws Exception{
        script("/sql/20261007_ai_analysis_run.sql");tables.add("ra_ai_analysis_run");
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setMapperLocations(new org.springframework.core.io.Resource[]{new ClassPathResource("mybatis/AiAnalysisRunMapper.xml")});
        AiAnalysisRunMapper mapper=new SqlSessionTemplate(factory.getObject()).getMapper(AiAnalysisRunMapper.class);
        byte[] bytes=new byte[8388608];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i%251);
        AnalysisRun run=new AnalysisRun();run.setId("00000000-0000-0000-0000-000000000057");run.setOwnerDoctorId(101L);run.setScopeFingerprint(String.join("",Collections.nCopies(64,"a")));run.setSortKey("test");run.setPayloadVersion(1);run.setPayload(bytes);run.setPayloadSha256(com.wenwen.ai.result.AnalysisValues.sha256(bytes));run.setCreatedAtMs(1L);run.setExpiresAtMs(1000L);
        assertEquals(1,mapper.insert(run));AnalysisRun stored=mapper.find(run.getId());assertArrayEquals(bytes,stored.getPayload());assertEquals(run.getPayloadSha256(),stored.getPayloadSha256());
    }
}
