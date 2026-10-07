package com.wenwen.service.impl;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.vo.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PatientVisitMatchingTest {
    Map<String,Object> row(Object... fields) { Map<String,Object> r=new HashMap<>(); for(int i=0;i<fields.length;i+=2) r.put((String)fields[i],fields[i+1]); return r; }
    PatientServiceImpl service(Clock clock) {
        PatientMapper mapper=mock(PatientMapper.class);
        when(mapper.listPatients(anyMap())).thenReturn(Collections.singletonList(row("patientId",1L)));
        when(mapper.getPatientBasic(anyMap())).thenReturn(Collections.emptyMap());
        when(mapper.listDas28(anyList())).thenReturn(Arrays.asList(
            row("patientId",1L,"visitId",99L,"visitDate","2025-01-02","bqpg",LegacyQcTestSupport.assessment("9")),
            row("patientId",1L,"visitId",20L,"visitDate","2025-01-01","bqpg",LegacyQcTestSupport.assessment("2")),
            row("patientId",1L,"visitId",100L,"visitDate",null,"bqpg",LegacyQcTestSupport.assessment("8")),
            row("patientId",1L,"visitId",21L,"visitDate","2025-01-01","bqpg",LegacyQcTestSupport.assessment("3")),
            row("patientId",1L,"visitId",22L,"visitDate","2025-01-01","bqpg",LegacyQcTestSupport.assessment("-0.001")),
            row("patientId",2L,"visitId",999L,"visitDate","2025-01-01","bqpg",LegacyQcTestSupport.assessment("7"))));
        PatientServiceImpl service=new PatientServiceImpl(); ReflectionTestUtils.setField(service,"patientMapper",mapper);
        LegacyQcTestSupport.wire(service,mapper,Collections.singletonList(1L));
        ReflectionTestUtils.setField(service,"rfUln",20.0); ReflectionTestUtils.setField(service,"ccpUln",25.0); ReflectionTestUtils.setField(service,"clock",clock);
        return service;
    }
    @Test void bothPublicConsumersUseShanghaiDayAndIgnoreMapperOrderFutureAndUndated() {
        Clock clock=Clock.fixed(Instant.parse("2025-01-01T15:59:59Z"),ZoneOffset.UTC);
        PatientItemVo list=service(clock).listPatients(101L,null,null,null,1,20).getItems().get(0);
        PatientDetailVo detail=service(clock).getPatientDetail(101L,1L);
        assertEquals(new BigDecimal("3.00"),list.getLatestDas28()); assertEquals(new BigDecimal("3.00"),detail.getLatestDas28());
        assertEquals("moderate",list.getDas28Activity()); assertEquals("moderate",detail.getDas28Activity());
        Clock next=Clock.fixed(Instant.parse("2025-01-01T16:00:00Z"),ZoneOffset.UTC);
        assertEquals(new BigDecimal("9.00"),service(next).listPatients(101L,null,null,null,1,20).getItems().get(0).getLatestDas28());
        assertEquals(new BigDecimal("9.00"),service(next).getPatientDetail(101L,1L).getLatestDas28());
    }
}
