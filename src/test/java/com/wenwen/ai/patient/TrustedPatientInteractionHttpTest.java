package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TrustedPatientInteractionHttpTest extends TrustedPatientHttpFixture {
    @Test void crossPatientVisitCannotBeAttachedEvenForSameDoctor() throws Exception {
        seed();
        org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/ai/visitDetail").session(session(101))
            .contentType("application/json").content("{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741003\"}")).andReturn();
        assertEquals(404,r.getResponse().getStatus());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());
        assertEquals("RESOURCE_NOT_FOUND",e.path("code").textValue());assertTrue(e.path("data").isNull());
        assertFalse(e.toString().contains("synthetic-patient"));
    }
    @Test void sensitiveExplicitReadReturnsExactValueAndRechecksAccess() throws Exception {
        seed();sql("UPDATE patient_basic_info SET card_no='SYNTHETIC-CARD-EVEN' WHERE id=9007199254740992");
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=post("/api/ra/ai/patientSensitive").session(session(101)).contentType("application/json").content("{\"patientId\":\"9007199254740992\"}");
        org.springframework.test.web.servlet.MvcResult r=http.perform(request).andReturn();assertEquals(200,r.getResponse().getStatus());
        JsonNode d=json.readTree(r.getResponse().getContentAsByteArray()).path("data");
        assertEquals("9007199254740992",d.path("patientId").textValue());assertEquals("SYNTHETIC-CARD-EVEN",d.path("cardNo").textValue());
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=9007199254740992");
        assertEquals(404,http.perform(request).andReturn().getResponse().getStatus());
    }
    @Test void auditReadsOnlyCurrentPatientInLegacyOrderWithStringIds() throws Exception {
        seed();org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/ai/patientAuditLogs").session(session(101)).contentType("application/json").content("{\"patientId\":\"9007199254740992\"}")).andReturn();
        assertEquals(200,r.getResponse().getStatus());JsonNode d=json.readTree(r.getResponse().getContentAsByteArray()).path("data");
        assertEquals("9007199254740992",d.path("patientId").textValue());JsonNode logs=d.path("logs");assertEquals(2,logs.size());
        assertEquals("9007199254741103",logs.get(0).path("id").textValue());assertEquals("9007199254741102",logs.get(1).path("id").textValue());
        assertEquals("9007199254741001",logs.get(0).path("visitId").textValue());assertEquals("SYNTHETIC-AUDIT-NEWER",logs.get(0).path("detail").textValue());
        assertFalse(d.toString().contains("SYNTHETIC-ODD-ONLY"));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,String body){return post("/api/ra/ai/"+path).session(session(101)).contentType("application/json").content(body);}
    private JsonNode expectError(String path,String body,int status,String code)throws Exception{
        org.springframework.test.web.servlet.MvcResult r=http.perform(request(path,body)).andReturn();assertEquals(status,r.getResponse().getStatus());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertEquals(code,e.path("code").textValue());assertTrue(e.path("data").isNull());assertFalse(e.path("success").asBoolean());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));return e;
    }
    @Test void allThreeRoutesAuthenticateAndGateBeforeBodyOrSql()throws Exception{
        for(String path:new String[]{"visitDetail","patientSensitive","patientAuditLogs"}){
            assertEquals(401,http.perform(post("/api/ra/ai/"+path).content("invalid").header("X-Doctor-Id","101").principal(()->"101")).andReturn().getResponse().getStatus());assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
        }
        context.close();apiEnabled=false;setup();
        for(String path:new String[]{"visitDetail","patientSensitive","patientAuditLogs"}){expectError(path,"invalid",503,"SERVICE_UNAVAILABLE");assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);}
    }
    @Test void exactSingleAndDualIdBodiesShareBoundedStrictDecoder()throws Exception{
        for(String path:new String[]{"visitDetail","patientSensitive","patientAuditLogs"}){
            String valid=path.equals("visitDetail")?"{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741001\"}":"{\"patientId\":\"9007199254740992\"}";
            for(String bad:new String[]{"", "[]", "null", "{}", valid+" {}",valid.replace("9007199254740992","01"),valid.replace("9007199254740992","9223372036854775808"),valid.replace("\"9007199254740992\"","9007199254740992"),valid.replace("}",",\"doctorId\":\"101\"}"),valid.replace("}",",\"patientId\":\"1\"}")}){expectError(path,bad,400,"INVALID_REQUEST");assertEquals(0,observed.borrowed);}
            assertEquals(400,http.perform(post("/api/ra/ai/"+path).session(session(101)).content(new byte[]{(byte)0xc3,(byte)0x28})).andReturn().getResponse().getStatus());
            expectError(path,String.join("",java.util.Collections.nCopies(4097," ")),413,"LIMIT_EXCEEDED");assertEquals(4097,body.bytes);assertEquals(0,observed.borrowed);
        }
        seed();String v="{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741001\"}";v+=String.join("",java.util.Collections.nCopies(4096-v.length()," "));assertEquals(200,http.perform(request("visitDetail",v)).andReturn().getResponse().getStatus());assertEquals(4096,body.bytes);
        for(String b:new String[]{"{\"patientId\":\"1\"}","{\"visitId\":\"1\"}","{\"patientId\":\"1\",\"visitId\":1}","{\"patientId\":\"1\",\"visitId\":\"01\"}"})expectError("visitDetail",b,400,"INVALID_REQUEST");
    }
    @Test void hiddenVisitsRevocationAndSourceFailureNeverBecomeSuccess()throws Exception{
        seed();for(String[] pair:new String[][]{{"8001","8101"},{"8002","8102"},{"9007199254740992","8103"},{"9007199254740992","8999"}})expectError("visitDetail","{\"patientId\":\""+pair[0]+"\",\"visitId\":\""+pair[1]+"\"}",404,"RESOURCE_NOT_FOUND");
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=9007199254740992");expectError("visitDetail","{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741001\"}",404,"RESOURCE_NOT_FOUND");
        for(String path:new String[]{"patientSensitive","patientAuditLogs"})for(String pid:new String[]{"8001","8002","8999","9007199254740992"})expectError(path,"{\"patientId\":\""+pid+"\"}",404,"RESOURCE_NOT_FOUND");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,9007199254740992,0)");
        for(String[] pair:new String[][]{{"visitDetail","patient_follow_up_history"},{"patientSensitive","patient_basic_info"},{"patientAuditLogs","patient_audit_log"}}){
            sql("RENAME TABLE "+pair[1]+" TO synthetic_source_offline");try{JsonNode e=expectError(pair[0],pair[0].equals("visitDetail")?"{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741001\"}":"{\"patientId\":\"9007199254740992\"}",503,"SERVICE_UNAVAILABLE");assertFalse(e.toString().contains("SQL"));assertFalse(e.toString().contains("SYNTHETIC"));}finally{sql("RENAME TABLE synthetic_source_offline TO "+pair[1]);}
        }
    }
    @Test void genuineEmptySensitiveAndAuditResultsRemainSuccessful()throws Exception{
        seed();for(String path:new String[]{"patientSensitive","patientAuditLogs"}){org.springframework.test.web.servlet.MvcResult r=http.perform(request(path,"{\"patientId\":\"9223372036854775807\"}")).andReturn();assertEquals(200,r.getResponse().getStatus());JsonNode d=json.readTree(r.getResponse().getContentAsByteArray()).path("data");assertEquals("9223372036854775807",d.path("patientId").textValue());if(path.equals("patientSensitive"))assertTrue(d.path("cardNo").isNull());else{assertTrue(d.path("logs").isArray());assertEquals(0,d.path("logs").size());}}
    }
    @Test void ownVisitBelongsToRequestedPatient() throws Exception {
        seed();
        org.springframework.test.web.servlet.MvcResult result=http.perform(post("/api/ra/ai/visitDetail")
            .session(session(101)).contentType("application/json")
            .content("{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741001\"}")).andReturn();
        assertEquals(200,result.getResponse().getStatus());
        JsonNode d=json.readTree(result.getResponse().getContentAsByteArray()).path("data");
        assertEquals("9007199254740992",d.path("patientId").textValue());
        assertEquals("9007199254741001",d.path("visitId").textValue());
        assertEquals("101",d.path("doctorId").textValue());
        assertEquals(7,d.path("modules").size());
        assertEquals("SYNTHETIC-HISTORY",d.path("modules").get(0).path("record").textValue());
        assertEquals("2026-09-01",d.path("modules").get(0).path("recordDate").textValue());
        assertTrue(observed.selects>0);
    }
}
