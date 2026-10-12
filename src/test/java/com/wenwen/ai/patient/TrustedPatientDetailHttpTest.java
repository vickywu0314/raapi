package com.wenwen.ai.patient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
class TrustedPatientDetailHttpTest extends TrustedPatientHttpFixture {
    @Test void typedSessionReadsOwnRealDetailWithExactIds() throws Exception {
        seed();
        org.springframework.test.web.servlet.MvcResult result=http.perform(post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content("{\"patientId\":\"9007199254740992\"}")).andReturn();
        assertEquals(200,result.getResponse().getStatus());
        com.fasterxml.jackson.databind.JsonNode d=json.readTree(result.getResponse().getContentAsByteArray()).path("data");
        assertEquals("9007199254740992",d.path("patientId").textValue());assertEquals("synthetic-patient-even",d.path("name").textValue());assertEquals("RA-EVEN",d.path("studyNo").textValue());
        assertEquals(1,d.path("visits").size());assertEquals("9007199254741001",d.path("visits").get(0).path("visitId").textValue());assertEquals("101",d.path("visits").get(0).path("doctorId").textValue());assertEquals("2026-09-01",d.path("visits").get(0).path("visitDate").textValue());
        assertTrue(d.path("latestDas28").isNull());assertTrue(d.path("age").isNull());assertEquals("UNKNOWN",d.path("fmState").textValue());assertTrue(observed.selects>0);
    }
    @Test void anonymousDetailIsRejectedBeforeBodyOrSql() throws Exception {
        org.springframework.test.web.servlet.MvcResult result=http.perform(post("/api/ra/ai/patientDetail").contentType("application/json").content("not-json doctorId=101").principal(()->"101").header("X-Doctor-Id","101")).andReturn();
        assertEquals(401,result.getResponse().getStatus());
        assertEquals("UNAUTHENTICATED",json.readTree(result.getResponse().getContentAsByteArray()).path("code").asText());
        assertNotNull(result.getResponse().getHeader("X-Trace-Id"));assertEquals(0,body.bytes);assertEquals(0,observed.selects);assertEquals(0,observed.borrowed);
    }

    @Test void adjacentBigIdsAndMaximumLongRemainDistinct() throws Exception {
        seed();String[] ids={"9007199254740992","9007199254740993","9223372036854775807"},names={"synthetic-patient-even","synthetic-patient-odd","synthetic-patient-max"},studies={"RA-EVEN","RA-ODD","RA-MAX"},visits={"9007199254741001","9007199254741003"},dates={"2026-09-01","2026-10-01"};
        for(int i=0;i<ids.length;i++){
            org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content("{\"patientId\":\""+ids[i]+"\"}")).andReturn();assertEquals(200,r.getResponse().getStatus());
            com.fasterxml.jackson.databind.JsonNode d=json.readTree(r.getResponse().getContentAsByteArray()).path("data");assertEquals(ids[i],d.path("patientId").textValue());assertEquals(names[i],d.path("name").textValue());assertEquals(studies[i],d.path("studyNo").textValue());assertEquals(i==2?0:1,d.path("visits").size());
            if(i<2){assertEquals(visits[i],d.path("visits").get(0).path("visitId").textValue());assertEquals(dates[i],d.path("visits").get(0).path("visitDate").textValue());}assertTrue(d.path("latestDas28").isNull());
        }
    }

    private com.fasterxml.jackson.databind.JsonNode error(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,int status,String code)throws Exception{
        org.springframework.test.web.servlet.MvcResult r=http.perform(request).andReturn();assertEquals(status,r.getResponse().getStatus());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));com.fasterxml.jackson.databind.JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertFalse(e.path("success").asBoolean());assertEquals(code,e.path("code").textValue());assertTrue(e.path("data").isNull());return e;
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String id){return post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content("{\"patientId\":\""+id+"\"}");}
    @Test void currentRelationshipsHideOtherNonRaMissingAndRevokedPatients()throws Exception{
        seed();for(String id:new String[]{"8001","8002","8999"}){com.fasterxml.jackson.databind.JsonNode e=error(request(id),404,"RESOURCE_NOT_FOUND");assertFalse(e.toString().contains("synthetic-"));}
        assertEquals(200,http.perform(request("9007199254740993")).andReturn().getResponse().getStatus());
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=9007199254740993");error(request("9007199254740993"),404,"RESOURCE_NOT_FOUND");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,9007199254740993,7)");assertEquals(200,http.perform(request("9007199254740993")).andReturn().getResponse().getStatus());
        sql("RENAME TABLE patient_follow_up_history TO synthetic_source_offline");try{com.fasterxml.jackson.databind.JsonNode e=error(request("9007199254740993"),503,"SERVICE_UNAVAILABLE");assertFalse(e.toString().contains("SQL"));assertFalse(e.toString().contains("synthetic_source"));}finally{sql("RENAME TABLE synthetic_source_offline TO patient_follow_up_history");}
    }

    @Test void malformedAndOversizedBodiesAreRejectedBeforeSql()throws Exception{
        String[] bad={"", "{}", "[]", "null", "{", "{} {}", "{\"patientId\":\"1\",\"patientId\":\"2\"}", "{\"patientId\":\"1\",\"doctorId\":\"101\"}", "{\"patientId\":1}", "{\"patientId\":null}"};
        for(String b:bad){error(post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content(b),400,"INVALID_REQUEST");assertEquals(0,observed.borrowed);}
        for(String id:new String[]{"0","01","1e1","-1",""," 1","9223372036854775808"}){error(request(id),400,"INVALID_REQUEST");assertEquals(0,observed.selects);}
        error(post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content(new byte[]{(byte)0xc3,(byte)0x28}),400,"INVALID_REQUEST");
        String spaces=String.join("",java.util.Collections.nCopies(4097," "));error(post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content(spaces),413,"LIMIT_EXCEEDED");assertEquals(4097,body.bytes);assertEquals(0,observed.borrowed);
        seed();String valid="{\"patientId\":\"9223372036854775807\"}";valid+=String.join("",java.util.Collections.nCopies(4096-valid.getBytes("UTF-8").length," "));assertEquals(200,http.perform(post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content(valid)).andReturn().getResponse().getStatus());assertEquals(4096,body.bytes);
    }

    @Test void untrustedExpiredAndDisabledSessionsRejectBeforeBodyAndSql()throws Exception{
        org.springframework.mock.web.MockHttpSession wrong=session(101);wrong.setAttribute(com.wenwen.ai.scope.AuthenticatedDoctorSession.ATTRIBUTE,"101");
        error(post("/api/ra/ai/patientDetail").session(wrong).content("{"),401,"UNAUTHENTICATED");assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
        org.springframework.mock.web.MockHttpSession expired=session(101);expired.invalidate();error(post("/api/ra/ai/patientDetail").session(expired).content("{"),401,"UNAUTHENTICATED");assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
        context.close();apiEnabled=false;setup();
        error(post("/api/ra/ai/patientDetail").session(session(101)).content("{"),503,"SERVICE_UNAVAILABLE");assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
        error(post("/api/ra/ai/patientDetail").content("{"),401,"UNAUTHENTICATED");assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
    }
}
