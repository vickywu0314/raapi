package com.wenwen.service.impl;

import com.wenwen.mapper.QualityMapper;
import com.wenwen.service.PatientService;
import com.wenwen.vo.PatientItemVo;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityQueryCostTest {
    @Test void sameDayIssuesKeepPatientTargetWithOnlyOneDateReadPerVisit() {
        PatientItemVo patient=new PatientItemVo();patient.setPatientId(1L);patient.setVisitCount(4);
        patient.setMissingItems(Collections.emptyList());
        AtomicInteger dateReads=new AtomicInteger();List<Map<String,Object>> visits=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Map<String,Object> row=new HashMap<String,Object>() {
                @Override public Object get(Object key) {if("visitDate".equals(key))dateReads.incrementAndGet();return super.get(key);}
            };
            row.put("patientId",1L);row.put("visitId",(long)i+1);row.put("visitDate",i<2?"2020-01-01":"2020-01-02");visits.add(row);
        }
        QualityMapper mapper=mock(QualityMapper.class);when(mapper.listVisits(101L)).thenReturn(visits);
        when(mapper.listPatientFacts(101L)).thenReturn(Collections.emptyList());when(mapper.listResolutions(101L)).thenReturn(Collections.emptyList());
        QualityServiceImpl service=new QualityServiceImpl();ReflectionTestUtils.setField(service,"mapper",mapper);
        PatientService.AllPatients all=new PatientService.AllPatients(Collections.singletonList(patient),Collections.emptyMap());
        Map<String,Object> result=service.issues(101L,all);
        assertEquals(2,result.get("issueCount"));
        for(Object value:(List<?>)result.get("issues")) {
            Map<?,?> issue=(Map<?,?>)value;assertTrue(issue.get("text").toString().contains("同一天有 2 条随访"));
            Map<?,?> target=(Map<?,?>)issue.get("target");assertEquals("patient",target.get("page"));assertNull(target.get("visitId"));
        }
        assertEquals(4,dateReads.get(),"每个日期只应在计数主遍历中读取一次");
    }
}
