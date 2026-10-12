package com.wenwen.ai.query;
import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.scope.TrustedDoctor;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** 严格请求解析；身份已由controller确定，问题不改变范围。 */
public final class ChatQuery {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public final String kind,question;public final List<String> previousQuestions;public final CohortQuery cohort;public final Long patientId,visitId;
    private ChatQuery(String kind,String question,List<String> history,CohortQuery cohort){this.kind=kind;this.question=question;previousQuestions=Collections.unmodifiableList(history);this.cohort=cohort;patientId=visitId=null;}
    private ChatQuery(String question,List<String> history,long patient,Long visit){kind="PATIENT";this.question=question;previousQuestions=Collections.unmodifiableList(history);cohort=null;patientId=patient;visitId=visit;}
    public static ChatQuery read(InputStream input,TrustedDoctor doctor)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];while(bytes.size()<=131072){int n=input.read(b,0,Math.min(b.length,131073-bytes.size()));if(n<0)break;bytes.write(b,0,n);if(bytes.size()>131072)throw limit();}
        JsonNode body;
        try{
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();if(!text.isEmpty()&&text.charAt(0)=='\ufeff')throw invalid();
            int depth=0,tokens=0;boolean complete=false;try(com.fasterxml.jackson.core.JsonParser parser=JSON.getFactory().createParser(text)){com.fasterxml.jackson.core.JsonToken token;while((token=parser.nextToken())!=null){if(complete||tokens==0&&token!=com.fasterxml.jackson.core.JsonToken.START_OBJECT)throw invalid();if(++tokens>20000)throw limit();if(token==com.fasterxml.jackson.core.JsonToken.START_OBJECT||token==com.fasterxml.jackson.core.JsonToken.START_ARRAY){if(++depth>8)throw limit();}else if(token==com.fasterxml.jackson.core.JsonToken.END_OBJECT||token==com.fasterxml.jackson.core.JsonToken.END_ARRAY){if(--depth==0)complete=true;}if(token==com.fasterxml.jackson.core.JsonToken.VALUE_STRING||token==com.fasterxml.jackson.core.JsonToken.FIELD_NAME)paired(parser.getText());}if(!complete)throw invalid();}
            body=JSON.readTree(text);
        }catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException malformed){throw invalid();}
        keys(body,"scope","question","previousQuestions");String q=question(body.get("question"));
        List<String> history=new ArrayList<>();if(body.has("previousQuestions")){JsonNode h=body.get("previousQuestions");if(!h.isArray()||h.size()>3)throw invalid();for(JsonNode v:h)history.add(question(v));}
        JsonNode scope=body.get("scope");if(scope==null||!scope.isObject()||!scope.path("kind").isTextual())throw invalid();String kind=scope.path("kind").textValue();
        if("PATIENT".equals(kind)){keys(scope,"kind","patientId","visitId");return new ChatQuery(q,history,PatientDetailQuery.readId(scope.path("patientId")),scope.hasNonNull("visitId")?PatientDetailQuery.readId(scope.get("visitId")):null);}
        if("ALL".equals(kind)){keys(scope,"kind");return new ChatQuery(kind,q,history,CohortQuery.read(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),doctor));}
        if(!"COHORT".equals(kind))throw invalid();keys(scope,"kind","filters");JsonNode f=scope.get("filters");keys(f,"studyCode","sex","age","sero","tx","at","act","cm","data","ids");if(f.size()!=10||!"RA".equals(f.path("studyCode").asText())||!Arrays.asList("now","6m").contains(f.path("at").asText()))throw invalid();
        try{return new ChatQuery(kind,q,history,CohortQuery.read(new ByteArrayInputStream(JSON.writeValueAsBytes(Collections.singletonMap("filters",f))),doctor));}catch(CohortException e){if(e.getStatus()==413)throw e;throw invalid();}
    }
    private static void paired(String text){for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}}
    private static String question(JsonNode v){if(v==null||!v.isTextual())throw invalid();String q=v.textValue().trim();int n=q.codePointCount(0,q.length());if(n<1||n>500)throw invalid();return q;}
    private static void keys(JsonNode v,String... fields){if(v==null||!v.isObject())throw invalid();Set<String> allowed=new HashSet<>(Arrays.asList(fields));v.fieldNames().forEachRemaining(f->{if(!allowed.contains(f))throw invalid();});}
    private static CohortException invalid(){return new CohortException(400,"INVALID_REQUEST","问答请求无效");}
    private static CohortException limit(){return new CohortException(413,"LIMIT_EXCEEDED","问答输入超出限制");}
}
