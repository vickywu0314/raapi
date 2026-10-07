package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClinicalCohortHttpTest extends ClinicalHttpFixture {
    @Test void painRetainsScalarRawWithoutPublishingAnyNumericValue() throws Exception {
        clinicalFixture();
        for(String raw:new String[]{"true","\"seven\"","7.0000000000000000001"}) {
            sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3},\"tjScore\":"+raw+"}' WHERE id=100");
            JsonNode pain=success("{}").path("patients").path("items").get(0).path("evaluation").path("pain");
            assertEquals(raw.replace("\"",""),pain.path("raw").asText()); assertTrue(pain.path("value").isNull()); assertTrue(pain.path("unit").isNull());
        }
    }
    @Test void birthdayChangesExactlyAcrossShanghaiMidnightAndConfirmationAliasesStayPrecise() throws Exception {
        clinicalFixture(); sql("UPDATE patient_basic_info SET confirm_date=null,confirmDate='2016-10-08' WHERE id=1");
        sql("UPDATE patient_basic_info SET confirm_date='2026-10-09' WHERE id=2");
        clock.now=java.time.Instant.parse("2026-10-07T15:59:59Z");
        JsonNode items=success("{}").path("patients").path("items");
        assertEquals(39,items.get(1).path("clinical").path("age").asInt());
        assertEquals(9,items.get(0).path("clinical").path("diseaseDurationYears").asInt());
        assertTrue(items.get(1).path("clinical").path("diseaseDurationYears").isNull()); assertTrue(items.get(2).path("clinical").path("diseaseDurationYears").isNull());
        clock.now=java.time.Instant.parse("2026-10-07T16:00:00Z");
        items=success("{}").path("patients").path("items");
        assertEquals(40,items.get(1).path("clinical").path("age").asInt()); assertEquals(10,items.get(0).path("clinical").path("diseaseDurationYears").asInt());
    }
    @Test void clinicalFiltersRejectInvalidBeforeSqlAndNullEmptyMeansUnlimited() throws Exception {
        clinicalFixture();
        String[] invalid={"\"sex\":1","\"sex\":\"female\"","\"age\":40","\"age\":\"-40\"","\"age\":\"121-\"","\"age\":\"0-121\"","\"age\":\"40-39\"","\"age\":\" 40-50\"","\"sero\":1","\"sero\":\"0\"","\"cm\":false","\"cm\":\"SS\"","\"duration\":\"1-2\"","\"data\":\"unsupported\"","\"q\":null","\"tx\":1","\"at\":\"6M\""};
        for(String fields:invalid) { observed.reset(); error("{\"filters\":{"+fields+"}}",400,"INVALID_FILTER"); assertEquals(0,observed.selects); assertEquals(0,observed.borrowed); }
        assertEquals(4,success("{\"filters\":{\"sex\":null,\"age\":\"\",\"sero\":null,\"cm\":\"\"}}").path("n").asInt());
        assertEquals(Arrays.asList("1","2","4"),ids(success("{\"filters\":{\"age\":\"0-120\"}}")));
        assertEquals(Arrays.asList("4"),ids(success("{\"filters\":{\"age\":\"80-\"}}")));
    }
    @Test void clinicalFactsCarrySourcesAndVersionsWithoutIdentityRaw() throws Exception {
        clinicalFixture(); sql("INSERT INTO patient_comorbidity VALUES (1,1,'FM',null)");
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":35}' WHERE id=100");
        JsonNode data=success("{}"); JsonNode provenance=data.path("patients").path("items").get(0).path("clinicalProvenance");
        assertEquals("patient_basic_info.gender",provenance.path("sex").path("sourceField").asText());
        assertEquals("patient_basic_info.card_no",provenance.path("age").path("sourceField").asText());
        assertEquals("COALESCE(patient_basic_info.confirm_date,patient_basic_info.confirmDate)",provenance.path("diseaseDurationYears").path("sourceField").asText());
        assertEquals("100",provenance.path("sero").path("observations").get(0).path("sourceVisitId").asText());
        assertEquals("fzjc.lfsyz",provenance.path("sero").path("observations").get(0).path("sourceField").asText());
        assertTrue(provenance.path("fm").path("quality").toString().contains("DATE_UNSPECIFIED"));
        assertEquals("NO_POSITIVE_ASSOCIATION",provenance.path("as").path("missingReason").asText());
        assertFalse(data.toString().contains("110101198610070011")); assertFalse(provenance.toString().contains("1986-10-07"));
        assertEquals("dev-clinical-v04",data.path("meta").path("policyVersions").path("clinical").asText());
        assertEquals("dev-ever-serology-v04",data.path("meta").path("policyVersions").path("serology").asText());
        assertEquals("P02_QC",data.path("meta").path("completion").asText());
        assertEquals(json.readTree("[\"studyCode\",\"at\",\"act\",\"ids\",\"sex\",\"age\",\"sero\",\"cm\",\"tx\",\"data\"]"),data.path("meta").path("supportedFilters"));
    }
    @Test void realPatientSerologySqlAcceptsJsonWhitespaceAndRetainsAuthorizedIdsAndRa() throws Exception {
        clinicalFixture();
        sql("UPDATE patient_follow_up_history SET fzjc='{ \"lfsyz\" : 35, \"kccpkt\" : 25 }' WHERE id=100");
        visit(901,1,6,"{}","2026-10-07",null,"{\"lfsyz\":960}");
        visit(902,5,0,"{}","2026-10-07",null,"{\"lfsyz\":960}");
        visit(903,1,0,"{}","2026-10-07",null,"broken{");
        com.wenwen.mapper.PatientMapper mapper=new org.mybatis.spring.SqlSessionTemplate(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)).getMapper(com.wenwen.mapper.PatientMapper.class);
        java.util.List<java.util.Map<String,Object>> rows=mapper.listSerology(java.util.Collections.singletonList(1L));
        assertEquals(2,rows.size()); assertEquals("broken{",rows.get(0).get("fzjc")); assertEquals(1L,((Number)rows.get(1).get("patientId")).longValue());
        assertEquals("{ \"lfsyz\" : 35, \"kccpkt\" : 25 }",rows.get(1).get("fzjc"));
        java.util.Map<String,String> scalars=com.wenwen.ai.source.ClinicalScalarReader.read((String)rows.get(1).get("fzjc"),new java.util.LinkedHashSet<>());
        assertEquals("35",scalars.get("/lfsyz"));assertEquals("25",scalars.get("/kccpkt"));assertEquals("2026-10-07",rows.get(1).get("visitDate"));
    }
    @Test void haqMainPresenceControlsAliasAndDiscrepancyWithoutGhSubstitution() throws Exception {
        clinicalFixture();
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3,\"hqaScore\":2},\"ztScoreByPatient\":50}' WHERE id=100");
        JsonNode haq=success("{}").path("patients").path("items").get(0).path("evaluation").path("haq");
        assertEquals(2,haq.path("value").asInt()); assertEquals("bqpg.result.hqaScore",haq.path("sourceField").asText());
        for(String main:new String[]{"null","\"\"","\"illegal\"","{}","true"}) {
            sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3,\"hqaScore\":2},\"hqaScore\":"+main+",\"ztScoreByPatient\":50}' WHERE id=100");
            haq=success("{}").path("patients").path("items").get(0).path("evaluation").path("haq");
            assertTrue(haq.path("value").isNull(),main); assertEquals("bqpg.hqaScore",haq.path("sourceField").asText());
        }
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3,\"hqaScore\":2},\"hqaScore\":1,\"ztScoreByPatient\":50}' WHERE id=100");
        JsonNode evaluation=success("{}").path("patients").path("items").get(0).path("evaluation");
        assertEquals(1,evaluation.path("haq").path("value").asInt());
        assertEquals(json.readTree("[\"LEGACY_UNVERIFIED\",\"DISCREPANCY\"]"),evaluation.path("haq").path("quality"));
        assertEquals(50,evaluation.path("gh").path("value").asInt());
    }
    @Test void evaluationRejectsNegativeInvalidAndOutOfRangeButPreservesZero() throws Exception {
        clinicalFixture();
        String[] names={"tjc","sjc","gh","haq","crp"}; String[] fields={"result.ytgjs","result.zzgjs","ztScoreByPatient","hqaScore","cfydb"};
        String[] overflow={"29","29","101","3.01",null};
        for(int i=0;i<names.length;i++) {
            for(String value:new String[]{"-1","\"NaN\"",overflow[i],"0",null}) {
                if(value==null) value="null";
                String bqpg="{\"result\":{\"crpScore\":3"+(i<2?",\""+(i==0?"ytgjs":"zzgjs")+"\":"+value:"")+"}"+(i==2||i==3?",\""+fields[i]+"\":"+value:"")+"}";
                String labs=i==4?"{\"cfydb\":"+value+"}":"{}";
                sql("UPDATE patient_follow_up_history SET bqpg='"+bqpg+"',fzjc='"+labs+"' WHERE id=100");
                JsonNode component=success("{}").path("patients").path("items").get(0).path("evaluation").path(names[i]);
                if("0".equals(value)) assertEquals(0,component.path("value").asInt());
                else { assertTrue(component.path("value").isNull(),names[i]+value); assertFalse(component.path("missingReason").asText().isEmpty()); }
            }
        }
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3,\"ytgjs\":1.5,\"zzgjs\":28}}' WHERE id=100");
        JsonNode evaluation=success("{}").path("patients").path("items").get(0).path("evaluation");
        assertTrue(evaluation.path("tjc").path("value").isNull()); assertEquals(28,evaluation.path("sjc").path("value").asInt());
    }
    @Test void evaluationKeepsAllComponentsOnSelectedCrpVisitAndNeverGuessesPainScale() throws Exception {
        clinicalFixture();
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3,\"ytgjs\":4,\"zzgjs\":1},\"ztScoreByPatient\":50,\"hqaScore\":1.25,\"tjScore\":7}',fzjc='{\"cfydb\":9}' WHERE id=100");
        visit(101,1,0,"{\"hqaScore\":2.5}","2026-10-07",null,null);
        JsonNode evaluation=success("{}").path("patients").path("items").get(0).path("evaluation");
        String[] names={"tjc","sjc","gh","haq","crp"}; double[] values={4,1,50,1.25,9};
        String[] fields={"bqpg.result.ytgjs","bqpg.result.zzgjs","bqpg.ztScoreByPatient","bqpg.hqaScore","fzjc.cfydb"};
        for(int i=0;i<names.length;i++) {
            JsonNode component=evaluation.path(names[i]); assertEquals(values[i],component.path("value").asDouble());
            assertEquals("100",component.path("sourceVisitId").asText()); assertEquals(fields[i],component.path("sourceField").asText());
            assertEquals("2026-10-07",component.path("observedAt").asText()); assertTrue(component.path("missingReason").isNull());
            assertEquals(json.readTree("[\"LEGACY_UNVERIFIED\"]"),component.path("quality"));
        }
        assertEquals("mg/L",evaluation.path("crp").path("unit").asText());
        JsonNode pain=evaluation.path("pain"); assertTrue(pain.path("value").isNull()); assertEquals("7",pain.path("raw").asText());
        assertTrue(pain.path("unit").isNull()); assertEquals("UNVERIFIED_SCALE",pain.path("missingReason").asText());
        assertEquals(json.readTree("[\"LEGACY_UNVERIFIED\",\"SCALE_UNVERIFIED\"]"),pain.path("quality"));
    }
    @Test void positiveAssociationsRemainThreeStateAndFiltersIntersectAfterIds() throws Exception {
        clinicalFixture();
        sql("INSERT INTO patient_comorbidity VALUES (1,1,'FM',null),(2,3,'AS',2026),(3,2,'FM',2027),(4,5,'FM',2000)");
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":35}' WHERE id=100");
        JsonNode items=success("{}").path("patients").path("items");
        assertEquals("TRUE",items.get(0).path("clinical").path("fm").asText());
        assertEquals("UNKNOWN",items.get(0).path("clinical").path("as").asText());
        assertEquals("UNKNOWN",items.get(1).path("clinical").path("fm").asText());
        assertEquals("TRUE",items.get(2).path("clinical").path("as").asText());
        assertEquals(Arrays.asList("1"),ids(success("{\"filters\":{\"sex\":\"F\",\"age\":\"40-50\",\"sero\":\"1\",\"cm\":\"FM\"}}")));
        assertEquals(Arrays.asList("3"),ids(success("{\"filters\":{\"cm\":\"AS\"}}")));
        assertEquals(0,success("{\"filters\":{\"cm\":\"none\"}}").path("n").asInt());
        JsonNode data=success("{\"filters\":{\"ids\":[\"1\",\"5\",\"999\"],\"cm\":\"AS\"}}");
        assertEquals(3,data.path("submittedUniqueIdsN").asInt()); assertEquals(1,data.path("effectiveIdsN").asInt()); assertEquals(4,data.path("studyTotal").asInt()); assertEquals(0,data.path("n").asInt());
        assertEquals(0,success("{\"filters\":{\"ids\":[],\"cm\":\"FM\"}}").path("n").asInt());
    }
    @Test void serologyUsesDatedEverPositiveAndConservativeUnknown() throws Exception {
        clinicalFixture();
        visit(90,1,0,"{}","2026-10-01",null,"{ \"lfsyz\" : 35 }");
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":10}' WHERE id=100");
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":20,\"kccpkt\":25}' WHERE id=200");
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":\"<100\"}' WHERE id=300");
        visit(401,4,0,"{}","2026-10-08",null,"{\"lfsyz\":35}");
        visit(402,4,0,"{}",null,null,"{\"lfsyz\":35}");
        JsonNode items=success("{}").path("patients").path("items");
        assertEquals("TRUE",items.get(0).path("clinical").path("sero").asText());
        assertEquals("FALSE",items.get(1).path("clinical").path("sero").asText());
        assertEquals("UNKNOWN",items.get(2).path("clinical").path("sero").asText());
        assertEquals("UNKNOWN",items.get(3).path("clinical").path("sero").asText());
        assertEquals(Arrays.asList("1"),ids(success("{\"filters\":{\"sero\":\"1\"}}")));
        clock.now=java.time.Instant.parse("2026-10-08T02:00:00Z");
        assertEquals(Arrays.asList("1","4"),ids(success("{\"filters\":{\"sero\":\"1\"}}")));
    }
    @Test void clinicalAgesAreExactAndUnknownNeverUsesLegacyAge() throws Exception {
        clinicalFixture();
        JsonNode data=success("{}");
        JsonNode items=data.path("patients").path("items");
        assertEquals(40,items.get(0).path("clinical").path("age").asInt());
        assertEquals(39,items.get(1).path("clinical").path("age").asInt());
        assertTrue(items.get(2).path("clinical").path("age").isNull());
        assertTrue(items.get(2).path("clinical").path("sex").isNull());
        assertEquals(80,items.get(3).path("clinical").path("age").asInt());
        assertEquals(9,items.get(0).path("clinical").path("diseaseDurationYears").asInt());
        assertEquals(Arrays.asList("1","2"),ids(success("{\"filters\":{\"age\":\"18-40\"}}")));
        clock.now=java.time.Instant.parse("2026-10-07T16:00:00Z");
        assertEquals(40,success("{}").path("patients").path("items").get(1).path("clinical").path("age").asInt());
        assertEquals(10,success("{}").path("patients").path("items").get(0).path("clinical").path("diseaseDurationYears").asInt());
    }
    @Test void femaleFortyToFiftyUsesRealHttpFacts() throws Exception {
        clinicalFixture();
        JsonNode data=success("{\"filters\":{\"sex\":\"F\",\"age\":\"40-50\"}}");
        assertEquals(Arrays.asList("1"),ids(data)); assertEquals(4,data.path("studyTotal").asInt());
    }
}
