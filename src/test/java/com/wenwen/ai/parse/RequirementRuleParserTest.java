package com.wenwen.ai.parse;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RequirementRuleParserTest {
    private final RuleParser rules=new RuleParser();
    private Map<String,Object> three(){Map<String,Object> c=new LinkedHashMap<>();c.put("tx","TNFi");c.put("at","6m");c.put("act","mod-high");return c;}
    @Test void finiteLocalWordsAndOriginalWhitespaceCaseMapExactly() {
        for(String q:new String[]{"TNFi 治疗 6 个月后仍处于中高疾病活动的患者","TNFi治疗6个月后仍处于中高疾病活动的患者","tnfi治疗6个月后仍处于中高疾病活动的患者","TNFi\u3000治疗6\u00a0个月后仍处于中高疾病活动的患者"}){
            ParseDecision d=rules.parse(q);assertEquals("OK",d.getStatus(),q);assertEquals(three(),d.getCandidate());assertEquals(0,d.getUnmapped());assertTrue(d.getWarnings().isEmpty());assertEquals("REPLACE_EXCEPT_IDS",ParseDecision.mode(d.getStatus()));assertFalse(rules.safeForOutbound(q));
        }
        String[][] cases={{"TNFi","tx","TNFi"},{"6个月后","at","6m"},{"仍处于中高疾病活动","act","mod-high"},{"TNF","tx","TNFi"},{"6个月","at","6m"},{"六个月","at","6m"},{"半年","at","6m"}};
        for(String[] c:cases){ParseDecision d=rules.parse(c[0]);assertEquals("OK",d.getStatus(),c[0]);assertEquals(Collections.singletonMap(c[1],c[2]),d.getCandidate());assertEquals(0,d.getUnmapped());}
    }
    @Test void tnfiAliasesCannotBeCutFromAsciiWords() {
        for(String q:new String[]{"XTNFi","TNFiX","TNFi_","TNFi1"}){ParseDecision d=rules.parse(q);assertEquals("UNRECOGNIZED",d.getStatus(),q);assertNull(d.getCandidate());assertEquals("KEEP_CURRENT",ParseDecision.mode(d.getStatus()));assertFalse(rules.safeForOutbound(q));}
    }
    @Test void unknownRemaindersAreNeverSwallowedByNewPhrases() {
        ParseDecision after=rules.parse("女性后");assertEquals("PARTIAL",after.getStatus());assertEquals(Collections.singletonMap("sex","F"),after.getCandidate());assertEquals(1,after.getUnmapped());
        ParseDecision smoking=rules.parse("TNFi 治疗 6 个月后仍处于中高疾病活动的患者且吸烟");assertEquals("PARTIAL",smoking.getStatus());assertEquals(three(),smoking.getCandidate());assertEquals(2,smoking.getUnmapped());assertEquals("REQUIRE_CONFIRMATION",ParseDecision.mode(smoking.getStatus()));
        ParseDecision inserted=rules.parse("TNFi治疗6个月后仍处于明显中高疾病活动的患者");assertEquals("PARTIAL",inserted.getStatus());assertEquals(three(),inserted.getCandidate());assertEquals(5,inserted.getUnmapped());assertFalse(rules.safeForOutbound("TNFi治疗6个月后仍处于明显中高疾病活动的患者"));
    }
    @Test void tnfiOrConflictAndNegationKeepExistingUnsupportedModes() {
        String[] questions={"TNFi或JAK","TNFi且JAK","非TNFi"},warnings={"UNSUPPORTED_OR","CONFLICTING_CONDITIONS","UNSUPPORTED_NEGATION"};
        for(int i=0;i<questions.length;i++){ParseDecision d=rules.parse(questions[i]);assertEquals("UNSUPPORTED",d.getStatus(),questions[i]);assertNull(d.getCandidate());assertEquals(Collections.singletonList(warnings[i]),d.getWarnings());assertEquals("KEEP_CURRENT",ParseDecision.mode(d.getStatus()));}
    }
}
