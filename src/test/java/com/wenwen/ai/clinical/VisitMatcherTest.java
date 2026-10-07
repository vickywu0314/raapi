package com.wenwen.ai.clinical;

import com.wenwen.ai.treatment.TreatmentEpisode;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisitMatcherTest {
    LocalDate date(String value) { return value==null?null:LocalDate.parse(value); }
    TreatmentEpisode episode(String start,String end) { return episode("ACTIVE",start,null,end,"EXPLICIT","episode-1"); }
    TreatmentEpisode episode(String state,String start,String estimated,String end,String confidence,String key) {
        return new TreatmentEpisode(key,state,"TNFi",2,1,date(start),date(estimated),date(end),confidence,Collections.singletonList("synthetic:ada"),Collections.emptyMap());
    }
    VisitMatcher.Visit v(long id,String date,String score) { return new VisitMatcher.Visit(id,1,date(date),score); }
    VisitMatcher.Visit six(String asOf,TreatmentEpisode episode,VisitMatcher.Visit... visits) { return VisitMatcher.sixMonth(Arrays.asList(visits),1,date(asOf),episode); }
    @Test void sixMonthCalendarUsesIndependentInclusiveLeapAndNonLeapOracles() {
        TreatmentEpisode leap=episode("2023-08-31",null);
        assertEquals(1,six("2024-04-29",leap,v(1,"2023-12-31","3")).getId());
        assertEquals(2,six("2024-04-29",leap,v(2,"2024-04-29","3")).getId());
        assertNull(six("2024-04-30",leap,v(3,"2023-12-30","3"),v(4,"2024-04-30","3")));
        assertEquals(5,six("2026-02-28",episode("2025-08-31",null),v(5,"2026-02-28","3")).getId());
    }
    @Test void sixMonthFiltersBeforeNearestAndAllowsEarlyObservationBeforeFutureTarget() {
        TreatmentEpisode e=episode("2023-08-31",null);
        assertEquals(611,six("2024-03-02",e,v(612,"2024-03-02","2"),v(610,"2024-02-27","3.1"),v(611,"2024-02-27","3")).getId());
        assertEquals(621,six("2024-03-01",e,v(620,"2024-02-29","-0.001"),v(621,"2024-03-01","3"),v(622,"2024-02-27","2")).getId());
        assertEquals(631,six("2024-02-28",e,v(630,"2024-02-29","3"),v(631,"2024-02-27","2")).getId());
        assertEquals(640,six("2024-03-02",episode("2023-08-31","2024-02-29"),v(640,"2024-02-29","3"),v(641,"2024-03-01","2")).getId());
        assertEquals(642,six("2024-03-02",episode("2023-08-31","2024-02-28"),v(640,"2024-02-29","3"),v(642,"2024-02-28","2")).getId());
        assertNull(six("2024-03-02",e,new VisitMatcher.Visit(650,2,date("2024-02-29"),"3"),v(651,null,"3")));
    }
    @Test void unreliableAndNoCurrentEpisodesCannotBorrowHistoricalStartButConflictCan() {
        for(TreatmentEpisode e:Arrays.asList(episode("UNKNOWN","2023-08-31",null,null,"EXPLICIT","unknown"),episode("NONE","2023-08-31",null,"2024-02-29","EXPLICIT","none"),episode("ACTIVE",null,"2023-08-31",null,"ESTIMATED_FIRST_OBSERVED","estimate"),episode("ACTIVE",null,"2023-08-31",null,"ESTIMATED_EXPLICIT_END","end")))
            assertNull(six("2024-03-02",e,v(1,"2024-02-29","2")));
        assertEquals(1,six("2024-03-02",episode("CONFLICT","2023-08-31",null,null,"EXPLICIT","conflict"),v(1,"2024-02-29","2")).getId());
    }
    @Test void baselineChoosesNearestValidPreStartWithEarlierTieAndLargestId() {
        TreatmentEpisode e=episode("2024-03-31",null);
        VisitMatcher.Visit result=VisitMatcher.baseline(Arrays.asList(v(303,"2024-04-05","2"),v(301,"2024-03-26","3.10"),v(302,"2024-03-26","3.00")),1,date("2024-04-14"),e);
        assertNotNull(result); assertEquals(302,result.getId());
    }
    @Test void baselineWindowAndInvalidClosestAndFixedAsOfUseIndependentOracles() {
        TreatmentEpisode e=episode("2024-03-31",null);
        assertEquals(1,VisitMatcher.baseline(Arrays.asList(v(1,"2024-01-01","3")),1,date("2024-04-14"),e).getId());
        assertEquals(2,VisitMatcher.baseline(Arrays.asList(v(2,"2024-04-14","3")),1,date("2024-04-14"),e).getId());
        assertNull(VisitMatcher.baseline(Arrays.asList(v(3,"2023-12-31","3"),v(4,"2024-04-15","3")),1,date("2024-04-15"),e));
        assertEquals(402,VisitMatcher.baseline(Arrays.asList(v(401,"2024-03-31","未查"),v(402,"2024-03-30","3")),1,date("2024-04-14"),e).getId());
        assertNull(VisitMatcher.baseline(Arrays.asList(v(501,"2025-01-15","3")),1,date("2025-01-10"),episode("2025-01-01",null)));
        assertNull(VisitMatcher.baseline(Arrays.asList(v(1,"2024-03-31","3")),1,date("2024-04-14"),episode("ACTIVE",null,"2024-03-31",null,"ESTIMATED_EXPLICIT_END","estimated")));
    }
    @Test void nowFiltersInvalidUndatedFutureAndUsesLocalDateThenId() {
        List<VisitMatcher.Visit> visits=Arrays.asList(v(99,"2025-01-02","9"),v(20,"2025-01-01","2"),v(21,"2025-01-01","3"),v(100,null,"8"),v(22,"2025-01-01","-0.001"));
        VisitMatcher.Visit result=VisitMatcher.now(visits,1,date("2025-01-01")); assertNotNull(result); assertEquals(21,result.getId());
        assertEquals(99,VisitMatcher.now(visits,1,date("2025-01-02")).getId());
    }
    @Test void pairingKeepsSignedCanonicalDeltaAndRejectsCrossPatientEpisodeAndReverseDates() {
        TreatmentEpisode e=episode("2023-08-31",null); VisitMatcher.Visit base=v(1,"2023-08-30","3.46"), eval=v(2,"2024-02-29","2.19");
        assertEquals(new BigDecimal("1.27"),VisitMatcher.delta(base,e.key,eval,e.key,e));
        assertEquals(new BigDecimal("-1.27"),VisitMatcher.delta(v(3,"2023-08-30","2.19"),e.key,v(4,"2024-02-29","3.46"),e.key,e));
        assertEquals(new BigDecimal("0.00"),VisitMatcher.delta(v(3,"2023-08-30","2.19"),e.key,v(4,"2024-02-29","2.19"),e.key,e));
        assertNull(VisitMatcher.delta(null,e.key,eval,e.key,e)); assertNull(VisitMatcher.delta(base,e.key,null,e.key,e));
        assertNull(VisitMatcher.delta(base,"other-episode",eval,e.key,e));
        assertNull(VisitMatcher.delta(base,e.key,new VisitMatcher.Visit(2,2,date("2024-02-29"),"2.19"),e.key,e));
        assertNull(VisitMatcher.delta(eval,e.key,base,e.key,e));
        assertNull(VisitMatcher.delta(base,e.key,v(5,"2023-08-30","2.19"),e.key,e));
        assertNull(VisitMatcher.delta(base,e.key,eval,e.key,episode("2023-08-31","2024-02-28")));
        assertNull(VisitMatcher.delta(v(6,null,"3.46"),e.key,eval,e.key,e));
        assertEquals("EVAL_BEFORE_SCHEME",VisitMatcher.deltaMissingReason(base,e.key,v(5,"2023-08-30","2.19"),e.key,e));
    }
}
