package com.wenwen.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.wenwen.util.*;
import com.wenwen.vo.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisitUpdatePersistenceTest extends VisitUpdateFixture {
    @Test void realSaveInvalidatesAndPreservesUnknownJsonWithAtomicAudit() throws Exception {
        Map<String,Object> before=row();VisitUpdateRequest r=request("fzjc","cfydb","");
        VisitUpdateResultVo result=service.updateVisit(r);released();
        assertEquals(2,result.getChangedCount());assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,sqlObserver.auditAttempts);
        assertEquals(Collections.singletonList(false),observed.updateAutoCommit);
        Map<String,Object> after=row();
        String expectedB=BQPG.replace("\"crpScore\":\"8.88\"","\"crpScore\":\"\"");
        assertEquals(expectedB,after.get("bqpg"));assertEquals(LAB.replace("\"cfydb\":9","\"cfydb\":\"\""),after.get("fzjc"));
        for(String column:Arrays.asList("bsbq","zyzd","zlfa","blsj","bblsj","follow_up_date","followUpDate"))assertEquals(before.get(column),after.get(column),column);
        assertEquals(1,auditCount());
        List<Map<String,Object>> rows=patientMapper.listDas28(Collections.singletonList(1L));
        assertEquals(1,rows.size());assertEquals(expectedB,rows.get(0).get("bqpg"));
        String stored=com.wenwen.ai.source.ClinicalScalarReader.read((String)rows.get(0).get("bqpg"),new LinkedHashSet<>()).get("/result/crpScore");assertEquals("",stored);assertNull(Das28Util.canonicalCrp(stored));
    }
    @Test void concurrentCaseOnlyUnknownChangeIsConflictAndPreservesWriter() throws Exception {
        VisitUpdateRequest r=request("fzjc","cfydb","");String writer=BQPG.replace("UPPER","upper");
        sqlObserver.afterVisitRead=()->writerModule("bqpg",writer);
        BizException failure=assertThrows(BizException.class,()->service.updateVisit(r));
        assertEquals("409",failure.getCode());released();
        assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,sqlObserver.readBarriers);assertEquals(1,writerCommits);assertEquals(0,sqlObserver.auditAttempts);
        assertEquals(writer,row().get("bqpg"));assertEquals(LAB,row().get("fzjc"));assertEquals(0,auditCount());
    }
    @Test void ghEditGuardsUnwrittenLabDependency() throws Exception {
        VisitUpdateRequest r=request("bqpg","ztScoreByPatient","60");String writer="{\"cfydb\":99,\"xc\":100,\"writer\":true}";
        sqlObserver.afterVisitRead=()->writerModule("fzjc",writer);
        BizException failure=assertThrows(BizException.class,()->service.updateVisit(r));
        assertEquals("409",failure.getCode());released();assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,sqlObserver.readBarriers);assertEquals(1,writerCommits);assertEquals(0,sqlObserver.auditAttempts);
        assertEquals(writer,row().get("fzjc"));assertEquals(BQPG,row().get("bqpg"));assertEquals(0,auditCount());
    }
    @Test void sameRoundedScoresAndMissingLabStillGuardSourceRaw() throws Exception {
        String stable=BQPG.replace("8.88","3.89").replace("7.77","4.35");
        for(String original:Arrays.asList(LAB,null,"","{}","{\"record\":\"synthetic\"}","malformed")) {
            rawModule("bqpg",stable);rawModule("fzjc",original);
            VisitUpdateRequest r=request("bqpg","ztScoreByPatient","50.01");String writer="{\"cfydb\":99,\"xc\":100}";
            sqlObserver.afterVisitRead=()->writerModule("fzjc",writer);
            assertEquals("409",assertThrows(BizException.class,()->service.updateVisit(r)).getCode());released();
            assertEquals(1,sqlObserver.readBarriers);assertEquals(1,writerCommits);assertEquals(1,sqlObserver.visitUpdates);
            assertEquals(stable,row().get("bqpg"));assertEquals(writer,row().get("fzjc"));assertEquals(0,auditCount());
        }
    }
    @Test void unrelatedTreatmentWriterDoesNotBlockGhSaveOrGetOverwritten() throws Exception {
        VisitUpdateRequest r=request("bqpg","ztScoreByPatient","60");String writer=" {\"legacy\":\"UPPER\",\"nil\":null} ";
        sqlObserver.afterVisitRead=()->writerModule("zlfa",writer);
        service.updateVisit(r);released();assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,writerCommits);
        assertEquals(writer,row().get("zlfa"));assertEquals(LAB,row().get("fzjc"));assertEquals(1,auditCount());
        JSONObject b=VisitJson.parse((String)row().get("bqpg"));assertEquals(60,((Number)b.get("ztScoreByPatient")).intValue());
        assertEquals("4.03",VisitJson.path(b,"result.crpScore"));assertEquals(new java.math.BigDecimal("4.49"),VisitJson.path(b,"result.esrScore"));
    }
    @Test void haqAndOrdinaryAssessmentEditsDoNotGuardUnrelatedLab() throws Exception {
        for(String key:Arrays.asList("q2","tjScore")) {
            JSONObject b=VisitEditorInvalidationTest.answered();b.put("unknown",VisitJson.parse("{\"n\":null}"));
            rawModule("bqpg",VisitJson.write(b));rawModule("fzjc",LAB);
            VisitUpdateRequest r=request("bqpg",key,"q2".equals(key)?"稍有困难":"20");String writer="{\"cfydb\":99,\"xc\":100}";
            if("tjScore".equals(key)) {
                Map<String,Object> fields=new HashMap<>(r.getModules().get("bqpg").getFields());fields.put("ztScoreByPatient","50");r.getModules().get("bqpg").setFields(fields);
            }
            sqlObserver.afterVisitRead=()->writerModule("fzjc",writer);
            VisitUpdateResultVo result=service.updateVisit(r);released();assertEquals(1,sqlObserver.readBarriers);assertEquals(1,writerCommits);
            assertEquals(writer,row().get("fzjc"));JSONObject saved=VisitJson.parse((String)row().get("bqpg"));
            assertEquals("8.88",VisitJson.path(saved,"result.crpScore"));assertEquals(new java.math.BigDecimal("7.77"),VisitJson.path(saved,"result.esrScore"));
            if("q2".equals(key)){assertEquals(new java.math.BigDecimal("1.00"),saved.get("hqaScore"));assertEquals(new java.math.BigDecimal("1.00"),VisitJson.path(saved,"result.hqaScore"));assertEquals(2,result.getChangedCount());}
        }
        assertEquals(2,auditCount());
    }
    @Test void physicalDeletionAfterSelectReturnsConflictWithoutResurrection() throws Exception {
        VisitUpdateRequest r=request("fzjc","cfydb","");sqlObserver.afterVisitRead=()->writerSql("DELETE FROM patient_follow_up_history WHERE id=10");
        assertEquals("409",assertThrows(BizException.class,()->service.updateVisit(r)).getCode());released();
        assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,writerCommits);assertEquals(0,sqlObserver.auditAttempts);
        assertNull(row());assertEquals(0,auditCount());
    }
    @Test void dateCasComparesBothOriginalTimestampsBeyondNormalizedDay() throws Exception {
        Map<String,Object> before=row();VisitUpdateRequest r=request(null,null,null);r.setVisitDate("2026-10-02");
        sqlObserver.afterVisitRead=()->writerSql("UPDATE patient_follow_up_history SET followUpDate='2026-09-30 20:00:00.654322' WHERE id=10");
        assertEquals("409",assertThrows(BizException.class,()->service.updateVisit(r)).getCode());released();
        assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,writerCommits);assertEquals(0,sqlObserver.auditAttempts);
        Map<String,Object> after=row();assertEquals(before.get("follow_up_date"),after.get("follow_up_date"));
        assertEquals("2026-09-30 20:00:00.654322",textQuery("SELECT DATE_FORMAT(followUpDate,'%Y-%m-%d %H:%i:%s.%f') FROM patient_follow_up_history WHERE id=10"));
        assertEquals(before.get("bsbq"),after.get("bsbq"));assertEquals(0,auditCount());
    }
    @Test void realAuditConstraintFailureRollsBackAlreadyExecutedUpdateAndDates() throws Exception {
        sql("ALTER TABLE patient_audit_log ADD CONSTRAINT p02a_fail_audit CHECK (action <> '编辑随访')");
        Map<String,Object> before=row();String counter=textQuery("SELECT DATE_FORMAT(last_follow_up_date,'%Y-%m-%d %H:%i:%s.%f') FROM patient_basic_info WHERE id=1");
        VisitUpdateRequest r=request("fzjc","cfydb","");r.setVisitDate("2026-10-02");
        RuntimeException failure=assertThrows(RuntimeException.class,()->service.updateVisit(r));released();
        assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,sqlObserver.auditAttempts);assertEquals(1,sqlObserver.auditFailures);
        assertEquals(Collections.singletonList(false),observed.updateAutoCommit);
        assertTrue(sqlObserver.completed.contains("com.wenwen.mapper.VisitMapper.updateVisit"));
        Throwable root=failure;while(root.getCause()!=null)root=root.getCause();
        assertTrue(root instanceof java.sql.SQLException,root.toString());assertTrue(root.getMessage().contains("p02a_fail_audit"),root.getMessage());
        assertEquals(before,row());assertEquals(0,auditCount());
        assertEquals(counter,textQuery("SELECT DATE_FORMAT(last_follow_up_date,'%Y-%m-%d %H:%i:%s.%f') FROM patient_basic_info WHERE id=1"));
        assertEquals("2026-10-01",textQuery("SELECT DATE_FORMAT(last_follow_up_date,'%Y-%m-%d') FROM patient_relation_doctor WHERE patient_id=1"));
    }
    @Test void unchangedInvalidAndStaleRequestsDoNotWriteOrAudit() throws Exception {
        Map<String,Object> before=row();VisitUpdateRequest same=request("fzjc","cfydb","9");
        assertEquals(0,service.updateVisit(same).getChangedCount());released();assertEquals(0,sqlObserver.visitUpdates);assertEquals(before,row());assertEquals(0,auditCount());
        VisitUpdateRequest bad=request("bqpg","ztScoreByPatient","not-number");
        assertThrows(IllegalArgumentException.class,()->service.updateVisit(bad));released();assertEquals(0,sqlObserver.visitUpdates);assertEquals(before,row());assertEquals(0,auditCount());
        VisitUpdateRequest stale=request("fzjc","cfydb","");String writer=LAB.replace("25","26");rawModule("fzjc",writer);
        assertEquals("409",assertThrows(BizException.class,()->service.updateVisit(stale)).getCode());released();assertEquals(0,sqlObserver.visitUpdates);assertEquals(0,sqlObserver.auditAttempts);
        assertEquals(writer,row().get("fzjc"));assertEquals(BQPG,row().get("bqpg"));assertEquals(0,auditCount());
    }
    @Test void dateSaveWithOriginalNullsUpdatesBothDatesAndCounters() throws Exception {
        for(String dates:Arrays.asList("follow_up_date='2026-10-01 10:00:00.123456', followUpDate='2026-09-30 20:00:00.654321'","follow_up_date=NULL, followUpDate='2026-10-01'","follow_up_date=NULL, followUpDate=NULL")) {
            sql("UPDATE patient_follow_up_history SET "+dates+", bsbq='{\"followDate\":\"2026-10-01\",\"u\":null}' WHERE id=10");
            VisitUpdateRequest r=request(null,null,null);r.setVisitDate("2026-10-02");
            service.updateVisit(r);released();assertEquals(1,sqlObserver.visitUpdates);
            assertEquals("2026-10-02",textQuery("SELECT DATE_FORMAT(follow_up_date,'%Y-%m-%d') FROM patient_follow_up_history WHERE id=10"));
            assertEquals("2026-10-02",textQuery("SELECT DATE_FORMAT(followUpDate,'%Y-%m-%d') FROM patient_follow_up_history WHERE id=10"));
            assertEquals("{\"followDate\":\"2026-10-02\",\"u\":null}",row().get("bsbq"));assertEquals(BQPG,row().get("bqpg"));
            assertEquals("2026-10-02",textQuery("SELECT DATE_FORMAT(last_follow_up_date,'%Y-%m-%d') FROM patient_basic_info WHERE id=1"));
            assertEquals("2026-10-02",textQuery("SELECT DATE_FORMAT(first_follow_up_date,'%Y-%m-%d') FROM patient_relation_doctor WHERE patient_id=1"));
        }
        assertEquals(3,auditCount());
        String form=com.alibaba.fastjson.JSON.toJSONString(service.getEditForm(101L,10L));assertFalse(form.contains("originalFollowUpDate"));assertFalse(form.contains("originalCamelDate"));
    }
    @Test void nullReplacementMatchesOnlyNullAndReadonlyModulesStayUntouched() throws Exception {
        rawModule("fzjc",null);VisitUpdateRequest r=request("fzjc","cfydb","9");
        service.updateVisit(r);released();assertEquals(1,sqlObserver.visitUpdates);assertEquals(1,auditCount());
        assertEquals("{\"cfydb\":\"9\",\"finish\":true}",row().get("fzjc"));
        rawModule("fzjc",null);r=request("fzjc","cfydb","9");final VisitUpdateRequest conflict=r;
        sqlObserver.afterVisitRead=()->writerModule("fzjc","");
        assertEquals("409",assertThrows(BizException.class,()->service.updateVisit(conflict)).getCode());released();assertEquals("",row().get("fzjc"));assertEquals(1,auditCount());
        r=request(null,null,null);Map<String,VisitModuleUpdate> modules=new HashMap<>();modules.put("zyzd",VisitEditorInvalidationTest.fields("zxxf","changed"));modules.put("blsj",VisitEditorInvalidationTest.fields("record","changed"));r.setModules(modules);
        Map<String,Object> before=row();assertEquals(0,service.updateVisit(r).getChangedCount());released();assertEquals(0,sqlObserver.visitUpdates);assertEquals(before,row());assertEquals(1,auditCount());
    }
}
