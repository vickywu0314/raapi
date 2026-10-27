package com.wenwen.ai.cohort;

import com.wenwen.ai.qc.QcSnapshot;
import com.wenwen.ai.qc.QcSnapshotReader;
import com.wenwen.mapper.*;
import com.wenwen.service.impl.ProjectServiceImpl;
import com.wenwen.vo.ProjectsDataVo;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** 固定5.7合成库，保旧统计作对照；不测墙钟、不连接患者库。 */
class ProjectQueryPerformanceTest extends MissingDataHttpFixture {
    PatientMapper patients;ProjectMapper projects;SqlSessionFactory factory;
    @BeforeEach @Override void setup() throws Exception {
        super.setup();
        try(Connection c=raw.getConnection()) {assertTrue(c.getMetaData().getDatabaseProductVersion().startsWith("5.7."),"本片SQL验收必须使用MySQL5.7");}
        try(InputStream in=getClass().getResourceAsStream("/ai-cohort/p02e/schema.sql")) {
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            for(String statement:ddl.split(";"))if(!statement.trim().isEmpty()){sql(statement);if(statement.contains("CREATE TABLE"))ownedTables.add("`user`");}
        }
        // 仅合成夹具复现存量单列FK索引；没有对生产库新增索引或执行迁移。
        sql("CREATE INDEX synthetic_relation_patient ON patient_relation_doctor(patient_id)");
        sql("CREATE INDEX synthetic_relation_doctor ON patient_relation_doctor(doctor_id)");
        sql("CREATE INDEX synthetic_visit_patient ON patient_follow_up_history(patient_basic_info_id)");
        factory=context.getBean(SqlSessionFactory.class);
        try(InputStream in=getClass().getResourceAsStream("/mybatis/QualityMapper.xml")) {
            new XMLMapperBuilder(in,factory.getConfiguration(),"mybatis/QualityMapper.xml",factory.getConfiguration().getSqlFragments()).parse();
        }
        SqlSessionTemplate session=new SqlSessionTemplate(factory);patients=session.getMapper(PatientMapper.class);projects=session.getMapper(ProjectMapper.class);observed.reset();
    }
    private QcSnapshotReader reader(){return new QcSnapshotReader(patients,projects,context.getBean(org.springframework.transaction.PlatformTransactionManager.class));}
    private ProjectServiceImpl service(){ProjectServiceImpl value=new ProjectServiceImpl();ReflectionTestUtils.setField(value,"qcReader",reader());ReflectionTestUtils.setField(value,"projectMapper",projects);ReflectionTestUtils.setField(value,"followUpCycleDays",90);return value;}
    private static int number(Map<String,Object> value,String key){return ((Number)value.get(key)).intValue();}
    private static Map<String,Object> scope(){Map<String,Object> scope=new HashMap<>();scope.put("doctorId",null);scope.put("patientId",null);return scope;}

