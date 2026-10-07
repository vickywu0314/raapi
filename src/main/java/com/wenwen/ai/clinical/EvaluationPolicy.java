package com.wenwen.ai.clinical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;

/** 原记录指标仅作来源展示，不新增 DAS 组成项复算。 */
public final class EvaluationPolicy {
    private EvaluationPolicy() { }
    public static Map<String,Map<String,Object>> read(long id, LocalDate date, Map<String,String> assessment, Map<String,String> labs) {
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();
        result.put("tjc",component(id,date,"bqpg.result.ytgjs",assessment.get("/result/ytgjs"),"count"));
        result.put("sjc",component(id,date,"bqpg.result.zzgjs",assessment.get("/result/zzgjs"),"count"));
        result.put("gh",component(id,date,"bqpg.ztScoreByPatient",assessment.get("/ztScoreByPatient"),"0-100"));
        boolean main=assessment.containsKey("/hqaScore");
        Map<String,Object> haq=component(id,date,main?"bqpg.hqaScore":"bqpg.result.hqaScore",assessment.get(main?"/hqaScore":"/result/hqaScore"),"0-3");
        Map<String,Object> alias=component(id,date,"bqpg.result.hqaScore",assessment.get("/result/hqaScore"),"0-3");
        if (main && haq.get("value")!=null && alias.get("value")!=null
                && ((BigDecimal)haq.get("value")).compareTo((BigDecimal)alias.get("value"))!=0) {
            Map<String,Object> discrepancy=new LinkedHashMap<>(haq);
            discrepancy.put("quality",Collections.unmodifiableList(Arrays.asList("LEGACY_UNVERIFIED","DISCREPANCY")));
            haq=Collections.unmodifiableMap(discrepancy);
        }
        result.put("haq",haq);
        result.put("crp",component(id,date,"fzjc.cfydb",labs.get("/cfydb"),"mg/L"));
        result.put("pain",object("value",null,"raw",assessment.get("/tjScore"),"sourceVisitId",Long.toString(id),
            "sourceField","bqpg.tjScore","observedAt",date==null?null:date.toString(),"unit",null,
            "quality",Collections.unmodifiableList(Arrays.asList("LEGACY_UNVERIFIED","SCALE_UNVERIFIED")),"missingReason","UNVERIFIED_SCALE"));
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> component(long id,LocalDate date,String field,String raw,String unit) {
        BigDecimal value=number(raw);
        String missing=raw==null || raw.trim().isEmpty()?"MISSING_VALUE":value==null?"INVALID_VALUE":null;
        BigDecimal maximum="count".equals(unit)?new BigDecimal("28"):"0-100".equals(unit)?new BigDecimal("100"):"0-3".equals(unit)?new BigDecimal("3"):null;
        if (value!=null && (value.signum()<0 || (maximum!=null && value.compareTo(maximum)>0))) { value=null; missing="OUT_OF_RANGE"; }
        if (value!=null && "count".equals(unit) && value.stripTrailingZeros().scale()>0) { value=null; missing="NON_INTEGER"; }
        return object("value",value,"sourceVisitId",Long.toString(id),"sourceField",field,"observedAt",date==null?null:date.toString(),
            "unit",unit,"quality",Collections.singletonList("LEGACY_UNVERIFIED"),"missingReason",missing);
    }
    public static BigDecimal number(String raw) {
        if (raw==null || !raw.trim().matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) return null;
        try { return new BigDecimal(raw.trim()); } catch(NumberFormatException e) { return null; }
    }
    public static Map<String,Object> missingCrp(String reason) {
        return object("value",null,"sourceVisitId",null,"sourceField",null,"observedAt",null,"unit","mg/L",
            "quality",Collections.emptyList(),"missingReason",reason);
    }
    public static Map<String,Map<String,Object>> missing() {
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();
        for(String name:Arrays.asList("tjc","sjc","gh","haq","crp","pain")) result.put(name,object("value",null,"sourceVisitId",null,"sourceField",null,
            "observedAt",null,"unit",null,"quality",Collections.emptyList(),"missingReason","NO_VALID_CRP"));
        return Collections.unmodifiableMap(result);
    }
}
