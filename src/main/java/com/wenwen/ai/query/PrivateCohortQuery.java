package com.wenwen.ai.query;
import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.scope.TrustedDoctor;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
/** 严格命令入口；身份只由可信调用方提供。 */
public final class PrivateCohortQuery {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public final String action,name,note,requestId,cohortId;
    public final CohortQuery filters;
    public final long page;
    public final int size;
    public final long version;
    public final boolean namePresent,notePresent;
    private PrivateCohortQuery(String action,String name,String note,String requestId,CohortQuery filters,String cohortId){
        this.version=0;
        this.namePresent=this.notePresent=false;
        this.page=1;
        this.size=20;
        this.cohortId=cohortId;
        this.action=action;
        this.name=name;
        this.note=note;
        this.requestId=requestId;
        this.filters=filters;
    }
    private PrivateCohortQuery(long page,int size){
        this.version=0;
        this.namePresent=this.notePresent=false;
        this.page=page;
        this.size=size;
        action="LIST";
        name=note=requestId=cohortId=null;
        filters=null;
    }
    private PrivateCohortQuery(String action,JsonNode root){
        this.action=action;
        cohortId=uuid(root.get("cohortId"));
        version=integer(root.get("version"));
        requestId=uuid(root.get("requestId"));
        namePresent=root.has("name");
        notePresent=root.has("note");
        if("RENAME".equals(action)&&!namePresent&&!notePresent)throw invalid();
        name=namePresent?name(root.get("name")):null;
        note=notePresent?note(root.get("note")):null;
        filters=null;
        page=1;
        size=20;
    }
    public static PrivateCohortQuery read(String action,InputStream input,TrustedDoctor doctor) throws IOException {
        JsonNode root=strict(bounded(input,"SAVE".equals(action)?128*1024:4096));
        if("LIST".equals(action)){
            keys(root,"page","size");
            long page=root.has("page")?integer(root.get("page")):1,size=root.has("size")?integer(root.get("size")):20;
            if(size>100)throw invalid();
            try{
                Math.multiplyExact(page-1,size);
            }catch(ArithmeticException e){
                throw invalid();
            }return new PrivateCohortQuery(page,(int)size);
        }
        if("OPEN".equals(action)){
            keys(root,"cohortId");
            return new PrivateCohortQuery(action,null,null,null,null,uuid(root.get("cohortId")));
        }
        if("RENAME".equals(action)){
            keys(root,"cohortId","version","requestId","name","note");
            return new PrivateCohortQuery(action,root);
        }
        if("DELETE".equals(action)){
            keys(root,"cohortId","version","requestId");
            return new PrivateCohortQuery(action,root);
        }
        keys(root,"name","note","filters","requestId");
        String name=name(root.get("name")),note=note(root.get("note")),requestId=uuid(root.get("requestId"));
        if(!root.has("filters")||!root.get("filters").isObject())throw invalid();
        CohortQuery filters=CohortQuery.read(new ByteArrayInputStream(JSON.writeValueAsBytes(Collections.singletonMap("filters",root.get("filters")))),doctor);
        return new PrivateCohortQuery(action,name,note,requestId,filters,null);
    }
    public static JsonNode strict(byte[] bytes) {
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode root=JSON.readTree(text);
            if(root==null||!root.isObject())throw invalid();
            unicode(root);
            return root;
        }catch(IOException e){
            throw invalid();
        }
    }
    private static void unicode(JsonNode node){
        if(node.isTextual())validUnicode(node.textValue());
        if(node.isContainerNode()){
            node.fields().forEachRemaining(x->validUnicode(x.getKey()));
            node.elements().forEachRemaining(PrivateCohortQuery::unicode);
        }
    }
    private static void validUnicode(String s){
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw invalid();
            }else if(Character.isLowSurrogate(c))throw invalid();
        }
    }
    private static byte[] bounded(InputStream in,int max)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] buf=new byte[4096];
        while(b.size()<=max){
            int n=in.read(buf,0,Math.min(buf.length,max+1-b.size()));
            if(n<0)return b.toByteArray();
            b.write(buf,0,n);
            if(b.size()>max)throw new CohortException(413,"LIMIT_EXCEEDED","输入超出限制");
        }throw invalid();
    }
    private static void keys(JsonNode n,String...allowed){
        Set<String>a=new HashSet<>(Arrays.asList(allowed));
        n.fieldNames().forEachRemaining(k->{
            if(!a.contains(k))throw invalid();
        });
    }
    private static String name(JsonNode n){
        if(n==null||!n.isTextual())throw invalid();
        String s=n.textValue().trim();
        if(s.codePointCount(0,s.length())<1||s.codePointCount(0,s.length())>30)throw invalid();
        return s;
    }
    private static String note(JsonNode n){
        if(n==null||n.isNull())return null;
        if(!n.isTextual()||n.textValue().codePointCount(0,n.textValue().length())>200)throw invalid();
        return n.textValue();
    }
    private static long integer(JsonNode n){
        if(n==null||!n.isIntegralNumber()||!n.canConvertToLong()||n.longValue()<1)throw invalid();
        return n.longValue();
    }
    private static String uuid(JsonNode n){
        if(n==null||!n.isTextual()||!n.textValue().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw invalid();
        String s=n.textValue();
        try{
            if(!UUID.fromString(s).toString().equals(s))throw invalid();
            return s;
        }catch(IllegalArgumentException e){
            throw invalid();
        }
    }
    public static CohortException invalid(){
        return new CohortException(400,"INVALID_REQUEST","队列输入无效");
    }
}
