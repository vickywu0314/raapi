package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.mapper.*;
import com.wenwen.service.impl.*;
import com.wenwen.vo.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class LegacyQcConsumersTest extends MissingDataHttpFixture {
    PatientMapper patients;
    ProjectMapper projects;
    @BeforeEach @Override void setup() throws Exception {
        super.setup();
        try(java.io.InputStream in=getClass().getResourceAsStream("/ai-cohort/p02e/schema.sql")) {
            String ddl=new Scanner(in,StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            for(String statement:ddl.split(";")) if(!statement.trim().isEmpty()) { sql(statement); if(statement.contains("CREATE TABLE")) ownedTables.add("`user`"); }
        }
        SqlSessionTemplate session=new SqlSessionTemplate(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class));
        patients=session.getMapper(PatientMapper.class); projects=session.getMapper(ProjectMapper.class);
        observed.reset();
    }
    PatientServiceImpl legacy() {
        PatientServiceImpl service=new PatientServiceImpl();
        ReflectionTestUtils.setField(service,"patientMapper",patients);
        ReflectionTestUtils.setField(service,"qcReader",new com.wenwen.ai.qc.QcSnapshotReader(patients,projects,context.getBean(org.springframework.transaction.PlatformTransactionManager.class)));
        ReflectionTestUtils.setField(service,"clock",clock);
        ReflectionTestUtils.setField(service,"rfUln",20.0); ReflectionTestUtils.setField(service,"ccpUln",25.0);
        return service;
    }
    void rawAssessment(String raw) throws Exception {
        try(java.sql.Connection c=this.raw.getConnection();java.sql.PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET bqpg=? WHERE id=10")) { s.setString(1,raw);s.executeUpdate(); }
    }
    @Test void rawDecimalPrecisionAgreesAcrossAiListAndDetail() throws Exception {
        rawAssessment("{\"result\":{\"crpScore\":2.2949999999999999999}}");
        JsonNode ai=success("{}").path("patients").path("items").get(0);
        assertEquals(new BigDecimal("2.29"),ai.path("das28At").decimalValue());
        PatientItemVo list=legacy().listPatients(101L,"synthetic-one",null,null,1,20).getItems().get(0);
        PatientDetailVo detail=legacy().getPatientDetail(101L,1L);
        assertEquals(new BigDecimal("2.29"),list.getLatestDas28()); assertEquals("remission",list.getDas28Activity());
        assertEquals(new BigDecimal("2.29"),detail.getLatestDas28()); assertEquals("remission",detail.getDas28Activity());
    }
    void sixPatientTable() throws Exception {
        sql("DELETE FROM patient_follow_up_history"); sql("DELETE FROM patient_relation_doctor"); sql("DELETE FROM patient_basic_info");
        for(int id=1;id<=8;id++) sql("INSERT INTO patient_basic_info(id,name,jws,create_date) VALUES ("+id+",'synthetic-p"+id+"','record','2023-01-01')");
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,1,1,0),(101,2,0,0),(101,3,0,0),(101,4,0,0),(101,5,0,1),(101,6,0,0),(202,7,0,0),(101,8,6,0)");
        for(int id=1;id<=8;id++) {
            String assessment=id==2?"{\"result\":{\"esrScore\":8}}":id==6?"{\"result\":{\"crpScore\":-1}}":"{\"result\":{\"crpScore\":"+(id==1?0:3)+"}}";
            visit(id*10,id,id==8?6:0,assessment,"2024-01-0"+(id<=6?7-id:1),null,id==3||id==6?"": "record");
        }
        sql("UPDATE patient_basic_info SET jws='' WHERE id IN (4,6)");
        sql("UPDATE patient_follow_up_history SET zlfa=CASE WHEN patient_basic_info_id IN (5,6) THEN '' ELSE 'record' END");
        sql("UPDATE patient_basic_info SET happen_date='2024-02-01',confirm_date='2024-01-01',smoke=0,smoke_years=1 WHERE id=7");
        observed.reset();
    }
    @Test void completenessFiltersBeforeCountsAndPaginationForDoctorUniverse() throws Exception {
        sixPatientTable();
        PatientsListVo first=legacy().listPatients(101L,null,null,"missing",1,1);
        assertEquals(6,first.getTotalPatients()); assertEquals(5,first.getIncompleteCount()); assertEquals(5,first.getTotal());
        assertEquals(2L,first.getItems().get(0).getPatientId()); assertEquals(Collections.singletonList("缺 DAS28 评分"),first.getItems().get(0).getMissingItems());
        PatientsListVo second=legacy().listPatients(101L,null,null,"missing",2,1);
        assertEquals(5,second.getTotal()); assertEquals(3L,second.getItems().get(0).getPatientId());
        PatientsListVo complete=legacy().listPatients(101L,null,null,"complete",1,20);
        assertEquals(1,complete.getTotal()); assertEquals(1L,complete.getItems().get(0).getPatientId()); assertFalse(complete.getItems().get(0).isIncomplete());
    }
    ProjectServiceImpl project() {
        ProjectServiceImpl service=new ProjectServiceImpl(); ReflectionTestUtils.setField(service,"projectMapper",projects);
        ReflectionTestUtils.setField(service,"followUpCycleDays",90);
        ReflectionTestUtils.setField(service,"qcReader",new com.wenwen.ai.qc.QcSnapshotReader(patients,projects,context.getBean(org.springframework.transaction.PlatformTransactionManager.class))); return service;
    }
    @Test void globalProjectCountsFourMissingAndTwoLogicRulesIndependently() throws Exception {
        sixPatientTable();
        ProjectsDataVo value=project().getProjectsData();
        assertEquals(7,value.getTotalPatients()); assertEquals(7,value.getEnrolledPatients());
        assertEquals(10,value.getPendingQcIssues()); assertEquals(6,value.getQcIssuePatients());
        assertEquals(new BigDecimal("14.3"),value.getDataQualityRate());
        PatientsListVo onlyLogic=legacy().listPatients(202L,null,null,"complete",1,20);
        assertEquals(1,onlyLogic.getTotal()); assertEquals(7L,onlyLogic.getItems().get(0).getPatientId()); assertFalse(onlyLogic.getItems().get(0).isIncomplete());
    }
    @Test void duplicateSerologyModuleCannotRecoverRfOrOtherCcpScalar() throws Exception {
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":10,\"lfsyz\":35,\"kccpkt\":25}' WHERE id=10");
        JsonNode ai=success("{}").path("patients").path("items").get(0);
        assertEquals("UNKNOWN",ai.path("clinical").path("sero").asText());
        assertEquals("COMPLETE",ai.path("qc").path("status").asText(),"原检验M仍以非空记录存在判定");
        PatientItemVo list=legacy().listPatients(101L,"synthetic-one",null,null,1,20).getItems().get(0);
        PatientDetailVo detail=legacy().getPatientDetail(101L,1L);
        assertNull(list.getSubtype()); assertEquals("untested",list.getRf().getStatus());assertEquals("untested",list.getCcp().getStatus());
        assertNull(detail.getSubtype());assertEquals("untested",detail.getRf().getStatus());assertEquals("untested",detail.getCcp().getStatus());
    }
    @Test void crossConsumersShareIndependentFourMissingTableAndAllFilteredIds() throws Exception {
        sixPatientTable();
        assertEquals(Arrays.asList("3","4","5","2","6"),ids(success("{\"filters\":{\"data\":\"missing\"}}")));
        assertEquals(Collections.singletonList("1"),ids(success("{\"filters\":{\"data\":\"complete\"}}")));
        String[][] codes={{},{"M_DAS28"},{"M_BASELINE_LAB"},{"M_COMORBIDITY"},{"M_MEDICATION"},{"M_BASELINE_LAB","M_COMORBIDITY","M_DAS28","M_MEDICATION"}};
        String[][] labels={{},{"缺 DAS28 评分"},{"缺基线检验"},{"缺合并疾病记录"},{"缺用药史"},{"缺基线检验","缺合并疾病记录","缺 DAS28 评分","缺用药史"}};
        JsonNode allData=success("{}");
        for(int i=0;i<6;i++) {
            assertEquals(json.valueToTree(codes[i]),patientById(allData,i+1).path("qc").path("missingCodes"));
            PatientDetailVo detail=legacy().getPatientDetail(101L,(long)i+1);
            assertEquals(Arrays.asList(labels[i]),detail.getMissingItems()); assertEquals(i!=0,detail.isIncomplete());
        }
        PatientsListVo keyword=legacy().listPatients(101L,"SYNTHETIC-p3",null,"missing",1,20);
        assertEquals(1,keyword.getTotal());assertEquals(3L,keyword.getItems().get(0).getPatientId());assertEquals(6,keyword.getTotalPatients());assertEquals(5,keyword.getIncompleteCount());
        assertEquals(Collections.singletonList(5L),legacy().listPatientIds(101L,null,"withdrawn","missing"));
        assertEquals(Arrays.asList(2L,3L,4L,5L,6L),legacy().listPatientIds(101L,null,null,"missing"));
        assertEquals(Collections.singletonList(1L),legacy().listPatientIds(101L,null,null,"complete"));
        PatientsListVo beyond=legacy().listPatients(101L,null,null,"missing",Integer.MAX_VALUE,1);
        assertEquals(5,beyond.getTotal());assertEquals(6,beyond.getTotalPatients());assertEquals(5,beyond.getIncompleteCount());assertTrue(beyond.getItems().isEmpty());
        PatientsListVo noMatch=legacy().listPatients(101L,"no-matching-patient",null,"missing",1,20);
        assertEquals(0,noMatch.getTotal());assertEquals(6,noMatch.getTotalPatients());assertEquals(5,noMatch.getIncompleteCount());assertTrue(noMatch.getItems().isEmpty());
        PatientsListVo empty=legacy().listPatients(303L,null,null,"complete",1,20);
        assertEquals(0,empty.getTotal());assertEquals(0,empty.getTotalPatients());assertEquals(0,empty.getIncompleteCount());assertTrue(empty.getItems().isEmpty());
        assertTrue(legacy().listPatientIds(303L,null,null,"missing").isEmpty());
        assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);
    }
    JsonNode qc(long id) throws Exception { return success("{\"filters\":{\"ids\":[\""+id+"\"]}}").path("patients").path("items").get(0).path("qc"); }
    void baseline(long id,boolean missing) throws Exception {
        assertEquals(missing,qc(id).path("missingCodes").toString().contains("M_BASELINE_LAB"));
        assertEquals(missing,legacy().getPatientDetail(101L,id).getMissingItems().contains("缺基线检验"));
    }
    @Test void baselineUsesExactEarliestTimestampTiesAndRaDates() throws Exception {
        sixPatientTable(); sql("UPDATE patient_follow_up_history SET follow_up_date='2024-01-04 08:00:00',fzjc='' WHERE id=30");
        visit(31,3,0,"{}","2024-01-04 20:00:00",null,"record"); baseline(3,true);
        visit(32,3,0,"{}","2024-01-04 08:00:00",null,"record");baseline(3,false);
        sql("DELETE FROM patient_follow_up_history WHERE id=32");
        visit(33,3,6,"{}","2024-01-04 08:00:00",null,"record");baseline(3,true);
        sql("UPDATE patient_follow_up_history SET follow_up_date=null,followUpDate=null WHERE patient_basic_info_id=1");baseline(1,true);
        assertFalse(qc(1).path("missingCodes").toString().contains("M_DAS28"));
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=2");baseline(2,true);
    }
    @Test void originalThreeMissingSentinelsKeepNonemptyTextAndNonRaCannotFill() throws Exception {
        for(String value:new String[]{null,""," "," {} "," [] "," null ","ordinary text","{\"notRequired\":\"value\"}"}) {
            try(java.sql.Connection c=raw.getConnection()) {
                try(java.sql.PreparedStatement p=c.prepareStatement("UPDATE patient_basic_info SET jws=? WHERE id=1")) {p.setString(1,value);p.executeUpdate();}
                try(java.sql.PreparedStatement p=c.prepareStatement("UPDATE patient_follow_up_history SET fzjc=?,zlfa=? WHERE id=10")) {p.setString(1,value);p.setString(2,value);p.executeUpdate();}
            }
            boolean missing=value==null || Arrays.asList(""," "," {} "," [] "," null ").contains(value);
            assertEquals(json.readTree(missing?"[\"M_BASELINE_LAB\",\"M_COMORBIDITY\",\"M_MEDICATION\"]":"[]"),qc(1).path("missingCodes"),String.valueOf(value));
            assertEquals(missing,legacy().getPatientDetail(101L,1L).isIncomplete());
        }
        sql("UPDATE patient_basic_info SET jws='' WHERE id=1");sql("UPDATE patient_follow_up_history SET fzjc='',zlfa='' WHERE id=10");
        visit(11,1,6,"{\"result\":{\"crpScore\":9}}","2023-01-01",null,"record");sql("UPDATE patient_follow_up_history SET zlfa='record' WHERE id=11");
        assertEquals(json.readTree("[\"M_BASELINE_LAB\",\"M_COMORBIDITY\",\"M_MEDICATION\"]"),qc(1).path("missingCodes"));
    }
    @Test void originalLogicRulesRemainIndependentOfCompleteness() throws Exception {
        sixPatientTable(); assertEquals(10,project().getProjectsData().getPendingQcIssues());
        sql("UPDATE patient_basic_info SET happen_date=null WHERE id=7"); assertEquals(9,project().getProjectsData().getPendingQcIssues());
        sql("UPDATE patient_basic_info SET smoke_years=0 WHERE id=7"); assertEquals(8,project().getProjectsData().getPendingQcIssues());
        visit(71,7,0,"{\"result\":{\"crpScore\":0}}","2099-01-01",null,"record");
        assertEquals(9,project().getProjectsData().getPendingQcIssues());assertEquals(6,project().getProjectsData().getQcIssuePatients());
        assertEquals(1,legacy().listPatients(202L,null,null,"complete",1,20).getTotal());
        sql("UPDATE patient_follow_up_history SET follow_up_date='2024-01-01' WHERE id=71");
        sql("UPDATE patient_basic_info SET acr_eular_score=11 WHERE id=7"); assertEquals(9,project().getProjectsData().getPendingQcIssues());
        assertEquals(1,legacy().listPatients(202L,null,null,"complete",1,20).getTotal());
    }
    void scoreConsumers(String expected,String activity,boolean complete) throws Exception {
        JsonNode ai=success("{}").path("patients").path("items").get(0);
        PatientItemVo list=legacy().listPatients(101L,"synthetic-one",null,null,1,20).getItems().get(0);
        PatientDetailVo detail=legacy().getPatientDetail(101L,1L);
        if(expected==null) { assertTrue(ai.path("das28At").isNull());assertTrue(ai.path("activity").isNull());assertNull(list.getLatestDas28());assertNull(detail.getLatestDas28()); }
        else { assertEquals(0,new BigDecimal(expected).compareTo(ai.path("das28At").decimalValue()));assertEquals(new BigDecimal(expected),list.getLatestDas28());assertEquals(new BigDecimal(expected),detail.getLatestDas28());assertEquals(activity,ai.path("activity").asText()); }
        assertEquals(activity,list.getDas28Activity());assertEquals(activity,detail.getDas28Activity());
        assertEquals(complete?"COMPLETE":"MISSING",ai.path("qc").path("status").asText());assertEquals(!complete,list.isIncomplete());assertEquals(!complete,detail.isIncomplete());
        assertEquals(complete?json.readTree("[]"):json.readTree("[\"M_DAS28\"]"),ai.path("qc").path("missingCodes"));
    }
    @Test void sharedRawBoundaryTableRejectsWholeMalformedModulesAndFallsBack() throws Exception {
        String[][] table={
            {"{\"result\":{\"crpScore\":2.2949999999999999999}}","2.29","remission"},
            {"{\"result\":{\"crpScore\":2.295}}","2.30","low"},
            {"{ \"result\" : {\"crpScore\" : \" 2.295 \"}}","2.30","low"},
            {"{\"result\":{\"crpScore\":0}}","0.00","remission"},
            {"{\"result\":{\"crpScore\":-0.001}}",null,null},
            {"{\"result\":{\"crpScore\":\"illegal\"}}",null,null},
            {"{\"result\":{\"crpScore\":null}}",null,null},
            {"{\"result\":{\"esrScore\":8}}",null,null},
            {"{\"result\":{\"crpScore\":{}}}",null,null},
            {"{\"result\":{\"crpScore\":[]}}",null,null},
            {"{\"result\":{\"crpScore\":3}",null,null},
            {"{\"result\":{\"crpScore\":3}} {}",null,null},
            {"{\"result\":{\"crpScore\":2.30,\"crpScore\":4.10}}",null,null},
            {null,null,null}
        };
        for(String[] value:table) {rawAssessment(value[0]);scoreConsumers(value[1],value[2],value[1]!=null);}
        visit(9,1,0,"{\"result\":{\"crpScore\":2.295}}","2023-01-01",null,"record");
        for(String raw:new String[]{"{\"result\":{\"crpScore\":2.30,\"crpScore\":4.10}}","malformed"}) {rawAssessment(raw);scoreConsumers("2.30","low",true);}
    }
    @Test void qcExistenceIncludesFutureAndUndatedButNowMatchingDoesNot() throws Exception {
        for(String date:new String[]{"2099-01-01",null}) {
            try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement p=c.prepareStatement("UPDATE patient_follow_up_history SET follow_up_date=?,followUpDate=null WHERE id=10")) {p.setString(1,date);p.executeUpdate();}
            if(date==null) visit(9,1,0,"{}","2023-01-01",null,"record");
            scoreConsumers(null,null,true);
            assertEquals(Collections.singletonList("1"),ids(success("{\"filters\":{\"data\":\"complete\"}}")));
            assertTrue(ids(success("{\"filters\":{\"data\":\"complete\",\"at\":\"6m\"}}" )).isEmpty());
        }
    }
    @Test void normalSerologyAndHistoricalPositiveKeepLatestNegativeDisplay() throws Exception {
        sql("UPDATE patient_follow_up_history SET fzjc='{ \"lfsyz\" : \"35\", \"kccpkt\" : 25 }' WHERE id=10");
        assertEquals("TRUE",success("{}").path("patients").path("items").get(0).path("clinical").path("sero").asText());
        PatientItemVo first=legacy().listPatients(101L,"synthetic-one",null,null,1,20).getItems().get(0);
        assertEquals("血清阳性",first.getSubtype());assertEquals("low_positive",first.getRf().getStatus());assertEquals("negative",first.getCcp().getStatus());
        visit(11,1,0,"{}","2025-01-01",null,"{\"lfsyz\":10}");
        for(Object value:Arrays.asList(legacy().listPatients(101L,"synthetic-one",null,null,1,20).getItems().get(0),legacy().getPatientDetail(101L,1L))) {
            JsonNode item=json.valueToTree(value);assertEquals("血清阳性",item.path("subtype").asText());assertEquals("negative",item.path("rf").path("status").asText());assertEquals("10",item.path("rf").path("value").asText());assertEquals("negative",item.path("ccp").path("status").asText());
        }
        assertEquals("TRUE",success("{}").path("patients").path("items").get(0).path("clinical").path("sero").asText());
    }
    @Test void newReadsCloseAndReopenMissingAndRespectDeletedOwnershipAndPatients() throws Exception {
        assertEquals(json.readTree("[\"M_DAS28\"]"),qc(2).path("missingCodes"));
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":0}}' WHERE id=20");
        assertEquals("COMPLETE",qc(2).path("status").asText());assertEquals(0,legacy().listPatients(101L,null,null,"missing",1,20).getTotal());assertEquals(0,project().getProjectsData().getPendingQcIssues());
        sql("UPDATE patient_follow_up_history SET bqpg='broken' WHERE id=20");
        assertEquals(json.readTree("[\"M_DAS28\"]"),qc(2).path("missingCodes"));assertEquals(Collections.singletonList(2L),legacy().listPatientIds(101L,null,null,"missing"));
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3}}' WHERE id=20");assertFalse(legacy().getPatientDetail(101L,2L).isIncomplete());
        sql("DELETE FROM patient_follow_up_history WHERE id=20");
        assertEquals(json.readTree("[\"M_BASELINE_LAB\",\"M_DAS28\",\"M_MEDICATION\"]"),qc(2).path("missingCodes"));assertEquals(3,project().getProjectsData().getPendingQcIssues());
        sql("DELETE FROM patient_relation_doctor WHERE patient_id=2");
        PatientsListVo reduced=legacy().listPatients(101L,null,null,null,1,20);assertEquals(1,reduced.getTotalPatients());assertEquals(0,reduced.getIncompleteCount());assertEquals(Collections.singletonList("1"),ids(success("{}")));
        assertThrows(com.wenwen.util.BizException.class,()->legacy().getPatientDetail(101L,2L));
        sql("DELETE FROM patient_basic_info WHERE id=1");
        assertEquals(0,success("{}").path("studyTotal").asInt());assertEquals(0,legacy().listPatients(101L,null,null,null,1,20).getTotalPatients());
        ProjectsDataVo empty=project().getProjectsData();assertEquals(0,empty.getTotalPatients());assertEquals(0,empty.getPendingQcIssues());assertEquals(0,empty.getQcIssuePatients());assertEquals(new BigDecimal("0.0"),empty.getDataQualityRate());
        assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);
    }
    @Test void legacyQcWriterCannotMixHeaderFilterScoresAndMissingFacts() throws Exception {
        java.util.concurrent.atomic.AtomicInteger commits=committedWriter(false);
        PatientsListVo current=legacy().listPatients(101L,null,null,"missing",1,20);
        assertEquals(1,commits.get());assertEquals(2,current.getTotalPatients());assertEquals(1,current.getIncompleteCount());assertEquals(1,current.getTotal());
        PatientItemVo old=current.getItems().get(0);assertEquals(2L,old.getPatientId());assertNull(old.getLatestDas28());assertEquals(Collections.singletonList("缺 DAS28 评分"),old.getMissingItems());
        assertEquals(6,observed.selects);assertEquals(3,observed.borrowed);snapshotQueries(4);
        observed.reset();PatientsListVo next=legacy().listPatients(101L,null,null,"missing",1,20);
        assertEquals(2,next.getTotalPatients());assertEquals(1,next.getIncompleteCount());assertEquals(1,next.getTotal());
        PatientItemVo newer=next.getItems().get(0);assertEquals(new BigDecimal("2.30"),newer.getLatestDas28());assertEquals(Arrays.asList("缺基线检验","缺合并疾病记录","缺用药史"),newer.getMissingItems());
        assertEquals(6,observed.selects);assertEquals(3,observed.borrowed);snapshotQueries(4);
        assertEquals(newer.getMissingItems(),legacy().getPatientDetail(101L,2L).getMissingItems());released();
    }
    @Test void globalQcWriterKeepsDenominatorAndIssuesInSameView() throws Exception {
        java.util.concurrent.atomic.AtomicInteger commits=committedWriter(true);
        ProjectsDataVo current=project().getProjectsData();assertEquals(1,commits.get());
        assertEquals(2,current.getTotalPatients());assertEquals(1,current.getPendingQcIssues());assertEquals(1,current.getQcIssuePatients());assertEquals(new BigDecimal("50.0"),current.getDataQualityRate());
        snapshotQueries(3);assertEquals(7,observed.selects);assertEquals(5,observed.borrowed);
        observed.reset();ProjectsDataVo next=project().getProjectsData();assertEquals(1,next.getTotalPatients());assertEquals(3,next.getPendingQcIssues());assertEquals(1,next.getQcIssuePatients());assertEquals(new BigDecimal("0.0"),next.getDataQualityRate());snapshotQueries(3);
    }
    @Test void realQcSqlFailureFailsEveryLegacyPublicEntryAndReleasesBeforeRecovery() throws Exception {
        for(Runnable action:Arrays.<Runnable>asList(
            ()->legacy().listPatients(101L,null,null,"complete",1,20),
            ()->legacy().listPatientIds(101L,null,null,"complete"),
            ()->legacy().getPatientDetail(101L,1L),
            ()->project().getProjectsData())) {
            observed.reset();observed.failQcSql=true;assertThrows(RuntimeException.class,action::run);
            assertTrue(observed.mysqlFailures>0);assertEquals("42S02",observed.lastSqlState);assertEquals(1146,observed.lastMysqlError);released();
            observed.reset();assertEquals(1,legacy().listPatients(101L,null,null,"missing",1,20).getTotal());snapshotQueries(4);
        }
    }
    @Test void sqlKeywordEscapingCollationAndNullLastVisitOrderingArePreserved() throws Exception {
        sixPatientTable();sql("UPDATE patient_follow_up_history SET follow_up_date='2024-01-06' WHERE id=20");sql("UPDATE patient_follow_up_history SET follow_up_date=null WHERE id IN (30,60)");
        assertEquals(Arrays.asList(2L,1L,4L,5L,6L,3L),legacy().listPatientIds(101L,null,null,null));
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement p=c.prepareStatement("UPDATE patient_basic_info SET name=? WHERE id=3")) {p.setString(1,"Case_%\\marker");p.executeUpdate();}
        sql("UPDATE patient_basic_info SET name='CaseABmarker' WHERE id=4");
        PatientsListVo literal=legacy().listPatients(101L," case_%\\marker ",null,"missing",1,20);
        assertEquals(1,literal.getTotal());assertEquals(3L,literal.getItems().get(0).getPatientId());assertEquals(6,literal.getTotalPatients());assertEquals(5,literal.getIncompleteCount());
        assertEquals(Collections.singletonList(5L),legacy().listPatientIds(101L,null,"withdrawn",null));
        observed.reset();for(int[] invalid:new int[][]{{0,20},{1,0},{1,201}}) assertThrows(IllegalArgumentException.class,()->legacy().listPatients(101L,null,null,null,invalid[0],invalid[1]));assertEquals(0,observed.borrowed);
    }
}
