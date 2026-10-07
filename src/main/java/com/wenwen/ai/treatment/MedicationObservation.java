package com.wenwen.ai.treatment;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;

/** zlfa 在源事务释放后解析为必要值，不保留整份JSON或自由文本。 */
public final class MedicationObservation {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public final List<Row> rows;
    public final boolean invalid;
    public MedicationObservation(List<Row> rows,boolean invalid) { this.rows=Collections.unmodifiableList(new ArrayList<>(rows)); this.invalid=invalid; }
    public static MedicationObservation empty() { return new MedicationObservation(Collections.emptyList(),false); }
    public static MedicationObservation parse(String source) {
        if(source==null || source.trim().isEmpty()) return empty();
        List<Row> rows=new ArrayList<>();
        try {
            JsonNode root=JSON.readTree(source);
            if(root==null || !root.isObject()) throw new IOException("用药对象形状无效");
            for(String field:Arrays.asList("xyList","zcyList")) {
                JsonNode list=root.get(field); if(list==null) continue;
                if(!list.isArray()) throw new IOException("药物列表形状无效");
                for(JsonNode row:list) {
                    if(!row.isObject()) throw new IOException("药物行形状无效");
                    rows.add(new Row(text(row,"drugName"),text(row,"startTime"),text(row,"endTime"),"zlfa."+field));
                }
            }
            return new MedicationObservation(rows,false);
        } catch(IOException e) { return new MedicationObservation(Collections.emptyList(),true); }
    }
    private static String text(JsonNode node,String field) throws IOException {
        JsonNode value=node.get(field);
        if(value==null || value.isNull()) return null;
        if(!value.isTextual()) throw new IOException("药物字段形状无效");
        return value.textValue().trim().isEmpty()?null:value.textValue().trim();
    }
    public static final class Row {
        public final String name,start,end,field;
        public Row(String name,String start,String end,String field) { this.name=name; this.start=start; this.end=end; this.field=field; }
    }
}