    @Test void projectUsesFourSelectsWhileQcRemainsOneShortRepeatableRead() {
        ProjectsDataVo result=service().getProjectsData();
        assertEquals(2,result.getTotalPatients());assertEquals(2,result.getTrackingPatients());assertEquals(1,result.getPendingQcIssues());
        assertEquals(4,observed.selects);assertEquals(2,observed.borrowed);snapshotQueries(3);
    }
    @Test void enrollByMonthUsesOverviewRaScopeLastTwelveMonthsWithinSameStatement() throws Exception {
        sql("DELETE FROM patient_follow_up_history");sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_basic_info");
        String[][] patients={{"1","2026-10-05 08:00:00",null},{"2","2025-11-01 00:00:00",null},{"3","2025-10-31 23:59:59",null},{"4",null,"2026-03-15"},
            {"5","2026-05-01",null},{"6","2026-05-02",null},{"7","2026-11-01",null},{"8","2026-06-01",null},{"9",null,null},{"10","2026-10-31 23:00:00",null}};
        for(String[] p:patients)sql("INSERT INTO patient_basic_info(id,name,jws,create_date,createDate) VALUES ("+p[0]+",'synthetic','record',"+(p[1]==null?"NULL":"'"+p[1]+"'")+","+(p[2]==null?"NULL":"'"+p[2]+"'")+")");
        // 5 只有 AS(6) 关系、8 没有任何关系 → 不算；6 已脱落仍算（同 enrolledPatients 含已脱落）；同一患者两条 RA 关系只算一次
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(202,1,7,0),(101,2,1,0),(101,3,0,0),(101,4,7,0),(101,5,6,0),(101,6,0,1),(101,7,0,0),(101,9,0,0),(101,10,4,0)");
        ProjectServiceImpl service=service();
        ReflectionTestUtils.setField(service,"clock",java.time.Clock.fixed(java.time.Instant.parse("2026-10-19T04:00:00Z"),java.time.ZoneOffset.UTC));
        observed.reset();
        ProjectsDataVo result=service.getProjectsData();
        assertEquals(4,observed.selects,"按月入组并入同一条统计 SQL，不增加查询");
        List<Map<String,Object>> months=result.getEnrollByMonth();
        assertEquals(12,months.size());
        assertEquals("2025-11",months.get(0).get("month"));assertEquals("2026-10",months.get(11).get("month"));
        Map<String,Integer> counts=new LinkedHashMap<>();int sum=0;
        for(Map<String,Object> m:months){counts.put((String)m.get("month"),(Integer)m.get("count"));sum+=(Integer)m.get("count");}
        assertEquals(1,counts.get("2025-11").intValue());assertEquals(1,counts.get("2026-03").intValue());
        assertEquals(1,counts.get("2026-05").intValue());assertEquals(2,counts.get("2026-10").intValue());
        assertEquals(0,counts.get("2026-06").intValue());assertEquals(5,sum);
        assertEquals(8,result.getEnrolledPatients(),"总数范围同一 RA 口径（含无建档日期、范围外月份）");
    }
    @Test void enrollByMonthFillsMissingMonthsWithZero() {
        List<Map<String,Object>> months=ProjectServiceImpl.enrollByMonth(java.time.YearMonth.of(2025,12),null);
        assertEquals(12,months.size());assertEquals("2026-11",months.get(11).get("month"));
        for(Map<String,Object> m:months)assertEquals(0,m.get("count"));
        assertEquals(3,ProjectServiceImpl.enrollByMonth(java.time.YearMonth.of(2025,12),"2026-01=3").get(1).get("count"));
    }
    @Test void consolidatedMetricsMatchIndependentBoundaryTableAndAllOriginalQueries() throws Exception {
        sql("DELETE FROM patient_follow_up_history");sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_basic_info");
        for(int i=1;i<=12;i++)sql("INSERT INTO patient_basic_info(id,name,jws,create_date) VALUES ("+i+",'synthetic','record','2026-01-01')");
        sql("UPDATE patient_basic_info SET create_date='2026-07-21 12:00:00' WHERE id=4");
        sql("UPDATE patient_basic_info SET create_date='2026-10-01' WHERE id=5");
        sql("UPDATE patient_basic_info SET create_date=NULL WHERE id=6");
        sql("UPDATE patient_basic_info SET createDate='2099-01-01' WHERE id=9");
        sql("UPDATE patient_basic_info SET create_date=NULL,createDate='2026-01-01' WHERE id=10");
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,1,1,0),(101,2,0,0),(202,2,7,1),(101,3,0,0),(101,3,6,1),(101,4,0,0),(101,5,0,0),(101,6,0,0),(101,7,6,0),(202,8,0,0),(101,9,0,0),(101,10,0,0),(101,12,0,0),(101,13,0,0)");
        int[] owners={2,3,4,7,8,9,10,11,12,13};
        String[] dates={"2026-01-01","2026-07-21 12:00:00","2026-10-19 12:00:00","2026-10-19",null,"2026-01-01",null,"2026-10-19","2099-01-01","2026-10-19"};
        for(int i=0;i<owners.length;i++)visit(100+i,owners[i],0,"{\"result\":{\"crpScore\":0}}",dates[i],owners[i]==9||owners[i]==10?"2026-10-19 12:00:00":null," record ");
        visit(200,1,6,"{\"result\":{\"crpScore\":0}}","2026-10-19",null,"record");
        sql("UPDATE patient_follow_up_history SET zlfa=' record '");
        try(SqlSession session=factory.openSession()) {
            try(Statement statement=session.getConnection().createStatement()){statement.execute("SET timestamp=UNIX_TIMESTAMP('2026-10-19 12:00:00')");}
            ProjectMapper mapper=session.getMapper(ProjectMapper.class);Map<String,Object> params=Collections.singletonMap("cycleDays",90);
            Map<String,Object> metrics=mapper.countProjectMetrics(params);
            assertEquals(9,number(metrics,"activeCount"));assertEquals(6,number(metrics,"dueCount"));assertEquals(2,number(metrics,"doneCount"));
            assertEquals(3,number(metrics,"pendingCount"));assertEquals(9,number(metrics,"usableCount"));
            assertEquals(mapper.countActivePatients(),number(metrics,"activeCount"));Map<String,Object> plan=mapper.countFollowUpPlan(params);
            assertEquals(number(plan,"dueCount"),number(metrics,"dueCount"));assertEquals(number(plan,"doneCount"),number(metrics,"doneCount"));
            assertEquals(mapper.countPendingFollowUp(params),number(metrics,"pendingCount"));assertEquals(mapper.countUsableRecords(),number(metrics,"usableCount"));
        }
    }
    @Test void usableSentinelsAndEmptyActiveSetKeepGlobalRaRecordScope() throws Exception {
        for(String value:Arrays.asList(null,""," "," {} "," [] "," null ","ordinary text","{\"x\":1}")) {
            try(Connection c=raw.getConnection();PreparedStatement statement=c.prepareStatement("UPDATE patient_follow_up_history SET bqpg=?,fzjc=?,zlfa=?")) {
                statement.setString(1,value);statement.setString(2,value);statement.setString(3,value);statement.executeUpdate();
            }
            int expected="ordinary text".equals(value)||"{\"x\":1}".equals(value)?2:0;
            assertEquals(expected,number(projects.countProjectMetrics(Collections.singletonMap("cycleDays",90)),"usableCount"));
        }
        sql("DELETE FROM patient_basic_info");
        Map<String,Object> empty=projects.countProjectMetrics(Collections.singletonMap("cycleDays",90));
        for(String key:Arrays.asList("activeCount","dueCount","doneCount","pendingCount"))assertEquals(0,number(empty,key));
        assertEquals(2,number(empty,"usableCount"),"旧口径包括没有当前RA关系/患者主行的RA访视");
    }
    @Test void projectQcKeepsEarliestTimestampTiesAndAcceptsUndatedHistoricalScores() throws Exception {
        sql("UPDATE patient_follow_up_history SET follow_up_date='2024-01-01 08:00:00',fzjc='' WHERE id=10");
        visit(11,1,0,"{}","2024-01-01 20:00:00",null,"record");
        assertTrue(reader().readProject().getQc().get(1L).getMissingCodes().contains("M_BASELINE_LAB"));
        visit(12,1,0,"{}","2024-01-01 08:00:00",null,"record");
        assertFalse(reader().readProject().getQc().get(1L).getMissingCodes().contains("M_BASELINE_LAB"));
        sql("DELETE FROM patient_follow_up_history WHERE id=12");visit(13,1,6,"{}","2024-01-01 08:00:00",null,"record");
        assertTrue(reader().readProject().getQc().get(1L).getMissingCodes().contains("M_BASELINE_LAB"));
        sql("UPDATE patient_follow_up_history SET follow_up_date=NULL,followUpDate=NULL WHERE patient_basic_info_id=1");
        QcSnapshot snapshot=reader().readProject();assertTrue(snapshot.getQc().get(1L).getMissingCodes().contains("M_BASELINE_LAB"));
        assertFalse(snapshot.getQc().get(1L).getMissingCodes().contains("M_DAS28"));assertTrue(snapshot.getVisits().isEmpty());
        for(Map<String,Object> row:patients.listQcDas28(scope()))assertEquals(new HashSet<>(Arrays.asList("patientId","bqpg")),row.keySet());
        try(SqlSession session=factory.openSession()) {
            for(Map<String,Object> row:session.getMapper(QualityMapper.class).listVisits(101L)) {
                assertFalse(row.containsKey("fzjc"));assertTrue(row.containsKey("visitId"));assertTrue(row.containsKey("patientId"));
            }
        }
    }
    @Test void mysql57CanExplainNarrowQcConsolidatedMetricsAndDetailQueries() throws Exception {
        Map<String,Object> detail=new HashMap<>();detail.put("doctorId",101L);detail.put("patientId",1L);detail.put("asOf","2026-10-19");
        explain("com.wenwen.mapper.PatientMapper.listQcDas28",scope());
        explain("com.wenwen.mapper.ProjectMapper.countProjectMetrics",Collections.singletonMap("cycleDays",90));
        explain("com.wenwen.mapper.PatientMapper.listPatients",detail);
    }
    private void explain(String id,Map<String,Object> params) throws Exception {
        MappedStatement statement=factory.getConfiguration().getMappedStatement(id);BoundSql sql=statement.getBoundSql(params);
        try(Connection connection=raw.getConnection();PreparedStatement explain=connection.prepareStatement("EXPLAIN "+sql.getSql())) {
            new DefaultParameterHandler(statement,params,sql).setParameters(explain);int rows=0;
            try(ResultSet plan=explain.executeQuery()) {while(plan.next()) {rows++;System.out.println("SQL_EXPLAIN statement="+id+" type="+plan.getString("type")+" key="+plan.getString("key")+" rows="+plan.getLong("rows")+" extra="+plan.getString("Extra"));}}
            assertTrue(rows>0);
        }
    }
}
