package com.wenwen.ai.parse;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RuleParserTest {
    private final RuleParser rules=new RuleParser();
    void condition(String question,String field,String value){ParseDecision d=rules.parse(question);assertEquals("OK",d.getStatus(),question);assertEquals(Collections.singletonMap(field,value),d.getCandidate(),question);}
    @Test void treatmentFixedKeywordsMapWithoutStoppingAtFirstGroup() {
        String[][] words={{"TNF","肿瘤坏死","阿达木","依那西普","英夫利昔"},{"JAK","托法替布","巴瑞替尼","乌帕替尼"},{"IL-6","IL6","托珠"},{"阿巴西普","Abatacept"},{"生物制剂","靶向","b/tsDMARD"},{"甲氨蝶呤","MTX","csDMARD","传统"}};
        String[] values={"TNFi","JAKi","IL-6i","Abatacept","bio","csDMARD"};
        for(int i=0;i<words.length;i++)for(String word:words[i])condition(word,"tx",values[i]);
        condition(" t n f ","tx","TNFi");condition("mTx","tx","csDMARD");
    }
    @Test void sexKeywordsCollectConflictsRatherThanChoosingFirst() {
        for(String q:new String[]{"女性","女患者","女"})condition(q,"sex","F");for(String q:new String[]{"男性","男患者"})condition(q,"sex","M");
        for(String q:new String[]{"女性且男性","男性和女性","女患者且男患者"}){ParseDecision d=rules.parse(q);assertEquals("UNSUPPORTED",d.getStatus(),q);assertNull(d.getCandidate());assertEquals(Collections.singletonList("CONFLICTING_CONDITIONS"),d.getWarnings());}
    }

    @Test void ageRangesAreClosedCanonicalAndNeverDroppedWhenInvalid() {
        for(String q:new String[]{"45-65岁","45~65岁","45至65岁"})condition(q,"age","45-65");condition("60岁以上","age","60-");condition("0-120岁","age","0-120");
        for(String q:new String[]{"年龄65-45岁","121岁以上","女性121-125岁"}){ParseDecision d=rules.parse(q);assertEquals("UNSUPPORTED",d.getStatus(),q);assertNull(d.getCandidate());assertTrue(d.getWarnings().contains("AGE_OUT_OF_RANGE"));}
        ParseDecision d=rules.parse("45-65岁且60岁以上");assertEquals("UNSUPPORTED",d.getStatus());assertNull(d.getCandidate());assertTrue(d.getWarnings().contains("CONFLICTING_CONDITIONS"));
    }

    @Test void comorbidityWordsRespectAsciiBoundaryAndConflict() {
        for(String q:new String[]{"纤维肌痛","FM","fm"})condition(q,"cm","FM");for(String q:new String[]{"强直","AS","as"})condition(q,"cm","AS");
        ParseDecision d=rules.parse("同时FM和AS");assertEquals("UNSUPPORTED",d.getStatus());assertNull(d.getCandidate());assertEquals(Collections.singletonList("CONFLICTING_CONDITIONS"),d.getWarnings());
        for(String q:new String[]{"case","CASE","format","XAS","ASX","FM1"}){d=rules.parse(q);assertEquals("UNRECOGNIZED",d.getStatus(),q);assertNull(d.getCandidate());}
    }

    @Test void serologyWordsArePositiveOnlyWithCompleteAsciiTokens() {
        for(String q:new String[]{"血清阳性","RF","ACPA","抗CCP","rf","acpa"})condition(q,"sero","1");
        for(String q:new String[]{"RF阴性","非血清阳性"}){ParseDecision d=rules.parse(q);assertEquals("UNSUPPORTED",d.getStatus());assertNull(d.getCandidate());}
        for(String q:new String[]{"XRF","ACPAx","SURFACE"}){ParseDecision d=rules.parse(q);assertEquals("UNRECOGNIZED",d.getStatus());assertNull(d.getCandidate());}
    }

    @Test void sixMonthWordsProduceOnlyExistingAtValue(){for(String q:new String[]{"6个月","六个月","半年"})condition(q,"at","6m");}

    @Test void missingDataPositiveWordsAreNotMistakenForNegation(){for(String q:new String[]{"缺失","待补全","不完整"})condition(q,"data","missing");}

    @Test void asciiOrSafetyCheckKeepsOriginalWordBoundariesBeforeRemovingSpaces(){
        for(String q:new String[]{"TNF or JAK","女性 OR 男性","女性 Or 非男性"}){ParseDecision d=rules.parse(q);assertEquals("UNSUPPORTED",d.getStatus(),q);assertNull(d.getCandidate());assertTrue(d.getWarnings().contains("UNSUPPORTED_OR"));}
        ParseDecision d=rules.parse("format");assertEquals("UNRECOGNIZED",d.getStatus());assertNull(d.getCandidate());
    }
    @Test void bareFemaleGuardPreservesCompleteSexKeywordsAndUnknownText(){
        condition("女","sex","F");condition("女性","sex","F");condition("女患者","sex","F");condition("男性","sex","M");condition("男患者","sex","M");
        for(String question:new String[]{"女性男同行","女患者男同行","男同行女性"}){
            ParseDecision d=rules.parse(question);assertEquals("PARTIAL",d.getStatus(),question);assertEquals(Collections.singletonMap("sex","F"),d.getCandidate());assertEquals(3,d.getUnmapped());
        }
        for(String question:new String[]{"女性且男性","女患者和男患者"}){
            ParseDecision d=rules.parse(question);assertEquals("UNSUPPORTED",d.getStatus(),question);assertNull(d.getCandidate());assertTrue(d.getWarnings().contains("CONFLICTING_CONDITIONS"));
        }
        for(String question:new String[]{"男同行女","男女且吸烟"}){
            ParseDecision d=rules.parse(question);assertEquals("UNRECOGNIZED",d.getStatus(),question);assertNull(d.getCandidate());assertEquals(4,d.getUnmapped());
        }
    }
}
