package com.wenwen.service.impl;

import com.wenwen.ai.qc.QcSnapshotReader;
import com.wenwen.mapper.PatientMapper;
import com.wenwen.mapper.ProjectMapper;
import com.wenwen.vo.ProjectsDataVo;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectStatisticsQueryCostTest {
    private static Map<String,Object> map(Object... pairs) {
        Map<String,Object> map=new HashMap<>();for(int i=0;i<pairs.length;i+=2)map.put((String)pairs[i],pairs[i+1]);return map;
    }
    @Test void projectPreservesAllCountersWithOneStatisticsRoundTrip() {
        AtomicInteger statistics=new AtomicInteger();
        ProjectMapper mapper=mock(ProjectMapper.class,call -> {
            String method=call.getMethod().getName();
            if("qcPatientIds".equals(method))return Arrays.asList(1L,2L);
            if("remainingQcIssues".equals(method))return Arrays.asList(map("patient_id",1L,"rule_code","L_DATE_ORDER"),map("patient_id",2L,"rule_code","M_MEDICATION"));
            if(method.startsWith("count"))statistics.incrementAndGet();
            if("countProjectMetrics".equals(method))return map("activeCount",1,"dueCount",1,"doneCount",1,"pendingCount",0,"usableCount",2);
            if("countFollowUpPlan".equals(method))return map("dueCount",1,"doneCount",1);
            if("countActivePatients".equals(method))return 1;
            if("countPendingFollowUp".equals(method))return 0;
            if("countUsableRecords".equals(method))return 2;
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        PatientMapper patients=mock(PatientMapper.class,call -> {
            if(call.getMethod().getName().startsWith("list")&&call.getMethod().getName().contains("Das28"))
                return Arrays.asList(map("patientId",1L,"bqpg","{\"result\":{\"crpScore\":0}}"),map("patientId",2L,"bqpg","{\"result\":{\"crpScore\":0}}"));
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ProjectServiceImpl service=new ProjectServiceImpl();ReflectionTestUtils.setField(service,"projectMapper",mapper);
        ReflectionTestUtils.setField(service,"qcReader",new QcSnapshotReader(patients,mapper,transactions));ReflectionTestUtils.setField(service,"followUpCycleDays",90);
        ProjectsDataVo value=service.getProjectsData();
        assertEquals(2,value.getEnrolledPatients());assertEquals(2,value.getTotalPatients());assertEquals(1,value.getCenterCount());
        assertEquals(1,value.getTrackingPatients());assertEquals(new BigDecimal("50.0"),value.getTrackingRate());
        assertEquals(1,value.getFollowUpDueCount());assertEquals(1,value.getFollowUpDoneCount());assertEquals(new BigDecimal("100.0"),value.getFollowUpCompletionRate());
        assertEquals(0,value.getPendingFollowUpPatients());assertEquals(2,value.getResearchUsableRecords());
        assertEquals(2,value.getPendingQcIssues());assertEquals(2,value.getQcIssuePatients());assertEquals(new BigDecimal("0.0"),value.getDataQualityRate());
        assertEquals(1,statistics.get(),"患者统计与全RA可用记录只需一次数据库往返");
    }
}
