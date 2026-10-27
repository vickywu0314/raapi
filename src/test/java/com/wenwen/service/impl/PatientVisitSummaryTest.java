package com.wenwen.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.wenwen.vo.VisitItemVo;

/** 时间线随访摘要的老数据形态：字符串 / 数组诱因、record 文字、非法 JSON、未知调整值。 */
public class PatientVisitSummaryTest {
    private static VisitItemVo of(String bqpg, String bsbq, String zlfa) {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("bqpg", bqpg); row.put("bsbq", bsbq); row.put("zlfa", zlfa);
        VisitItemVo vi = new VisitItemVo();
        PatientServiceImpl.visitClinical(vi, row);
        return vi;
    }

    @Test public void readsLegacyShapes() {
        VisitItemVo v = of("{\"result\":{\"crpScore\":\"2.1\",\"esrScore\":7.464}}", "{\"cause\":\"感冒\"}", "{\"tz\":\"\"}");
        assertEquals(new BigDecimal("2.10"), v.getDas28Crp());
        assertEquals(new BigDecimal("7.46"), v.getDas28Esr());
        assertEquals("感冒", v.getCause());
        assertNull(v.getRegimenChanged());
        v = of("not json", "{\"record\":\"诱因：劳累\"}", "{\"tz\":\"未知\"}");
        assertNull(v.getDas28Crp()); assertNull(v.getDas28Esr()); assertNull(v.getCause()); assertNull(v.getRegimenChanged());
        v = of(null, "{\"cause\":[\"无\",\" \",\"其他\"]}", null);
        assertEquals("其他", v.getCause(), "其他但没有 extraCause 时只写「其他」");
        v = of("{\"result\":{\"esrScore\":true}}", "{\"cause\":[]}", "{\"tz\":\"是\"}");
        assertNull(v.getDas28Esr()); assertNull(v.getCause()); assertTrue(v.getRegimenChanged());
    }
}
