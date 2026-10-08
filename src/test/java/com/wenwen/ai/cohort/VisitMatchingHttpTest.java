package com.wenwen.ai.cohort;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class VisitMatchingHttpTest extends VisitMatchingHttpFixture {
    @Test void sixMonthHttpSelectsLeapDayRatherThanCurrentScore() throws Exception {
        matchingFixture();
        JsonNode data=success("{\"filters\":{\"at\":\"6m\",\"act\":\"target\",\"ids\":[\"1\",\"2\"]}}");
        assertEquals(Arrays.asList("1"),ids(data)); assertEquals(2,data.path("studyTotal").asInt());
        assertEquals(2,data.path("effectiveIdsN").asInt()); assertEquals(2.19,item(data,1).path("das28At").asDouble());
        released();
    }
    @Test void sixMonthPublishesBaselineCurrentAndSignedPairedDelta() throws Exception {
        matchingFixture(); JsonNode data=success("{\"filters\":{\"at\":\"6m\"}}"); JsonNode row=item(data,1);
        assertEquals(3.46,row.path("das28Base").asDouble()); assertEquals(3.90,row.path("das28Current").asDouble());
        assertEquals(1.27,row.path("deltaDas28").asDouble());
        assertEquals("100",row.path("selection").path("baselineVisitId").asText()); assertEquals("200",row.path("selection").path("evalVisitId").asText());
        assertEquals("201",row.path("selection").path("nowVisitId").asText()); assertEquals("2024-02-29",row.path("selection").path("target6mDate").asText());
        assertEquals("6m",row.path("selection").path("at").asText()); assertTrue(row.path("selection").path("eligible6m").asBoolean());
        assertEquals(1.25,row.path("evaluation").path("haq").path("value").asDouble());
        assertEquals("LEGACY_STORED",row.path("baselineProvenance").path("source").asText());
        assertEquals("6m",data.path("meta").path("at").asText()); assertEquals("P03_RESULT",data.path("meta").path("completion").asText());
        assertEquals("dev-visit-match-v04",data.path("meta").path("policyVersions").path("visitMatcher").asText());
        assertEquals(1,data.path("activity").path("baseN").asInt()); assertEquals(0,data.path("activity").path("baseUnknownN").asInt());
        assertEquals(1,data.path("activity").path("current").get(0).path("count").asInt());
        assertEquals(1,data.path("activity").path("base").get(2).path("count").asInt()); released();
    }
    @Test void currentLaboratoryCrpCanComeFromNewerScorelessVisit() throws Exception {
        matchingFixture(); JsonNode row=item(success("{\"filters\":{\"at\":\"6m\"}}"),1);
        assertEquals(4,row.path("crpAt").path("value").asInt()); assertEquals("200",row.path("crpAt").path("sourceVisitId").asText());
        assertEquals(12,row.path("crpCurrent").path("value").asInt()); assertEquals("202",row.path("crpCurrent").path("sourceVisitId").asText());
        assertEquals("2024-03-02",row.path("crpCurrent").path("observedAt").asText()); assertEquals("mg/L",row.path("crpCurrent").path("unit").asText());
        assertEquals("fzjc.cfydb",row.path("crpCurrent").path("sourceField").asText()); assertEquals(3.90,row.path("das28Current").asDouble()); released();
    }
    @Test void actualLegacyScoreSqlAndBothPublicConsumersAgreeWithAiAcrossShanghaiMidnight() throws Exception {
        matchingFixture(); sql("DELETE FROM patient_follow_up_history");
        visit(20,1,0,"{\"result\":{\"crpScore\":2}}","2025-01-01 23:00:00",null,null);
        visit(21,1,0,"{\"result\":{\"crpScore\":3}}","2025-01-01 01:00:00",null,null);
        visit(22,1,0,"{\"result\":{\"crpScore\":-0.001}}","2025-01-01",null,null);
        visit(99,1,0,"{\"result\":{\"crpScore\":9}}","2025-01-02",null,null);
        visit(100,1,0,"{\"result\":{\"crpScore\":8}}",null,null,null);
        visit(101,1,6,"{\"result\":{\"crpScore\":7}}","2025-01-01",null,null);
        for(String[] oracle:new String[][]{{"2025-01-01T15:59:59Z","3.00","21","2025-01-01"},{"2025-01-01T16:00:00Z","9.00","99","2025-01-02"}}) {
            clock.now=java.time.Instant.parse(oracle[0]); observed.reset(); JsonNode data=success("{}"); JsonNode row=item(data,1);
            assertEquals(0,new java.math.BigDecimal(oracle[1]).compareTo(row.path("das28At").decimalValue()));
            assertEquals(oracle[2],row.path("selection").path("evalVisitId").asText()); assertEquals(oracle[3],data.path("meta").path("asOfDate").asText()); released();
            com.wenwen.service.impl.PatientServiceImpl legacy=legacyService();
            assertEquals(new java.math.BigDecimal(oracle[1]),legacy.listPatients(101L,null,null,null,1,20).getItems().get(0).getLatestDas28());
            assertEquals(new java.math.BigDecimal(oracle[1]),legacy.getPatientDetail(101L,1L).getLatestDas28());
            assertEquals(0,observed.active.get());
        }
    }
    @Test void selectedVisitUpdateAndDeletionAreVisibleOnNextRealRequestOnly() throws Exception {
        matchingFixture();
        observed.afterFirstQuery=() -> { try(java.sql.Connection c=raw.getConnection();java.sql.Statement s=c.createStatement()) {
            c.setAutoCommit(false); s.executeUpdate("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":2.29}}' WHERE id=200"); c.commit();
        } catch(Exception e) { throw new AssertionError(e); } };
        JsonNode first=item(success("{\"filters\":{\"at\":\"6m\"}}"),1); assertEquals(2.19,first.path("das28At").asDouble()); released();
        assertEquals(1,new java.util.HashSet<>(observed.queryConnections).size());
        assertEquals(java.util.Collections.nCopies(5,false),observed.autoCommits);
        assertEquals(java.util.Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);
        observed.reset(); JsonNode next=item(success("{\"filters\":{\"at\":\"6m\"}}"),1); assertEquals(2.29,next.path("das28At").asDouble()); released();
        sql("DELETE FROM patient_follow_up_history WHERE id=200"); observed.reset();
        JsonNode changed=item(success("{\"filters\":{\"at\":\"6m\"}}"),1); assertEquals("201",changed.path("selection").path("evalVisitId").asText()); assertEquals(3.90,changed.path("das28At").asDouble()); released();
        sql("DELETE FROM patient_follow_up_history WHERE id=201"); observed.reset();
        JsonNode none=success("{\"filters\":{\"at\":\"6m\"}}"); assertEquals(0,none.path("n").asInt()); assertEquals(2,none.path("studyTotal").asInt()); released();
    }
    @Test void sixMonthNormalCalculationAndRealSqlFailureReleaseBeforeResponse() throws Exception {
        matchingFixture(); java.util.concurrent.atomic.AtomicInteger ticks=new java.util.concurrent.atomic.AtomicInteger();
        clock.onInstant=() -> { if(ticks.incrementAndGet()>1) assertEquals(0,observed.active.get()); };
        assertEquals(1,success("{\"filters\":{\"at\":\"6m\"}}").path("n").asInt()); released(); clock.onInstant=null;
        observed.reset(); observed.failSecondSql=true;
        error("{\"filters\":{\"at\":\"6m\"}}",503,"SERVICE_UNAVAILABLE"); assertTrue(observed.mysqlFailures>0);
        assertEquals(1,observed.borrowed); assertEquals(1,observed.returned); assertEquals(0,observed.active.get());
        observed.reset(); dictionary().view=new com.wenwen.ai.treatment.DrugDictionary.Snapshot("synthetic-failure",java.util.Collections.emptyMap()) {
            @Override public com.wenwen.ai.treatment.DrugDictionary.Drug identify(String name) { sourceReleased(); throw new IllegalStateException("synthetic calculation failure"); }
        };
        error("{\"filters\":{\"at\":\"6m\"}}",503,"SERVICE_UNAVAILABLE"); sourceReleased();
        dictionary().view=new com.wenwen.ai.treatment.DevelopmentDrugDictionary().snapshot(); observed.reset();
        assertEquals(2.19,item(success("{\"filters\":{\"at\":\"6m\"}}"),1).path("das28At").asDouble()); released();
    }
    @Test void sixMonthClockBeforeTargetStillUsesObservedEarlyWindowAndRecomputesAtMidnight() throws Exception {
        matchingFixture(); visit(199,1,0,"{\"result\":{\"crpScore\":2.50}}","2024-02-27",null,null);
        clock.now=java.time.Instant.parse("2024-02-28T15:59:59Z");
        JsonNode early=item(success("{\"filters\":{\"at\":\"6m\"}}"),1);
        assertEquals("199",early.path("selection").path("evalVisitId").asText()); assertTrue(early.path("selection").path("eligible6m").asBoolean());
        assertEquals("2024-02-29",early.path("selection").path("target6mDate").asText()); released();
        clock.now=java.time.Instant.parse("2024-02-28T16:00:00Z"); observed.reset();
        JsonNode next=item(success("{\"filters\":{\"at\":\"6m\"}}"),1);
        assertEquals("200",next.path("selection").path("evalVisitId").asText()); assertEquals(2.19,next.path("das28At").asDouble()); released();
    }
    @Test void missingBaselineDoesNotExcludeSixMonthAndMissingFieldsStayExplicitNull() throws Exception {
        matchingFixture(); sql("DELETE FROM patient_follow_up_history WHERE id=100");
        JsonNode data=success("{\"filters\":{\"at\":\"6m\"}}"); JsonNode row=item(data,1);
        assertEquals(1,data.path("n").asInt()); assertTrue(row.path("das28Base").isNull()); assertTrue(row.path("deltaDas28").isNull());
        assertTrue(row.path("selection").path("baselineVisitId").isNull()); assertTrue(row.path("selection").path("baselineDate").isNull());
        assertEquals("MISSING_BASELINE",row.path("selection").path("deltaMissingReason").asText());
        assertEquals(0,data.path("activity").path("baseN").asInt()); assertEquals(1,data.path("activity").path("baseUnknownN").asInt()); released();
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1"); observed.reset();
        JsonNode now=success("{}"); row=item(now,1); assertEquals(2,now.path("n").asInt());
        for(String field:Arrays.asList("das28Base","das28Current","das28At","deltaDas28")) assertTrue(row.path(field).isNull(),field);
        assertTrue(row.path("crpAt").path("value").isNull()); assertTrue(row.path("crpCurrent").path("value").isNull());
        assertEquals("mg/L",row.path("crpCurrent").path("unit").asText()); assertEquals("NO_VALID_CRP_LAB",row.path("crpCurrent").path("missingReason").asText());
        assertEquals("NO_VALID_CRP",row.path("selection").path("missingReason").asText()); assertEquals(1,now.path("activity").path("unknownN").asInt()); released();
    }
    @Test void nowBeforeReliableOrEstimatedSchemeRemainsDescriptiveButNeverPairs() throws Exception {
        for(String start:new String[]{"2024-01-01",null}) {
            matchingFixture(); sql("DELETE FROM patient_follow_up_history");
            visit(100,1,0,"{\"result\":{\"crpScore\":3.46}}","2023-12-31",null,null);
            medication(101,1,"2024-01-01",drugs(drug("阿达木单抗",start,null))); sql("UPDATE patient_follow_up_history SET bqpg='{}' WHERE id=101"); observed.reset();
            JsonNode row=item(success("{}"),1); assertEquals(3.46,row.path("das28At").asDouble()); assertEquals(3.46,row.path("das28Current").asDouble());
            assertTrue(row.path("selection").path("quality").toString().contains("EVAL_BEFORE_SCHEME")); assertTrue(row.path("deltaDas28").isNull());
            assertEquals(start==null?"NO_RELIABLE_START":"EVAL_BEFORE_SCHEME",row.path("selection").path("deltaMissingReason").asText()); released();
        }
    }
    @Test void reliableConflictHasSixMonthEligibilityWithoutTxButRemainsExcludedFromBio() throws Exception {
        matchingFixture(); replaceMedication(101,drugs(drug("阿达木单抗","2023-08-31",null),drug("托法替布","2023-08-31",null)));
        JsonNode data=success("{\"filters\":{\"at\":\"6m\"}}"); assertEquals(Arrays.asList("1"),ids(data));
        assertEquals("CONFLICT",item(data,1).path("treatment").path("state").asText());
        assertEquals(1,data.path("activity").path("unknownTxN").asInt()); assertEquals(1.27,item(data,1).path("deltaDas28").asDouble()); released();
        for(String tx:Arrays.asList("bio","TNFi","JAKi")) { observed.reset();
            JsonNode excluded=success("{\"filters\":{\"at\":\"6m\",\"tx\":\""+tx+"\"}}"); assertEquals(0,excluded.path("n").asInt()); assertEquals(2,excluded.path("studyTotal").asInt()); released(); }
    }
    @Test void endedOrUnknownCurrentSchemeCannotReuseHistoricalReliableSixMonth() throws Exception {
        for(String source:new String[]{drugs(drug("阿达木单抗","2023-08-31","2024-02-28")),drugs(drug("阿达木单抗","2023-08-31",null),drug("unmapped",null,null))}) {
            matchingFixture(); replaceMedication(101,source); observed.reset();
            JsonNode current=item(success("{}"),1); assertTrue(current.path("das28Base").isNull()); assertFalse(current.path("selection").path("eligible6m").asBoolean());
            assertTrue(current.path("selection").path("target6mDate").isNull()); assertEquals(3.90,current.path("das28At").asDouble()); released();
            observed.reset(); assertEquals(0,success("{\"filters\":{\"at\":\"6m\"}}").path("n").asInt()); released();
        }
    }
    @Test void endDerivedEstimatedCurrentStartNeverUpgradesFromOriginalDrugFacts() throws Exception {
        matchingFixture(); sql("DELETE FROM patient_follow_up_history");
        medication(100,1,"2023-01-01",drugs(drug("甲氨蝶呤","2023-01-01",null)));
        medication(101,1,"2023-03-01",drugs(drug("甲氨蝶呤","2023-01-01",null),drug("来氟米特","2023-03-01","2023-04-30")));
        medication(102,1,"2023-05-01",drugs(drug("甲氨蝶呤","2023-01-01",null)));
        visit(200,1,0,"{\"result\":{\"crpScore\":2.19}}","2023-07-01",null,null);
        JsonNode row=item(success("{}"),1); assertEquals("ESTIMATED_EXPLICIT_END",row.path("treatment").path("startConfidence").asText());
        assertTrue(row.path("treatment").path("startDate").isNull()); assertTrue(row.path("das28Base").isNull());
        assertFalse(row.path("selection").path("eligible6m").asBoolean()); assertEquals("NO_RELIABLE_START",row.path("baselineProvenance").path("missingReason").asText()); released();
        observed.reset(); assertEquals(0,success("{\"filters\":{\"at\":\"6m\"}}").path("n").asInt()); released();
    }
    @Test void currentCrpIgnoresInvalidUndatedAndFutureLabsUsesIdAndNeverFillsEvaluation() throws Exception {
        matchingFixture();
        visit(203,1,0,"{}","2024-03-02",null,"{\"cfydb\":14}");
        visit(204,1,0,"{}","2024-03-02",null,"{\"cfydb\":-0.001}");
        visit(205,1,0,"{}",null,null,"{\"cfydb\":88}");
        visit(206,1,0,"{}","2024-03-03",null,"{\"cfydb\":99}");
        visit(207,2,0,"{}","2024-03-02",null,"{\"cfydb\":77}");
        sql("UPDATE patient_follow_up_history SET fzjc='{}' WHERE id=200");
        JsonNode row=item(success("{\"filters\":{\"at\":\"6m\"}}"),1);
        assertEquals("203",row.path("crpCurrent").path("sourceVisitId").asText()); assertEquals(14,row.path("crpCurrent").path("value").asInt());
        assertTrue(row.path("crpAt").path("value").isNull()); assertTrue(row.path("evaluation").path("crp").path("value").isNull());
        assertEquals("200",row.path("crpAt").path("sourceVisitId").asText()); assertEquals("MISSING_VALUE",row.path("crpAt").path("missingReason").asText());
        assertEquals(1.25,row.path("evaluation").path("haq").path("value").asDouble()); assertTrue(row.path("evaluation").path("tjc").path("value").isNull()); released();
    }
    @Test void switchingCurrentSchemeCannotReusePreviousSixMonthAndAllowsNegativeNewSchemeDelta() throws Exception {
        matchingFixture(); medication(203,1,"2024-03-01",drugs(drug("托法替布","2024-02-29",null)));
        sql("UPDATE patient_follow_up_history SET bqpg='{}' WHERE id=203");
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3.46}}' WHERE id=201");
        JsonNode now=item(success("{}"),1); assertEquals("2024-02-29",now.path("treatment").path("startDate").asText());
        assertEquals("JAKi",now.path("treatment").path("category").asText()); assertEquals("2024-08-29",now.path("selection").path("target6mDate").asText());
        assertEquals(2.19,now.path("das28Base").asDouble()); assertEquals(-1.27,now.path("deltaDas28").asDouble());
        assertFalse(now.path("selection").path("eligible6m").asBoolean()); released();
        observed.reset(); JsonNode six=success("{\"filters\":{\"at\":\"6m\"}}"); assertEquals(0,six.path("n").asInt()); assertEquals(2,six.path("studyTotal").asInt()); released();
    }
    @Test void atDefaultsAndExactEnumsAreValidatedBeforeSourceSql() throws Exception {
        matchingFixture();
        for(String body:Arrays.asList("{}","{\"filters\":{\"at\":null}}","{\"filters\":{\"at\":\"\"}}","{\"filters\":{\"at\":\"now\"}}")) {
            observed.reset(); JsonNode data=success(body); assertEquals("now",data.path("meta").path("at").asText());
            assertEquals(2,data.path("n").asInt()); assertEquals(3.90,item(data,1).path("das28At").asDouble()); released();
        }
        for(String value:Arrays.asList("\"Now\"","\"6M\"","\"6m \"", "6", "false", "[]", "{}")) {
            observed.reset(); error("{\"filters\":{\"at\":"+value+"}}",400,"INVALID_FILTER"); assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        }
    }
}
