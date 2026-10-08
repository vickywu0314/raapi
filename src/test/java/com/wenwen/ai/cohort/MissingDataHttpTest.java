package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MissingDataHttpTest extends MissingDataHttpFixture {
    @Test void realMissingFilterUsesCrpAndExcludesEsrOnly() throws Exception {
        JsonNode data=success("{\"filters\":{\"data\":\"missing\"}}");
        assertEquals(Collections.singletonList("2"),ids(data));
        assertEquals(1,data.path("n").asInt()); assertEquals(2,data.path("studyTotal").asInt());
        JsonNode qc=data.path("patients").path("items").get(0).path("qc");
        assertEquals("MISSING",qc.path("status").asText());
        assertEquals(json.readTree("[\"M_DAS28\"]"),qc.path("missingCodes"));
        assertEquals("dev-missing-v04",qc.path("ruleVersion").asText());
    }
    @Test void completeUnrestrictedAndAndFiltersKeepScopeMetadata() throws Exception {
        JsonNode complete=success("{\"filters\":{\"data\":\"complete\"}}");
        assertEquals(Collections.singletonList("1"),ids(complete));
        assertEquals("COMPLETE",complete.path("patients").path("items").get(0).path("qc").path("status").asText());
        assertEquals("dev-missing-v04",complete.path("meta").path("policyVersions").path("qc").asText());
        assertEquals("P03_RESULT",complete.path("meta").path("completion").asText());
        assertTrue(complete.path("meta").path("supportedFilters").toString().contains("data"));
        for(String filter:new String[]{"{}","{\"filters\":{\"data\":null}}","{\"filters\":{\"data\":\"\"}}"}) assertEquals(Arrays.asList("1","2"),ids(success(filter)));
        JsonNode and=success("{\"filters\":{\"data\":\"missing\",\"act\":\"target\",\"ids\":[\"1\",\"2\",\"99\"]}}");
        assertEquals(0,and.path("n").asInt()); assertEquals(2,and.path("studyTotal").asInt()); assertEquals(3,and.path("submittedUniqueIdsN").asInt()); assertEquals(2,and.path("effectiveIdsN").asInt());
    }
    @Test void invalidDataValuesAndTypesAreRejectedBeforeSql() throws Exception {
        for(String value:new String[]{"\"COMPLETE\"","\"unknown\"","\" complete\"","1","false","[]","{}"}) {
            observed.reset(); error("{\"filters\":{\"data\":"+value+"}}",400,"INVALID_FILTER");
            assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        }
    }
    @Test void aiWriterCannotMixOldScoresWithNewQcFactsAndNextReadSeesCommit() throws Exception {
        java.util.concurrent.atomic.AtomicInteger commits=committedWriter(false);
        JsonNode current=success("{\"filters\":{\"data\":\"missing\"}}");
        assertEquals(1,commits.get());assertEquals(Collections.singletonList("2"),ids(current));assertEquals(2,current.path("studyTotal").asInt());
        JsonNode old=current.path("patients").path("items").get(0);
        assertTrue(old.path("das28At").isNull());assertEquals(json.readTree("[\"M_DAS28\"]"),old.path("qc").path("missingCodes"));
        assertEquals(5,observed.selects);assertEquals(2,observed.borrowed);snapshotQueries(5);
        observed.reset();JsonNode next=success("{\"filters\":{\"data\":\"missing\"}}").path("patients").path("items").get(0);
        assertEquals(2.30,next.path("das28At").asDouble());assertEquals(json.readTree("[\"M_BASELINE_LAB\",\"M_COMORBIDITY\",\"M_MEDICATION\"]"),next.path("qc").path("missingCodes"));
        assertEquals(5,observed.selects);assertEquals(2,observed.borrowed);snapshotQueries(5);
    }
    @Test void realQcSqlFailureReturns503AndReleasesBeforeRecovery() throws Exception {
        observed.failQcSql=true; org.springframework.test.web.servlet.MvcResult result=request("{\"filters\":{\"data\":\"complete\"}}");
        assertEquals(503,result.getResponse().getStatus());JsonNode envelope=json.readTree(result.getResponse().getContentAsByteArray());assertTrue(envelope.path("data").isNull());assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText());
        assertTrue(observed.mysqlFailures>0);assertEquals("42S02",observed.lastSqlState);assertEquals(1146,observed.lastMysqlError);
        assertEquals(5,observed.selects);assertEquals(1,observed.borrowed);snapshotQueries(5);
        String body=result.getResponse().getContentAsString();for(String secret:new String[]{"SELECT","p02e_missing_qc_source","jdbc:","crpScore","synthetic"}) assertFalse(body.contains(secret));
        observed.reset();assertEquals(2,success("{}").path("n").asInt());snapshotQueries(5);
    }
}
