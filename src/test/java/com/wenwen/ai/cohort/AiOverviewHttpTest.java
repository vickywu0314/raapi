package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AiOverviewHttpTest extends DescriptiveHttpFixture {
    @Override void buildContext(boolean ignored) { super.buildContext(false); }
    boolean apiEnabled=true;
    @Override void beforeRefresh() { context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("overview-feature",java.util.Collections.singletonMap("ra.ai.api.enabled",String.valueOf(apiEnabled))));super.beforeRefresh();context.register(com.wenwen.controller.ra.AiOverviewController.class,com.wenwen.ai.overview.AiOverviewService.class); }
    MockHttpSession session(long id) {
        MockHttpSession s=new MockHttpSession();
        s.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,new AuthenticatedDoctorSession(new TrustedDoctor(id)));return s;
    }
    MvcResult overview(byte[] body,MockHttpSession session) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r=post("/api/ra/ai/overview").contentType("application/json;charset=UTF-8").content(body);
        if(session!=null)r.session(session);
        return http.perform(r).andReturn();
    }
    JsonNode overviewSuccess() throws Exception {
        MvcResult r=overview("{}".getBytes("UTF-8"),session(101));
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertTrue(e.path("success").asBoolean());assertEquals("200",e.path("code").asText());
        assertEquals(r.getResponse().getHeader("X-Trace-Id"),e.path("data").path("meta").path("traceId").asText());return e.path("data");
    }
    @Test void returnsFiveCardsFromOneTrustedSourceRead() throws Exception {
        sixPatients();observed.failInsertSql=true;
        JsonNode d=overviewSuccess();
        assertEquals(6,d.path("studyTotal").asInt());assertEquals(.4,d.path("targetRate").path("value").asDouble());
        JsonNode m=d.path("targetRate").path("meta");assertEquals(2,m.path("numerator").asInt());assertEquals(5,m.path("denominator").asInt());assertEquals(1,m.path("unknownN").asInt());assertEquals("OK",m.path("status").asText());assertEquals("40%",m.path("displayText").asText());
        assertEquals(3,d.path("modHighN").asInt());assertEquals(3,d.path("fmN").asInt());assertEquals(2,d.path("missingN").asInt());
        sourceReleased();assertEquals(0,observed.inserts);assertEquals(0,observed.deletes);assertEquals(0,observed.writeCommits);
    }

    @Test void sourceMetadataPrivacyAndSnapshotAreExplicit() throws Exception {
        sixPatients();int snapshots=dictionary().snapshots;
        clock.onInstant=()->{ if(observed.selects==5)assertEquals(0,observed.active.get(),"completed clock after source release"); };
        JsonNode d=overviewSuccess(),m=d.path("meta");
        assertEquals("RA",m.path("studyCode").asText());assertEquals("now",m.path("at").asText());assertEquals("2026-10-07",m.path("asOfDate").asText());
        assertEquals("2026-10-07T02:00:00Z",m.path("readStartedAt").asText());assertEquals("2026-10-07T02:00:00Z",m.path("readCompletedAt").asText());assertEquals(snapshots+1,dictionary().snapshots);
        assertEquals(9,m.path("policyVersions").size());assertTrue(m.path("policyVersions").path("drugDictionary").asText().startsWith("dev-"));
        assertEquals(6,d.size());for(String secret:new String[]{"patientId","合成患者","110101","analysisId","cursor","OTHER-SECRET"})assertFalse(d.toString().contains(secret));
        sourceReleased();assertEquals(1,new java.util.HashSet<>(observed.queryConnections).size());assertEquals(java.util.Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);assertEquals(java.util.Collections.nCopies(5,false),observed.autoCommits);
        try(java.sql.Connection c=raw.getConnection();java.sql.Statement s=c.createStatement();java.sql.ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){r.next();assertEquals(0,r.getInt(1));}
    }

    @Test void emptyUnknownAndRealZeroRemainDistinct() throws Exception {
        sixPatients();sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101");observed.reset();JsonNode empty=overviewSuccess();assertEquals(0,empty.path("studyTotal").asInt());assertEquals("NO_DATA",empty.path("targetRate").path("meta").path("status").asText());sourceReleased();
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,2,0,0)");sql("UPDATE patient_follow_up_history SET bqpg=NULL WHERE patient_basic_info_id<=2");observed.reset();JsonNode unknown=overviewSuccess();assertEquals(2,unknown.path("studyTotal").asInt());assertEquals("NO_DATA",unknown.path("targetRate").path("meta").path("status").asText());assertEquals(2,unknown.path("targetRate").path("meta").path("unknownN").asInt());assertEquals(0,unknown.path("modHighN").asInt());sourceReleased();
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":4.11}}' WHERE patient_basic_info_id<=2");observed.reset();JsonNode zero=overviewSuccess();assertEquals("OK",zero.path("targetRate").path("meta").path("status").asText());assertEquals(2,zero.path("targetRate").path("meta").path("denominator").asInt());assertEquals(0,zero.path("targetRate").path("value").asDouble());assertEquals("0%",zero.path("targetRate").path("meta").path("displayText").asText());assertEquals(2,zero.path("modHighN").asInt());sourceReleased();
    }

    void overviewError(byte[] body,MockHttpSession session,int status,String code)throws Exception {
        MvcResult r=overview(body,session);assertEquals(status,r.getResponse().getStatus());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertFalse(e.path("success").asBoolean());assertEquals(code,e.path("code").asText());assertTrue(e.path("data").isNull());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
    }
    @Test void strictEmptyBodyIsBoundedBeforeSql()throws Exception {
        for(String body:new String[]{"", "null", "[]", "{", "{} {}", "{\"doctorId\":\"101\"}", "{\"x\":1,\"x\":2}"}){overviewError(body.getBytes("UTF-8"),session(101),400,"INVALID_REQUEST");assertEquals(0,observed.borrowed);}
        overviewError(new byte[]{(byte)0xc3,(byte)0x28},session(101),400,"INVALID_REQUEST");
        bodyRead.bytes=0;overviewError(String.join("",java.util.Collections.nCopies(5000," ")).getBytes("UTF-8"),session(101),413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);assertEquals(0,observed.borrowed);
        byte[] exact=("{}"+String.join("",java.util.Collections.nCopies(4094," "))).getBytes("UTF-8");assertEquals(200,overview(exact,session(101)).getResponse().getStatus());sourceReleased();
    }

    @Test void productionIdentityAndGateRejectBeforeReading()throws Exception {
        MvcResult anonymous=http.perform(post("/api/ra/ai/overview").content("{").principal(()->"101").header("X-Doctor-Id","101")).andReturn();assertEquals(401,anonymous.getResponse().getStatus());assertEquals(0,bodyRead.bytes);assertEquals(0,observed.borrowed);
        MockHttpSession wrong=session(101);wrong.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,"101");overviewError("{".getBytes("UTF-8"),wrong,401,"UNAUTHENTICATED");assertEquals(0,bodyRead.bytes);
        context.close();apiEnabled=false;buildContext(false);observed.reset();bodyRead.bytes=0;
        overviewError("{".getBytes("UTF-8"),session(101),503,"SERVICE_UNAVAILABLE");assertEquals(0,bodyRead.bytes);assertEquals(0,observed.borrowed);overviewError("{".getBytes("UTF-8"),null,401,"UNAUTHENTICATED");assertEquals(0,bodyRead.bytes);
    }

    @Test void snapshotIsCompleteAndNextRequestReadsCommittedChanges()throws Exception {
        sixPatients();observed.afterFirstQuery=()->{try{sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":4.11}}' WHERE patient_basic_info_id=1");sql("DELETE FROM patient_comorbidity WHERE patient_id=1");sql("UPDATE patient_basic_info SET jws=NULL WHERE id=1");}catch(Exception e){throw new RuntimeException(e);}};
        JsonNode old=overviewSuccess();assertEquals(.4,old.path("targetRate").path("value").asDouble());assertEquals(3,old.path("fmN").asInt());assertEquals(2,old.path("missingN").asInt());sourceReleased();
        observed.reset();JsonNode next=overviewSuccess();assertEquals(.2,next.path("targetRate").path("value").asDouble());assertEquals(4,next.path("modHighN").asInt());assertEquals(2,next.path("fmN").asInt());assertEquals(3,next.path("missingN").asInt());sourceReleased();
        observed.reset();observed.failSecondSql=true;overviewError("{}".getBytes("UTF-8"),session(101),503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());assertTrue(observed.mysqlFailures>0);assertEquals(0,observed.inserts);
    }
}
