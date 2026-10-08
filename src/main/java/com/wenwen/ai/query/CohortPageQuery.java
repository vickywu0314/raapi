package com.wenwen.ai.query;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;
public final class CohortPageQuery {
    private final String analysisId,cursor;
    private CohortPageQuery(String id,String cursor){analysisId=id;this.cursor=cursor;}
    public String getAnalysisId(){return analysisId;}public String getCursor(){return cursor;}
    public static CohortPageQuery read(InputStream input)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buffer=new byte[1024];while(b.size()<=4096){int n=input.read(buffer,0,Math.min(buffer.length,4097-b.size()));if(n<0)break;b.write(buffer,0,n);if(b.size()>4096)throw new CohortException(413,"LIMIT_EXCEEDED","查询输入超出限制");}
        try{JsonNode node=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(b.toByteArray());if(node==null||!node.isObject()||node.size()!=2||!node.has("analysisId")||!node.has("cursor"))throw invalid();
            for(String k:Arrays.asList("analysisId","cursor"))if(!node.path(k).isTextual()||node.path(k).textValue().isEmpty())throw invalid();return new CohortPageQuery(node.path("analysisId").textValue(),node.path("cursor").textValue());
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw invalid();}
    }
    private static CohortException invalid(){return new CohortException(400,"INVALID_REQUEST","分页输入无效");}
}
