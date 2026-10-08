package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CohortScopeTest extends CohortHttpFixture {
    @Test void activityFiltersConsumeCanonicalScoresAndKeepStudyTotal() throws Exception {
        String[] filters={"target","mod-high","remission","low","moderate","high"};
        String[][] expected={{"1","7"},{"5","3"},{"7"},{"1"},{"3"},{"5"}};
        for(int i=0;i<filters.length;i++) {
            JsonNode data = success("{\"filters\":{\"act\":\""+filters[i]+"\"}}");
            assertEquals(Arrays.asList(expected[i]),ids(data));
            assertEquals(expected[i].length,data.path("n").asInt()); assertEquals(5,data.path("studyTotal").asInt());
            assertEquals(expected[i].length,data.path("activity").path("evalN").asInt()); assertEquals(0,data.path("activity").path("unknownN").asInt());
        }
    }
    @Test void idsIntersectScopeBeforeActivityAndEmptyNeverWidens() throws Exception {
        String submitted = "[\"1\",\"3\",\"4\",\"999\",\"1\"]";
        JsonNode data = success("{\"filters\":{\"ids\":"+submitted+"}}");
        assertEquals(Arrays.asList("3","1"),ids(data)); assertEquals(4,data.path("submittedUniqueIdsN").asInt()); assertEquals(2,data.path("effectiveIdsN").asInt());
        data = success("{\"filters\":{\"ids\":"+submitted+",\"act\":\"target\"}}");
        assertEquals(Collections.singletonList("1"),ids(data)); assertEquals(2,data.path("effectiveIdsN").asInt()); assertEquals(5,data.path("studyTotal").asInt());
        for(String list : new String[]{"[]","[\"4\",\"999\"]"}) {
            data=success("{\"filters\":{\"ids\":"+list+"}}");
            assertEquals(0,data.path("n").asInt()); assertEquals(5,data.path("studyTotal").asInt()); assertEquals(0,data.path("effectiveIdsN").asInt());
            assertEquals(0,data.path("patients").path("items").size()); assertEquals(0,data.path("patients").path("total").asInt());
            assertEquals(0,data.path("activity").path("evalN").asInt()); assertEquals(0,data.path("activity").path("unknownN").asInt());
            assertEquals(4,data.path("activity").path("current").size());
            data.path("activity").path("current").forEach(x -> assertEquals(0,x.path("count").asInt()));
        }
        for(String body : new String[]{"{}","{\"filters\":{\"ids\":null}}"}) {
            data=success(body); assertEquals(5,data.path("n").asInt()); assertTrue(data.path("submittedUniqueIdsN").isNull()); assertTrue(data.path("effectiveIdsN").isNull());
        }
    }
    @Test void sameRelationDoctorAndRaTypesDefineScope() throws Exception {
        int id=90;
        for(int type:new int[]{5,6,9,999}) {
            sql("INSERT INTO patient_basic_info (id,name) VALUES ("+id+",'synthetic-excluded')");
            sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,"+id+","+type+")");
            visit(1000+id,1,type,"{\"result\":{\"crpScore\":99}}","2026-10-07",null,null); id++;
        }
        visit(800,8,0,"{\"result\":{\"crpScore\":99}}","2026-10-07",null,null);
        JsonNode data=success("{}"); assertEquals(Arrays.asList("5","3","1","7","6"),ids(data));
        assertEquals(5,data.path("studyTotal").asInt()); assertEquals(2.30,patientById(data,1).path("das28At").asDouble());
        context.getBean(FakePrincipalProvider.class).doctor=202;
        data=success("{}"); assertEquals(Arrays.asList("2","4"),ids(data)); assertEquals(2,data.path("studyTotal").asInt());
        assertEquals(0,data.path("activity").path("evalN").asInt()); assertEquals(2,data.path("activity").path("unknownN").asInt());
        data=success("{\"filters\":{\"ids\":[\"1\",\"2\",\"9223372036854775807\"]}}");
        assertEquals(Collections.singletonList("2"),ids(data)); assertEquals(3,data.path("submittedUniqueIdsN").asInt()); assertEquals(1,data.path("effectiveIdsN").asInt());
    }
    @Test void connectionGuardRejectsMissingRemoteAndOtherSchemas() {
        String url="jdbc:mysql://127.0.0.1:43306/ra_synthetic_test";
        assertDoesNotThrow(() -> guard(url,"synthetic","synthetic"));
        assertDoesNotThrow(() -> guard("jdbc:mysql://localhost/ra_synthetic_test","synthetic","synthetic"));
        assertThrows(IllegalStateException.class,() -> guard(null,"synthetic","synthetic"));
        assertThrows(IllegalStateException.class,() -> guard(url,null,"synthetic"));
        assertThrows(IllegalStateException.class,() -> guard(url,"synthetic",null));
        assertThrows(IllegalStateException.class,() -> guard("jdbc:mysql://192.0.2.1/ra_synthetic_test","synthetic","synthetic"));
        assertThrows(IllegalStateException.class,() -> guard("jdbc:mysql://127.0.0.1/other_schema","synthetic","synthetic"));
        assertThrows(IllegalStateException.class,() -> guard("jdbc:mysql://user@localhost/ra_synthetic_test","synthetic","synthetic"));
    }
}
