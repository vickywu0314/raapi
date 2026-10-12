package com.wenwen.ai.query;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
/** 可信搜索只接受关键词和分页；不接收医生身份。 */
public final class PatientSearchQuery {
    private final String keyword;private final int page,size;
    private PatientSearchQuery(String keyword,int page,int size){this.keyword=keyword;this.page=page;this.size=size;}
    public String getKeyword(){return keyword;}public int getPage(){return page;}public int getSize(){return size;}
    public static PatientSearchQuery read(InputStream input)throws IOException{
        JsonNode n=PatientDetailQuery.readObject(input);
        if(n==null||!n.isObject()||n.size()!=3||!n.has("keyword")||!n.has("page")||!n.has("size")||!n.path("keyword").isTextual())throw invalid();
        String k=n.path("keyword").textValue();
        if(k.codePointCount(0,k.length())>200)throw invalid();
        for(int i=0;i<k.length();i++){char c=k.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=k.length()||!Character.isLowSurrogate(k.charAt(++i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}
        if(!n.path("page").isIntegralNumber()||!n.path("page").canConvertToInt()||n.path("page").intValue()<1||!n.path("size").isIntegralNumber()||!n.path("size").canConvertToInt()||n.path("size").intValue()<1||n.path("size").intValue()>200)throw invalid();
        return new PatientSearchQuery(k,n.path("page").intValue(),n.path("size").intValue());
    }
    private static CohortException invalid(){return new CohortException(400,"INVALID_REQUEST","患者搜索输入无效");}
}
