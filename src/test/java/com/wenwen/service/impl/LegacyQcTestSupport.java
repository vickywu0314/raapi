package com.wenwen.service.impl;

import com.wenwen.mapper.*;
import com.wenwen.ai.qc.QcSnapshotReader;
import java.util.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 旧值政策单测仅stub外部范围/三M事实与事务边界；QC及raw政策都用生产实现。 */
public final class LegacyQcTestSupport {
    private LegacyQcTestSupport() { }
    public static void wire(PatientServiceImpl service,PatientMapper mapper,List<Long> ids) {
        ProjectMapper projects=mock(ProjectMapper.class);
        when(projects.qcPatientIds(anyMap())).thenAnswer(call -> {
            Map<String,Object> scope=call.getArgument(0);Object id=scope.get("patientId");
            return id==null?new ArrayList<>(ids):ids.contains(id)?Collections.singletonList((Long)id):Collections.emptyList();
        });
        when(projects.remainingQcIssues(anyMap())).thenReturn(Collections.emptyList());
        PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        ReflectionTestUtils.setField(service,"qcReader",new QcSnapshotReader(mapper,projects,transactions));
    }
    public static Object assessment(Object value) {
        if(value!=null && !(value instanceof String) && !(value instanceof Number)) return value;
        try {return "{\"result\":{\"crpScore\":"+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value)+"}}";}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new AssertionError("合成raw构造失败",e);}
    }
}
