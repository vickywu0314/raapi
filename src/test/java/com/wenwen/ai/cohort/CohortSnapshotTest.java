package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CohortSnapshotTest extends CohortHttpFixture {
    @Test void eachNewHttpReadSeesCommittedUpdatesAndDeletions() throws Exception {
        assertEquals(2.30,patientById(success("{}"),1).path("das28At").asDouble());
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":4.105}}' WHERE id=11");
        JsonNode data=success("{}"); assertEquals(4.11,patientById(data,1).path("das28At").asDouble());
        sql("DELETE FROM patient_follow_up_history WHERE id=11");
        data=success("{}"); assertEquals(9.0,patientById(data,1).path("das28At").asDouble());
        sql("DELETE FROM patient_basic_info WHERE id=5");
        data=success("{}"); assertEquals(Arrays.asList("1","3","7","6"),ids(data)); assertEquals(4,data.path("studyTotal").asInt());
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=3");
        data=success("{}"); assertEquals(Arrays.asList("1","7","6"),ids(data)); assertEquals(3,data.path("studyTotal").asInt());
    }
    @Test void betweenSelectWriterCannotTearSnapshotAndNextReadSeesNewView() throws Exception {
        AtomicInteger commits=new AtomicInteger();
        observed.afterFirstQuery=() -> {
            assertEquals(1,observed.selects); assertEquals(1,observed.active.get());
            try(Connection writer=raw.getConnection(); Statement statement=writer.createStatement()) {
                assertFalse(observed.queryConnections.contains(System.identityHashCode(writer))); writer.setAutoCommit(false);
                statement.execute("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":8}}' WHERE id=11");
                statement.execute("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=3");
                statement.execute("DELETE FROM patient_basic_info WHERE id=5");
                statement.execute("INSERT INTO patient_basic_info (id,name) VALUES (20,'synthetic-added')");
                statement.execute("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,20,0)");
                statement.execute("INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,follow_up_date,followUpDate,bqpg,fzjc) VALUES (200,20,0,'2026-10-07',null,'{\"result\":{\"crpScore\":3}}',null)");
                writer.commit(); commits.incrementAndGet(); assertEquals(1,observed.selects);
            } catch(SQLException e) { throw new AssertionError("合成 writer 屏障失败",e); }
        };
        JsonNode data=success("{}"); assertEquals(1,commits.get());
        assertEquals(Arrays.asList("5","3","1","7","6"),ids(data)); assertEquals(5,data.path("n").asInt());
        JsonNode items=data.path("patients").path("items");
        assertEquals(2.30,patientById(data,1).path("das28At").asDouble()); assertEquals(2.71,patientById(data,3).path("das28At").asDouble()); assertEquals(4.11,patientById(data,5).path("das28At").asDouble());
        assertEquals(4,data.path("activity").path("evalN").asInt()); assertEquals(1,data.path("activity").path("unknownN").asInt());
        assertSnapshotReleased();
        observed.reset(); data=success("{}"); assertEquals(Arrays.asList("1","20","7","6"),ids(data)); assertEquals(4,data.path("studyTotal").asInt());
        assertEquals(8.0,patientById(data,1).path("das28At").asDouble()); assertEquals(3.0,patientById(data,20).path("das28At").asDouble());
        assertEquals(3,data.path("activity").path("evalN").asInt()); assertSnapshotReleased();
    }
    private void assertSnapshotReleased() { assertSnapshotReleased(5,2); }
    private void assertSnapshotReleased(int selects,int owners) {
        assertEquals(selects,observed.selects); assertEquals(owners,observed.borrowed); assertEquals(owners,observed.returned); assertEquals(0,observed.active.get());
        assertEquals(1,new HashSet<>(observed.queryConnections).size());
        assertEquals(Collections.nCopies(selects,Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);
        assertEquals(Collections.nCopies(selects,false),observed.autoCommits);
    }
    @Test void sourceConnectionIsReleasedBeforeParsingComputingAndHttpSuccess() throws Exception {
        AtomicInteger instants=new AtomicInteger();
        clock.onInstant=() -> { assertEquals(0,observed.active.get(),"Clock观察点位于源读前/事务完成后/计算完成时"); instants.incrementAndGet(); };
        assertEquals(5,success("{}").path("n").asInt()); assertEquals(3,instants.get()); assertSnapshotReleased();
        // 断言发生在 @AfterEach 的任何 fixture 清理之前。
    }
    @Test void realSecondSqlFailureReturns503AndReleasesBeforeCleanup() throws Exception {
        AtomicInteger firstRead=new AtomicInteger();
        observed.afterFirstQuery=() -> { assertEquals(1,observed.active.get()); assertEquals(1,observed.selects); firstRead.incrementAndGet(); };
        observed.failSecondSql=true;
        org.springframework.test.web.servlet.MvcResult response=request("{}");
        assertEquals(1,firstRead.get()); assertEquals(503,response.getResponse().getStatus());
        JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());
        assertFalse(envelope.path("success").asBoolean()); assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText()); assertTrue(envelope.path("data").isNull());
        assertNotNull(response.getResponse().getHeader("X-Trace-Id")); assertTrue(observed.mysqlFailures>0,"故障必须实际到达 MySQL 而非仅提前抛异常");
        assertSnapshotReleased(3,1);
        String body=response.getResponse().getContentAsString();
        for(String secret:new String[]{"SELECT","p01c_missing_source_table","jdbc:","crpScore","synthetic"}) assertFalse(body.contains(secret));
    }
    @Test void computationFailureDoesNotPublishPartialOrOldResult() throws Exception {
        assertEquals(5,success("{}").path("n").asInt()); observed.reset();
        AtomicInteger instants=new AtomicInteger();
        clock.onInstant=() -> {
            assertEquals(0,observed.active.get());
            if(instants.incrementAndGet()==3) throw new IllegalStateException("synthetic-private-program-fault");
        };
        org.springframework.test.web.servlet.MvcResult response=request("{}");
        assertEquals(503,response.getResponse().getStatus());
        JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());
        assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText()); assertFalse(envelope.path("success").asBoolean()); assertTrue(envelope.path("data").isNull());
        assertEquals(3,instants.get()); assertSnapshotReleased(5,1); assertFalse(response.getResponse().getContentAsString().contains("synthetic-private-program-fault"));
    }
}
