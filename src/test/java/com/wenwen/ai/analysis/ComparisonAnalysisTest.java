package com.wenwen.ai.analysis;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 独立资源给定输入/期望；不以生产函数生成expected。 */
class ComparisonAnalysisTest {
    private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private JsonNode resource(String name)throws Exception{return json.readTree(getClass().getResourceAsStream("/ai-cohort/p04b/"+name+".json"));}
    private static List<BigDecimal> decimals(JsonNode values){List<BigDecimal> out=new ArrayList<>();for(JsonNode v:values)out.add(new BigDecimal(v.asText()));return out;}
    @Test void type7AndPolicyBoundariesMatchIndependentOracles() throws Exception {
        JsonNode inputs=resource("fixed-inputs"),oracle=resource("independent-oracles");
        Iterator<String> keys=inputs.path("quantileProbes").fieldNames();while(keys.hasNext()){String key=keys.next();JsonNode e=oracle.path("quantileProbes").path(key);Map<String,Object> actual=ComparisonAnalysis.continuous(decimals(inputs.path("quantileProbes").path(key)),e.path("n").asInt());for(String field:Arrays.asList("q1","median","q3","iqr")){if(e.path(field).isNull())assertNull(actual.get(field),key+field);else assertEquals(0,new BigDecimal(e.path(field).asText()).compareTo((BigDecimal)actual.get(field)),key+field);}}
        for(JsonNode probe:inputs.path("boundaryCases").path("validN")){Map<String,Object> t=ComparisonAnalysis.continuousTest(decimals(probe.path("A")),decimals(probe.path("B")),0,false);assertTest(oracle.path("boundaryCases").path("validN").path(probe.path("id").asText()),t);}
        for(String control:Arrays.asList("tieAsymptoticControl","precisionOrderControl"))assertTest(oracle.path(control),ComparisonAnalysis.continuousTest(decimals(inputs.path(control).path("A")),decimals(inputs.path(control).path("B")),0,false));
        for(String control:Arrays.asList("allOneActivityCategory","expectedExactlyFive")){JsonNode table=inputs.path("boundaryCases").path(control).path("tableByGroup");long[][] counts=new long[2][table.get(0).size()];for(int i=0;i<2;i++)for(int j=0;j<counts[i].length;j++)counts[i][j]=table.get(i).get(j).asLong();int n="expectedExactlyFive".equals(control)?10:6;String denominator="expectedExactlyFive".equals(control)?"KNOWN_CATEGORY":"EVALUABLE_ACTIVITY";assertTest(oracle.path("boundaryCases").path(control),ComparisonAnalysis.contingencyTest(counts,n,n,denominator,0,false));}
        Map<String,Object> overlap=ComparisonAnalysis.continuousTest(decimals(inputs.path("boundaryCases").path("validN").get(1).path("A")),Collections.emptyList(),1,false);assertEquals("OVERLAP",overlap.get("status"));assertEquals("COHORTS_OVERLAP",overlap.get("reason"));assertNull(overlap.get("pValue"));
        List<BigDecimal> constant=Arrays.asList(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE);assertEquals("DEGENERATE",ComparisonAnalysis.continuousTest(constant,constant,0,false).get("status"));
    }
    private void assertTest(JsonNode e,Map<String,Object> t){JsonNode a=json.valueToTree(t);for(String field:Arrays.asList("method","status","reason","nA","nB","warnings","displayP"))assertEquals(e.path(field),a.path(field),field);if(e.path("pValue").isNull())assertTrue(a.path("pValue").isNull());else assertEquals(e.path("pValue").asDouble(),a.path("pValue").asDouble(),1e-12);assertEquals(e.path("significantRawAtAlpha005"),a.path("significant"));}
}
