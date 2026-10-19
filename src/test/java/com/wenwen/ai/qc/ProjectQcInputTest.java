package com.wenwen.ai.qc;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.mapper.ProjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 项目四M只需历史有效值的存在性，不借用详情的日期/Visit适配。 */
class ProjectQcInputTest {
    private QcSnapshot read(List<Long> ids,List<Map<String,Object>> scores) {
        PatientMapper patients=mock(PatientMapper.class,call -> {
            String name=call.getMethod().getName();
            if("listDas28".equals(name)||"listQcDas28".equals(name)) return scores;
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        ProjectMapper projects=mock(ProjectMapper.class);
        when(projects.qcPatientIds(anyMap())).thenReturn(ids);
        when(projects.remainingQcIssues(anyMap())).thenReturn(Collections.emptyList());
        PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new QcSnapshotReader(patients,projects,transactions).readProject();
    }
    private static Map<String,Object> row(long patient,String raw) {
        Map<String,Object> row=new HashMap<>();row.put("patientId",patient);row.put("visitId",patient);
        row.put("bqpg",raw);return row;
    }
    @Test void projectDoesNotReadOrParseVisitDatesOrRetainVisitObjects() {
        Map<String,Object> row=row(1,"{\"result\":{\"crpScore\":0}}");
        row.put("visitDate","not-a-date");
        QcSnapshot snapshot=read(Collections.singletonList(1L),Collections.singletonList(row));
        assertTrue(snapshot.getVisits().isEmpty());assertTrue(snapshot.getRows().isEmpty());
        assertEquals("COMPLETE",snapshot.getQc().get(1L).getStatus());
    }
    @Test void alreadyValidPatientDoesNotReadAnyLaterAssessmentDocument() {
        Map<String,Object> unread=new HashMap<String,Object>() {
            @Override public Object get(Object key) {
                if("bqpg".equals(key)) fail("已有完整有效分数后不应再读取/解析该患者其余JSON");
                return super.get(key);
            }
        };
        unread.put("patientId",1L);unread.put("visitId",2L);
        QcSnapshot snapshot=read(Collections.singletonList(1L),Arrays.asList(row(1,"{\"result\":{\"crpScore\":0}}"),unread));
        assertEquals("COMPLETE",snapshot.getQc().get(1L).getStatus());
    }
    @Test void everyCandidateDocumentIsStrictlyCompletedBeforeItsScoreIsAccepted() {
        String[] invalid={"{\"result\":{\"crpScore\":3}","{\"result\":{\"crpScore\":3}} {}",
            "{\"result\":{\"crpScore\":3},\"later\":{\"x\":1,\"x\":2}}",
            "{\"result\":{\"crpScore\":3,\"crpScore\":4}}","{\"result\":{\"crpScore\":-0.001}}",
            "{\"result\":{\"esrScore\":0}}","{\"result\":{\"crpScore\":{}}}",null};
        for(String raw:invalid) {
            QcSnapshot rejected=read(Collections.singletonList(1L),Collections.singletonList(row(1,raw)));
            assertEquals(Collections.singletonList("M_DAS28"),rejected.getQc().get(1L).getMissingCodes(),String.valueOf(raw));
            QcSnapshot recovered=read(Collections.singletonList(1L),Arrays.asList(row(1,raw),row(1,"{\"result\":{\"crpScore\":\" 2.295 \"}}")));
            assertEquals("COMPLETE",recovered.getQc().get(1L).getStatus());
        }
    }
}
