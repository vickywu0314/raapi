package com.wenwen.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.vo.PatientItemVo;
import com.wenwen.vo.PatientDetailVo;

public class PatientDas28PolicyTest {
    private static final long PATIENT = 101L;

    private static Map<String, Object> score(Object value) {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("patientId", PATIENT);
        row.put("das28", value);
        return row;
    }

    private static PatientServiceImpl service(PatientMapper mapper) {
        Map<String, Object> patient = new HashMap<String, Object>();
        patient.put("patientId", PATIENT);
        when(mapper.countSummary(anyMap())).thenReturn(Collections.<String, Object>emptyMap());
        when(mapper.countPatients(anyMap())).thenReturn(1);
        when(mapper.listPatients(anyMap())).thenReturn(Collections.singletonList(patient));
        when(mapper.getPatientBasic(anyMap())).thenReturn(Collections.<String, Object>emptyMap());
        when(mapper.listComorbidities(anyList())).thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.listSerology(anyList())).thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.listVisits(anyMap())).thenReturn(Collections.<Map<String, Object>>emptyList());
        PatientServiceImpl service = new PatientServiceImpl();
        ReflectionTestUtils.setField(service, "patientMapper", mapper);
        ReflectionTestUtils.setField(service, "rfUln", 20.0);
        ReflectionTestUtils.setField(service, "ccpUln", 25.0);
        return service;
    }

    @Test
    public void listRejectsNegativeBeforeRoundingAndFallsBack() {
        PatientMapper mapper = mock(PatientMapper.class);
        when(mapper.listDas28(anyList())).thenReturn(Arrays.asList(
                score("-0.001"), score("未查"), score("2.705"), score("4.105")));
        PatientItemVo result = service(mapper).listPatients(7L, null, null, null, 1, 20).getItems().get(0);
        assertEquals(new BigDecimal("2.71"), result.getLatestDas28());
        assertEquals("moderate", result.getDas28Activity());
        assertEquals("中疾病活动度", result.getDas28ActivityLabel());
        verify(mapper).listDas28(Collections.singletonList(PATIENT));
    }

    @Test
    public void publicConsumersShareIndependentCanonicalTable() {
        String[][] cases = {
            {"0", "0.00", "remission", "临床缓解"},
            {"2.294", "2.29", "remission", "临床缓解"},
            {"2.295", "2.30", "low", "低疾病活动度"},
            {"2.3", "2.30", "low", "低疾病活动度"},
            {"2.7", "2.70", "low", "低疾病活动度"},
            {"2.704", "2.70", "low", "低疾病活动度"},
            {"2.705", "2.71", "moderate", "中疾病活动度"},
            {"4.1", "4.10", "moderate", "中疾病活动度"},
            {"4.104", "4.10", "moderate", "中疾病活动度"},
            {"4.105", "4.11", "high", "高疾病活动度"},
            {"123456.789", "123456.79", "high", "高疾病活动度"}
        };
        for (String[] value : cases) {
            // 两入口各有独立 Mapper/患者/序列，既不共享 VO，也不 mock 值政策。
            PatientMapper listMapper = mock(PatientMapper.class);
            when(listMapper.listDas28(anyList())).thenReturn(Arrays.asList(
                    score("-0.001"), score("未查"), score(new BigDecimal(value[0])), score("9")));
            PatientItemVo list = service(listMapper).listPatients(7L, null, null, null, 1, 20).getItems().get(0);
            PatientMapper detailMapper = mock(PatientMapper.class);
            when(detailMapper.listDas28(anyList())).thenReturn(Arrays.asList(
                    score("-0.001"), score("未查"), score(value[0]), score("9")));
            PatientDetailVo detail = service(detailMapper).getPatientDetail(7L, PATIENT);
            assertEquals(new BigDecimal(value[1]), list.getLatestDas28(), value[0]);
            assertEquals(value[2], list.getDas28Activity(), value[0]);
            assertEquals(value[3], list.getDas28ActivityLabel(), value[0]);
            assertEquals(new BigDecimal(value[1]), detail.getLatestDas28(), value[0]);
            assertEquals(value[2], detail.getDas28Activity(), value[0]);
            assertEquals(value[3], detail.getDas28ActivityLabel(), value[0]);
            verify(listMapper).listDas28(Collections.singletonList(PATIENT));
            verify(detailMapper).listDas28(Collections.singletonList(PATIENT));
        }
    }

    @Test
    public void bothConsumersReturnNullWhenNoCrpIsEvaluable() {
        List<Map<String, Object>> invalid = new ArrayList<Map<String, Object>>();
        for (Object raw : new Object[] {null, "", " ", "未查", "NaN", "Infinity", "-1", "-0.001", "{}", "[]"}) {
            invalid.add(score(raw));
        }
        Map<String, Object> esrOnly = score(null);
        esrOnly.put("esrScore", "5.1");
        invalid.add(esrOnly);
        PatientMapper listMapper = mock(PatientMapper.class);
        when(listMapper.listDas28(anyList())).thenReturn(invalid);
        PatientItemVo list = service(listMapper).listPatients(7L, null, null, null, 1, 20).getItems().get(0);
        PatientMapper detailMapper = mock(PatientMapper.class);
        when(detailMapper.listDas28(anyList())).thenReturn(new ArrayList<Map<String, Object>>(invalid));
        PatientDetailVo detail = service(detailMapper).getPatientDetail(7L, PATIENT);
        assertNull(list.getLatestDas28());
        assertNull(list.getDas28Activity());
        assertNull(list.getDas28ActivityLabel());
        assertNull(detail.getLatestDas28());
        assertNull(detail.getDas28Activity());
        assertNull(detail.getDas28ActivityLabel());
    }

    @Test
    public void databaseFailureStillFailsBothPublicCalls() {
        RuntimeException databaseFailure = new org.springframework.dao.DataAccessResourceFailureException("synthetic DB failure");
        PatientMapper listMapper = mock(PatientMapper.class);
        when(listMapper.listDas28(anyList())).thenThrow(databaseFailure);
        PatientServiceImpl listService = service(listMapper);
        assertSame(databaseFailure, assertThrows(RuntimeException.class,
                () -> listService.listPatients(7L, null, null, null, 1, 20)));
        PatientMapper detailMapper = mock(PatientMapper.class);
        when(detailMapper.listDas28(anyList())).thenThrow(databaseFailure);
        PatientServiceImpl detailService = service(detailMapper);
        assertSame(databaseFailure, assertThrows(RuntimeException.class,
                () -> detailService.getPatientDetail(7L, PATIENT)));
    }

    @Test
    public void programmingFailureIsNotSilentlyTreatedAsMissing() {
        final IllegalStateException failure = new IllegalStateException("synthetic programming failure");
        Object raw = new Object() {
            @Override public String toString() { throw failure; }
        };
        PatientMapper mapper = mock(PatientMapper.class);
        when(mapper.listDas28(anyList())).thenReturn(Arrays.asList(score(raw), score("2.705")));
        PatientServiceImpl instance = service(mapper);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> instance.listPatients(7L, null, null, null, 1, 20)));
    }
}
