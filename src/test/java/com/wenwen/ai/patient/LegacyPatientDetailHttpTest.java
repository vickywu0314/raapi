package com.wenwen.ai.patient;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class LegacyPatientDetailHttpTest extends TrustedPatientHttpFixture {
    @Test void legacyListHttpBytesPreserveAdjacentAndMaxPatientIds()throws Exception{
        seed();
        org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/patient/patientsList").param("doctorId","101").param("size","200")).andReturn();
        assertEquals(200,r.getResponse().getStatus());
        String raw=r.getResponse().getContentAsString();com.fasterxml.jackson.databind.JsonNode response=json.readTree(raw);
        assertEquals("200",response.path("code").textValue());assertTrue(response.path("success").asBoolean());
        java.util.Set<String> ids=new java.util.HashSet<String>();
        for(com.fasterxml.jackson.databind.JsonNode item:response.path("data").path("items")){assertTrue(item.path("patientId").isTextual());ids.add(item.path("patientId").textValue());}
        for(String id:new String[]{"9007199254740992","9007199254740993","9223372036854775807"}){assertTrue(ids.contains(id));assertTrue(raw.contains("\"patientId\":\""+id+"\""));}
    }
    @Test void anonymousForgedDoctorIsRejectedBeforeSql()throws Exception{
        seed();
        org.springframework.test.web.servlet.MvcResult result=http.perform(post("/api/ra/patient/patientDetail")
            .contentType("application/x-www-form-urlencoded").param("patientId","9007199254740993").param("doctorId","101")
            .param("from","patients").param("source","ordinary").header("X-Doctor-Id","101").principal(()->"101")).andReturn();
        assertEquals(401,result.getResponse().getStatus());
        com.fasterxml.jackson.databind.JsonNode response=json.readTree(result.getResponse().getContentAsByteArray());
        assertEquals("UNAUTHENTICATED",response.path("code").asText());assertFalse(response.path("success").asBoolean());assertTrue(response.path("data").isNull());
        assertNotNull(result.getResponse().getHeader("X-Trace-Id"));assertEquals(0,observed.selects);assertEquals(0,observed.borrowed);
    }
    @Test void typedDetailPreservesExactIdsAndFullLegacyFields()throws Exception{
        seed();
        for(String id:new String[]{"9007199254740992","9007199254740993","9223372036854775807"}){
            org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/patient/patientDetail").session(session(101)).param("patientId",id)).andReturn();
            assertEquals(200,r.getResponse().getStatus());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
            String raw=r.getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode d=json.readTree(raw).path("data");
            assertEquals(id,d.path("patientId").textValue());assertTrue(raw.contains("\"patientId\":\""+id+"\""));
            assertTrue(d.has("diagnosisInformed"));assertTrue(d.has("consentSigned"));assertTrue(d.has("acrEularScore"));
            if(!id.equals("9223372036854775807")){
                com.fasterxml.jackson.databind.JsonNode visit=d.path("visits").get(0);
                assertEquals(id.endsWith("2")?"9007199254741001":"9007199254741003",visit.path("visitId").textValue());assertEquals("101",visit.path("doctorId").textValue());
            }
        }
    }
    @Test void optionalExtraMissingTableIsCompatibleButBadColumnIsUnavailable()throws Exception{
        seed();
        sql("RENAME TABLE patient_ra_extra TO synthetic_optional_extra");
        try{assertEquals(200,http.perform(post("/api/ra/patient/patientDetail").session(session(101)).param("patientId","9007199254740993")).andReturn().getResponse().getStatus());}
        finally{sql("RENAME TABLE synthetic_optional_extra TO patient_ra_extra");}
        sql("ALTER TABLE patient_ra_extra CHANGE consent_signed broken_consent TINYINT NULL");
        org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/patient/patientDetail").session(session(101)).param("patientId","9007199254740993")).andReturn();
        assertEquals(503,r.getResponse().getStatus());
        assertEquals("SERVICE_UNAVAILABLE",json.readTree(r.getResponse().getContentAsByteArray()).path("code").textValue());
        assertFalse(r.getResponse().getContentAsString().contains("broken_consent"));
    }
    @Test void adjacentVisitReadIdsAreExactStringsWithNullCreateVisit()throws Exception{
        seed();
        String[] routes={"visitDetail","visitEditForm","visitCreateForm"};
        for(String route:routes){
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder q=post("/api/ra/visit/"+route).param("doctorId","101");
            if(route.equals("visitCreateForm"))q.param("patientId","9007199254740993");else q.param("visitId","9007199254741003");
            org.springframework.test.web.servlet.MvcResult r=http.perform(q).andReturn();
            assertEquals(200,r.getResponse().getStatus());
            com.fasterxml.jackson.databind.JsonNode d=json.readTree(r.getResponse().getContentAsByteArray()).path("data");
            assertEquals("9007199254740993",d.path("patientId").textValue());
            if(route.equals("visitCreateForm"))assertTrue(d.path("visitId").isNull());
            else assertEquals("9007199254741003",d.path("visitId").textValue());
            if(route.equals("visitDetail"))assertEquals("101",d.path("doctorId").textValue());
            else {if(route.equals("visitCreateForm"))assertTrue(d.path("visitNo").isIntegralNumber());else assertTrue(d.path("visitNo").isNull());assertTrue(d.path("modules").isArray());}
        }
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String id){return post("/api/ra/patient/patientDetail").session(session(101)).param("patientId",id);}
    private void failure(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder q,int status,String code)throws Exception{
        org.springframework.test.web.servlet.MvcResult r=http.perform(q).andReturn();assertEquals(status,r.getResponse().getStatus());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
        com.fasterxml.jackson.databind.JsonNode d=json.readTree(r.getResponse().getContentAsByteArray());assertEquals(code,d.path("code").textValue());assertFalse(d.path("success").asBoolean());assertTrue(d.path("data").isNull());assertFalse(r.getResponse().getContentAsString().contains("SQL"));assertFalse(r.getResponse().getContentAsString().contains("synthetic-patient"));
    }
    @Test void malformedDuplicateAndMismatchedHintsRejectWithoutSql()throws Exception{
        for(String id:new String[]{"0","01","-1","1e1",""," 1","9223372036854775808"})failure(request(id),400,"INVALID_REQUEST");
        failure(post("/api/ra/patient/patientDetail").session(session(101)),400,"INVALID_REQUEST");
        failure(request("1").param("patientId","2"),400,"INVALID_REQUEST");
        failure(request("1").param("doctorId","101","101"),400,"INVALID_REQUEST");
        failure(request("1").param("doctorId","01"),400,"INVALID_REQUEST");
        failure(request("1").param("doctorId","202"),403,"FORBIDDEN");
        assertEquals(0,observed.selects);assertEquals(0,observed.borrowed);
    }
    @Test void anonymousIdentityAlwaysPrecedesInputAndSourceHints()throws Exception{
        for(String source:new String[]{"ordinary","ai-cohort","ai-analysis","patients"})failure(post("/api/ra/patient/patientDetail").param("from",source).param("source",source).param("patientId","bad").param("doctorId","101","202").principal(()->"101"),401,"UNAUTHENTICATED");
        org.springframework.mock.web.MockHttpSession untyped=session(101);untyped.setAttribute(com.wenwen.ai.scope.AuthenticatedDoctorSession.ATTRIBUTE,"101");untyped.setAttribute("ra.doctorId",101L);
        failure(post("/api/ra/patient/patientDetail").session(untyped).param("patientId","1"),401,"UNAUTHENTICATED");
        org.springframework.mock.web.MockHttpSession expired=session(101);expired.invalidate();failure(post("/api/ra/patient/patientDetail").session(expired),401,"UNAUTHENTICATED");assertEquals(0,observed.borrowed);
    }
    @Test void currentOwnRaRelationsHideOthersDeletedRevokedAndSqlFaults()throws Exception{
        seed();for(String id:new String[]{"8001","8002","8999"})failure(request(id),404,"RESOURCE_NOT_FOUND");
        assertEquals(200,http.perform(request("9007199254740993").param("doctorId","101")).andReturn().getResponse().getStatus());
        failure(post("/api/ra/patient/patientDetail").session(session(202)).param("patientId","9007199254740993"),404,"RESOURCE_NOT_FOUND");
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,9007199254740993,7)");
        com.fasterxml.jackson.databind.JsonNode d=json.readTree(http.perform(request("9007199254740993")).andReturn().getResponse().getContentAsByteArray()).path("data");assertEquals(1,d.path("visits").size());
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=9007199254740993");failure(request("9007199254740993"),404,"RESOURCE_NOT_FOUND");
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,9007199254740993,7)");
        sql("DELETE FROM patient_basic_info WHERE id=9223372036854775807");failure(request("9223372036854775807"),404,"RESOURCE_NOT_FOUND");
        sql("RENAME TABLE patient_follow_up_history TO synthetic_unavailable");try{failure(request("9007199254740993"),503,"SERVICE_UNAVAILABLE");}finally{sql("RENAME TABLE synthetic_unavailable TO patient_follow_up_history");}
    }
    @Test void ordinaryDetailDoesNotDependOnAiFeatureFlag()throws Exception{
        context.close();apiEnabled=false;setup();seed();assertEquals(200,http.perform(request("9007199254740993")).andReturn().getResponse().getStatus());
    }
}
