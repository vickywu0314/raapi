package com.wenwen.vo;

import java.util.*;
import lombok.Value;

/** P02 真实临床、治疗及共享now/6m评估响应；后续完整分析能力不以占位字段冒充。 */
@Value
public class AiCohortVo {
    String analysisId;
    int n;
    int studyTotal;
    Integer submittedUniqueIdsN;
    Integer effectiveIdsN;
    Map<String,Object> activity;
    Map<String,Object> patients;
    Map<String,Object> meta;

    Map<String,Object> stats;
    Map<String,Object> metricMeta;
    Map<String,Object> byTx;
    Map<String,Object> fm;
    Map<String,Object> lines;

    public static Map<String,Object> object(Object... fields) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0; i<fields.length; i+=2) result.put((String)fields[i], fields[i+1]);
        return Collections.unmodifiableMap(result);
    }
}
