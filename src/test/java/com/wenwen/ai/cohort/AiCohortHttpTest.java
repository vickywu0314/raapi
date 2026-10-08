package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiCohortHttpTest extends CohortHttpFixture {
    @Test void trustedDoctorGetsRealFivePatientDistribution() throws Exception {
        JsonNode data = success("{}");
        assertEquals(5, data.path("n").asInt());
        assertEquals(5, data.path("studyTotal").asInt());
        assertEquals(Arrays.asList("5","3","1","7","6"), ids(data));
        assertEquals(4, data.path("activity").path("evalN").asInt());
        assertEquals(1, data.path("activity").path("unknownN").asInt());
        String[] levels = {"remission","low","moderate","high"};
        JsonNode counts = data.path("activity").path("current"); assertEquals(4,counts.size());
        for (int i=0;i<4;i++) { assertEquals(levels[i],counts.get(i).path("level").asText()); assertEquals(1,counts.get(i).path("count").asInt()); }
        String[] scores = {"4.11","2.71","2.3","0.0",null};
        for (int i=0;i<5;i++) {
            JsonNode item = data.path("patients").path("items").get(i);
            if (scores[i] == null) assertTrue(item.path("das28At").isNull());
            else assertEquals(new java.math.BigDecimal(scores[i]),item.path("das28At").decimalValue());
        }
    }
    @Test void defaultProviderIgnoresForgedIdentityBeforeSql() throws Exception {
        context.close(); buildContext(false);
        org.springframework.test.web.servlet.MvcResult response = http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ra/ai/cohort")
            .contentType("application/json").content("{\"doctorId\":\"101\"}").header("X-Doctor-Id","101").principal(() -> "101")).andReturn();
        assertEquals(401,response.getResponse().getStatus());
        assertEquals("UNAUTHENTICATED",json.readTree(response.getResponse().getContentAsByteArray()).path("code").asText());
        assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        assertNotNull(response.getResponse().getHeader("X-Trace-Id"));
    }
    @Test void doctorAssertionCannotChangeTrustedScope() throws Exception {
        error("{\"doctorId\":\"202\"}",403,"FORBIDDEN");
        assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        for (String id : new String[]{"101",null}) {
            JsonNode data = success(id == null ? "{\"doctorId\":null}" : "{\"doctorId\":\"101\"}");
            assertEquals(Arrays.asList("5","3","1","7","6"),ids(data));
        }
        observed.reset();
        for (String id : new String[]{"101", "\"01\"", "\"-1\"", "\"9223372036854775808\"", "\"\""}) {
            error("{\"doctorId\":"+id+"}",400,"INVALID_FILTER");
            assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        }
    }
    @Test void invalidUnknownAndUnsupportedInputsFailBeforeSql() throws Exception {
        String[] invalid = {"", "[]", "null", "1", "{", "{} {}", "{\"filters\":[],\"doctorId\":null}", "{\"filters\":true}",
            "{\"filters\":{},\"filters\":{}}", "{\"filters\":{\"act\":\"low\",\"act\":\"high\"}}", "{\"cohortId\":\"x\"}",
            "{\"filters\":{\"studyCode\":\"AS\"}}", "{\"filters\":{\"studyCode\":0}}", "{\"filters\":{\"at\":\"6M\"}}",
            "{\"filters\":{\"at\":[]}}", "{\"filters\":{\"act\":\"unknown\"}}", "{\"filters\":{\"act\":0}}",
            "{\"filters\":{\"sex\":\"X\"}}", "{\"filters\":{\"age\":\"40-18\"}}", "{\"filters\":{\"tx\":\"OTHER\"}}", "{\"filters\":{\"q\":\"x\"}}",
            "{\"filters\":{\"ids\":\"1,3\"}}", "{\"filters\":{\"ids\":[1]}}", "{\"filters\":{\"ids\":[null]}}",
            "{\"filters\":{\"ids\":[\"0\"]}}", "{\"filters\":{\"ids\":[\"-1\"]}}", "{\"filters\":{\"ids\":[\"01\"]}}",
            "{\"filters\":{\"ids\":[\"+1\"]}}", "{\"filters\":{\"ids\":[\"1.0\"]}}", "{\"filters\":{\"ids\":[\"1e1\"]}}",
            "{\"filters\":{\"ids\":[\" 1\"]}}", "{\"filters\":{\"ids\":[\"\"]}}", "{\"filters\":{\"ids\":[\"9223372036854775808\"]}}"};
        for(String body:invalid) { error(body,400,"INVALID_FILTER"); assertEquals(0,observed.selects,body); assertEquals(0,observed.borrowed,body); }
        for(String body:new String[]{"{\"filters\":null}","{\"filters\":{\"studyCode\":null,\"at\":null,\"act\":null}}",
            "{\"filters\":{\"studyCode\":\"\",\"at\":\"\",\"act\":\"\"}}", "{\"filters\":{\"studyCode\":\"RA\",\"at\":\"now\"}}"}) {
            assertEquals(5,success(body).path("n").asInt());
        }
    }
    @Test void originalIdsArrayAndUtf8BodyAreBoundedBeforeSql() throws Exception {
        String ids = String.join(",",java.util.Collections.nCopies(5001,"\"1\""));
        error("{\"filters\":{\"ids\":["+ids+"]}}",413,"LIMIT_EXCEEDED");
        assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        String oversize = "{}" + String.join("",java.util.Collections.nCopies(131072," "));
        error(oversize,413,"LIMIT_EXCEEDED"); assertEquals(131073,bodyRead.bytes); assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        String utf8 = "{\"q\":\"" + String.join("",java.util.Collections.nCopies(50000,"合")) + "\"}";
        assertTrue(utf8.length()<131072); error(utf8,413,"LIMIT_EXCEEDED"); assertEquals(131073,bodyRead.bytes); assertEquals(0,observed.selects);
        assertEquals(5,success("{}"+String.join("",java.util.Collections.nCopies(131070," "))).path("n").asInt()); assertEquals(131072,bodyRead.bytes);
        assertEquals(1,success("{\"filters\":{\"ids\":["+String.join(",",java.util.Collections.nCopies(5000,"\"1\""))+"]}}").path("n").asInt());
    }
    @Test void exactDecimalLegacyProvenanceAndMalformedFieldsStayDistinct() throws Exception {
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id IN (1,5,6,7)");
        String precise="2.2949999999999999999";
        visit(101,1,0,"{ \"result\": { \"crpScore\": \""+precise+"\", \"ytgjs\":3, \"zzgjs\":0 }, \"ztScoreByPatient\":50, \"secret\":\"synthetic-private-json\" }","2026-10-07",null,"{\"cfydb\":\"3\"}");
        visit(701,7,7,"{\"result\":{\"crpScore\":"+precise+"}}","2026-10-07",null,null);
        visit(301,3,3,"malformed {","2026-10-07",null,null);
        visit(501,5,4,"{\"result\":{\"crpScore\":null}}","2026-10-05",null,null);
        visit(502,5,4,"{\"result\":{\"crpScore\":-0.001}}","2026-10-06",null,null);
        visit(503,5,4,"{\"result\":{\"esrScore\":8}}","2026-10-07",null,null);
        visit(601,6,0,"{\"result\":{\"crpScore\":4.105}","2026-10-07",null,"bad");
        JsonNode data=success("{}"); JsonNode items=data.path("patients").path("items");
        for(long id:new long[]{1,7}) { JsonNode item=patientById(data,id);assertEquals(2.29,item.path("das28At").asDouble()); assertEquals(precise,item.path("scoreProvenance").path("raw").asText()); }
        JsonNode provenance=patientById(data,1).path("scoreProvenance");
        assertEquals("LEGACY_STORED",provenance.path("source").asText()); assertEquals("101",provenance.path("sourceVisitId").asText());
        assertEquals("bqpg.result.crpScore",provenance.path("sourceField").asText()); assertEquals("2026-10-07",provenance.path("observedAt").asText());
        assertEquals(json.readTree("[\"LEGACY_UNVERIFIED\"]"),provenance.path("quality"));
        assertEquals(json.readTree("[\"LEGACY_UNVERIFIED\",\"COMPONENTS_MISSING\"]"),patientById(data,3).path("scoreProvenance").path("quality"));
        assertEquals("30",patientById(data,3).path("scoreProvenance").path("sourceVisitId").asText());
        for(long id:new long[]{5,6}) {
            assertTrue(patientById(data,id).path("das28At").isNull()); assertTrue(patientById(data,id).path("activity").isNull());
            provenance=patientById(data,id).path("scoreProvenance"); assertEquals("NO_VALID_CRP",provenance.path("missingReason").asText());
            for(String field:new String[]{"source","sourceVisitId","sourceField","observedAt","raw"}) assertTrue(provenance.path(field).isNull());
        }
        assertEquals(json.readTree("[\"INVALID_JSON\"]"),patientById(data,6).path("scoreProvenance").path("quality"));
        assertEquals(3,data.path("activity").path("evalN").asInt()); assertEquals(2,data.path("activity").path("unknownN").asInt());
        String serialized=data.toString(); assertFalse(serialized.contains("synthetic-private-json")); assertEquals("synthetic-same-name",patientById(data,1).path("name").asText());assertTrue(patientById(data,1).path("studyNo").isNull());
        assertEquals(new java.util.HashSet<>(Arrays.asList("analysisId","n","studyTotal","submittedUniqueIdsN","effectiveIdsN","activity","patients","meta","stats","metricMeta","byTx","fm","lines")),fields(data));
        assertEquals(new java.util.HashSet<>(Arrays.asList("total","items","returnedCount","nextCursor","hasMore")),fields(data.path("patients")));
    }
    private java.util.Set<String> fields(JsonNode node) {
        java.util.Set<String> fields=new java.util.HashSet<>(); node.fieldNames().forEachRemaining(fields::add); return fields;
    }
    @Test void nowUsesShanghaiDateValidCandidatesAndSameDayIdThenReevaluates() throws Exception {
        clock.now=java.time.Instant.parse("2026-10-06T18:00:00Z");
        sql("UPDATE patient_follow_up_history SET follow_up_date='2026-10-07 23:59:00' WHERE id=10");
        sql("UPDATE patient_follow_up_history SET follow_up_date='2026-10-07 00:01:00' WHERE id=11");
        visit(12,1,0,"{\"result\":{\"crpScore\":4.105}}","2026-10-08",null,null);
        visit(13,1,0,"{\"result\":{\"crpScore\":99}}",null,null,null);
        visit(14,1,0,"{\"result\":{\"crpScore\":null}}","2026-10-07",null,null);
        visit(15,1,0,"{\"result\":{\"crpScore\":-1}}","2026-10-07",null,null);
        JsonNode data=success("{}"); JsonNode first=patientById(data,1);
        assertEquals(2.30,first.path("das28At").asDouble()); assertEquals("11",first.path("scoreProvenance").path("sourceVisitId").asText());
        assertEquals("2026-10-07",data.path("meta").path("asOfDate").asText());
        assertEquals(json.readTree("{\"statistics\":\"dev-stats-v04\",\"descriptive\":\"dev-descriptive-v04\",\"qc\":\"dev-missing-v04\",\"crp\":\"dev-crp-v04\",\"now\":\"dev-now-v04\",\"clinical\":\"dev-clinical-v04\",\"serology\":\"dev-ever-serology-v04\",\"treatment\":\"dev-timeline-v04\",\"visitMatcher\":\"dev-visit-match-v04\",\"drugDictionary\":\"dev-drug-v04\"}"),data.path("meta").path("policyVersions"));
        assertEquals(json.readTree("[\"studyCode\",\"at\",\"act\",\"ids\",\"sex\",\"age\",\"sero\",\"cm\",\"tx\",\"data\"]"),data.path("meta").path("supportedFilters")); assertEquals("P03_RESULT",data.path("meta").path("completion").asText());
        for(String field:new String[]{"readStartedAt","readCompletedAt","computedAt"}) assertEquals(clock.now,java.time.Instant.parse(data.path("meta").path(field).asText()));
        clock.now=java.time.Instant.parse("2026-10-09T02:00:00Z");
        data=success("{}"); first=patientById(data,1);
        assertEquals(4.11,first.path("das28At").asDouble()); assertEquals("12",first.path("scoreProvenance").path("sourceVisitId").asText());
        assertEquals("2026-10-09",data.path("meta").path("asOfDate").asText());
    }
    @Test void firstTenAreRealNumericOrderAndTotalRemainsSeventeen() throws Exception {
        for(int id=20;id<=31;id++) {
            sql("INSERT INTO patient_basic_info (id,name) VALUES ("+id+",'synthetic-extra')");
            sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,"+id+",0)");
        }
        java.util.List<String> expected=Arrays.asList("5","3","1","7","6","20","21","22","23","24");
        JsonNode data=success("{}"); assertEquals(17,data.path("n").asInt()); assertEquals(17,data.path("studyTotal").asInt());
        assertEquals(17,data.path("patients").path("total").asInt()); assertEquals(expected,ids(data));
        assertEquals(4,data.path("activity").path("evalN").asInt()); assertEquals(13,data.path("activity").path("unknownN").asInt());
        assertEquals(new java.util.HashSet<>(Arrays.asList("total","items","returnedCount","nextCursor","hasMore")),fields(data.path("patients")));
        for(int index=4;index<10;index++) assertTrue(data.path("patients").path("items").get(index).path("das28At").isNull());
        JsonNode next=success("{}");
        for(String key:Arrays.asList("total","items","returnedCount","hasMore"))assertEquals(data.path("patients").path(key),next.path("patients").path(key));
        assertNotEquals(data.path("analysisId"),next.path("analysisId"));assertNotEquals(data.path("patients").path("nextCursor"),next.path("patients").path("nextCursor"));assertEquals(data.path("activity"),next.path("activity"));
    }
}
