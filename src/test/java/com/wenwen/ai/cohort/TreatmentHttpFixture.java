package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

abstract class TreatmentHttpFixture extends CohortHttpFixture {
    @org.springframework.context.annotation.Configuration
    static class SyntheticDictionaryConfiguration {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        public SyntheticDictionary syntheticDictionary() { return new SyntheticDictionary(); }
    }
    static final class SyntheticDictionary implements com.wenwen.ai.treatment.DrugDictionary {
        Snapshot view=new com.wenwen.ai.treatment.DevelopmentDrugDictionary().snapshot(); int snapshots;
        public Snapshot snapshot() { snapshots++; return view; }
    }
    @Override void beforeRefresh() { context.register(SyntheticDictionaryConfiguration.class); }
    SyntheticDictionary dictionary() { return context.getBean(SyntheticDictionary.class); }

    void timelineFixture() throws Exception {
        sql("DELETE FROM patient_follow_up_history");
        clock.now=Instant.parse("2025-06-01T02:00:00Z");
    }
    void medication(long id,long patient,String date,String source) throws Exception {
        visit(id,patient,0,"{\"result\":{\"crpScore\":2.3}}",date,null,null);
        try(Connection c=raw.getConnection(); PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE id=?")) {
            s.setString(1,source); s.setLong(2,id); s.executeUpdate();
        }
    }
    String drug(String name,String start,String end) throws Exception {
        return json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("drugName",name,"startTime",start,"endTime",end));
    }
    String drugs(String... rows) { return "{\"xyList\":["+String.join(",",rows)+"]}"; }
    JsonNode treatment(JsonNode data,long patient) {
        for(JsonNode item:data.path("patients").path("items")) if(item.path("patientId").asText().equals(Long.toString(patient))) return item.path("treatment");
        fail("未找到患者 "+patient); return null;
    }
    void released() { assertReleased(2); }
    void sourceReleased() { assertReleased(1); }
    private void assertReleased(int owners) {
        assertEquals(5,observed.selects); assertEquals(owners,observed.borrowed); assertEquals(owners,observed.returned); assertEquals(0,observed.active.get());
    }
}
