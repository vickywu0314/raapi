package com.wenwen.service.impl;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.vo.PatientItemVo;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 无外部数据库；经生产患者读服务验证疑似失访，不复制政策计算。 */
class PatientFollowUpPolicyTest {
    private static final String AS_OF = "2026-10-12T00:00:00Z";

    private PatientServiceImpl service(int count, String last, String created, String status, String instant) {
        Map<String,Object> row = new HashMap<>();
        row.put("patientId",1L);
        row.put("visitCount",count);
        row.put("lastVisitDate",last);
        row.put("createDate",created);
        row.put("createYear",2020); // 年份不得代替实际建档日期。
        row.put("followStatus",status);
        PatientMapper mapper = mock(PatientMapper.class);
        when(mapper.listPatients(anyMap())).thenReturn(Collections.singletonList(row));
        when(mapper.getPatientBasic(anyMap())).thenReturn(Collections.emptyMap());
        PatientServiceImpl service = new PatientServiceImpl();
        ReflectionTestUtils.setField(service,"patientMapper",mapper);
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(Instant.parse(instant),ZoneOffset.UTC));
        LegacyQcTestSupport.wire(service,mapper,Collections.singletonList(1L));
        return service;
    }

    private void assertReads(boolean expected, int count, String last, String created, String status, String instant) {
        PatientServiceImpl service = service(count,last,created,status,instant);
        PatientItemVo listed = service.listPatients(101L,null,null,null,1,20).getItems().get(0);
        assertEquals(expected,listed.isSuspectedLostToFollowUp(),"分页列表");
        assertEquals(status,listed.getFollowStatus());
        assertEquals(count,listed.getVisitCount());
        assertEquals(expected,service.listAllPatients(101L).items.get(0).isSuspectedLostToFollowUp(),"全量列表");
        assertEquals(expected,service.getPatientDetail(101L,1L).isSuspectedLostToFollowUp(),"档案详情");
        assertEquals(Collections.singletonList(1L),service.listPatientIds(101L,null,null,null));
    }

    @Test void unvisitedPatientCreatedMoreThanTwoYearsAgoIsFlagged() {
        assertReads(true,0,null,"2024-10-11","pending_first",AS_OF);
    }

    @Test void unvisitedCreationDateKeepsStrictBoundaryAndUnknowns() {
        for(String created:Arrays.asList("2024-10-12","2024-10-13","2026-10-12","2027-01-01",null)) {
            assertReads(false,0,null,created,"pending_first",AS_OF);
        }
        assertReads(false,0,null,"2020-01-01","withdrawn",AS_OF);
    }

    @Test void recordedVisitsNeverFallBackToOldCreationDate() {
        assertReads(false,1,"2026-09-01","2020-01-01","active",AS_OF);
        assertReads(false,1,null,"2020-01-01","active",AS_OF);
        assertReads(false,2,"2024-10-12","2020-01-01","overdue",AS_OF);
        assertReads(false,1,"2027-01-01","2020-01-01","active",AS_OF);
        assertReads(true,1,"2024-10-11","2026-01-01","overdue",AS_OF);
        assertReads(false,1,"2020-01-01",null,"withdrawn",AS_OF);
    }

    @Test void creationFallbackUsesShanghaiDayAndCalendarLeapBoundary() {
        assertReads(false,0,null,"2024-10-11","pending_first","2026-10-11T15:59:59Z");
        assertReads(true,0,null,"2024-10-11","pending_first","2026-10-11T16:00:00Z");
        assertReads(false,0,null,"2026-02-28","pending_first","2028-02-29T00:00:00Z");
        assertReads(true,0,null,"2026-02-27","pending_first","2028-02-29T00:00:00Z");
    }
}
