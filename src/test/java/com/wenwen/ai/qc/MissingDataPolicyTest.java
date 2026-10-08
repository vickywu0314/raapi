package com.wenwen.ai.qc;

import com.wenwen.ai.clinical.VisitMatcher;
import com.wenwen.ai.source.SourceBatch;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.wenwen.vo.AiCohortVo.object;
import static org.junit.jupiter.api.Assertions.*;

class MissingDataPolicyTest {
    @Test void independentExistenceTableAndSourceResultsAreImmutableAndFailClosed() {
        List<Long> ids=new ArrayList<>(Arrays.asList(1L,2L,3L));
        List<VisitMatcher.Visit> visits=Arrays.asList(new VisitMatcher.Visit(1,1,LocalDate.of(2099,1,1),"0"),new VisitMatcher.Visit(2,2,LocalDate.of(2024,1,1),"-0.001"),new VisitMatcher.Visit(3,3,null,"2.295"));
        List<Map<String,Object>> issues=new ArrayList<>(Arrays.asList(object("patient_id",2L,"rule_code","M_MEDICATION"),object("patient_id",2L,"rule_code","M_BASELINE_LAB"),object("patient_id",3L,"rule_code","M_COMORBIDITY"),object("patient_id",1L,"rule_code","L_DATE_ORDER")));
        Map<Long,MissingDataStatus> result=MissingDataPolicy.evaluate(ids,visits,issues);issues.clear();
        assertEquals("COMPLETE",result.get(1L).getStatus());assertTrue(result.get(1L).matches("complete"));assertFalse(result.get(1L).matches("missing"));
        assertEquals(Arrays.asList("M_BASELINE_LAB","M_DAS28","M_MEDICATION"),result.get(2L).getMissingCodes());assertEquals(Collections.singletonList("M_COMORBIDITY"),result.get(3L).getMissingCodes());
        assertThrows(UnsupportedOperationException.class,()->result.clear());assertThrows(UnsupportedOperationException.class,()->result.get(2L).getMissingCodes().clear());assertThrows(UnsupportedOperationException.class,()->result.get(2L).asMap().clear());
        Map<Long,MissingDataStatus> supplied=new LinkedHashMap<>(result);
        SourceBatch batch=new SourceBatch(ids,Collections.emptyList(),Instant.EPOCH,Collections.emptyMap(),supplied,Collections.emptyMap());supplied.clear();ids.clear();
        assertEquals(3,batch.getQc().size());assertEquals(3,batch.getPatientIds().size());assertThrows(UnsupportedOperationException.class,()->batch.getQc().clear());
        assertThrows(RuntimeException.class,()->MissingDataPolicy.evaluate(Arrays.asList(1L),visits,null));
    }
}
