package com.wenwen.ai.result;
import com.fasterxml.jackson.databind.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
/** 聚合v1载荷，十进制从解码开始保持BigDecimal。 */
public final class ComparisonResultCodec {
    private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public byte[] encode(Map<String,Object> payload){ComparisonPayloadValidation.validate(payload);try{return json.writeValueAsBytes(payload);}catch(java.io.IOException e){throw AnalysisSettings.unavailable();}}
    @SuppressWarnings("unchecked") public Map<String,Object> decode(ComparisonRun run){
        byte[] bytes=run.getPayload();if(!AnalysisCursor.canonicalId(run.getId())||run.getScopeFingerprint()==null||!run.getScopeFingerprint().matches("[0-9a-f]{64}")||run.getExpiresAtMs()<=run.getCreatedAtMs()||run.getPayloadVersion()!=1||bytes==null||!AnalysisValues.sha256(bytes).equals(run.getPayloadSha256()))throw AnalysisSettings.unavailable();
        try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();Map<String,Object> payload=json.readValue(text,Map.class);ComparisonPayloadValidation.validate(payload);Map<?,?> meta=(Map<?,?>)payload.get("meta");if(java.time.Instant.parse((String)meta.get("computedAt")).toEpochMilli()!=run.getCreatedAtMs()||!InstantString(run.getExpiresAtMs()).equals(meta.get("expiresAt")))throw AnalysisSettings.unavailable();return payload;}catch(java.io.IOException e){throw AnalysisSettings.unavailable();}
    }
    private static String InstantString(long epoch){return java.time.Instant.ofEpochMilli(epoch).toString();}
}
