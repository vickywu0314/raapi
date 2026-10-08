package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClinicalSnapshotTest extends ClinicalHttpFixture {
    @Test void writerAfterFirstSelectCannotMixClinicalPatientsVisitsAndAssociations() throws Exception {
        clinicalFixture();sql("UPDATE patient_basic_info SET name='合成旧姓名',study_no='OLD-STUDY' WHERE id=1"); sql("INSERT INTO patient_comorbidity VALUES (1,1,'FM',null)");
        AtomicInteger commits=new AtomicInteger();
        observed.afterFirstQuery=() -> {
            assertEquals(1,observed.selects); assertEquals(1,observed.active.get());
            try(Connection writer=raw.getConnection(); Statement statement=writer.createStatement()) {
                assertFalse(observed.queryConnections.contains(System.identityHashCode(writer))); writer.setAutoCommit(false);
                statement.execute("UPDATE patient_basic_info SET name='合成新姓名',study_no='NEW-STUDY',gender=1,card_no='110101198610080011' WHERE id=1");
                statement.execute("DELETE FROM patient_comorbidity WHERE id=1");
                statement.execute("INSERT INTO patient_comorbidity VALUES (2,1,'AS',2026)");
                statement.execute("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":8}}',fzjc='{\"lfsyz\":35}' WHERE id=100");
                writer.commit(); commits.incrementAndGet();
            } catch(SQLException e) { throw new AssertionError("临床 writer 屏障失败",e); }
        };
        JsonNode data=success("{}"); assertEquals(1,commits.get());
        JsonNode item=data.path("patients").path("items").get(0);
        // 先观察数据，让变异因真实旧/新混合失败，随后才断言事务仪器。
        assertEquals("合成旧姓名",item.path("name").asText());assertEquals("OLD-STUDY",item.path("studyNo").asText());
        assertEquals("F",item.path("clinical").path("sex").asText()); assertEquals(40,item.path("clinical").path("age").asInt());
        assertEquals("TRUE",item.path("clinical").path("fm").asText()); assertEquals("UNKNOWN",item.path("clinical").path("as").asText());
        assertEquals("UNKNOWN",item.path("clinical").path("sero").asText()); assertEquals(3,item.path("das28At").asInt()); released();
        observed.reset(); data=success("{}"); item=data.path("patients").path("items").get(0);
        assertEquals("合成新姓名",item.path("name").asText());assertEquals("NEW-STUDY",item.path("studyNo").asText());
        assertEquals("M",item.path("clinical").path("sex").asText()); assertEquals(39,item.path("clinical").path("age").asInt());
        assertEquals("UNKNOWN",item.path("clinical").path("fm").asText()); assertEquals("TRUE",item.path("clinical").path("as").asText());
        assertEquals("TRUE",item.path("clinical").path("sero").asText()); assertEquals(8,item.path("das28At").asInt()); released();
    }
    @Test void eachAddedClinicalSqlFailureReturns503WithoutPartialFactsAndReleases() throws Exception {
        clinicalFixture();
        for(String table:Arrays.asList("SELECT p.id, p.gender","patient_comorbidity")) {
            observed.reset(); observed.failClinicalTable=table;
            org.springframework.test.web.servlet.MvcResult result=request("{}");
            assertEquals(503,result.getResponse().getStatus());
            JsonNode envelope=json.readTree(result.getResponse().getContentAsByteArray());
            assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText()); assertTrue(envelope.path("data").isNull());
            assertTrue(observed.mysqlFailures>0); assertEquals(table.startsWith("SELECT")?1:4,observed.selects);
            assertEquals(1,observed.borrowed); assertEquals(1,observed.returned); assertEquals(0,observed.active.get());
            assertEquals(1,new HashSet<>(observed.queryConnections).size());
            assertEquals(Collections.nCopies(observed.selects,Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);
            assertEquals(Collections.nCopies(observed.selects,false),observed.autoCommits);
        }
    }
    void released() {
        assertEquals(5,observed.selects); assertEquals(2,observed.borrowed); assertEquals(2,observed.returned); assertEquals(0,observed.active.get());
        assertEquals(1,new HashSet<>(observed.queryConnections).size());
        assertEquals(Collections.nCopies(5,Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);
        assertEquals(Collections.nCopies(5,false),observed.autoCommits);
    }
}
