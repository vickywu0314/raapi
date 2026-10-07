package com.wenwen.service.impl;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.vo.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PatientClinicalPolicyTest {
    static Map<String,Object> row(Object... fields) {
        Map<String,Object> row=new HashMap<>(); for(int i=0;i<fields.length;i+=2) row.put((String)fields[i],fields[i+1]); return row;
    }
    PatientServiceImpl service(String card, int legacyAge) {
        PatientMapper mapper=mock(PatientMapper.class);
        when(mapper.listPatients(anyMap())).thenReturn(Collections.singletonList(row("patientId",1L,"cardNo",card,"age",legacyAge,"createYear",2010)));
        when(mapper.getPatientBasic(anyMap())).thenReturn(Collections.emptyMap());
        when(mapper.listSerology(anyList())).thenReturn(Arrays.asList(row("patientId",1L,"visitDate","2026-10-08","fzjc","{\"lfsyz\":\"960\"}"),row("patientId",1L,"visitDate",null,"fzjc","{\"lfsyz\":\"960\"}"),row("patientId",1L,"visitDate","2026-10-07","fzjc","{\"lfsyz\":\"10\"}"),row("patientId",1L,"visitDate","2026-10-01","fzjc","{\"lfsyz\":\"35\"}")));
        when(mapper.listComorbidities(anyList())).thenReturn(Arrays.asList(row("patientId",1L,"code","FM","sinceYear",null),row("patientId",1L,"code","AS","sinceYear",2027)));
        PatientServiceImpl service=new PatientServiceImpl(); ReflectionTestUtils.setField(service,"patientMapper",mapper);
        LegacyQcTestSupport.wire(service,mapper,Collections.singletonList(1L));
        ReflectionTestUtils.setField(service,"rfUln",20.0); ReflectionTestUtils.setField(service,"ccpUln",25.0);
        return service;
    }
    @Test void publicComorbidityStatesRetainRecordsButDoNotInferAbsentNegative() {
        PatientServiceImpl service=service("invalid",55);
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(Instant.parse("2026-10-07T02:00:00Z"),ZoneOffset.UTC));
        com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
        for(Object value:Arrays.asList(service.listPatients(101L,null,null,null,1,20).getItems().get(0),service.getPatientDetail(101L,1L))) {
            com.fasterxml.jackson.databind.JsonNode item=json.valueToTree(value);
            assertEquals("TRUE",item.path("fmState").asText()); assertEquals("UNKNOWN",item.path("asState").asText());
            assertEquals(2,item.path("comorbidities").size());
        }
    }
    @Test void publicConsumersUseEverPositiveButLatestDatedTestCanBeNegative() {
        PatientServiceImpl service=service("110101198610070011",55);
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(Instant.parse("2026-10-07T02:00:00Z"),ZoneOffset.UTC));
        PatientItemVo list=service.listPatients(101L,null,null,null,1,20).getItems().get(0);
        PatientDetailVo detail=service.getPatientDetail(101L,1L);
        assertEquals("血清阳性",list.getSubtype()); assertEquals("negative",list.getRf().getStatus());
        assertEquals("血清阳性",detail.getSubtype()); assertEquals("negative",detail.getRf().getStatus());
        assertEquals(40,list.getAge()); assertEquals(1986,list.getBirthYear()); assertEquals(40,detail.getAge());
    }
    @Test void publicListAndDetailDoNotEstimateMissingBirthday() {
        PatientServiceImpl service=service("invalid",55);
        PatientItemVo list=service.listPatients(101L,null,null,null,1,20).getItems().get(0);
        PatientDetailVo detail=service.getPatientDetail(101L,1L);
        assertNull(list.getAge()); assertNull(list.getBirthYear()); assertNull(detail.getAge()); assertNull(detail.getBirthYear());
    }
}
