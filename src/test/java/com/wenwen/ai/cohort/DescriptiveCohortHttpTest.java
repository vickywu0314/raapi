package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DescriptiveCohortHttpTest extends DescriptiveHttpFixture {
    @Test void targetUsesFiveActualEvaluationsAndOneUnknown() throws Exception {
        sixPatients(); JsonNode d=success("{}");
        assertEquals(6,d.path("n").asInt()); assertEquals(5,d.path("activity").path("evalN").asInt());
        assertTrue(d.path("stats").path("targetRate").isNumber(),"targetRate必须是实际HTTP数值");
        assertEquals(0,new BigDecimal(".4").compareTo(d.path("stats").path("targetRate").decimalValue()));
        JsonNode m=d.path("metricMeta").path("targetRate");
        assertEquals(2,m.path("numerator").asInt());assertEquals(5,m.path("denominator").asInt());assertEquals(1,m.path("unknownN").asInt());
        released();
    }
    @Test void filteredCohortKeepsUnfilteredStudyTarget() throws Exception {
        sixPatients();JsonNode d=success("{\"filters\":{\"sex\":\"F\"}}");
        assertEquals(java.util.Arrays.asList("3","1","6"),ids(d));
        assertEquals(0,new BigDecimal(".5").compareTo(d.path("stats").path("targetRate").decimalValue()));
        assertTrue(d.path("byTx").path("studyTargetRate").isNumber(),"研究基准必须存在");
        assertEquals(0,new BigDecimal(".4").compareTo(d.path("byTx").path("studyTargetRate").decimalValue()));
        JsonNode m=d.path("byTx").path("studyTargetMeta");assertEquals(2,m.path("numerator").asInt());assertEquals(5,m.path("denominator").asInt());assertEquals(1,m.path("unknownN").asInt());
        released();
    }
    @Test void mediansUseOnlyKnownAgesAndDurations() throws Exception {
        sixPatients();JsonNode u=success("{}");
        number(u.path("stats"),"ageMedian","50");number(u.path("stats"),"durationMedian","3");
        median(u,"ageMedian",5,1,"50");median(u,"durationMedian",5,1,"3");
        observed.reset();JsonNode c=success("{\"filters\":{\"sex\":\"F\"}}");
        number(c.path("stats"),"ageMedian","55");number(c.path("stats"),"durationMedian","3");
        median(c,"ageMedian",2,1,"55");median(c,"durationMedian",3,0,"3");released();
    }
    void number(JsonNode node,String key,String expected) {
        assertTrue(node.path(key).isNumber(),key+"必须有数值");assertEquals(0,new BigDecimal(expected).compareTo(node.path(key).decimalValue()),key);
    }
    void median(JsonNode d,String key,int valid,int unknown,String display) {
        JsonNode m=d.path("metricMeta").path(key);assertEquals(valid,m.path("validN").asInt());assertEquals(unknown,m.path("unknownN").asInt());assertEquals("OK",m.path("status").asText());assertEquals(display,m.path("displayText").asText());
    }
    @Test void shareFemaleAndSerologyRetainCohortDenominators() throws Exception {
        sixPatients();JsonNode u=success("{}");number(u.path("stats"),"cohortRate","1");number(u.path("stats"),"femaleRate",".5");number(u.path("stats"),"seroRate",".3333333333333333");
        ratio(u,"cohortRate",6,6,0,"100%");ratio(u,"femaleRate",3,6,0,"50%");ratio(u,"seroRate",2,6,2,"33%");
        observed.reset();JsonNode c=success("{\"filters\":{\"sex\":\"F\"}}");number(c.path("stats"),"cohortRate",".5");number(c.path("stats"),"femaleRate","1");ratio(c,"seroRate",1,3,2,"33%");released();
        sql("UPDATE patient_basic_info SET gender=NULL WHERE id=6");observed.reset();JsonNode unknown=success("{}");ratio(unknown,"femaleRate",2,6,1,"33%");released();
    }
    void ratio(JsonNode d,String key,int num,int den,int unknown,String display) {
        JsonNode m=d.path("metricMeta").path(key);assertEquals(num,m.path("numerator").asInt());assertEquals(den,m.path("denominator").asInt());assertEquals(unknown,m.path("unknownN").asInt());assertEquals(den==0?"NO_DATA":"OK",m.path("status").asText());assertEquals(display,m.path("displayText").asText());
    }
    @Test void completenessUsesRealQcAndPreservesIncompleteCount() throws Exception {
        sixPatients();JsonNode u=success("{}");number(u.path("stats"),"completeRate",".6666666666666667");ratio(u,"completeRate",4,6,0,"67%");assertEquals(2,u.path("stats").path("incompleteCount").asInt());assertEquals(0,u.path("stats").path("qcUnknownN").asInt());assertEquals(5,u.path("stats").path("evaluable").asInt());
        observed.reset();JsonNode c=success("{\"filters\":{\"sex\":\"F\"}}");ratio(c,"completeRate",1,3,0,"33%");assertEquals(2,c.path("stats").path("incompleteCount").asInt());released();
    }
    @Test void activityRowsUseSeparateBaseAndEvaluationDenominators() throws Exception {
        sixPatients();JsonNode u=success("{}");activity(u,"base",new int[]{1,1,1,2},new String[]{".2",".2",".2",".4"});activity(u,"current",new int[]{1,1,2,1},new String[]{".2",".2",".4",".2"});released();
        observed.reset();JsonNode c=success("{\"filters\":{\"sex\":\"F\"}}");activity(c,"base",new int[]{0,0,1,1},new String[]{"0","0",".5",".5"});activity(c,"current",new int[]{1,0,1,0},new String[]{".5","0",".5","0"});assertEquals(1,c.path("activity").path("unknownN").asInt());assertEquals(1,c.path("activity").path("baseUnknownN").asInt());released();
    }
    void activity(JsonNode d,String key,int[] counts,String[] rates) {
        JsonNode rows=d.path("activity").path(key);assertEquals(4,rows.size());
        for(int i=0;i<4;i++){JsonNode row=rows.get(i);assertEquals(counts[i],row.path("count").asInt());assertTrue(row.path("value").isNumber());assertEquals(0,new BigDecimal(rates[i]).compareTo(row.path("value").decimalValue()));assertEquals("OK",row.path("status").asText());assertTrue(row.path("displayText").asText().endsWith("%"));}
    }
    @Test void treatmentGroupsKeepKnownMembersAndActualTargets() throws Exception {
        sixPatients();JsonNode u=success("{}");JsonNode groups=u.path("byTx").path("groups");assertEquals(5,groups.size());
        String[] tx={"csDMARD","TNFi","JAKi","IL-6i","Abatacept"};
        for(int i=0;i<5;i++){JsonNode g=groups.get(i);assertEquals(tx[i],g.path("tx").asText());assertEquals(1,g.path("n").asInt());assertEquals(1,g.path("evalN").asInt());assertEquals(i<2?1:0,g.path("targetN").asInt());number(g,"rate",i<2?"1":"0");assertEquals("OK",g.path("status").asText());assertEquals(i<2?"100%":"0%",g.path("displayText").asText());}
        assertEquals(1,u.path("byTx").path("unknownTxN").asInt());assertEquals(0,u.path("byTx").path("noCurrentTxN").asInt());released();
        observed.reset();JsonNode c=success("{\"filters\":{\"sex\":\"F\"}}");assertEquals(2,c.path("byTx").path("groups").size());assertEquals("JAKi",c.path("byTx").path("groups").get(1).path("tx").asText());released();
    }
    @Test void linesKeepThreeBucketsWithoutInventingUnknownLine() throws Exception {
        sixPatients();JsonNode u=success("{}");lineRows(u,new int[]{1,2,2},new String[]{".1666666666666667",".3333333333333333",".3333333333333333"});released();
        observed.reset();JsonNode c=success("{\"filters\":{\"sex\":\"F\"}}");lineRows(c,new int[]{1,0,1},new String[]{".3333333333333333","0",".3333333333333333"});released();
    }
    void lineRows(JsonNode d,int[] counts,String[] rates) {
        JsonNode lines=d.path("lines");assertEquals(1,lines.path("unknownLineN").asInt());assertEquals(3,lines.path("groups").size());
        for(int i=0;i<3;i++){JsonNode row=lines.path("groups").get(i);assertEquals(i+1,row.path("line").asInt());assertEquals(counts[i],row.path("count").asInt());number(row,"value",rates[i]);assertEquals("OK",row.path("status").asText());}
    }
    @Test void realFmSourceSuppressesComparisonWithoutFalseFacts() throws Exception {
        sixPatients();JsonNode u=success("{}");fmSuppressed(u,3,0,3);released();
        observed.reset();JsonNode c=success("{\"filters\":{\"sex\":\"F\"}}");fmSuppressed(c,2,0,1);released();
        assertEquals("P03_RESULT",c.path("meta").path("completion").asText());assertEquals("dev-descriptive-v04",c.path("meta").path("policyVersions").path("descriptive").asText());
    }
    void fmSuppressed(JsonNode d,int fm,int other,int unknown) {
        JsonNode f=d.path("fm");assertEquals(fm,f.path("nFM").asInt());assertEquals(other,f.path("nOther").asInt());assertEquals(unknown,f.path("unknownN").asInt());assertEquals("INSUFFICIENT_SAMPLE",f.path("status").asText());assertEquals("GROUP_SIZE_LT_3",f.path("reason").asText());assertTrue(f.path("rows").isArray());assertEquals(0,f.path("rows").size());
    }
    @Test void displayComesOnlyFromAuthorizedPatientSource() throws Exception {
        sixPatients();JsonNode d=success("{}");assertEquals(java.util.Arrays.asList("5","4","3","2","1","6"),ids(d));
        for(int i=0;i<6;i++){JsonNode p=patientById(d,i+1);assertEquals("合成患者"+(i+1),p.path("name").asText());assertEquals("RA-P03-0000"+(i+1),p.path("studyNo").asText());}
        assertFalse(d.toString().contains("OTHER-SECRET"));assertFalse(d.toString().contains("另一医生专有姓名"));assertFalse(d.toString().contains("110101198610070011"));assertFalse(d.toString().contains("cardNo"));released();
        sql("UPDATE patient_basic_info SET study_no=NULL WHERE id=1");observed.reset();JsonNode missing=success("{\"filters\":{\"ids\":[\"1\",\"7\"]}}");assertEquals(java.util.Collections.singletonList("1"),ids(missing));assertTrue(missing.path("patients").path("items").get(0).path("studyNo").isNull());released();
    }
    @Test void explicitEmptyIdsKeepsStudyAndReturnsHonestEmptyShapes() throws Exception {
        sixPatients();JsonNode d=success("{\"filters\":{\"ids\":[]}}");assertEquals(0,d.path("n").asInt());assertEquals(6,d.path("studyTotal").asInt());assertEquals(0,d.path("patients").path("items").size());
        for(String key:java.util.Arrays.asList("cohortRate","femaleRate","seroRate","targetRate","completeRate")){number(d.path("stats"),key,"0");assertEquals("NO_DATA",d.path("metricMeta").path(key).path("status").asText(),key);}
        for(String key:java.util.Arrays.asList("ageMedian","durationMedian")){assertTrue(d.path("stats").path(key).isNull());assertEquals(0,d.path("metricMeta").path(key).path("validN").asInt());assertEquals("NO_DATA",d.path("metricMeta").path(key).path("status").asText());}
        for(String key:java.util.Arrays.asList("current","base")){assertEquals(4,d.path("activity").path(key).size());for(JsonNode row:d.path("activity").path(key)){assertEquals(0,row.path("count").asInt());number(row,"value","0");assertEquals("NO_DATA",row.path("status").asText());}}
        assertEquals(3,d.path("lines").path("groups").size());for(JsonNode row:d.path("lines").path("groups")){assertEquals(0,row.path("count").asInt());number(row,"value","0");assertEquals("NO_DATA",row.path("status").asText());}
        assertEquals(0,d.path("byTx").path("groups").size());assertEquals(0,d.path("fm").path("rows").size());number(d.path("byTx"),"studyTargetRate",".4");released();
        context.getBean(FakePrincipalProvider.class).doctor=999;observed.reset();JsonNode noU=success("{}");assertEquals(0,noU.path("studyTotal").asInt());number(noU.path("stats"),"cohortRate","0");released();
    }
    @Test void missingScoresAndDemographicsStayMissingWhileKnownTreatmentRemains() throws Exception {
        sixPatients();sql("UPDATE patient_follow_up_history SET bqpg=NULL WHERE patient_basic_info_id<=6");sql("UPDATE patient_basic_info SET card_no=NULL,confirm_date=NULL WHERE id<=6");observed.reset();JsonNode d=success("{}");
        assertEquals(6,d.path("n").asInt());number(d.path("stats"),"targetRate","0");ratio(d,"targetRate",0,0,6,"0%");
        for(String key:java.util.Arrays.asList("ageMedian","durationMedian")){assertTrue(d.path("stats").path(key).isNull());assertEquals(0,d.path("metricMeta").path(key).path("validN").asInt());assertEquals(6,d.path("metricMeta").path(key).path("unknownN").asInt());assertEquals("NO_DATA",d.path("metricMeta").path(key).path("status").asText());assertEquals("—",d.path("metricMeta").path(key).path("displayText").asText());}
        assertEquals(5,d.path("byTx").path("groups").size());for(JsonNode g:d.path("byTx").path("groups")){assertEquals(1,g.path("n").asInt());assertEquals(0,g.path("evalN").asInt());number(g,"rate","0");assertEquals("NO_DATA",g.path("status").asText());}
        released();
    }
    @Test void oddEvenMediansAndEffectiveIdsAreIndependentOfLaterFilters() throws Exception {
        sixPatients();JsonNode odd=success("{\"filters\":{\"ids\":[\"1\",\"5\",\"6\"]}}");number(odd.path("stats"),"ageMedian","40");median(odd,"ageMedian",3,0,"40");released();
        observed.reset();JsonNode even=success("{\"filters\":{\"ids\":[\"1\",\"5\",\"6\",\"7\",\"999\"],\"sex\":\"F\"}}");assertEquals(java.util.Arrays.asList("1","6"),ids(even));assertEquals(5,even.path("submittedUniqueIdsN").asInt());assertEquals(3,even.path("effectiveIdsN").asInt());assertEquals(6,even.path("studyTotal").asInt());number(even.path("stats"),"ageMedian","55");median(even,"ageMedian",2,0,"55");number(even.path("byTx"),"studyTargetRate",".4");released();
    }
    @Test void noneAndConflictHaveDistinctCountsAndNoInventedLines() throws Exception {
        sixPatients();
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE patient_basic_info_id=?")) {
            s.setString(1,drugs(drug("甲氨蝶呤","2026-04-06","2026-09-01")));s.setInt(2,1);s.executeUpdate();
            s.setString(1,drugs(drug("依那西普","2026-04-06",null),drug("托法替布","2026-04-06",null)));s.setInt(2,2);s.executeUpdate();
        }
        observed.reset();JsonNode d=success("{}");assertEquals("NONE",treatment(d,1).path("state").asText());assertEquals("CONFLICT",treatment(d,2).path("state").asText());
        assertEquals(1,d.path("byTx").path("noCurrentTxN").asInt());assertEquals(2,d.path("byTx").path("unknownTxN").asInt());assertEquals(3,d.path("byTx").path("groups").size());assertEquals(3,d.path("lines").path("unknownLineN").asInt());released();
    }
    @Test void studyBaselineUsesSameAtWithoutCohortFilters() throws Exception {
        sixPatients();rawVisit(130,1,"2026-10-07","3","{\"cfydb\":12}",drugs(drug("甲氨蝶呤","2026-04-06",null)));observed.reset();
        JsonNode now=success("{}");number(now.path("stats"),"targetRate",".2");number(now.path("byTx"),"studyTargetRate",".2");activity(now,"current",new int[]{0,1,3,1},new String[]{"0",".2",".6",".2"});released();
        observed.reset();JsonNode six=success("{\"filters\":{\"at\":\"6m\",\"ids\":[\"1\"]}}");assertEquals(1,six.path("n").asInt());assertEquals(6,six.path("studyTotal").asInt());number(six.path("stats"),"targetRate","1");number(six.path("byTx"),"studyTargetRate",".4");assertEquals(5,six.path("byTx").path("studyTargetMeta").path("denominator").asInt());number(six.path("patients").path("items").get(0),"das28At","2");number(six.path("patients").path("items").get(0),"das28Current","3");released();
        observed.reset();JsonNode allSix=success("{\"filters\":{\"at\":\"6m\"}}");assertEquals(5,allSix.path("n").asInt());assertEquals(6,allSix.path("studyTotal").asInt());number(allSix.path("byTx"),"studyTargetRate",".4");released();
    }
}
