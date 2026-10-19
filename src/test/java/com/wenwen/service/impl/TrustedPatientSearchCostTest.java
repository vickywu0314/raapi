package com.wenwen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.query.PatientSearchQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.mapper.PatientMapper;
import com.wenwen.service.PatientService;
import com.wenwen.service.VisitService;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrustedPatientSearchCostTest {
    @Test void searchUsesOnlyNarrowDoctorScopedCountAndBoundedProjection() throws Exception {
        AtomicInteger queries=new AtomicInteger();List<Map<String,Object>> scopes=new ArrayList<>();
        PatientMapper mapper=mock(PatientMapper.class,call -> {
            String name=call.getMethod().getName();
            if("countSearchPatients".equals(name)||"searchPatients".equals(name)) {
                queries.incrementAndGet();scopes.add(new HashMap<>(call.getArgument(0)));
                if("countSearchPatients".equals(name))return 3;
                Map<String,Object> item=new HashMap<>();item.put("patientId",9007199254740993L);item.put("name","synthetic");item.put("studyNo",null);
                return Collections.singletonList(item);
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        PatientService patients=mock(PatientService.class);
        when(patients.listPatients(anyLong(),anyString(),isNull(),isNull(),anyInt(),anyInt())).thenAnswer(call -> {throw new AssertionError("三字段搜索不应调用全量临床/QC列表");});
        PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        try(AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("patients",patients);context.getBeanFactory().registerSingleton("visits",mock(VisitService.class));
            context.getBeanFactory().registerSingleton("mapper",mapper);context.getBeanFactory().registerSingleton("transactions",transactions);
            context.register(TrustedPatientServiceImpl.class);context.refresh();
            TrustedPatientServiceImpl service=context.getBean(TrustedPatientServiceImpl.class);
            PatientSearchQuery query=PatientSearchQuery.read(new ByteArrayInputStream("{\"keyword\":\" %_\\\\ \",\"page\":2,\"size\":1}".getBytes("UTF-8")));
            JsonNode result=service.search(new TrustedDoctor(101),query);
            assertEquals(3,result.path("total").intValue());assertEquals(2,result.path("page").intValue());assertEquals(1,result.path("size").intValue());
            JsonNode item=result.path("items").get(0);assertEquals(3,item.size());assertEquals("9007199254740993",item.path("patientId").textValue());assertTrue(item.path("studyNo").isNull());
            assertEquals(2,queries.get());
            for(Map<String,Object> scope:scopes){assertEquals(101L,scope.get("doctorId"));assertEquals("!%!_\\",scope.get("keyword"));assertEquals("!",scope.get("keywordEscape"));}
            assertEquals(1L,scopes.get(1).get("offset"));assertEquals(1,scopes.get(1).get("size"));
            verifyNoMoreInteractions(patients);
        }
    }
}
