package com.wenwen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.util.BizException;
import com.wenwen.vo.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** 在真实 MySQL/Spring 事务边界验证拒绝、确认、回滚、CAS 和旧 JSON 保留。 */
class VisitEntryValidationPersistenceTest extends VisitUpdateFixture {
    void configure(String rules) throws Exception {
        Object target = ((Advised) service).getTargetSource().getTarget();
        ReflectionTestUtils.setField(target, "validationRules", rules);
    }
    VisitUpdateRequest confirmed() {
        VisitUpdateRequest request = request("fzjc", "bxb", "11");
        String version = VisitEntryValidationTest.field(new ObjectMapper().valueToTree(service.getEditForm(101L, 10L)), "fzjc", "bxb")
            .path("validation").path("ruleVersion").asText();
        request.getModules().get("fzjc").setConfirmations(Collections.singletonMap("bxb",
            VisitEntryValidationTest.confirmation("11", "×10⁹/L", version, "visit:10")));
        reset(); return request;
    }
    @Test void unconfirmedAbnormalValueRejectsBeforeWritesOrAudit() throws Exception {
        configure(VisitEntryValidationTest.WBC_RULE); Map<String,Object> before = row();
        VisitUpdateRequest request = request("fzjc", "bxb", "11");
        assertThrows(IllegalArgumentException.class, () -> service.updateVisit(request)); released();
        assertEquals(0, sqlObserver.visitUpdates); assertEquals(0, sqlObserver.auditAttempts); assertEquals(before, row()); assertEquals(0, auditCount());
    }
    @Test void confirmedValueAndSpecificAuditCommitAtomicallyWithoutTouchingLegacyJson() throws Exception {
        rawModule("bblsj", " {\"unknown\":{\"safe\":\"<script>\"},\"list\":[1,null,2]} ");
        configure(VisitEntryValidationTest.WBC_RULE); Map<String,Object> before = row();
        assertEquals(1, service.updateVisit(confirmed()).getChangedCount()); released();
        assertEquals(1, sqlObserver.visitUpdates); assertEquals(Collections.singletonList(false), observed.updateAutoCommit);
        assertEquals(before.get("bblsj"), row().get("bblsj")); assertEquals(before.get("bqpg"), row().get("bqpg"));
        assertEquals("11", com.wenwen.util.VisitJson.parse((String) row().get("fzjc")).get("bxb"));
        assertEquals("101", textQuery("SELECT operator_id FROM patient_audit_log"));
        assertTrue(textQuery("SELECT detail FROM patient_audit_log").contains("医生已核对并确认检验结果：白细胞 WBC=11 ×10⁹/L"));
        assertEquals(1, auditCount());
    }
    @Test void confirmedAuditFailureRollsBackTheAlreadyExecutedValueUpdate() throws Exception {
        configure(VisitEntryValidationTest.WBC_RULE);
        sql("CREATE TRIGGER entry_fail_audit BEFORE INSERT ON patient_audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='entry_fail_audit'");
        Map<String,Object> before = row(); VisitUpdateRequest request = confirmed();
        assertThrows(RuntimeException.class, () -> service.updateVisit(request)); released();
        assertEquals(1, sqlObserver.visitUpdates); assertEquals(1, sqlObserver.auditFailures); assertEquals(before, row()); assertEquals(0, auditCount());
    }
    @Test void confirmationDoesNotBypassVersionOrExactOriginalCas() throws Exception {
        configure(VisitEntryValidationTest.WBC_RULE); VisitUpdateRequest stale = confirmed();
        rawModule("fzjc", LAB + " "); assertEquals("409", assertThrows(BizException.class, () -> service.updateVisit(stale)).getCode());
        released(); assertEquals(0, sqlObserver.visitUpdates); assertEquals(0, auditCount());
        rawModule("fzjc", LAB); VisitUpdateRequest concurrent = confirmed();
        sqlObserver.afterVisitRead = () -> writerModule("fzjc", LAB.replace("false", "true"));
        assertEquals("409", assertThrows(BizException.class, () -> service.updateVisit(concurrent)).getCode()); released();
        assertEquals(1, sqlObserver.visitUpdates); assertEquals(0, auditCount()); assertEquals(LAB.replace("false", "true"), row().get("fzjc"));
    }
    @Test void untouchedLegacyTextAndMissingNewRequiredFieldDoNotBlockUnrelatedEditing() throws Exception {
        rawModule("fzjc", "{\"bxb\":\"old nonnumeric text\",\"cfydb\":9,\"xcgWx\":{\"bxb\":true}}");
        configure("{\"fzjc.bxb\":{\"required\":true},\"fzjc.xxdbMissing\":{}}");
        // 无效配置显式不可用，不能误当作无临床规则。
        assertEquals("503", assertThrows(BizException.class, () -> service.getEditForm(101L, 10L)).getCode());
        configure("{\"fzjc.bxb\":{\"required\":true},\"fzjc.xhdb\":{\"required\":true}}");
        String before = (String) row().get("fzjc");
        VisitUpdateRequest request = request("bsbq", "happenDate", "2026-01-01");
        assertEquals(1, service.updateVisit(request).getChangedCount()); released(); assertEquals(before, row().get("fzjc"));
    }
}
