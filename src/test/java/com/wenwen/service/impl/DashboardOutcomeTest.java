package com.wenwen.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.wenwen.mapper.DashboardMapper;
import com.wenwen.mapper.PatientMapper;
import com.wenwen.service.FollowupService;
import com.wenwen.service.PatientService;
import com.wenwen.service.QualityService;
import com.wenwen.vo.PatientItemVo;

/** 看板 outcome：首次 vs 最近 DAS28-CRP（±0.6），只算有 ≥2 次有效评分的患者；与患者列表 trend 同一计算。 */
class DashboardOutcomeTest {
    private static long nextId = 1000;

    private static Map<String, Object> score(long patient, String date, Object crp) {
        Map<String, Object> row = new HashMap<>();
        row.put("patientId", patient); row.put("visitId", nextId++); row.put("visitDate", date);
        row.put("bqpg", LegacyQcTestSupport.assessment(crp));
        return row;
    }

    @Test void outcomeCountsFirstVersusLatest() {
        PatientMapper mapper = mock(PatientMapper.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (long id = 1; id <= 6; id++) { Map<String, Object> r = new HashMap<>(); r.put("patientId", id); rows.add(r); }
        when(mapper.listPatients(anyMap())).thenReturn(rows);
        when(mapper.listComorbidities(anyList())).thenReturn(Collections.emptyList());
        when(mapper.listSerology(anyList())).thenReturn(Collections.emptyList());
        when(mapper.listDas28(anyList())).thenReturn(Arrays.asList(
            // 1：5.0 → 4.0 → 4.4：首次比最近 −0.6 → better（中间那次不参与）
            score(1, "2024-01-01", 5.0), score(1, "2024-06-01", 4.0), score(1, "2025-01-01", 4.4),
            // 2：3.0 → 3.59 → stable；3：3.0 → 3.6 → worse（边界含 0.6）
            score(2, "2024-01-01", 3.0), score(2, "2025-01-01", 3.59),
            score(3, "2024-01-01", 3.0), score(3, "2025-01-01", "3.6"),
            // 4：只有一次有效（另一次未查、一次在未来）→ 不计入
            score(4, "2024-01-01", 2.0), score(4, "2024-02-01", "未查"), score(4, "2099-01-01", 9.0),
            // 5：同一天两次，按 visitId 先后：2.0 → 1.2 → better
            score(5, "2024-03-01", 2.0), score(5, "2024-03-01", 1.2)));
        PatientServiceImpl patients = new PatientServiceImpl();
        ReflectionTestUtils.setField(patients, "patientMapper", mapper);
        LegacyQcTestSupport.wire(patients, mapper, Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L));
        ReflectionTestUtils.setField(patients, "rfUln", 20.0);
        ReflectionTestUtils.setField(patients, "ccpUln", 25.0);
        ReflectionTestUtils.setField(patients, "clock", java.time.Clock.fixed(java.time.Instant.parse("2026-01-01T02:00:00Z"), java.time.ZoneOffset.UTC));

        PatientService.AllPatients all = patients.listAllPatients(101L);
        Map<Long, PatientItemVo> byId = new HashMap<>();
        for (PatientItemVo p : all.items) byId.put(p.getPatientId(), p);
        assertEquals("better", byId.get(1L).getTrend());
        assertEquals(new BigDecimal("5.00"), byId.get(1L).getDas28First());
        assertEquals(new BigDecimal("4.00"), byId.get(1L).getDas28Prev());
        assertEquals(new BigDecimal("4.00"), all.previousDas28.get(1L), "看板上一次 DAS28 口径不变");
        assertEquals("stable", byId.get(2L).getTrend());
        assertEquals("worse", byId.get(3L).getTrend());
        assertNull(byId.get(4L).getTrend());
        assertNull(all.previousDas28.get(4L));
        assertEquals("better", byId.get(5L).getTrend());
        assertNull(byId.get(6L).getTrend());

        DashboardServiceImpl dashboard = new DashboardServiceImpl();
        PatientService ps = mock(PatientService.class);
        when(ps.listAllPatients(101L)).thenReturn(all);
        FollowupService followups = mock(FollowupService.class);
        when(followups.listPlans(101L)).thenReturn(Collections.emptyList());
        DashboardMapper dm = mock(DashboardMapper.class);
        when(dm.listCreateDates(101L)).thenReturn(Collections.emptyList());
        when(dm.listCrpVisits(101L)).thenReturn(Collections.emptyList());
        when(dm.listRecentVisits(eq(101L), anyInt())).thenReturn(Collections.emptyList());
        QualityService quality = mock(QualityService.class);
        when(quality.issues(eq(101L), any())).thenReturn(Collections.singletonMap("researchReady", 0));
        ReflectionTestUtils.setField(dashboard, "patientService", ps);
        ReflectionTestUtils.setField(dashboard, "followupService", followups);
        ReflectionTestUtils.setField(dashboard, "mapper", dm);
        ReflectionTestUtils.setField(dashboard, "qualityService", quality);
        @SuppressWarnings("unchecked")
        Map<String, Object> outcome = (Map<String, Object>) dashboard.summary(101L).get("outcome");
        assertEquals(4, outcome.get("n"));
        assertEquals(2, outcome.get("better"));
        assertEquals(1, outcome.get("stable"));
        assertEquals(1, outcome.get("worse"));
        assertEquals(Arrays.asList("n", "better", "stable", "worse"), new ArrayList<>(outcome.keySet()));
    }
}
