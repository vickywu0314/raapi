package com.wenwen.ai.query;

import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.scope.TrustedDoctor;
import java.io.*;
import java.util.*;

/** 入口校验；未经校验的 doctorId 永远不作为范围来源。 */
public final class CohortQuery {
    private static final int MAX_BODY_BYTES = 128 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final String act;
    private final String at;
    public String getAt() { return at; }
    private final String sex, age, sero, cm, tx, data;
    public String getData() { return data; }
    public String getTx() { return tx; }
    public String getSex() { return sex; }
    public String getCm() { return cm; }
    public String getSero() { return sero; }
    public String getAge() { return age; }
    private final Set<Long> ids;
    private CohortQuery(String act, Set<Long> ids, String sex, String age, String sero, String cm, String tx, String at, String data) { this.data=data; this.at=at==null?"now":at; this.tx=tx; this.cm=cm; this.sero=sero; this.sex=sex; this.age=age; this.act = act; this.ids = ids == null ? null : Collections.unmodifiableSet(ids); }
    public Set<Long> getIds() { return ids; }
    public String getAct() { return act; }
    public static CohortQuery read(InputStream input, TrustedDoctor doctor) throws IOException {
        JsonNode body;
        try { body = JSON.readTree(bounded(input)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw invalid(); }
        if (body == null || !body.isObject()) throw invalid();
        keys(body,"filters","doctorId");
        JsonNode assertion = body.get("doctorId");
        if (assertion != null && !assertion.isNull() && id(assertion) != doctor.getId())
            throw new CohortException(403,"FORBIDDEN","医生身份不匹配");
        JsonNode filters = body.get("filters");
        if (filters == null || filters.isNull()) filters = JSON.createObjectNode();
        if (!filters.isObject()) throw invalid();
        keys(filters,"studyCode","at","act","ids","sex","age","sero","cm","tx","data");
        String data=text(filters.get("data"));
        if(data!=null && !Arrays.asList("complete","missing").contains(data)) throw invalid();
        String tx=text(filters.get("tx"));
        if(tx!=null && !Arrays.asList("csDMARD","bio","TNFi","JAKi","IL-6i","Abatacept").contains(tx)) throw invalid();
        String cm=text(filters.get("cm"));
        if (cm!=null && !Arrays.asList("FM","AS","none").contains(cm)) throw invalid();
        String sero=text(filters.get("sero"));
        if (sero!=null && !"1".equals(sero)) throw invalid();
        String sex=text(filters.get("sex")), age=text(filters.get("age"));
        if (sex != null && !Arrays.asList("F","M").contains(sex)) throw invalid();
        if (age != null) {
            if (!age.matches("[0-9]{1,3}-[0-9]{0,3}")) throw invalid();
            String[] bounds=age.split("-",-1);
            int min=Integer.parseInt(bounds[0]), max=bounds[1].isEmpty()?120:Integer.parseInt(bounds[1]);
            if (min>120 || max>120 || min>max) throw invalid();
        }
        String study = text(filters.get("studyCode"));
        String at = text(filters.get("at"));
        String act = text(filters.get("act"));
        if ((study != null && !"RA".equals(study)) || (at != null && !Arrays.asList("now","6m").contains(at))) throw invalid();
        if (act != null && !Arrays.asList("target","mod-high","remission","low","moderate","high").contains(act)) throw invalid();
        Set<Long> ids = null;
        JsonNode submitted = filters.get("ids");
        if (submitted != null && !submitted.isNull()) {
            if (!submitted.isArray()) throw invalid();
            if (submitted.size() > 5000) throw limit();
            ids = new TreeSet<>();
            for (JsonNode value : submitted) ids.add(id(value));
        }
        return new CohortQuery(act, ids, sex, age, sero, cm, tx, at, data);
    }
    private static byte[] bounded(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (bytes.size() <= MAX_BODY_BYTES) {
            int count = input.read(buffer,0,Math.min(buffer.length,MAX_BODY_BYTES+1-bytes.size()));
            if (count == -1) return bytes.toByteArray();
            bytes.write(buffer,0,count);
            if (bytes.size() > MAX_BODY_BYTES) throw limit();
        }
        throw limit();
    }
    private static CohortException limit() { return new CohortException(413,"LIMIT_EXCEEDED","查询输入超出限制"); }
    private static void keys(JsonNode node, String... allowed) {
        Set<String> keys = new HashSet<>(Arrays.asList(allowed));
        node.fieldNames().forEachRemaining(key -> { if (!keys.contains(key)) throw invalid(); });
    }
    private static String text(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue().isEmpty() ? null : value.textValue();
    }
    public static long id(JsonNode value) {
        if (!value.isTextual() || !value.textValue().matches("[1-9][0-9]*")) throw invalid();
        try { return Long.parseLong(value.textValue()); }
        catch (NumberFormatException e) { throw invalid(); }
    }
    public static CohortException invalid() { return new CohortException(400,"INVALID_FILTER","查询条件无效"); }
}
