package com.wenwen.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.mapper.VisitMapper;
import com.wenwen.vo.VisitDetailVo;
import com.wenwen.vo.VisitFieldVo;
import com.wenwen.vo.VisitModuleVo;

/** 访视详情的检验参考范围与异常标记：只读显示，与录入校验同一份 ra.visit.validation.rules。 */
class VisitDetailReferenceTest {
    private static final String RULES = "{\"fzjc.bxb\":{\"required\":true,\"allowUnchecked\":true,\"referenceMin\":3.5,\"referenceMax\":9.5},"
        + "\"fzjc.cfydb\":{\"referenceMin\":0,\"referenceMax\":10},\"fzjc.lfsyz\":{\"referenceMax\":20},\"fzjc.hdl_c\":{\"referenceMin\":1.0},"
        + "\"fzjc.xhdb\":{\"referenceMin\":115,\"referenceMax\":175}}";

    private static VisitDetailVo detail(String rules, String fzjc) {
        VisitMapper visits = mock(VisitMapper.class);
        PatientMapper patients = mock(PatientMapper.class);
        Map<String, Object> row = new HashMap<>();
        row.put("visitId", 5L); row.put("patientId", 7L); row.put("doctorId", 101L); row.put("visitDate", "2026-10-01");
        row.put("fzjc", fzjc);
        row.put("bqpg", "{\"result\":{\"crpScore\":2.1}}");
        when(visits.getVisit(anyMap())).thenReturn(row);
        Map<String, Object> timeline = new HashMap<>(); timeline.put("visitId", 5L); timeline.put("visitTime", "2026-10-01 00:00:00.000000");
        when(patients.listVisits(anyMap())).thenReturn(Collections.singletonList(timeline));
        VisitServiceImpl service = new VisitServiceImpl();
        ReflectionTestUtils.setField(service, "visitMapper", visits);
        ReflectionTestUtils.setField(service, "patientMapper", patients);
        ReflectionTestUtils.setField(service, "validationRules", rules);
        return service.getVisitDetail(101L, 5L);
    }

    private static Map<String, VisitFieldVo> fields(VisitDetailVo d, String column) {
        Map<String, VisitFieldVo> out = new HashMap<>();
        for (VisitModuleVo m : d.getModules()) if (column.equals(m.getColumn()))
            m.getGroups().forEach(g -> g.getItems().forEach(f -> out.put(f.getKey(), f)));
        return out;
    }

    @Test void configuredLabItemsCarryRangeAndAbnormal() {
        Map<String, VisitFieldVo> f = fields(detail(RULES,
            "{\"bxb\":\"12.1\",\"xhdb\":\"110\",\"cfydb\":\"<5\",\"lfsyz\":\">20IU/mL\",\"hdl_c\":\"1.2\",\"alt\":\"80\",\"ast\":\"abc\",\"xxb\":\"200\"}"), "fzjc");
        assertEquals(new BigDecimal("3.5"), f.get("bxb").getReferenceMin());
        assertEquals(new BigDecimal("9.5"), f.get("bxb").getReferenceMax());
        assertEquals("high", f.get("bxb").getAbnormal());
        assertEquals("low", f.get("xhdb").getAbnormal());
        assertNull(f.get("cfydb").getAbnormal(), "<5 在 0~10 内");
        assertEquals(new BigDecimal("0"), f.get("cfydb").getReferenceMin());
        assertEquals("high", f.get("lfsyz").getAbnormal(), ">20 整个集合都超过上限 20");
        assertNull(f.get("lfsyz").getReferenceMin(), "单侧范围");
        assertNull(f.get("hdl_c").getAbnormal());
        assertNull(f.get("hdl_c").getReferenceMax());
        // 没配置范围的检验项：不给范围也不判异常
        assertNull(f.get("alt").getReferenceMin());
        assertNull(f.get("alt").getAbnormal());
        assertNull(f.get("xxb").getReferenceMax());
        // 病情评估等非检验模块不受影响
        assertNull(fields(detail(RULES, "{}"), "bqpg").get("result.crpScore").getReferenceMax());
    }

    @Test void uncheckedAndQualitativeValuesAreNotFlagged() {
        Map<String, VisitFieldVo> f = fields(detail(RULES, "{\"xcgWx\":{\"bxb\":true},\"lfsyz\":\"阳性\",\"cfydb\":\"未查\"}"), "fzjc");
        assertTrue(f.get("bxb").isNotChecked());
        assertEquals(new BigDecimal("9.5"), f.get("bxb").getReferenceMax());
        assertNull(f.get("bxb").getAbnormal());
        assertNull(f.get("lfsyz").getAbnormal());
        assertNull(f.get("cfydb").getAbnormal());
    }

    @Test void brokenOrEmptyConfigOnlyHidesRanges() {
        for (String rules : new String[] {"", "{\"fzjc.unknown\":{}}"}) {
            Map<String, VisitFieldVo> f = fields(detail(rules, "{\"bxb\":\"12.1\"}"), "fzjc");
            assertEquals("12.1", f.get("bxb").getValue());
            assertNull(f.get("bxb").getReferenceMax());
            assertNull(f.get("bxb").getAbnormal());
        }
    }

    @Test void comparatorBoundaries() {
        BigDecimal min = new BigDecimal("3.5"), max = new BigDecimal("9.5");
        assertEquals("low", VisitValidationPolicy.abnormal(min, max, "bxb", "<3.5", ""));
        assertNull(VisitValidationPolicy.abnormal(min, max, "bxb", "≤3.5", ""));
        assertEquals("low", VisitValidationPolicy.abnormal(min, max, "bxb", "≤3.4", ""));
        assertEquals("high", VisitValidationPolicy.abnormal(min, max, "bxb", "＞9.5", ""));
        assertNull(VisitValidationPolicy.abnormal(min, max, "bxb", ">=9.5", ""));
        assertNull(VisitValidationPolicy.abnormal(min, max, "bxb", "9.5", ""));
        assertNull(VisitValidationPolicy.abnormal(min, max, "bxb", "3.5", ""));
        assertEquals("high", VisitValidationPolicy.abnormal(min, max, "bxb", "=1e1", ""));
        assertNull(VisitValidationPolicy.abnormal(null, new BigDecimal("25"), "kccpkt", "30IU/mL", "U/mL"), "单位不一致不判断");
        assertEquals("high", VisitValidationPolicy.abnormal(null, new BigDecimal("25"), "kccpkt", "30 U/mL", "U/mL"));
    }
}
