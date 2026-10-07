package com.wenwen.ai.cohort;

import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

/** 日期、分数及指标全部是冻结Prompt的独立合成表。 */
abstract class VisitMatchingHttpFixture extends TreatmentHttpFixture {
    void matchingFixture() throws Exception {
        sql("DELETE FROM patient_follow_up_history"); sql("DELETE FROM patient_relation_doctor");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (101,1,0),(101,2,0)");
        clock.now=Instant.parse("2024-03-02T02:00:00Z");
        visit(100,1,0,"{\"result\":{\"crpScore\":3.46}}","2023-08-30",null,null);
        medication(101,1,"2023-08-31",drugs(drug("阿达木单抗","2023-08-31",null)));
        sql("UPDATE patient_follow_up_history SET bqpg='{}' WHERE id=101");
        visit(200,1,0,"{\"result\":{\"crpScore\":2.19},\"hqaScore\":1.25}","2024-02-29",null,"{\"cfydb\":4}");
        visit(201,1,0,"{\"result\":{\"crpScore\":3.90},\"hqaScore\":2.50}","2024-03-01",null,"{\"cfydb\":9}");
        visit(202,1,0,"{}","2024-03-02",null,"{\"cfydb\":12}");
        medication(300,2,"2023-08-31",drugs(drug("阿达木单抗",null,null)));
    }
    JsonNode item(JsonNode data,long id) {
        for(JsonNode value:data.path("patients").path("items")) if(value.path("patientId").asText().equals(Long.toString(id))) return value;
        fail("未找到患者 "+id); return null;
    }
    com.wenwen.service.impl.PatientServiceImpl legacyService() {
        com.wenwen.mapper.PatientMapper mapper=org.mockito.Mockito.mock(com.wenwen.mapper.PatientMapper.class);
        com.wenwen.mapper.PatientMapper real=new org.mybatis.spring.SqlSessionTemplate(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)).getMapper(com.wenwen.mapper.PatientMapper.class);
        org.mockito.Mockito.when(mapper.listDas28(org.mockito.ArgumentMatchers.anyList())).thenAnswer(call -> real.listDas28(call.getArgument(0)));
        org.mockito.Mockito.when(mapper.listPatients(org.mockito.ArgumentMatchers.anyMap())).thenAnswer(call -> {
            java.util.Map<String,Object> params=call.getArgument(0); Object id=params.get("patientId");
            return id==null?java.util.Arrays.asList(com.wenwen.vo.AiCohortVo.object("patientId",1L),com.wenwen.vo.AiCohortVo.object("patientId",2L))
                :java.util.Collections.singletonList(com.wenwen.vo.AiCohortVo.object("patientId",id));
        });
        org.mockito.Mockito.when(mapper.getPatientBasic(org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.Collections.emptyMap());
        com.wenwen.service.impl.PatientServiceImpl service=new com.wenwen.service.impl.PatientServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"patientMapper",mapper);
        com.wenwen.service.impl.LegacyQcTestSupport.wire(service,mapper,java.util.Arrays.asList(1L,2L));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",clock);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"rfUln",20.0); org.springframework.test.util.ReflectionTestUtils.setField(service,"ccpUln",25.0);
        return service;
    }
    void replaceMedication(long id,String source) throws Exception {
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE id=?")) {
            s.setString(1,source);s.setLong(2,id);s.executeUpdate();
        }
    }
}
