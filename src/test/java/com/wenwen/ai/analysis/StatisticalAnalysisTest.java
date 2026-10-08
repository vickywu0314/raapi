package com.wenwen.ai.analysis;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 明确合成derived TRUE/FALSE；真实Service消费同聚合器，不伪造临床源。 */
class StatisticalAnalysisTest {
    final DescriptiveAnalysisTest fixture=new DescriptiveAnalysisTest();
    @Test void actualFmAggregatorConsumesExactMannWhitney() {
        List<DerivedPatient> people=fixture.groups(new String[]{"1","2","3","4","5"},new String[]{"6","7","8","9","10"});
        Map<?,?> row=fixture.row(people,"das28");assertTrue(row.get("test") instanceof Map,"实际FM行必须消费推断");Map<?,?> test=(Map<?,?>)row.get("test");
        assertEquals("MANN_WHITNEY_EXACT",test.get("method"));assertEquals("OK",test.get("status"));p(test,1.0/126);assertEquals(5,test.get("nA"));assertEquals(5,test.get("nB"));assertEquals("NON_MISSING_EVALUATION_METRIC",test.get("testDenominator"));assertEquals(Collections.emptyList(),test.get("warnings"));assertEquals("dev-stats-v04",test.get("policyVersion"));
        assertEquals(0,new BigDecimal("-5").compareTo((BigDecimal)row.get("difference")));assertEquals("-5.0",row.get("displayDifference"));assertEquals(true,row.get("highlight"));
    }
    @Test void tiedValuesUseAsymptoticTieAndContinuityCorrection() {
        Map<?,?> test=(Map<?,?>)fixture.row(fixture.groups(new String[]{"1","2","2","4","5"},new String[]{"2","3","4","5","6"}),"das28").get("test");
        assertEquals("MANN_WHITNEY_ASYMPTOTIC",test.get("method"));p(test,.2873330696714993);assertEquals(Collections.singletonList("SMALL_SAMPLE_TIES"),test.get("warnings"));assertEquals(5,test.get("nA"));assertEquals(5,test.get("nB"));
    }
    @Test void preciseDecimalOrderSurvivesLibraryRankInput() {
        List<DerivedPatient> people=fixture.groups(new String[]{".300000000000000001",".300000000000000003",".300000000000000005",".300000000000000007",".300000000000000009"},new String[]{".300000000000000011",".300000000000000013",".300000000000000015",".300000000000000017",".300000000000000019"});
        Map<?,?> row=fixture.row(people,"das28"),test=(Map<?,?>)row.get("test");assertEquals("MANN_WHITNEY_EXACT",test.get("method"));p(test,1.0/126);assertEquals(Collections.emptyList(),test.get("warnings"));
        assertEquals(0,new BigDecimal(".300000000000000005").compareTo((BigDecimal)row.get("meanFM")));assertEquals(0,new BigDecimal("-.00000000000000001").compareTo((BigDecimal)row.get("difference")));assertEquals(false,row.get("highlight"));
    }
    @Test void allIdenticalValuesAreExplicitlyUntestable() {
        Map<?,?> row=assertDoesNotThrow(()->fixture.row(fixture.groups(new String[]{"1","1","1","1","1"},new String[]{"1","1","1","1","1"}),"das28"));Map<?,?> test=(Map<?,?>)row.get("test");
        assertEquals("DEGENERATE",test.get("status"));assertEquals("ZERO_RANK_VARIANCE",test.get("reason"));assertNull(test.get("method"));assertNull(test.get("pValue"));assertNull(test.get("significant"));assertEquals("—",test.get("displayP"));assertEquals(Collections.emptyList(),test.get("warnings"));assertEquals(5,test.get("nA"));assertEquals(5,test.get("nB"));assertEquals(0,((BigDecimal)row.get("difference")).signum());
    }
    @Test void fmInferenceUsesFieldwiseFiveNotDisplayThree() {
        List<DerivedPatient> small=fixture.groups(new String[]{"1","2","3"},new String[]{"4","5","6"});Map<?,?> row=fixture.row(small,"das28"),test=(Map<?,?>)row.get("test");
        assertEquals("OK",DescriptiveAnalysis.fm(small).get("status"));assertEquals("INSUFFICIENT_SAMPLE",test.get("status"));assertEquals("VALID_N_LT_5",test.get("reason"));assertEquals(3,test.get("nA"));assertEquals(3,test.get("nB"));assertNull(test.get("pValue"));assertNull(test.get("significant"));
        List<DerivedPatient> people=new ArrayList<>();for(int i=0;i<5;i++){people.add(fixture.patient(i+1,"TRUE",""+(i+1),i==0?Collections.emptyMap():Collections.singletonMap("tjc",""+(i+1))));people.add(fixture.patient(i+10,"FALSE",""+(i+6),Collections.singletonMap("tjc",""+(i+6))));}
        people.add(fixture.patient(100,"UNKNOWN","99",Collections.singletonMap("tjc","99")));
        Map<?,?> das=(Map<?,?>)fixture.row(people,"das28").get("test");p(das,1.0/126);assertEquals(5,das.get("nA"));assertEquals(5,das.get("nB"));
        Map<?,?> tjc=(Map<?,?>)fixture.row(people,"tjc").get("test");assertEquals("INSUFFICIENT_SAMPLE",tjc.get("status"));assertEquals(4,tjc.get("nA"));assertEquals(5,tjc.get("nB"));assertNull(tjc.get("pValue"));
        Map<?,?> pain=(Map<?,?>)fixture.row(people,"pain").get("test");assertEquals("INSUFFICIENT_SAMPLE",pain.get("status"));assertEquals(0,pain.get("nA"));assertEquals(0,pain.get("nB"));assertEquals(Collections.emptyList(),pain.get("warnings"));
        assertEquals(1,DescriptiveAnalysis.fm(people).get("unknownN"));
    }
    @Test void fmInferenceOnlyReadsTheChosenEvaluationVisit() {
        List<DerivedPatient> people=new ArrayList<>();
        for(int i=0;i<10;i++) {
            DerivedPatient eval=fixture.patient(i+1,i<5?"TRUE":"FALSE",""+(i+1),Collections.singletonMap("crp",""+(i+1)));
            DerivedPatient other=fixture.patient(100+i,"UNKNOWN","1",Collections.singletonMap("crp","1"));
            people.add(new DerivedPatient(eval.id,eval.clinical,eval.qc,eval.treatment,other.evaluation,eval.evaluation,other.evaluation,eval.evaluation));
        }
        for(String metric:Arrays.asList("das28","crp")){Map<?,?> row=fixture.row(people,metric);p((Map<?,?>)row.get("test"),1.0/126);assertEquals(0,new BigDecimal("-5").compareTo((BigDecimal)row.get("difference")));}
    }
    static void p(Map<?,?> test,double expected) {
        assertNotNull(test.get("pValue"));double actual=((Number)test.get("pValue")).doubleValue();assertEquals(expected,actual,1e-12);if(expected!=0)assertEquals(1,actual/expected,1e-10);
    }
}
