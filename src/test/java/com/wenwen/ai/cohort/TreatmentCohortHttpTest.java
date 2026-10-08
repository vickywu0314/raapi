package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentCohortHttpTest extends TreatmentHttpFixture {
    @Test void firstTargetedSchemeFlowsThroughRealTxHttp() throws Exception {
        timelineFixture(); medication(100,1,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null)));
        JsonNode data=success("{\"filters\":{\"tx\":\"TNFi\"}}");
        assertEquals(Arrays.asList("1"),ids(data));
        JsonNode tx=treatment(data,1); assertEquals("ACTIVE",tx.path("state").asText());
        assertEquals("TNFi",tx.path("category").asText()); assertEquals(2,tx.path("line").asInt());
        assertEquals(1,tx.path("targetedDrugHistoryN").asInt()); assertEquals(5,tx.path("schemeDurationMonths").asInt());
        released();
    }
    @Test void thirdTargetedGenericIsThirdLineWithUncappedHistoryThroughRealHttp() throws Exception {
        timelineFixture();
        medication(100,1,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01","2025-02-28")));
        medication(200,1,"2025-03-01",drugs(drug("依那西普","2025-03-01","2025-04-30")));
        medication(300,1,"2025-05-01",drugs(drug("托法替布","2025-05-01",null)));
        JsonNode data=success("{\"filters\":{\"tx\":\"JAKi\"}}");
        assertEquals(Arrays.asList("1"),ids(data));
        JsonNode tx=treatment(data,1); assertEquals("ACTIVE",tx.path("state").asText());
        assertEquals("JAKi",tx.path("category").asText()); assertEquals(3,tx.path("line").asInt());
        assertEquals(3,tx.path("targetedDrugHistoryN").asInt());
        assertEquals("2025-05-01",tx.path("startDate").asText());
        assertEquals(1,tx.path("schemeDurationMonths").asInt()); released();
        observed.reset(); assertEquals(Arrays.asList("1"),ids(success("{\"filters\":{\"tx\":\"bio\"}}"))); released();
    }

    @Test void fourthTargetedGenericKeepsThirdLineAndFourthHistoryThroughRealHttp() throws Exception {
        timelineFixture(); clock.now=java.time.Instant.parse("2025-08-01T02:00:00Z");
        medication(100,1,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01","2025-02-28")));
        medication(200,1,"2025-03-01",drugs(drug("依那西普","2025-03-01","2025-04-30")));
        medication(300,1,"2025-05-01",drugs(drug("托法替布","2025-05-01","2025-06-30")));
        medication(400,1,"2025-07-01",drugs(drug("阿巴西普","2025-07-01","2025-08-31")));
        JsonNode data=success("{\"filters\":{\"tx\":\"Abatacept\"}}");
        assertEquals(Arrays.asList("1"),ids(data));
        JsonNode tx=treatment(data,1); assertEquals("ACTIVE",tx.path("state").asText());
        assertEquals("Abatacept",tx.path("category").asText()); assertEquals(3,tx.path("line").asInt());
        assertEquals(4,tx.path("targetedDrugHistoryN").asInt());
        assertEquals("2025-07-01",tx.path("startDate").asText()); assertEquals("2025-08-31",tx.path("endDate").asText());
        assertEquals("visit:400:dev:阿巴西普",tx.path("episodeKey").asText());
        assertEquals(json.readTree("[\"dev:阿巴西普\"]"),tx.path("genericDrugIds"));
        assertEquals("400",tx.path("provenance").path("visitId").asText());
        assertEquals("2025-07-01",tx.path("provenance").path("observedAt").asText());
        assertEquals(1,tx.path("schemeDurationMonths").asInt()); released();
        observed.reset(); assertEquals(Arrays.asList("1"),ids(success("{\"filters\":{\"tx\":\"bio\"}}"))); released();
    }

    @Test void traditionalReturnAfterFourthTargetedKeepsFirstLineAndFullHistoryThroughRealHttp() throws Exception {
        timelineFixture(); clock.now=java.time.Instant.parse("2025-09-01T02:00:00Z");
        medication(100,1,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01","2025-02-28")));
        medication(200,1,"2025-03-01",drugs(drug("依那西普","2025-03-01","2025-04-30")));
        medication(300,1,"2025-05-01",drugs(drug("托法替布","2025-05-01","2025-06-30")));
        medication(400,1,"2025-07-01",drugs(drug("阿巴西普","2025-07-01","2025-08-31")));
        medication(500,1,"2025-09-01",drugs(drug("甲氨蝶呤","2025-09-01",null)));
        JsonNode data=success("{\"filters\":{\"tx\":\"csDMARD\"}}");
        assertEquals(Arrays.asList("1"),ids(data));
        JsonNode tx=treatment(data,1); assertEquals("ACTIVE",tx.path("state").asText());
        assertEquals("csDMARD",tx.path("category").asText()); assertEquals(1,tx.path("line").asInt());
        assertEquals(4,tx.path("targetedDrugHistoryN").asInt());
        assertEquals("2025-09-01",tx.path("startDate").asText()); assertTrue(tx.path("endDate").isNull());
        assertEquals("visit:500:dev:甲氨蝶呤",tx.path("episodeKey").asText());
        assertEquals(json.readTree("[\"dev:甲氨蝶呤\"]"),tx.path("genericDrugIds"));
        assertEquals(0,tx.path("schemeDurationMonths").asInt()); released();
        observed.reset(); assertTrue(ids(success("{\"filters\":{\"tx\":\"bio\"}}")).isEmpty()); released();
    }

    @Test void fiveExactCategoriesAndBioUseScopedUniverse() throws Exception {
        timelineFixture(); sql("DELETE FROM patient_relation_doctor");
        for(int id=1;id<=6;id++) sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,"+id+",0)");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (202,7,0)");
        String[] names={"甲氨蝶呤","阿达木单抗","托法替布","托珠单抗","阿巴西普"};
        String[] categories={"csDMARD","TNFi","JAKi","IL-6i","Abatacept"};
        for(int i=0;i<names.length;i++) medication(100+i,i+1,"2025-01-01",drugs(drug(names[i],"2025-01-01",null)));
        medication(200,7,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null)));
        for(int i=0;i<categories.length;i++) {
            observed.reset(); JsonNode data=success("{\"filters\":{\"tx\":\""+categories[i]+"\"}}");
            assertEquals(Arrays.asList(Integer.toString(i+1)),ids(data)); assertEquals(6,data.path("studyTotal").asInt()); released();
        }
        observed.reset(); JsonNode bio=success("{\"filters\":{\"tx\":\"bio\"}}");
        assertEquals(Arrays.asList("2","3","4","5"),ids(bio)); assertEquals(6,bio.path("studyTotal").asInt());
        assertEquals("dev-drug-v04",bio.path("meta").path("policyVersions").path("drugDictionary").asText()); released();
    }

    @Test void unrecognizedRowsCannotBorrowKnownDrugIdentity() throws Exception {
        timelineFixture();
        medication(100,1,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null),drug("未知西药",null,null)));
        JsonNode data=success("{}"); JsonNode tx=treatment(data,1);
        assertEquals("UNKNOWN",tx.path("state").asText()); assertTrue(tx.path("line").isNull());
        assertTrue(tx.path("provenance").path("quality").toString().contains("UNMAPPED_DRUG"));
        observed.reset(); assertTrue(ids(success("{\"filters\":{\"tx\":\"bio\"}}")).isEmpty()); released();
        for(String row:Arrays.asList(drug("MTX",null,null),drug("长期使用阿达木单抗有效",null,null),"{\"goodsName\":\"synthetic-brand\",\"category\":\"TNFi\"}")) {
            sql("DELETE FROM patient_follow_up_history"); medication(101,1,"2025-01-01",drugs(row)); observed.reset();
            assertEquals("UNKNOWN",treatment(success("{}"),1).path("state").asText()); released();
        }
    }

    @Test void targetedEntitySetControlsCombinationsAndArrayOrder() throws Exception {
        timelineFixture();
        String ada=drug("阿达木单抗","2025-01-01",null), mtx=drug("甲氨蝶呤","2024-01-01",null);
        medication(100,1,"2025-01-01",drugs(mtx,ada,ada));
        JsonNode first=treatment(success("{}"),1);
        assertEquals("TNFi",first.path("category").asText()); assertEquals("2025-01-01",first.path("startDate").asText());
        assertEquals(json.readTree("[\"dev:阿达木单抗\"]"),first.path("genericDrugIds"));
        for(String second:Arrays.asList("托法替布","依那西普")) {
            sql("DELETE FROM patient_follow_up_history"); medication(100,1,"2025-01-01",drugs(ada,drug(second,"2025-02-01",null))); observed.reset();
            JsonNode conflict=treatment(success("{}"),1);
            assertEquals("CONFLICT",conflict.path("state").asText()); assertTrue(conflict.path("category").isNull()); assertTrue(conflict.path("line").isNull());
            assertEquals("2025-02-01",conflict.path("startDate").asText()); assertEquals(2,conflict.path("targetedDrugHistoryN").asInt());
            sql("DELETE FROM patient_follow_up_history"); medication(100,1,"2025-01-01",drugs(drug(second,"2025-02-01",null),ada)); observed.reset();
            JsonNode reversed=treatment(success("{}"),1); assertEquals(conflict,reversed);
            observed.reset(); assertTrue(ids(success("{\"filters\":{\"tx\":\"bio\"}}")).isEmpty()); released();
        }
    }

    @Test void unknownEvidenceKeepsKnownHistoryAndSourceProvenanceInEitherOrder() throws Exception {
        timelineFixture();
        String known=drug("阿达木单抗","2025-01-01",null), unknown=drug("未映射",null,null);
        medication(100,1,"2025-01-01",drugs(known,unknown)); JsonNode value=treatment(success("{}"),1);
        assertEquals(1,value.path("targetedDrugHistoryN").asInt()); assertEquals("100",value.path("provenance").path("visitId").asText());
        assertEquals("2025-01-01",value.path("provenance").path("observedAt").asText()); assertEquals("zlfa.xyList",value.path("provenance").path("field").asText());
        sql("DELETE FROM patient_follow_up_history"); medication(100,1,"2025-01-01",drugs(unknown,known)); observed.reset();
        assertEquals(value,treatment(success("{}"),1)); released();
    }

    @Test void sourceSnapshotContainsOldMedicationAndNextRequestSeesUpdateAndDeletion() throws Exception {
        timelineFixture(); medication(100,1,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null)));
        java.util.concurrent.atomic.AtomicInteger commits=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterFirstQuery=() -> {
            try(java.sql.Connection writer=raw.getConnection(); java.sql.PreparedStatement update=writer.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE id=100")) {
                writer.setAutoCommit(false); update.setString(1,drugs(drug("托法替布","2025-03-01",null))); update.executeUpdate(); writer.commit(); commits.incrementAndGet();
            } catch(Exception e) { throw new AssertionError(e); }
        };
        JsonNode current=treatment(success("{}"),1); assertEquals(1,commits.get()); assertEquals("TNFi",current.path("category").asText()); assertEquals(5,current.path("schemeDurationMonths").asInt()); released();
        assertEquals(1,new java.util.HashSet<>(observed.queryConnections).size());
        assertEquals(java.util.Collections.nCopies(5,false),observed.autoCommits);
        assertEquals(java.util.Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);
        observed.reset(); JsonNode next=treatment(success("{}"),1); assertEquals("JAKi",next.path("category").asText()); assertEquals(3,next.path("schemeDurationMonths").asInt()); released();
        sql("DELETE FROM patient_follow_up_history WHERE id=100"); observed.reset(); assertEquals("UNKNOWN",treatment(success("{}"),1).path("state").asText()); released();
    }

    @Test void explicitSyntheticAliasUsesFixedDictionaryVersionForWholeRequest() throws Exception {
        timelineFixture();
        com.wenwen.ai.treatment.DrugDictionary.Drug ada=new com.wenwen.ai.treatment.DrugDictionary.Drug("synthetic:ada","阿达木单抗","TNFi");
        java.util.Map<String,com.wenwen.ai.treatment.DrugDictionary.Drug> aliases=new java.util.LinkedHashMap<>();
        aliases.put("synthetic-explicit-alias",ada); aliases.put("阿达木单抗",ada);
        dictionary().view=new com.wenwen.ai.treatment.DrugDictionary.Snapshot("synthetic-alias-v1",aliases);
        aliases.clear();
        medication(100,1,"2025-01-01",drugs(drug("synthetic-explicit-alias","2025-01-01",null)));
        medication(200,3,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null)));
        java.util.concurrent.atomic.AtomicInteger ticks=new java.util.concurrent.atomic.AtomicInteger();
        clock.onInstant=() -> { assertEquals(0,observed.active.get()); if(ticks.incrementAndGet()==2) dictionary().view=new com.wenwen.ai.treatment.DevelopmentDrugDictionary().snapshot(); };
        JsonNode data=success("{\"filters\":{\"tx\":\"TNFi\"}}"); assertEquals(Arrays.asList("1","3"),ids(data));
        assertEquals(1,dictionary().snapshots); assertEquals("synthetic-alias-v1",data.path("meta").path("policyVersions").path("drugDictionary").asText());
        for(long patient:new long[]{1,3}) { JsonNode tx=treatment(data,patient); assertEquals("synthetic:ada",tx.path("genericDrugIds").get(0).asText()); assertEquals("synthetic-alias-v1",tx.path("provenance").path("dictionaryVersion").asText()); }
        released(); clock.onInstant=null; observed.reset();
        JsonNode next=success("{\"filters\":{\"tx\":\"TNFi\"}}"); assertEquals(Arrays.asList("3"),ids(next)); assertEquals(2,dictionary().snapshots); released();
    }

    @Test void dictionaryExecutionAndRealSqlFailuresReturn503AfterSourceRelease() throws Exception {
        timelineFixture(); medication(100,1,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null)));
        java.util.concurrent.atomic.AtomicInteger failures=new java.util.concurrent.atomic.AtomicInteger();
        dictionary().view=new com.wenwen.ai.treatment.DrugDictionary.Snapshot("synthetic-failure",java.util.Collections.emptyMap()) {
            @Override public com.wenwen.ai.treatment.DrugDictionary.Drug identify(String name) {
                sourceReleased(); failures.incrementAndGet(); throw new IllegalStateException("合成字典执行失败");
            }
        };
        error("{}",503,"SERVICE_UNAVAILABLE"); assertEquals(1,failures.get()); sourceReleased();
        dictionary().view=new com.wenwen.ai.treatment.DevelopmentDrugDictionary().snapshot(); observed.reset(); observed.failSecondSql=true;
        error("{}",503,"SERVICE_UNAVAILABLE"); assertTrue(observed.mysqlFailures>0); assertEquals(3,observed.selects);
        assertEquals(1,observed.borrowed); assertEquals(1,observed.returned); assertEquals(0,observed.active.get());
        observed.reset(); assertEquals("TNFi",treatment(success("{}"),1).path("category").asText()); released();
    }

    @Test void txAndClinicalAndIdsPreserveUniversePrecisionAndUnknownDenominator() throws Exception {
        timelineFixture(); sql("DELETE FROM patient_relation_doctor");
        for(int id=8;id<=10;id++) sql("INSERT INTO patient_basic_info (id,name) VALUES ("+id+",'synthetic')");
        for(int id=1;id<=10;id++) sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES ("+(id==10?202:101)+","+id+",0,"+(id==3?1:0)+")");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,2,1)");
        String[] names={"甲氨蝶呤","阿达木单抗","托法替布","托珠单抗","阿巴西普"};
        for(int i=0;i<names.length;i++) medication(100+i,i+1,"2025-01-01",drugs(drug(names[i],"2025-01-01",null)));
        medication(106,6,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01","2025-05-31")));
        medication(108,8,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null),drug("托法替布","2025-01-01",null)));
        medication(109,9,"2025-01-01",drugs(drug("unknown",null,null)));
        medication(110,10,"2025-01-01",drugs(drug("阿达木单抗","2025-01-01",null)));
        sql("UPDATE patient_basic_info SET gender=2,card_no='110101198501010011' WHERE id=2");
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":2.295,\"ytgjs\":4,\"zzgjs\":1},\"ztScoreByPatient\":50,\"hqaScore\":1.25}',fzjc='{\"cfydb\":9,\"lfsyz\":35}' WHERE id=101");
        JsonNode data=success("{}"); assertEquals(9,data.path("n").asInt()); assertEquals(9,data.path("studyTotal").asInt()); assertEquals(3,data.path("activity").path("unknownTxN").asInt());
        assertEquals("NONE",treatment(data,6).path("state").asText()); assertEquals("UNKNOWN",treatment(data,7).path("state").asText()); assertEquals("CONFLICT",treatment(data,8).path("state").asText()); released();
        observed.reset(); JsonNode filtered=success("{\"filters\":{\"tx\":\"TNFi\",\"sex\":\"F\",\"age\":\"40-40\",\"sero\":\"1\",\"act\":\"target\",\"ids\":[\"1\",\"2\",\"6\",\"8\",\"10\",\"999\",\"2\"]}}");
        assertEquals(Arrays.asList("2"),ids(filtered)); assertEquals(9,filtered.path("studyTotal").asInt()); assertEquals(6,filtered.path("submittedUniqueIdsN").asInt()); assertEquals(4,filtered.path("effectiveIdsN").asInt());
        JsonNode item=filtered.path("patients").path("items").get(0); assertEquals(2.30,item.path("das28At").asDouble()); assertEquals(4,item.path("evaluation").path("tjc").path("value").asInt()); assertEquals(9,item.path("evaluation").path("crp").path("value").asInt()); assertEquals(40,item.path("clinical").path("age").asInt()); released();
        for(String tx:Arrays.asList("null","\"\"")) { observed.reset(); assertEquals(9,success("{\"filters\":{\"tx\":"+tx+"}}").path("n").asInt()); released(); }
        for(Object invalidTx:Arrays.asList(1,false,new int[0],"tnfi","TNFi ","OTHER")) {
            String tx=json.writeValueAsString(invalidTx);
            observed.reset(); error("{\"filters\":{\"tx\":"+tx+"}}",400,"INVALID_FILTER"); assertEquals(0,observed.selects); assertEquals(0,observed.borrowed);
        }
    }

    @Test void parsedSourceShapeRetainsLocationAndNeverExposesWholeMedicationText() throws Exception {
        timelineFixture();
        String source="{\"zcyList\":[{\"drugName\":\"阿达木单抗\",\"startTime\":\"2025-01-01\",\"goodsName\":\"private-brand-sentinel\",\"company\":\"private-company-sentinel\",\"tyyy\":\"private-free-text-sentinel\"}],\"cyList\":[{\"zz\":\"not-western\",\"fj\":\"MTX\",\"bccy\":\"托法替布\"}],\"zywzList\":[{\"drugName\":\"托法替布\"}]}";
        medication(100,1,"2025-01-01",source); JsonNode data=success("{}"); JsonNode tx=treatment(data,1);
        assertEquals("TNFi",tx.path("category").asText()); assertEquals("zlfa.zcyList",tx.path("provenance").path("field").asText());
        assertFalse(data.toString().contains("private-")); assertFalse(data.toString().contains("not-western")); assertFalse(data.toString().contains("xyList")); released();
        sql("UPDATE patient_follow_up_history SET zlfa='{"+"\"xyList\":[1]}"+"' WHERE id=100"); observed.reset();
        JsonNode invalid=treatment(success("{}"),1); assertEquals("UNKNOWN",invalid.path("state").asText());
        assertEquals("100",invalid.path("provenance").path("visitId").asText()); assertEquals("2025-01-01",invalid.path("provenance").path("observedAt").asText());
        assertEquals("INVALID_MEDICATION_SOURCE",invalid.path("provenance").path("missingReason").asText()); released();
    }

    @Test void realHttpConsumesEndDerivedTraditionalSegmentInsteadOfOldDrugStart() throws Exception {
        timelineFixture();
        medication(100,1,"2025-01-01",drugs(drug("甲氨蝶呤","2025-01-01",null)));
        medication(200,1,"2025-03-01",drugs(drug("甲氨蝶呤","2025-01-01",null),drug("来氟米特","2025-03-01","2025-04-30")));
        medication(300,1,"2025-05-01",drugs(drug("甲氨蝶呤","2025-01-01",null)));
        JsonNode data=success("{\"filters\":{\"tx\":\"csDMARD\"}}"); assertEquals(Arrays.asList("1"),ids(data));
        JsonNode tx=treatment(data,1); assertEquals(1,tx.path("schemeDurationMonths").asInt()); assertTrue(tx.path("startDate").isNull());
        assertEquals("2025-05-01",tx.path("estimatedStartDate").asText()); assertEquals("ESTIMATED_EXPLICIT_END",tx.path("startConfidence").asText());
        assertTrue(tx.path("provenance").path("quality").toString().contains("END_DERIVED_SCHEME_START")); assertEquals(1,tx.path("line").asInt());
        assertEquals(0,tx.path("targetedDrugHistoryN").asInt()); released();
        observed.reset(); assertTrue(ids(success("{\"filters\":{\"tx\":\"bio\"}}")).isEmpty()); released();
    }

    @Test void realHttpClockDerivesRemainingSchemeWithoutNewVisitAndKeepsBoundaryAfterRepeat() throws Exception {
        timelineFixture(); medication(100,1,"2025-01-01",drugs(drug("甲氨蝶呤","2025-01-01",null)));
        medication(200,1,"2025-03-01",drugs(drug("甲氨蝶呤","2025-01-01",null),drug("来氟米特","2025-03-01","2025-04-30")));
        clock.now=java.time.Instant.parse("2025-04-30T02:00:00Z"); JsonNode april=treatment(success("{\"filters\":{\"tx\":\"csDMARD\"}}"),1);
        assertEquals("2025-03-01",april.path("startDate").asText()); assertEquals(2,april.path("genericDrugIds").size()); assertEquals("2025-04-30",april.path("endDate").asText()); released();
        clock.now=java.time.Instant.parse("2025-05-01T02:00:00Z"); observed.reset(); JsonNode may=treatment(success("{}"),1);
        assertTrue(may.path("startDate").isNull()); assertEquals("2025-05-01",may.path("estimatedStartDate").asText()); assertEquals(0,may.path("schemeDurationMonths").asInt());
        assertNotEquals(april.path("episodeKey"),may.path("episodeKey")); released();
        clock.now=java.time.Instant.parse("2025-06-01T02:00:00Z"); observed.reset(); JsonNode june=treatment(success("{}"),1);
        assertEquals(may.path("episodeKey"),june.path("episodeKey")); assertEquals(1,june.path("schemeDurationMonths").asInt()); released();
        medication(300,1,"2025-05-20",drugs(drug("甲氨蝶呤","2025-01-01",null))); medication(400,1,"2025-05-25","{}"); observed.reset();
        JsonNode repeated=treatment(success("{}"),1); assertEquals(may.path("episodeKey"),repeated.path("episodeKey")); assertEquals(1,repeated.path("schemeDurationMonths").asInt());
        assertEquals("ESTIMATED_EXPLICIT_END",repeated.path("startConfidence").asText()); assertTrue(repeated.path("startDate").isNull());
        assertTrue(repeated.path("provenance").path("quality").toString().contains("CARRIED_FORWARD")); released();
    }

}
