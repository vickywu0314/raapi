package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatisticalCohortHttpTest extends StatisticalHttpFixture {
    @Test void actualTreatmentTableUsesTwoSidedFisher() throws Exception {
        treatmentTable(new int[][]{{1,7},{6,2}});JsonNode data=success("{}");JsonNode tx=data.path("byTx"), test=tx.path("test");
        assertTrue(test.isObject(),"真实byTx必须消费统计检验");assertEquals("OK",test.path("status").asText());assertEquals("FISHER_EXACT",test.path("method").asText());
        p(test,.040559440559440565);assertEquals("0.041",test.path("displayP").asText());assertTrue(test.path("significant").asBoolean());
        assertEquals(8,test.path("nA").asInt());assertEquals(8,test.path("nB").asInt());assertEquals("EVALUABLE_KNOWN_TREATMENT",test.path("testDenominator").asText());
        assertEquals("dev-stats-v04",test.path("policyVersion").asText());assertTrue(test.path("reason").isNull());assertEquals(0,test.path("warnings").size());
        assertEquals(4,test.path("expectedBelow5Cells").asInt());assertEquals(4,test.path("expectedCellCount").asInt());
        assertEquals("csDMARD",test.path("includedGroups").get(0).asText());assertEquals("TNFi",test.path("includedGroups").get(1).asText());assertEquals(0,test.path("excludedGroups").size());
        for(int i=0;i<2;i++){assertEquals(8,tx.path("groups").get(i).path("n").asInt());assertEquals(8,tx.path("groups").get(i).path("evalN").asInt());assertEquals(i==0?1:6,tx.path("groups").get(i).path("targetN").asInt());assertEquals(8,test.path("groupNs").get(i).path("n").asInt());}
        released();
    }
    @Test void adequateExpectedCountsUsePearsonWithoutYates() throws Exception {
        treatmentTable(new int[][]{{14,6},{6,14}});JsonNode test=success("{}").path("byTx").path("test");
        assertEquals("CHI_SQUARE",test.path("method").asText());p(test,.01141203638600166);assertEquals("0.011",test.path("displayP").asText());assertEquals(0,test.path("expectedBelow5Cells").asInt());assertEquals(4,test.path("expectedCellCount").asInt());assertEquals(20,test.path("nA").asInt());released();
    }
    @Test void multiGroupPIsOverallAndWarnsForSparseExpectedCells() throws Exception {
        treatmentTable(new int[][]{{10,0},{9,1},{8,2}});JsonNode test=success("{}").path("byTx").path("test");
        assertEquals("CHI_SQUARE",test.path("method").asText());p(test,.3291929878079054);assertEquals(3,test.path("expectedBelow5Cells").asInt());assertEquals(6,test.path("expectedCellCount").asInt());
        assertEquals("[\"SPARSE_EXPECTED_COUNTS\"]",test.path("warnings").toString());assertTrue(test.path("nA").isNull());assertTrue(test.path("nB").isNull());assertEquals(3,test.path("groupNs").size());for(JsonNode g:test.path("groupNs"))assertEquals(10,g.path("n").asInt());released();
    }
    @Test void smallGroupsStayDescribedButUseEvaluationNForEligibility() throws Exception {
        treatmentTable(new int[][]{{1,7},{6,2},{4,0}});JsonNode tx=success("{}").path("byTx"),test=tx.path("test");p(test,.040559440559440565);
        assertEquals(3,tx.path("groups").size());assertEquals(4,tx.path("groups").get(2).path("n").asInt());assertEquals("JAKi",test.path("excludedGroups").get(0).path("tx").asText());assertEquals(4,test.path("excludedGroups").get(0).path("evalN").asInt());assertEquals("VALID_N_LT_5",test.path("excludedGroups").get(0).path("reason").asText());released();
        sql("UPDATE patient_follow_up_history SET bqpg=NULL WHERE patient_basic_info_id BETWEEN 9 AND 12");observed.reset();tx=success("{}").path("byTx");test=tx.path("test");
        assertEquals(8,tx.path("groups").get(1).path("n").asInt());assertEquals(4,tx.path("groups").get(1).path("evalN").asInt());assertEquals("INSUFFICIENT_SAMPLE",test.path("status").asText());assertEquals("ELIGIBLE_GROUPS_LT_2",test.path("reason").asText());assertTrue(test.path("pValue").isNull());assertTrue(test.path("significant").isNull());assertTrue(test.path("expectedCellCount").isNull());assertEquals(1,test.path("includedGroups").size());released();
        observed.reset();test=success("{\"filters\":{\"tx\":\"csDMARD\"}}").path("byTx").path("test");assertEquals("INSUFFICIENT_SAMPLE",test.path("status").asText());released();
    }
    @Test void allTargetIsDegenerateInsteadOfSuccessfulP() throws Exception {
        treatmentTable(new int[][]{{8,0},{8,0}});JsonNode test=success("{}").path("byTx").path("test");
        assertEquals("DEGENERATE",test.path("status").asText());assertEquals("ZERO_MARGIN",test.path("reason").asText());assertTrue(test.path("method").isNull());assertTrue(test.path("pValue").isNull());assertTrue(test.path("significant").isNull());assertEquals("—",test.path("displayP").asText());assertTrue(test.path("expectedCellCount").isNull());assertTrue(test.path("expectedBelow5Cells").isNull());assertEquals(0,test.path("warnings").size());released();
    }
    @Test void expectedExactlyFiveStillUsesPearson() throws Exception {
        treatmentTable(new int[][]{{5,5},{5,5}});JsonNode test=success("{}").path("byTx").path("test");assertEquals("CHI_SQUARE",test.path("method").asText());p(test,1);assertEquals(0,test.path("expectedBelow5Cells").asInt());assertFalse(test.path("significant").asBoolean());assertEquals("1.000",test.path("displayP").asText());released();
    }
    @Test void emptyIdsAndEmptyUniverseAreInsufficientAndDoNotTestStudy() throws Exception {
        treatmentTable(new int[][]{{1,7},{6,2}});JsonNode d=success("{\"filters\":{\"ids\":[]}}");JsonNode test=d.path("byTx").path("test");
        assertEquals(0,d.path("n").asInt());assertEquals(16,d.path("studyTotal").asInt());assertEquals("INSUFFICIENT_SAMPLE",test.path("status").asText());assertEquals("ELIGIBLE_GROUPS_LT_2",test.path("reason").asText());assertTrue(test.path("pValue").isNull());assertEquals(0,test.path("includedGroups").size());assertEquals(0,test.path("excludedGroups").size());assertFalse(d.path("byTx").path("studyTargetMeta").has("test"));released();
        context.getBean(FakePrincipalProvider.class).doctor=999;observed.reset();d=success("{}");assertEquals(0,d.path("studyTotal").asInt());assertEquals("INSUFFICIENT_SAMPLE",d.path("byTx").path("test").path("status").asText());released();
    }
    @Test void actualHttpDisclosesStatisticsAndKeepsRealFmInsufficient() throws Exception {
        treatmentTable(new int[][]{{1,7},{6,2}});sql("INSERT INTO patient_comorbidity VALUES (1,1,'FM',null),(2,2,'FM',null),(3,3,'FM',null)");observed.reset();JsonNode d=success("{}");
        assertEquals("P03_RESULT",d.path("meta").path("completion").asText());assertEquals("dev-stats-v04",d.path("meta").path("policyVersions").path("statistics").asText());assertEquals("dev-descriptive-v04",d.path("meta").path("policyVersions").path("descriptive").asText());
        assertEquals("探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系",d.path("meta").path("statisticalDisclosure").asText());assertEquals("组间基线不同，差异不代表疗效差异",d.path("byTx").path("comparisonNotice").asText());
        assertEquals(3,d.path("fm").path("nFM").asInt());assertEquals(0,d.path("fm").path("nOther").asInt());assertEquals(13,d.path("fm").path("unknownN").asInt());assertEquals("INSUFFICIENT_SAMPLE",d.path("fm").path("status").asText());assertEquals(0,d.path("fm").path("rows").size());
        assertEquals(13,d.size());assertTrue(com.wenwen.ai.result.AnalysisCursor.canonicalId(d.path("analysisId").asText()));assertFalse(d.has("cursor"));assertEquals(10,d.path("patients").path("items").size());assertTrue(d.path("patients").path("items").get(0).path("evaluation").path("pain").path("value").isNull());released();
    }
    @Test void unknownAndNoneNeverEnterTheKnownTreatmentTest() throws Exception {
        treatmentTable(new int[][]{{1,7},{6,2}});
        for(int id=17;id<=18;id++){sql("INSERT INTO patient_basic_info(id,name) VALUES ("+id+",'P03b未知治疗')");sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES (101,"+id+",0,0)");rawVisit(id*100L+1,id,"2026-10-06","2","{\"cfydb\":6}",id==17?null:drugs(drug("甲氨蝶呤","2026-04-06","2026-09-01")));}
        observed.reset();JsonNode d=success("{}"),tx=d.path("byTx"),test=tx.path("test");p(test,.040559440559440565);assertEquals(18,d.path("n").asInt());assertEquals(1,tx.path("unknownTxN").asInt());assertEquals(1,tx.path("noCurrentTxN").asInt());assertEquals(2,tx.path("groups").size());assertEquals(2,test.path("includedGroups").size());assertEquals(0,test.path("excludedGroups").size());assertEquals(8,test.path("nA").asInt());assertEquals(8,test.path("nB").asInt());assertEquals(18,tx.path("studyTargetMeta").path("denominator").asInt());released();
    }
    static void p(JsonNode test,double expected) {
        assertTrue(test.path("pValue").isNumber());double actual=test.path("pValue").asDouble();assertEquals(expected,actual,1e-12);if(expected!=0)assertEquals(1,actual/expected,1e-10);
    }
}
