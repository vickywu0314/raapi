package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.scope.*;
import com.wenwen.ai.treatment.DrugDictionary;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ConfiguredDrugDictionaryHttpTest extends CohortHttpFixture {
    final Map<String,Object> properties=new HashMap<>();
    static final String V1="{\"version\":\"synthetic-drug-v1\",\"drugs\":[{\"id\":\"synthetic:g1\",\"name\":\"synthetic-generic-A\",\"category\":\"TNFi\",\"aliases\":[\"synthetic-brand-A\"]}]}";
    ConfiguredDrugDictionaryHttpTest(){properties.put("ra.ai.drug-dictionary.json",V1);}
    @Override void buildContext(boolean ignored){super.buildContext(false);}
    @Override void beforeRefresh(){context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic-drugs",properties));context.register(com.wenwen.controller.ra.AiOverviewController.class,com.wenwen.ai.overview.AiOverviewService.class);}
    MockHttpSession session(){MockHttpSession s=new MockHttpSession();s.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,new AuthenticatedDoctorSession(new TrustedDoctor(101)));return s;}
    @Override MvcResult request(String body)throws Exception{return http.perform(post("/api/ra/ai/cohort").session(session()).contentType("application/json").content(body)).andReturn();}
    void seed(String rows)throws Exception{
        sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_follow_up_history");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0)");
        visit(100,1,0,"{\"result\":{\"crpScore\":2.3}}","2025-01-01",null,null);
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE id=100")){s.setString(1,"{\"xyList\":["+rows+"]}");s.executeUpdate();}
        observed.reset();
    }
    String row(String name){return "{\"drugName\":\""+name+"\",\"startTime\":\"2025-01-01\",\"endTime\":null}";}
    JsonNode treatment(JsonNode d){return patientById(d,1).path("treatment");}
    @Test void configuredAliasUsesProductionDictionary()throws Exception{
        assertEquals(Collections.singleton("cohortDrugDictionary"),context.getBeansOfType(DrugDictionary.class).keySet());
        assertTrue(context.getBean(PrincipalProvider.class) instanceof SessionPrincipalProvider);
        seed(row("synthetic-brand-A"));
        JsonNode d=success("{\"filters\":{\"tx\":\"TNFi\"}}");
        assertEquals(1,d.path("n").asInt());assertEquals(Arrays.asList("1"),ids(d));
        assertEquals("synthetic-drug-v1",d.path("meta").path("policyVersions").path("drugDictionary").asText());
        assertEquals(json.readTree("[\"synthetic:g1\"]"),treatment(d).path("genericDrugIds"));
    }
    @Test void aliasesShareIdentityWhileDifferentEntitiesConflictAndUnknownStaysUnknown()throws Exception{
        seed(row("synthetic-generic-A")+","+row("synthetic-brand-A"));
        JsonNode tx=treatment(success("{\"filters\":{\"tx\":\"TNFi\"}}"));
        assertEquals("ACTIVE",tx.path("state").asText());assertEquals(2,tx.path("line").asInt());
        assertEquals(1,tx.path("targetedDrugHistoryN").asInt());assertEquals(json.readTree("[\"synthetic:g1\"]"),tx.path("genericDrugIds"));
        properties.put("ra.ai.drug-dictionary.json",V1.replace("}]}","},{\"id\":\"synthetic:g2\",\"name\":\"synthetic-generic-B\",\"category\":\"JAKi\",\"aliases\":[]}] }"));
        seed(row("synthetic-brand-A")+","+row("synthetic-generic-B"));
        tx=treatment(success("{}"));assertEquals("CONFLICT",tx.path("state").asText());assertTrue(tx.path("category").isNull());
        assertEquals(2,tx.path("targetedDrugHistoryN").asInt());
        observed.reset();assertEquals(0,success("{\"filters\":{\"tx\":\"TNFi\"}}").path("n").asInt());
        seed(row("unmapped"));tx=treatment(success("{}"));assertEquals("UNKNOWN",tx.path("state").asText());
        assertEquals("UNMAPPED_DRUG",tx.path("provenance").path("missingReason").asText());
        seed("{\"goodsName\":\"synthetic-brand-A\"}");assertEquals("UNKNOWN",treatment(success("{}")).path("state").asText());
    }
    void zeroSql(){assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);assertEquals(0,observed.inserts);assertEquals(0,observed.deletes);assertEquals(0,observed.writeCommits);assertEquals(0,observed.active.get());}
    @Test void invalidConfigurationIs503BeforeAnySqlAndIdentityAndFeatureGatesRemainFirst()throws Exception{
        seed(row("synthetic-brand-A"));
        for(String bad:Arrays.asList("", "not-json-private-canary",V1.replace("TNFi","bad-private-canary"),V1+" {}")){
            properties.put("ra.ai.drug-dictionary.json",bad);
            MvcResult r=request("{}");assertEquals(503,r.getResponse().getStatus());
            JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertFalse(e.path("success").asBoolean());assertEquals("SERVICE_UNAVAILABLE",e.path("code").asText());assertTrue(e.path("data").isNull());
            assertNotNull(r.getResponse().getHeader("X-Trace-Id"));assertFalse(r.getResponse().getContentAsString().contains("private-canary"));zeroSql();
        }
        bodyRead.bytes=0;
        MvcResult anonymous=http.perform(post("/api/ra/ai/cohort").content("{").principal(()->"101").header("X-Doctor-Id","101")).andReturn();
        assertEquals(401,anonymous.getResponse().getStatus());assertEquals(0,bodyRead.bytes);zeroSql();
        context.close();properties.put("ra.ai.api.enabled","false");buildContext(false);bodyRead.bytes=0;
        error("{",503,"SERVICE_UNAVAILABLE");assertEquals(0,bodyRead.bytes);zeroSql();
    }
    void cohortReleased(){assertEquals(4,observed.selects);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());assertEquals(1,observed.inserts);assertEquals(1,observed.writeCommits);assertEquals(1,new HashSet<>(observed.queryConnections).size());assertEquals(Collections.nCopies(4,Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);assertEquals(Collections.nCopies(4,false),observed.autoCommits);}
    @Test void realFirstSqlBarrierChangesOnlyTheNextRequestDictionary()throws Exception{
        seed(row("synthetic-brand-A"));java.util.concurrent.atomic.AtomicInteger reached=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterFirstQuery=()->{
            assertEquals(1,observed.selects);assertEquals(1,observed.active.get());reached.incrementAndGet();
            properties.put("ra.ai.drug-dictionary.json",V1.replace("synthetic-drug-v1","synthetic-drug-v2").replace("TNFi","JAKi"));
            System.out.println("P03a barrier reached after first real SELECT; active=1; configuration V1 -> V2");
        };
        JsonNode old=success("{\"filters\":{\"tx\":\"TNFi\"}}");assertEquals(1,reached.get());assertNull(observed.afterFirstQuery);
        assertEquals(Arrays.asList("1"),ids(old));assertEquals("synthetic-drug-v1",old.path("meta").path("policyVersions").path("drugDictionary").asText());
        assertEquals("TNFi",treatment(old).path("category").asText());assertEquals("synthetic-drug-v1",treatment(old).path("provenance").path("dictionaryVersion").asText());cohortReleased();
        observed.reset();JsonNode next=success("{\"filters\":{\"tx\":\"JAKi\"}}");assertEquals(Arrays.asList("1"),ids(next));
        assertEquals("synthetic-drug-v2",next.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals("JAKi",treatment(next).path("category").asText());cohortReleased();
        observed.reset();assertEquals(0,success("{\"filters\":{\"tx\":\"TNFi\"}}").path("n").asInt());cohortReleased();
    }
    MvcResult overview()throws Exception{return http.perform(post("/api/ra/ai/overview").session(session()).contentType("application/json").content("{}")).andReturn();}
    @Test void configuredOverviewUsesSameProductionBeanAndFiveReadOnlySelects()throws Exception{
        seed(row("synthetic-brand-A"));observed.failInsertSql=true;
        assertEquals(Collections.singleton("cohortDrugDictionary"),context.getBeansOfType(DrugDictionary.class).keySet());
        MvcResult r=overview();assertEquals(200,r.getResponse().getStatus());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertTrue(e.path("success").asBoolean());
        JsonNode d=e.path("data");assertEquals(1,d.path("studyTotal").asInt());assertEquals(1,d.path("targetRate").path("value").asDouble());
        assertEquals(1,d.path("targetRate").path("meta").path("numerator").asInt());assertEquals(1,d.path("targetRate").path("meta").path("denominator").asInt());assertEquals(0,d.path("targetRate").path("meta").path("unknownN").asInt());
        assertEquals(0,d.path("modHighN").asInt());assertEquals(0,d.path("fmN").asInt());assertEquals(1,d.path("missingN").asInt());
        assertEquals("synthetic-drug-v1",d.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals(r.getResponse().getHeader("X-Trace-Id"),d.path("meta").path("traceId").asText());
        assertEquals(4,observed.selects);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());
        assertEquals(1,new HashSet<>(observed.queryConnections).size());assertEquals(Collections.nCopies(4,Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);assertEquals(Collections.nCopies(4,false),observed.autoCommits);
        assertEquals(0,observed.inserts);assertEquals(0,observed.deletes);assertEquals(0,observed.writeCommits);
        try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet rows=s.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
        observed.reset();properties.put("ra.ai.drug-dictionary.json","");r=overview();assertEquals(503,r.getResponse().getStatus());assertEquals("SERVICE_UNAVAILABLE",json.readTree(r.getResponse().getContentAsByteArray()).path("code").asText());zeroSql();
    }
    com.wenwen.ai.result.AnalysisRun retained(JsonNode d){com.wenwen.ai.result.AnalysisRun run=context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).find(d.path("analysisId").asText());assertNotNull(run);assertEquals(101,run.getOwnerDoctorId());assertEquals(0,observed.active.get());return run;}
    JsonNode decoded(com.wenwen.ai.result.AnalysisRun run){return json.valueToTree(new com.wenwen.ai.result.AnalysisResultCodec().decode(run));}
    @Test void querySessionRequiresReanalysisAfterDictionaryVersionChanges()throws Exception {
        properties.put("ra.ai.drug-dictionary.json",V1);seed(row("synthetic-brand-A"));JsonNode first=success("{\"filters\":{\"tx\":\"TNFi\"}}");cohortReleased();
        com.wenwen.ai.result.AnalysisRun run=retained(first);assertNull(run.getPayload());assertNotNull(run.getQueryJson());assertEquals(2,run.getPayloadVersion());
        assertEquals("synthetic-drug-v1",first.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals("TNFi",first.path("patients").path("items").get(0).path("treatment").path("category").asText());
        properties.put("ra.ai.drug-dictionary.json",V1.replace("synthetic-drug-v1","synthetic-drug-v2").replace("TNFi","JAKi"));
        com.wenwen.ai.query.CohortException e=assertThrows(com.wenwen.ai.query.CohortException.class,()->context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).export(101,run.getId(),clock.instant(),100));assertEquals(410,e.getStatus());
        JsonNode second=success("{\"filters\":{\"tx\":\"JAKi\"}}");assertEquals("synthetic-drug-v2",second.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals("JAKi",second.path("patients").path("items").get(0).path("treatment").path("category").asText());
    }
}
