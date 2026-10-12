package com.wenwen.ai.query;

import com.fasterxml.jackson.databind.*;
import java.io.*;

/** 患者详情仅接收患者标识；医生身份由调用方的可信 Principal 提供。 */
public final class PatientDetailQuery {
    private static final ObjectMapper JSON=new ObjectMapper()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final long patientId;
    private PatientDetailQuery(long id) { patientId=id; }
    public long getPatientId() { return patientId; }
    public static PatientDetailQuery read(InputStream input) throws IOException {
        JsonNode node=readObject(input);
        if(node==null||!node.isObject()||node.size()!=1||!node.has("patientId"))throw invalid();
        return new PatientDetailQuery(readId(node.path("patientId")));
    }
    static long readId(JsonNode node){try{return CohortQuery.id(node);}catch(CohortException e){throw invalid();}}
    static JsonNode readObject(InputStream input) throws IOException {
        byte[] bytes=bounded(input);
        try {
            String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            JsonNode node=JSON.readTree(text);
            return node;
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
    private static CohortException invalid() { return new CohortException(400,"INVALID_REQUEST","患者详情输入无效"); }
}
