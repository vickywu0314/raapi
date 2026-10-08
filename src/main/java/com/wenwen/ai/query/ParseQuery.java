package com.wenwen.ai.query;

import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.scope.TrustedDoctor;
import java.io.*;
import java.util.*;

/** 本片两个有界入口；过滤条件仍由现存公共 CohortQuery 验证。 */
public final class ParseQuery {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final String question,status,questionRef;
    private final Map<String,Object> current,candidate;
    private final boolean confirm;
    private ParseQuery(String question,String status,String questionRef,Map<String,Object> current,Map<String,Object> candidate,boolean confirm) {
        this.question=question;this.status=status;this.questionRef=questionRef;this.current=current;this.candidate=candidate;this.confirm=confirm;
    }
    public String getQuestion(){return question;} public String getStatus(){return status;} public String getQuestionRef(){return questionRef;}
    public Map<String,Object> getCurrent(){return current;} public Map<String,Object> getCandidate(){return candidate;} public boolean isConfirm(){return confirm;}
    public static ParseQuery parse(InputStream input) throws IOException {
        JsonNode root=read(input,4096);
        keys(root,"question");if(!root.has("question")||!root.get("question").isTextual())throw invalid();
        String question=root.get("question").textValue();
        if(question.codePointCount(0,question.length())>200)throw invalid();
        return new ParseQuery(question,null,null,null,null,false);
    }
    public static ParseQuery apply(InputStream input,TrustedDoctor doctor) throws IOException {
        JsonNode root=read(input,128*1024);
        keys(root,"currentFilters","candidateFilters","parseStatus","questionRef","confirmPartial");
        if(!root.has("currentFilters")||!root.get("currentFilters").isObject()||!root.has("candidateFilters")||!root.has("parseStatus")||!root.get("parseStatus").isTextual()||!root.has("questionRef")||!root.get("questionRef").isTextual())throw invalid();
        String status=root.get("parseStatus").textValue(),ref=root.get("questionRef").textValue();
        if(!Arrays.asList("OK","PARTIAL","EMPTY","UNRECOGNIZED","UNSUPPORTED").contains(status)||!ref.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid();
        if(root.has("confirmPartial")&&!root.get("confirmPartial").isBoolean())throw invalid();
        Map<String,Object> current=normalize(root.get("currentFilters"),doctor);
        JsonNode offered=root.get("candidateFilters");if(!offered.isNull()&&!offered.isObject())throw invalid();
        Map<String,Object> candidate=null;
        if(!offered.isNull()){
            Set<String> allowed=new HashSet<>(Arrays.asList("sex","age","sero","tx","at","act","cm","data"));
            offered.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw CohortQuery.invalid();});
            Map<String,Object> normalized=normalize(offered,doctor);candidate=new LinkedHashMap<>();
            Iterator<String> fields=offered.fieldNames();while(fields.hasNext()){String field=fields.next();JsonNode value=offered.get(field);candidate.put(field,value.isNull()||value.isTextual()&&value.textValue().isEmpty()?null:normalized.get(field));}
        }
        boolean meaningful=false;if(candidate!=null)for(Object value:candidate.values())if(value instanceof String&&!((String)value).isEmpty())meaningful=true;
        boolean needs="OK".equals(status)||"PARTIAL".equals(status);
        if(needs!=meaningful)throw invalid();
        return new ParseQuery(null,status,ref,current,candidate,root.path("confirmPartial").asBoolean());
    }
    public static Map<String,Object> normalize(Object filters,TrustedDoctor doctor) throws IOException {
        byte[] bytes=JSON.writeValueAsBytes(Collections.singletonMap("filters",filters));
        return CohortQuery.read(new ByteArrayInputStream(bytes),doctor).normalizedFilters();
    }
    private static CohortException invalid(){return new CohortException(400,"INVALID_REQUEST","解析输入无效");}
    private static void keys(JsonNode node,String... allowed){Set<String> accepted=new HashSet<>(Arrays.asList(allowed));node.fieldNames().forEachRemaining(key->{if(!accepted.contains(key))throw invalid();});}
    private static JsonNode read(InputStream input,int max) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];
        while(bytes.size()<=max){int n=input.read(buffer,0,Math.min(buffer.length,max+1-bytes.size()));if(n<0)break;bytes.write(buffer,0,n);if(bytes.size()>max)throw new CohortException(413,"LIMIT_EXCEEDED","解析输入超出限制");}
        try{String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
            JsonNode root=JSON.readTree(text);if(root==null||!root.isObject())throw invalid();strings(root,0);return root;
        }catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException e){throw invalid();}
    }

    private static void strings(JsonNode node,int depth){
        if(depth>4)throw invalid();
        if(node.isTextual())paired(node.textValue());
        else if(node.isObject()){Iterator<String> fields=node.fieldNames();while(fields.hasNext()){String key=fields.next();paired(key);strings(node.get(key),depth+1);}}
        else if(node.isArray())for(JsonNode value:node)strings(value,depth+1);
    }
    private static void paired(String text){
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}
    }

}
