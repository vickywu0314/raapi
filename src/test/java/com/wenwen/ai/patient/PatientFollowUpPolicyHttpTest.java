package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.service.PatientService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PatientFollowUpPolicyHttpTest extends TrustedPatientHttpFixture {
    private static final long EVEN=9007199254740992L;
    private JsonNode detail(long id)throws Exception {
        org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/ai/patientDetail").session(session(101)).contentType("application/json").content("{\"patientId\":\""+id+"\"}")).andReturn();
        assertEquals(200,r.getResponse().getStatus());return json.readTree(r.getResponse().getContentAsByteArray()).path("data");
    }
    @Test void otherDoctorWithdrawalDoesNotWithdrawCurrentDoctor()throws Exception {
        seed();sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss,reason) VALUES (202,9007199254740992,0,1,'OTHER-DOCTOR')");
        JsonNode d=detail(EVEN);assertNotEquals("withdrawn",d.path("followStatus").asText());assertFalse(d.toString().contains("OTHER-DOCTOR"));
        PatientService service=context.getBean(PatientService.class);
        assertFalse(service.listPatients(101L,null,"withdrawn",null,1,200).getItems().stream().anyMatch(p->p.getPatientId()==EVEN));
        assertFalse(service.listPatientIds(101L,null,"withdrawn",null).contains(EVEN));
    }
    @Test void ownRaWithdrawalUsesOnlyOwnLatestReason()throws Exception {
        seed();sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss,reason) VALUES (101,9007199254740992,6,1,'NON-RA'),(202,9007199254740992,0,1,'OTHER-DOCTOR')");
        assertNotEquals("withdrawn",detail(EVEN).path("followStatus").asText());
        sql("UPDATE patient_relation_doctor SET miss=1,reason='OWN-DOCTOR',other_miss_reason='OWN-OTHER',note='OWN-NOTE' WHERE doctor_id=101 AND patient_id=9007199254740992 AND research_type=7");
        JsonNode d=detail(EVEN);assertEquals("withdrawn",d.path("followStatus").asText());assertEquals("OWN-DOCTOR；OWN-OTHER；OWN-NOTE",d.path("withdrawReason").asText());
        PatientService service=context.getBean(PatientService.class);assertTrue(service.listPatientIds(101L,null,"withdrawn",null).contains(EVEN));assertEquals("withdrawn",service.listPatients(101L,null,"withdrawn",null,1,200).getItems().get(0).getFollowStatus());
    }
    @Test void totalRecordsAndFollowUpsRemainDistinctAcrossLiveReads()throws Exception {
        seed();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=9007199254740992 AND research_type<>6");
        PatientService service=context.getBean(PatientService.class);
        for(int total=0;total<=3;total++) {
            JsonNode d=detail(EVEN);assertEquals(total,d.path("visitCount").asInt());assertTrue(d.has("followUpCount"));assertEquals(total==0?0:total-1,d.path("followUpCount").asInt());assertEquals(total==0,"pending_first".equals(d.path("followStatus").asText()));
            com.wenwen.vo.PatientItemVo item=service.listPatients(101L,null,null,null,1,200).getItems().stream().filter(p->p.getPatientId()==EVEN).findFirst().get();
            JsonNode listed=json.valueToTree(item);assertEquals(total,listed.path("visitCount").asInt());assertEquals(total==0?0:total-1,listed.path("followUpCount").asInt());
            assertEquals(total==0,service.listPatientIds(101L,null,"pending_first",null).contains(EVEN));assertEquals("9007199254740992",d.path("patientId").textValue());
            if(total<3)sql("INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,doctor_id,follow_up_date) VALUES ("+(9007199254741011L+total)+",9007199254740992,7,101,'2026-09-0"+(total+1)+"')");
        }
    }
    private JsonNode visit(long id)throws Exception {
        org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/ai/visitDetail").session(session(101)).contentType("application/json").content("{\"patientId\":\"9007199254740992\",\"visitId\":\""+id+"\"}")).andReturn();assertEquals(200,r.getResponse().getStatus());return json.readTree(r.getResponse().getContentAsByteArray()).path("data");
    }
    @Test void fullTimestampDeterminesBaselineAndBothReadSidesAgree()throws Exception {
        seed();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=9007199254740992 AND research_type<>6");
        sql("INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,doctor_id,follow_up_date) VALUES (9007199254741012,9007199254740992,0,101,'2026-09-01 08:00:00'),(9007199254741011,9007199254740992,7,101,'2026-09-01 20:00:00')");
        JsonNode d=detail(EVEN);JsonNode first=d.path("visits").get(1);assertEquals("9007199254741012",first.path("visitId").asText());assertTrue(first.path("baseline").asBoolean());
        for(JsonNode v:d.path("visits")){JsonNode vd=visit(Long.parseLong(v.path("visitId").asText()));assertEquals(v.path("baseline"),vd.path("baseline"));assertEquals(v.path("visitType"),vd.path("visitType"));assertTrue(v.has("followUpNumber"));assertEquals(v.path("followUpNumber"),vd.path("followUpNumber"));assertEquals("2026-09-01",v.path("visitDate").asText());}
        assertEquals("基线访视",first.path("visitType").asText());assertEquals(0,first.path("followUpNumber").asInt());assertEquals("第 1 次随访",d.path("visits").get(0).path("visitType").asText());assertEquals(1,d.path("visits").get(0).path("followUpNumber").asInt());
    }
    @Test void equalTimeUndatedAndLegacyColumnKeepIndependentOrder()throws Exception {
        seed();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=9007199254740992 AND research_type<>6");
        sql("INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,doctor_id,follow_up_date,followUpDate) VALUES (9007199254741012,9007199254740992,0,101,'2026-09-01 08:00:00',NULL),(9007199254741011,9007199254740992,7,101,'2026-09-01 08:00:00',NULL),(9007199254741010,9007199254740992,0,101,NULL,'2026-08-31 21:00:00'),(9007199254741009,9007199254740992,0,101,NULL,NULL)");
        long[] ids={9007199254741012L,9007199254741011L,9007199254741010L,9007199254741009L};int[] numbers={2,1,0,3};
        JsonNode d=detail(EVEN);assertEquals(4,d.path("visits").size());
        for(int i=0;i<ids.length;i++){JsonNode v=d.path("visits").get(i),vd=visit(ids[i]);assertEquals(Long.toString(ids[i]),v.path("visitId").asText());assertEquals(numbers[i],v.path("followUpNumber").asInt());assertEquals(v.path("visitType"),vd.path("visitType"));assertEquals(v.path("followUpNumber"),vd.path("followUpNumber"));assertEquals(numbers[i]==0,v.path("baseline").asBoolean());}
        assertEquals("2026-08-31",d.path("visits").get(2).path("visitDate").asText());assertTrue(d.path("visits").get(3).path("visitDate").isNull());
        sql("UPDATE patient_follow_up_history SET follow_up_date=NULL,followUpDate=NULL WHERE patient_basic_info_id=9007199254740992");
        d=detail(EVEN);for(int i=0;i<ids.length;i++){JsonNode v=d.path("visits").get(i);assertEquals(3-i,v.path("followUpNumber").asInt());assertEquals(i==3,v.path("baseline").asBoolean());assertEquals(v.path("visitType"),visit(ids[i]).path("visitType"));}
    }
    private static final class SamplingClock extends java.time.Clock {
        final java.time.Instant now;int samples;
        SamplingClock(String now){this.now=java.time.Instant.parse(now);}
        public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
        public java.time.Clock withZone(java.time.ZoneId z){return this;}
        public java.time.Instant instant(){samples++;return samples==1?now:now.plusSeconds(86400);}
    }
    @Test void oneShanghaiAsOfControlsDueFilteringAndDetailDays()throws Exception {
        seed();sql("UPDATE patient_follow_up_history SET follow_up_date='2026-07-12' WHERE id=9007199254741001");
        PatientService service=context.getBean(PatientService.class);
        String[] instants={"2026-10-11T15:59:59Z","2026-10-11T16:00:00Z","2026-10-12T16:00:00Z","2030-01-01T00:00:00Z"};
        int[] days={1,0,-1,-1177};String[] statuses={"soon","soon","overdue","overdue"};
        for(int i=0;i<instants.length;i++) {
            SamplingClock c=new SamplingClock(instants[i]);org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",c);
            JsonNode d=detail(EVEN);assertEquals(days[i],d.path("nextDueDays").asInt());assertEquals(statuses[i],d.path("followStatus").asText());assertEquals(1,c.samples);
            c=new SamplingClock(instants[i]);org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",c);assertTrue(service.listPatientIds(101L,null,statuses[i],null).contains(EVEN));assertEquals(1,c.samples);
            c=new SamplingClock(instants[i]);org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",c);assertTrue(service.listPatients(101L,null,statuses[i],null,1,200).getItems().stream().anyMatch(p->p.getPatientId()==EVEN));assertEquals(1,c.samples);
        }
    }
    @Test void suspectedLossIsStrictlyBeyondTwoYearsIncludingLeapDay()throws Exception {
        seed();PatientService service=context.getBean(PatientService.class);
        String[] clocks={"2026-10-11T15:59:59Z","2026-10-11T16:00:00Z","2026-10-11T16:00:00Z","2028-02-29T00:00:00Z","2028-02-29T00:00:00Z","2028-02-29T00:00:00Z"};
        String[] last={"2024-10-11","2024-10-11","2024-10-12","2026-02-28","2026-02-27",null};boolean[] lost={false,true,false,false,true,false};
        for(int i=0;i<clocks.length;i++) {
            sql("UPDATE patient_follow_up_history SET follow_up_date="+(last[i]==null?"NULL":"'"+last[i]+"'")+",followUpDate=NULL WHERE id=9007199254741001");
            SamplingClock c=new SamplingClock(clocks[i]);org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",c);JsonNode d=detail(EVEN);assertTrue(d.has("suspectedLostToFollowUp"));assertEquals(lost[i],d.path("suspectedLostToFollowUp").asBoolean());assertEquals(1,c.samples);
            c=new SamplingClock(clocks[i]);org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",c);JsonNode listed=json.valueToTree(service.listPatients(101L,null,null,null,1,200).getItems().stream().filter(p->p.getPatientId()==EVEN).findFirst().get());assertEquals(lost[i],listed.path("suspectedLostToFollowUp").asBoolean());assertEquals(1,c.samples);
        }
        sql("UPDATE patient_follow_up_history SET follow_up_date='2020-01-01' WHERE id=9007199254741001");sql("UPDATE patient_relation_doctor SET miss=1 WHERE doctor_id=101 AND patient_id=9007199254740992 AND research_type=0");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",java.time.Clock.fixed(java.time.Instant.parse("2028-02-29T00:00:00Z"),java.time.ZoneOffset.UTC));JsonNode d=detail(EVEN);assertEquals("withdrawn",d.path("followStatus").asText());assertFalse(d.path("suspectedLostToFollowUp").asBoolean());
    }
    private void assertLostAcrossReads(boolean lost, int count, String status)throws Exception {
        PatientService service=context.getBean(PatientService.class);
        JsonNode trusted=detail(EVEN);
        assertEquals(lost,trusted.path("suspectedLostToFollowUp").asBoolean(),"可信档案");
        assertEquals(count,trusted.path("visitCount").asInt());assertEquals(status,trusted.path("followStatus").asText());
        org.springframework.test.web.servlet.MvcResult legacy=http.perform(post("/api/ra/patient/patientDetail").session(session(101)).param("patientId",Long.toString(EVEN))).andReturn();
        assertEquals(200,legacy.getResponse().getStatus());
        assertEquals(lost,json.readTree(legacy.getResponse().getContentAsByteArray()).path("data").path("suspectedLostToFollowUp").asBoolean(),"普通档案");
        com.wenwen.vo.PatientItemVo listed=service.listPatients(101L,null,null,null,1,200).getItems().stream().filter(p->p.getPatientId()==EVEN).findFirst().get();
        assertEquals(lost,listed.isSuspectedLostToFollowUp(),"分页列表");assertEquals(status,listed.getFollowStatus());
        assertEquals(lost,service.listAllPatients(101L).items.stream().filter(p->p.getPatientId()==EVEN).findFirst().get().isSuspectedLostToFollowUp(),"全量列表");
        assertTrue(service.listPatientIds(101L,null,status,null).contains(EVEN));
    }
    @Test void unvisitedOldCreationIsFlaggedThroughRealMapperAndBothDetailRoutes()throws Exception {
        seed();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=9007199254740992 AND research_type<>6");
        sql("UPDATE patient_basic_info SET create_date='2024-10-11 23:59:59',createDate='2026-10-12' WHERE id=9007199254740992");
        org.springframework.test.util.ReflectionTestUtils.setField(context.getBean(PatientService.class),"clock",java.time.Clock.fixed(java.time.Instant.parse("2026-10-11T16:00:00Z"),java.time.ZoneOffset.UTC));
        assertLostAcrossReads(true,0,"pending_first"); // 仍有非RA记录，不能阻止无RA访视回退。
    }
    @Test void creationFallbackKeepsDatePrecedenceShanghaiLeapAndUnknownBoundaries()throws Exception {
        seed();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=9007199254740992 AND research_type<>6");
        PatientService service=context.getBean(PatientService.class);
        String[] clocks={"2026-10-11T15:59:59Z","2026-10-11T16:00:00Z","2026-10-11T16:00:00Z","2026-10-11T16:00:00Z","2026-10-11T16:00:00Z","2026-10-11T16:00:00Z","2026-10-11T16:00:00Z","2028-02-29T00:00:00Z","2028-02-29T00:00:00Z"};
        String[] created={"2024-10-11","2024-10-11","2024-10-12","2024-10-13","2027-01-01",null,null,"2026-02-28","2026-02-27"};
        String[] camel={null,null,"2020-01-01",null,null,null,"2024-10-11",null,null};
        boolean[] lost={false,true,false,false,false,false,true,false,true};
        for(int i=0;i<clocks.length;i++) {
            sql("UPDATE patient_basic_info SET create_date="+(created[i]==null?"NULL":"'"+created[i]+"'")+",createDate="+(camel[i]==null?"NULL":"'"+camel[i]+"'")+" WHERE id=9007199254740992");
            org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",java.time.Clock.fixed(java.time.Instant.parse(clocks[i]),java.time.ZoneOffset.UTC));
            assertLostAcrossReads(lost[i],0,"pending_first");
        }
        sql("UPDATE patient_basic_info SET create_date='2020-01-01' WHERE id=9007199254740992");
        sql("UPDATE patient_relation_doctor SET miss=1 WHERE doctor_id=101 AND patient_id=9007199254740992 AND research_type=0");
        assertLostAcrossReads(false,0,"withdrawn");
    }
    @Test void oldCreationNeverSubstitutesForAnExistingUndatedOrRecentRaVisit()throws Exception {
        seed();sql("UPDATE patient_basic_info SET create_date='2020-01-01' WHERE id=9007199254740992");
        PatientService service=context.getBean(PatientService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",java.time.Clock.fixed(java.time.Instant.parse("2026-10-12T00:00:00Z"),java.time.ZoneOffset.UTC));
        assertLostAcrossReads(false,1,"active");
        sql("UPDATE patient_follow_up_history SET follow_up_date=NULL,followUpDate=NULL WHERE id=9007199254741001");
        assertLostAcrossReads(false,1,"active");
        sql("UPDATE patient_follow_up_history SET follow_up_date='2024-10-11' WHERE id=9007199254741001");
        assertLostAcrossReads(true,1,"overdue");
    }

}
