package com.wenwen.ai.statistics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatisticalPolicyTest {
    static final String TX="EVALUABLE_KNOWN_TREATMENT",FM="NON_MISSING_EVALUATION_METRIC";
    @Test void zeroMarginsArePrunedBeforeOverallPearson() {
        Map<String,Object> test=assertDoesNotThrow(()->StatisticalPolicy.contingency(new long[][]{{10,0,0},{9,1,0},{8,2,0},{0,0,0}},null,null,TX),"合法零边际必须剔除");
        assertEquals("CHI_SQUARE",test.get("method"));p(test,.3291929878079054);assertEquals(3,test.get("expectedBelow5Cells"));assertEquals(6,test.get("expectedCellCount"));assertEquals(Collections.singletonList("SPARSE_EXPECTED_COUNTS"),test.get("warnings"));
    }
    @Test void engineRequiresFiveValidCasesInEachNonzeroRow() {
        Map<String,Object> test=StatisticalPolicy.contingency(new long[][]{{1,3},{3,5}},4,8,TX);
        assertEquals("INSUFFICIENT_SAMPLE",test.get("status"));assertEquals("VALID_N_LT_5",test.get("reason"));assertNull(test.get("method"));assertNull(test.get("pValue"));assertNull(test.get("expectedCellCount"));assertEquals(Collections.emptyList(),test.get("warnings"));
    }
    @Test void zeroTotalAndSingleMarginHaveNoProbability() {
        for(long[][] table:Arrays.asList(new long[][]{{0,0},{0,0}},new long[][]{{8,0},{8,0}},new long[][]{{0,0},{3,4}})) {
            Map<String,Object> test=StatisticalPolicy.contingency(table,null,null,TX);assertEquals("DEGENERATE",test.get("status"));assertEquals("ZERO_MARGIN",test.get("reason"));assertNull(test.get("pValue"));assertNull(test.get("expectedCellCount"));assertEquals(Collections.emptyList(),test.get("warnings"));
        }
    }
    @Test void exactSelectionUsesBothInclusiveSampleBoundaries() {
        Map<?,?> nine=StatisticalPolicy.mannWhitney(sequence(1,9),sequence(10,9),FM);assertEquals("MANN_WHITNEY_ASYMPTOTIC",nine.get("method"));p(nine,.00041229480206169127);
        Map<?,?> fiftyOne=StatisticalPolicy.mannWhitney(sequence(1,5),sequence(6,51),FM);assertEquals("MANN_WHITNEY_ASYMPTOTIC",fiftyOne.get("method"));p(fiftyOne,.00026315152533834416);
        Map<?,?> boundary=StatisticalPolicy.mannWhitney(sequence(1,8),sequence(9,50),FM);assertEquals("MANN_WHITNEY_EXACT",boundary.get("method"));p(boundary,1.0434071398799036e-9);assertEquals("<0.001",boundary.get("displayP"));
    }
    @Test void twoSidedInferenceIsSymmetricUnderGroupSwap() {
        int index=0;double[] expected={1.0/126,.2873330696714993};
        for(List<List<java.math.BigDecimal>> samples:Arrays.asList(Arrays.asList(sequence(1,5),sequence(6,5)),Arrays.asList(decimals("1","2","2","4","5"),decimals("2","3","4","5","6")))) {
            Map<?,?> forward=StatisticalPolicy.mannWhitney(samples.get(0),samples.get(1),FM),reverse=StatisticalPolicy.mannWhitney(samples.get(1),samples.get(0),FM);p(forward,expected[index]);p(reverse,expected[index++]);assertEquals(forward.get("method"),reverse.get("method"));assertEquals(forward.get("warnings"),reverse.get("warnings"));
        }
    }
    @Test void decimalScaleDoesNotSplitNumericallyEqualTies() {
        Map<?,?> test=StatisticalPolicy.mannWhitney(decimals(".2",".30",".300",".5",".6"),decimals(".300",".4",".50",".6",".7"),FM);
        assertEquals("MANN_WHITNEY_ASYMPTOTIC",test.get("method"));p(test,.2873330696714993);assertEquals(Collections.singletonList("SMALL_SAMPLE_TIES"),test.get("warnings"));
    }
    @Test void largeTiesKeepTinyProbabilityAndHaveNoSmallSampleWarning() {
        List<java.math.BigDecimal> a=new ArrayList<>(),b=new ArrayList<>();for(int v=0;v<3;v++)for(int i=0;i<20;i++){a.add(java.math.BigDecimal.valueOf(v));b.add(java.math.BigDecimal.valueOf(v+1));}
        for(boolean swap:Arrays.asList(false,true)){Map<?,?> test=StatisticalPolicy.mannWhitney(swap?b:a,swap?a:b,FM);assertEquals("MANN_WHITNEY_ASYMPTOTIC",test.get("method"));p(test,4.2751119533928335e-8);assertEquals(60,test.get("nA"));assertEquals(60,test.get("nB"));assertEquals(Collections.emptyList(),test.get("warnings"));assertEquals("<0.001",test.get("displayP"));}
    }
    @Test void rawProbabilityControlsSignificanceBeforeDisplayRounding() {
        double[] raw={.0496,.0009,.0521,.05,0};String[] display={"0.050","<0.001","0.052","0.050","<0.001"};boolean[] significant={true,true,false,false,true};
        for(int i=0;i<raw.length;i++){Map<?,?> test=StatisticalPolicy.result("CHI_SQUARE",raw[i],5,5,TX,Collections.emptyList());p(test,raw[i]);assertEquals(display[i],test.get("displayP"));assertEquals(significant[i],test.get("significant"));assertEquals(new HashSet<>(Arrays.asList("method","status","pValue","displayP","significant","nA","nB","reason","warnings","policyVersion","testDenominator")),test.keySet());}
        Map<?,?> missing=StatisticalPolicy.unavailable("INSUFFICIENT_SAMPLE","VALID_N_LT_5",0,0,FM);assertNull(missing.get("pValue"));assertNull(missing.get("significant"));assertEquals("—",missing.get("displayP"));
    }
    @Test void unexpectedInvalidInputsAndProbabilitiesPropagate() {
        assertThrows(IllegalArgumentException.class,()->StatisticalPolicy.contingency(new long[][]{{-1,1},{3,5}},null,null,TX));
        assertThrows(IllegalArgumentException.class,()->StatisticalPolicy.contingency(new long[][]{{3,5},{3,5,1}},null,null,TX));
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-.01,1.01})assertThrows(IllegalStateException.class,()->StatisticalPolicy.result("CHI_SQUARE",invalid,5,5,TX,Collections.emptyList()));
    }
    @Test void rationalExpectedExactlyFiveCannotRoundIntoFisher() {
        Map<?,?> test=StatisticalPolicy.contingency(new long[][]{{5,72},{5,72}},77,77,TX);
        assertEquals("CHI_SQUARE",test.get("method"));assertEquals(0,test.get("expectedBelow5Cells"));p(test,1);
    }
    static List<java.math.BigDecimal> decimals(String... values) {List<java.math.BigDecimal> out=new ArrayList<>();for(String value:values)out.add(new java.math.BigDecimal(value));return out;}
    static List<java.math.BigDecimal> sequence(int first,int count) {List<java.math.BigDecimal> values=new ArrayList<>();for(int i=0;i<count;i++)values.add(java.math.BigDecimal.valueOf(first+i));return values;}
    static void p(Map<?,?> test,double expected) {
        assertNotNull(test.get("pValue"));double actual=((Number)test.get("pValue")).doubleValue();assertTrue(Double.isFinite(actual));assertEquals(expected,actual,1e-12);if(expected!=0)assertEquals(1,actual/expected,1e-10);
    }
}
