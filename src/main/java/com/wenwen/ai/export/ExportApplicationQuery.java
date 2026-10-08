package com.wenwen.ai.export;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.query.*;
import com.wenwen.ai.result.AnalysisCursor;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** 申请协议独立浅结构门；只有规范静态请求参与幂等摘要。 */
public final class ExportApplicationQuery {
    private static final ObjectMapper JSON=new ObjectMapper();
    public final String requestId,startDate,endDate,purposeCode,reason,hash;
    public final AiExportQuery export;
    private ExportApplicationQuery(JsonNode root)throws IOException {
        keys(root,"requestId","export","startDate","endDate","purposeCode","reason");
        requestId=text(root.get("requestId"));if(!AnalysisCursor.canonicalId(requestId))throw invalid();
        if(!root.get("export").isObject())throw invalid();
        export=AiExportQuery.read(new ByteArrayInputStream(JSON.writeValueAsBytes(root.get("export"))));
        if(!"EXPLICIT".equals(export.selectionMode))throw invalid();
        startDate=date(text(root.get("startDate")));endDate=date(text(root.get("endDate")));if(startDate.compareTo(endDate)>0)throw invalid();
        purposeCode=text(root.get("purposeCode"));if(!Arrays.asList("RESEARCH","DATA_CHECK","OTHER").contains(purposeCode))throw invalid();
        reason=text(root.get("reason")).trim();int n=reason.codePointCount(0,reason.length());if(n<1||n>1000)throw invalid();
        Map<String,Object> canonical=new LinkedHashMap<>();canonical.put("source",export.source);canonical.put("referenceId",export.referenceId);canonical.put("selectedItems",export.requested);canonical.put("startDate",startDate);canonical.put("endDate",endDate);canonical.put("purposeCode",purposeCode);canonical.put("reason",reason);
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(canonical));StringBuilder h=new StringBuilder();for(byte b:digest)h.append(String.format(Locale.ROOT,"%02x",b&255));hash=h.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static ExportApplicationQuery read(InputStream input)throws IOException{return new ExportApplicationQuery(root(input,16384,3));}
    public static long retry(InputStream input)throws IOException {JsonNode root=root(input,4096,1);keys(root,"applicationId");String value=text(root.get("applicationId"));if(!value.matches("[1-9][0-9]*"))throw invalid();try{return Long.parseLong(value);}catch(NumberFormatException e){throw invalid();}}
    private static JsonNode root(InputStream input,int max,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];while(out.size()<=max){int n=input.read(buffer,0,Math.min(buffer.length,max+1-out.size()));if(n<0)break;out.write(buffer,0,n);if(out.size()>max)throw new CohortException(413,"LIMIT_EXCEEDED","申请输入超出限制");}
        byte[] bytes=out.toByteArray();
        try{String decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();try(JsonParser parser=new JsonFactory().createParser(decoded)){int depth=0;JsonToken token;while((token=parser.nextToken())!=null){if(token==JsonToken.START_OBJECT||token==JsonToken.START_ARRAY){if(++depth>limit)throw invalid();}else if(token==JsonToken.END_OBJECT||token==JsonToken.END_ARRAY)depth--;}}}catch(IOException e){throw invalid();}
        return PrivateCohortQuery.strict(bytes);
    }
    public static String date(String value){try{LocalDate d=LocalDate.parse(value);if(d.getYear()<1000||d.getYear()>9999||!d.toString().equals(value))throw invalid();return value;}catch(java.time.format.DateTimeParseException e){throw invalid();}}
    public static String text(JsonNode n){if(n==null||!n.isTextual())throw invalid();return n.textValue();}
    public static void keys(JsonNode node,String...keys){Set<String> actual=new HashSet<>();node.fieldNames().forEachRemaining(actual::add);if(!node.isObject()||!actual.equals(new HashSet<>(Arrays.asList(keys))))throw invalid();}
    private static CohortException invalid(){return new CohortException(400,"INVALID_REQUEST","申请输入无效");}
}
