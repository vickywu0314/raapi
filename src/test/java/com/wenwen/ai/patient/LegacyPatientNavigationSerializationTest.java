package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.vo.PatientItemVo;
import com.wenwen.vo.VisitCreateResultVo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 独立字面真值；不依赖数据库、不执行写入。 */
class LegacyPatientNavigationSerializationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void patientListIdsRemainExactStringsInRawBytes() throws Exception {
        for (String id : new String[]{"9007199254740992", "9007199254740993", "9223372036854775807"}) {
            PatientItemVo item = new PatientItemVo();
            item.setPatientId(Long.valueOf(id));item.setVisitCount(7);item.setFollowUpCount(6);
            String raw = new String(json.writeValueAsBytes(item), java.nio.charset.StandardCharsets.UTF_8);
            JsonNode data = json.readTree(raw);
            assertTrue(raw.contains("\"patientId\":\"" + id + "\""));
            assertEquals(id, data.path("patientId").textValue());
            assertTrue(data.path("visitCount").isIntegralNumber());assertEquals(7, data.path("visitCount").intValue());
            assertTrue(data.path("followUpCount").isIntegralNumber());assertEquals(6, data.path("followUpCount").intValue());
        }
    }

    @Test void createResultIdsRemainExactStringsAndNullWithoutChangingCounts() throws Exception {
        VisitCreateResultVo result = new VisitCreateResultVo();
        result.setPatientId(9007199254740993L);result.setVisitId(9007199254741003L);result.setFilledCount(6);
        String raw = new String(json.writeValueAsBytes(result), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(raw.contains("\"patientId\":\"9007199254740993\""));
        assertTrue(raw.contains("\"visitId\":\"9007199254741003\""));
        JsonNode data = json.readTree(raw);
        assertEquals("9007199254740993",data.path("patientId").textValue());
        assertEquals("9007199254741003",data.path("visitId").textValue());
        assertTrue(data.path("filledCount").isIntegralNumber());assertEquals(6,data.path("filledCount").intValue());
        result.setPatientId(null);result.setVisitId(null);
        JsonNode empty=json.readTree(json.writeValueAsBytes(result));
        assertTrue(empty.path("patientId").isNull());assertTrue(empty.path("visitId").isNull());
        PatientItemVo item=new PatientItemVo();assertTrue(json.readTree(json.writeValueAsBytes(item)).path("patientId").isNull());
    }
}
