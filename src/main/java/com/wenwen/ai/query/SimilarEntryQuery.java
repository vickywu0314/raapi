package com.wenwen.ai.query;

import com.fasterxml.jackson.databind.*;
import java.io.*;

/** 相似入口仅接收索引标识；医生身份由调用方的可信 Principal 提供。 */
public final class SimilarEntryQuery {
    private static final ObjectMapper JSON=new ObjectMapper()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final long indexPatientId;
    private SimilarEntryQuery(long id) { indexPatientId=id; }
    public long getIndexPatientId() { return indexPatientId; }
    public static SimilarEntryQuery read(InputStream input) throws IOException {
        byte[] bytes=bounded(input);
        try {
            String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            JsonNode node=JSON.readTree(text);
            if(node==null||!node.isObject()||node.size()!=2||!node.has("source")||!node.has("indexPatientId"))throw invalid();
            if(!node.path("source").isTextual()||!"SIMILAR".equals(node.path("source").textValue()))throw invalid();
            return new SimilarEntryQuery(CohortQuery.id(node.path("indexPatientId")));
        } catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException|CohortException e) { throw invalid(); }
    }
    private static byte[] bounded(InputStream input) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];
        while(bytes.size()<=4096) {
            int count=input.read(buffer,0,Math.min(buffer.length,4097-bytes.size()));
            if(count<0)return bytes.toByteArray();bytes.write(buffer,0,count);
            if(bytes.size()>4096)throw new CohortException(413,"LIMIT_EXCEEDED","查询输入超出限制");
        }
        throw new CohortException(413,"LIMIT_EXCEEDED","查询输入超出限制");
    }
    private static CohortException invalid() { return new CohortException(400,"INVALID_REQUEST","相似入口输入无效"); }
}
