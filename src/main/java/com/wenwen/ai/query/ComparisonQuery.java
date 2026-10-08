package com.wenwen.ai.query;
import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.result.AnalysisCursor;
import java.io.*;
import java.util.*;
/** 双侧原节点先完整验证；语法不会借定义/源连接或预去重原ids。 */
public final class ComparisonQuery {
    private static final ObjectMapper JSON=new ObjectMapper();
    public final Side a,b;
    public final boolean excludeOverlap;
    public static final class Side {
        public final String source,cohortId;
        public final CohortQuery filters;
        private Side(String source,String id,CohortQuery filters){this.source=source;cohortId=id;this.filters=filters;}
    }
    private ComparisonQuery(Side a,Side b,boolean exclude){this.a=a;this.b=b;excludeOverlap=exclude;}
    public static ComparisonQuery readCompare(InputStream input,TrustedDoctor doctor) throws IOException {
        JsonNode root=PrivateCohortQuery.strict(bounded(input,256*1024));keys(root,"a","b","excludeOverlap");
        JsonNode exclusion=root.get("excludeOverlap");if(exclusion!=null&&!exclusion.isBoolean())throw invalid();
        Side a=side(root.get("a"),doctor),b=side(root.get("b"),doctor);
        return new ComparisonQuery(a,b,exclusion!=null&&exclusion.booleanValue());
    }
    private static Side side(JsonNode node,TrustedDoctor doctor) throws IOException {
        if(node==null||!node.isObject())throw invalid();JsonNode source=node.get("source");if(source==null||!source.isTextual())throw invalid();
        if("SAVED".equals(source.textValue())){keys(node,"source","cohortId");return new Side("SAVED",uuid(node.get("cohortId")),null);}
        if(!"FILTERS".equals(source.textValue()))throw invalid();keys(node,"source","filters");JsonNode filters=node.get("filters");if(filters==null||!filters.isObject())throw invalid();
        try{return new Side("FILTERS",null,CohortQuery.read(new ByteArrayInputStream(JSON.writeValueAsBytes(Collections.singletonMap("filters",filters))),doctor));}
        catch(CohortException e){if(e.getStatus()==400)throw invalid();throw e;}
    }
    public static String readResult(InputStream input)throws IOException {JsonNode root=PrivateCohortQuery.strict(bounded(input,4096));keys(root,"compareResultId");return uuid(root.get("compareResultId"));}
    private static String uuid(JsonNode node){if(node==null||!node.isTextual()||!AnalysisCursor.canonicalId(node.textValue()))throw invalid();return node.textValue();}
    private static void keys(JsonNode node,String...allowed){Set<String> keys=new HashSet<>(Arrays.asList(allowed));node.fieldNames().forEachRemaining(key->{if(!keys.contains(key))throw invalid();});}
    private static byte[] bounded(InputStream in,int max)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];while(bytes.size()<=max){int n=in.read(buffer,0,Math.min(buffer.length,max+1-bytes.size()));if(n<0)return bytes.toByteArray();bytes.write(buffer,0,n);if(bytes.size()>max)throw new CohortException(413,"LIMIT_EXCEEDED","比较输入超出限制");}throw invalid();
    }
    private static CohortException invalid(){return new CohortException(400,"INVALID_REQUEST","比较输入无效");}
}
