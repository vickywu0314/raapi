package com.wenwen.ai.result;
import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.query.CohortException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static com.wenwen.vo.AiCohortVo.object;
public final class AnalysisCursor {
    private final AnalysisSettings settings;
    private final ObjectMapper json=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public AnalysisCursor(AnalysisSettings settings){this.settings=settings;}
    private byte[] mac(byte[] b){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(settings.key(),"HmacSHA256"));return m.doFinal(b);}catch(java.security.GeneralSecurityException e){throw AnalysisSettings.unavailable();}}
    public String issue(String id,long owner,int offset,long expires){try{byte[] bytes=json.writeValueAsBytes(object("v",1,"analysisId",id,"doctorId",owner,"nextOffset",offset,"sort",AnalysisValues.SORT,"expiresAtMs",expires));return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(bytes));}catch(java.io.IOException e){throw AnalysisSettings.unavailable();}}
    public Token verify(String bodyId,String value){
        try{
            if(value==null||value.length()>2048||!value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))throw invalid();String[] p=value.split("\\.");byte[] bytes=Base64.getUrlDecoder().decode(p[0]),signature=Base64.getUrlDecoder().decode(p[1]);
            if(bytes.length>1536||signature.length!=32||!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(p[0])||!Base64.getUrlEncoder().withoutPadding().encodeToString(signature).equals(p[1])||!MessageDigest.isEqual(mac(bytes),signature))throw invalid();
            String payload=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            JsonNode n=json.readTree(payload);Set<String> keys=new HashSet<>();if(n==null||!n.isObject())throw invalid();n.fieldNames().forEachRemaining(keys::add);
            if(!keys.equals(new HashSet<>(Arrays.asList("v","analysisId","doctorId","nextOffset","sort","expiresAtMs"))))throw invalid();
            if(!n.path("v").isIntegralNumber()||!n.path("v").canConvertToInt()||n.path("v").intValue()!=1||!n.path("analysisId").isTextual()||!canonicalId(n.path("analysisId").asText())||!n.path("analysisId").asText().equals(bodyId)||!n.path("sort").isTextual()||!AnalysisValues.SORT.equals(n.path("sort").asText()))throw invalid();
            for(String k:Arrays.asList("doctorId","nextOffset","expiresAtMs"))if(!n.path(k).isIntegralNumber()||!n.path(k).canConvertToLong())throw invalid();
            long owner=n.path("doctorId").longValue(),offset=n.path("nextOffset").longValue(),expires=n.path("expiresAtMs").longValue();if(owner<=0||offset<10||(offset-10)%20!=0||offset>Integer.MAX_VALUE)throw invalid();
            return new Token(bodyId,owner,(int)offset,expires);
        }catch(CohortException e){throw e;}catch(java.io.IOException|RuntimeException e){throw invalid();}
    }
    public static boolean canonicalId(String id){try{return id!=null&&id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")&&UUID.fromString(id).toString().equals(id);}catch(RuntimeException e){return false;}}
    public static CohortException invalid(){return new CohortException(400,"CURSOR_INVALID","分页游标无效");}
    public static final class Token {public final String id;public final long owner,expires;public final int offset;private Token(String id,long owner,int offset,long expires){this.id=id;this.owner=owner;this.offset=offset;this.expires=expires;}}
}
