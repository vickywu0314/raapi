package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 患者详情时间线每次随访的 DAS28-CRP / ESR、方案是否调整、加重诱因（真实 MVC + 合成 MySQL5.7）。 */
class PatientDetailVisitSummaryHttpTest extends TrustedPatientHttpFixture {
    @Test void timelineCarriesScoresRegimenAndCause() throws Exception {
        seed();
        sql("INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,doctor_id,follow_up_date,bqpg,bsbq,zlfa) VALUES "
            + "(9007199254741010,9007199254740993,7,101,'2026-10-10','{\"result\":{\"zzgjs\":2,\"crpScore\":3.415,\"esrScore\":\"3.90\"},\"finish\":true}',"
            + "'{\"cause\":[\"劳累\",\"其他\"],\"extraCause\":\"熬夜\",\"ache\":\"疼痛较轻\"}','{\"tz\":\"是\",\"tz_reason\":\"疗效不佳\",\"xyList\":[]}'),"
            + "(9007199254741011,9007199254740993,7,101,'2026-10-12','{\"result\":{\"crpScore\":-1,\"esrScore\":null}}','{\"cause\":[\"无\"]}','{\"tz\":\"否\"}')");
        org.springframework.test.web.servlet.MvcResult r = http.perform(post("/api/ra/patient/patientDetail").session(session(101)).param("patientId", "9007199254740993")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode visits = json.readTree(r.getResponse().getContentAsByteArray()).path("data").path("visits");
        assertEquals(3, visits.size());
        JsonNode latest = visits.get(0), scored = visits.get(1), baseline = visits.get(2);
        assertEquals("9007199254741011", latest.path("visitId").textValue());
        assertTrue(latest.path("das28Crp").isNull(), "负值不接纳");
        assertTrue(latest.path("das28Esr").isNull());
        assertFalse(latest.path("regimenChanged").booleanValue());
        assertTrue(latest.path("regimenChanged").isBoolean());
        assertTrue(latest.path("cause").isNull(), "只有「无」视为没有诱因");
        assertEquals("9007199254741010", scored.path("visitId").textValue());
        assertEquals(3.42, scored.path("das28Crp").doubleValue());
        assertEquals(3.9, scored.path("das28Esr").doubleValue());
        assertTrue(scored.path("regimenChanged").booleanValue());
        assertEquals("劳累、其他：熬夜", scored.path("cause").textValue());
        // 基线：bqpg / bsbq 都是 {record} 文字，zlfa 为空 → 都为 null
        assertEquals("9007199254741003", baseline.path("visitId").textValue());
        assertTrue(baseline.has("das28Crp") && baseline.path("das28Crp").isNull());
        assertTrue(baseline.path("regimenChanged").isNull());
        assertTrue(baseline.path("cause").isNull());
    }
}
