package com.wenwen.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.wenwen.util.VisitFieldDict;
import com.wenwen.util.VisitJson;
import com.wenwen.vo.VisitModuleUpdate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisitEditorInvalidationTest {
    static JSONObject assessment() {
        return VisitJson.parse("{\"result\":{\"ytgjs\":4,\"zzgjs\":1,\"crpScore\":\"8.88\",\"esrScore\":7.77,\"hqaScore\":2},\"ztScoreByPatient\":50,\"hqaScore\":2}");
    }
    static VisitModuleUpdate fields(String key, Object value) {
        VisitModuleUpdate u = new VisitModuleUpdate();
        u.setFields(Collections.singletonMap(key,value)); return u;
    }
    static Set<String> apply(VisitEditor e, String column, JSONObject o, VisitModuleUpdate u) {
        for (VisitFieldDict.Module m : VisitFieldDict.MODULES) if (column.equals(m.column)) return e.apply(m,o,u);
        throw new AssertionError(column);
    }
    @Test void clearingCrpInvalidatesOnlyStringCrp() {
        VisitEditor e=new VisitEditor(); JSONObject lab=VisitJson.parse("{\"cfydb\":9,\"xc\":25}"), b=assessment();
        Set<String> changed=apply(e,"fzjc",lab,fields("cfydb",""));
        assertTrue(e.recompute(lab,b,changed,Collections.emptySet()));
        assertEquals("",VisitJson.path(b,"result.crpScore"));
        assertEquals(new java.math.BigDecimal("7.77"),VisitJson.path(b,"result.esrScore"));
    }
    @Test void clearingEsrInvalidatesNumberWithoutChangingCrp() {
        VisitEditor e=new VisitEditor(); JSONObject lab=VisitJson.parse("{\"cfydb\":9,\"xc\":25}"), b=assessment();
        Set<String> changed=apply(e,"fzjc",lab,fields("xc",""));
        assertTrue(e.recompute(lab,b,changed,Collections.emptySet()));
        assertNull(VisitJson.path(b,"result.esrScore"));
        assertTrue(b.getJSONObject("result").containsKey("esrScore"));
        assertEquals("8.88",VisitJson.path(b,"result.crpScore"));
    }
    @Test void markingCrpUncheckedInvalidatesPresentValue() {
        VisitEditor e=new VisitEditor(); JSONObject lab=VisitJson.parse("{\"cfydb\":9,\"xc\":25}"), b=assessment();
        VisitModuleUpdate u=new VisitModuleUpdate();u.setWx(Collections.singletonMap("cfydb",true));
        Set<String> changed=apply(e,"fzjc",lab,u);
        assertTrue(e.recompute(lab,b,changed,Collections.emptySet()));
        assertEquals("",VisitJson.path(b,"result.crpScore"));
        assertEquals(new java.math.BigDecimal("7.77"),VisitJson.path(b,"result.esrScore"));
    }
    static JSONObject answered() {
        JSONObject b=assessment(); for (int q : new int[]{1,3,5,8,10,13,15,18}) b.put("q"+q,"稍有困难"); return b;
    }
    @Test void missingHaqDimensionInvalidatesMainAndExistingAlias() {
        VisitEditor e=new VisitEditor(); JSONObject b=answered();
        Set<String> changed=apply(e,"bqpg",b,fields("q1",""));
        assertTrue(e.recompute(null,b,Collections.emptySet(),changed));
        assertNull(b.get("hqaScore")); assertNull(VisitJson.path(b,"result.hqaScore"));
        assertTrue(b.containsKey("hqaScore")); assertTrue(b.getJSONObject("result").containsKey("hqaScore"));
        assertEquals("8.88",VisitJson.path(b,"result.crpScore"));
    }
    @Test void aliasOnlyCorrectionIsPersistentChangeWithoutDuplicateAudit() {
        VisitEditor e=new VisitEditor(); JSONObject b=answered(); b.put("hqaScore",new java.math.BigDecimal("1.00"));
        Set<String> changed=apply(e,"bqpg",b,fields("q2","稍有困难")); int before=e.changes.size();
        assertTrue(e.recompute(null,b,Collections.emptySet(),changed));
        assertEquals(new java.math.BigDecimal("1.00"),VisitJson.path(b,"result.hqaScore"));
        assertEquals(before,e.changes.size());
    }
    @Test void commonInputsUseIndependentFormulaValuesAndNeverHaqAsGh() {
        JSONObject lab=VisitJson.parse("{\"cfydb\":9,\"xc\":25}"), b=answered(); VisitEditor e=new VisitEditor();
        // 手写列表固定TJC=4/SJC=1，经真实apply触发重评，不调用生产公式生成期望。
        Set<String> changed=new HashSet<>(apply(e,"bqpg",b,fields("zzgj",Collections.singletonList("a"))));
        changed.addAll(apply(e,"bqpg",b,fields("ytgj",Arrays.asList("a","b","c","d"))));
        assertTrue(e.recompute(lab,b,Collections.emptySet(),changed));
        assertEquals("3.89",VisitJson.path(b,"result.crpScore"));
        assertEquals(new java.math.BigDecimal("4.35"),VisitJson.path(b,"result.esrScore"));
        b.put("hqaScore",1); changed=apply(e,"bqpg",b,fields("ztScoreByPatient",""));
        assertTrue(e.recompute(lab,b,Collections.emptySet(),changed));
        assertEquals("",VisitJson.path(b,"result.crpScore")); assertNull(VisitJson.path(b,"result.esrScore"));
        changed=new HashSet<>(apply(e,"bqpg",b,fields("ztScoreByPatient","0")));
        changed.addAll(apply(e,"bqpg",b,fields("zzgj",Collections.emptyList())));
        changed.addAll(apply(e,"bqpg",b,fields("ytgj",Collections.emptyList())));
        assertTrue(e.recompute(lab,b,apply(e,"fzjc",lab,fields("cfydb","0")),changed));
        assertEquals("0.96",VisitJson.path(b,"result.crpScore"));
    }
    @Test void invalidLabValuesKeepKeysTypesAndUnrelatedHistory() {
        for (Object bad : Arrays.asList("", "-1", "未查")) {
            VisitEditor e=new VisitEditor();JSONObject b=assessment(), lab=VisitJson.parse("{\"cfydb\":9,\"xc\":25}");
            b.getJSONObject("result").put("crpScore",true);
            assertTrue(e.recompute(lab,b,apply(e,"fzjc",lab,fields("cfydb",bad)),Collections.emptySet()));
            assertNull(VisitJson.path(b,"result.crpScore"));assertTrue(b.getJSONObject("result").containsKey("crpScore"));
            assertEquals(new java.math.BigDecimal("7.77"),VisitJson.path(b,"result.esrScore"));
        }
        for (Object bad : Arrays.asList("", "0", "-1", "未查")) {
            VisitEditor e=new VisitEditor();JSONObject b=assessment(), lab=VisitJson.parse("{\"cfydb\":9,\"xc\":25}");
            b.getJSONObject("result").put("esrScore","7.77");
            assertTrue(e.recompute(lab,b,apply(e,"fzjc",lab,fields("xc",bad)),Collections.emptySet()));
            assertEquals("",VisitJson.path(b,"result.esrScore"));assertEquals("8.88",VisitJson.path(b,"result.crpScore"));
        }
        JSONObject esrUnchecked=assessment(), labUnchecked=VisitJson.parse("{\"cfydb\":9,\"xc\":25}");VisitEditor wxEditor=new VisitEditor();
        VisitModuleUpdate wxUpdate=new VisitModuleUpdate();wxUpdate.setWx(Collections.singletonMap("xc",true));
        assertTrue(wxEditor.recompute(labUnchecked,esrUnchecked,apply(wxEditor,"fzjc",labUnchecked,wxUpdate),Collections.emptySet()));
        assertNull(VisitJson.path(esrUnchecked,"result.esrScore"));assertEquals("8.88",VisitJson.path(esrUnchecked,"result.crpScore"));
        JSONObject b=assessment();b.getJSONObject("result").remove("crpScore");
        assertFalse(new VisitEditor().recompute(null,b,Collections.singleton("cfydb"),Collections.emptySet()));
        assertFalse(b.getJSONObject("result").containsKey("crpScore"));
        String raw=VisitJson.write(b);assertFalse(new VisitEditor().recompute(null,b,Collections.emptySet(),Collections.emptySet()));assertEquals(raw,VisitJson.write(b));
    }
    @Test void partialHaqAnswerUsesDimensionMaxAndDoesNotCreateAlias() {
        JSONObject b=answered();b.put("q2","无困难");VisitEditor e=new VisitEditor();
        assertTrue(e.recompute(null,b,Collections.emptySet(),apply(e,"bqpg",b,fields("q2",""))));
        assertEquals(new java.math.BigDecimal("1.00"),b.get("hqaScore"));
        assertEquals(new java.math.BigDecimal("1.00"),VisitJson.path(b,"result.hqaScore"));
        b.getJSONObject("result").remove("hqaScore");b.put("hqaScore","2");
        assertTrue(new VisitEditor().recompute(null,b,Collections.emptySet(),Collections.singleton("q1")));
        assertEquals("1.00",b.get("hqaScore"));assertFalse(b.getJSONObject("result").containsKey("hqaScore"));
    }
}
