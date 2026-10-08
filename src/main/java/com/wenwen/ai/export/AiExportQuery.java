package com.wenwen.ai.export;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.wenwen.ai.query.*;
import com.wenwen.ai.result.AnalysisCursor;
import java.io.*;
import java.util.*;
/** 唯一可信请求协议，客户端不能提供身份或政策结论。 */
public final class AiExportQuery {
    private static final JsonFactory STRUCTURE_JSON=new JsonFactory();
    public static final List<String> FIELDS=Collections.unmodifiableList(Arrays.asList("patientId","sex","age","studyNo","treatmentCategory","genericDrugIds","line","schemeDurationMonths","das28Base","das28At","das28Current","crpCurrent","fm","as","completeness"));
    public static final List<String> METRICS=Collections.unmodifiableList(Arrays.asList("n","age","duration","das28At","crpAt","haqAt","female","sero","target","complete","activity","line"));
    public final String source,referenceId,selectionMode;
    public final List<String> requested;
    private AiExportQuery(String source,String id,String mode,List<String> items){this.source=source;referenceId=id;selectionMode=mode;requested=Collections.unmodifiableList(items);}
    public static AiExportQuery read(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];while(out.size()<=16384){int n=in.read(buffer,0,Math.min(buffer.length,16385-out.size()));if(n<0)break;out.write(buffer,0,n);if(out.size()>16384)throw new CohortException(413,"LIMIT_EXCEEDED","导出输入超出限制");}
        byte[] bytes=out.toByteArray();requireShallowStructure(bytes);JsonNode root=PrivateCohortQuery.strict(bytes);String source=text(root.get("source"));boolean cohort="COHORT".equals(source);if(!cohort&&!"COMPARE".equals(source))throw invalid();String ref=cohort?"analysisId":"compareResultId",selector=cohort?"fields":"metrics";
        Set<String> keys=new HashSet<>(Arrays.asList("source",ref,selector));root.fieldNames().forEachRemaining(k->{if(!keys.contains(k))throw invalid();});String id=text(root.get(ref));if(!AnalysisCursor.canonicalId(id))throw invalid();List<String> catalogue=cohort?FIELDS:METRICS,items=new ArrayList<>();
        if(!root.has(selector))return new AiExportQuery(source,id,"DEFAULT",new ArrayList<>(catalogue));JsonNode selected=root.get(selector);if(!selected.isArray()||selected.size()==0)throw invalid();Set<String> values=new HashSet<>();for(JsonNode item:selected){String value=text(item);if(!catalogue.contains(value)||!values.add(value))throw invalid();}for(String item:catalogue)if(values.contains(item))items.add(item);return new AiExportQuery(source,id,"EXPLICIT",items);
    }
    /** 合法协议仅根对象与字符串选择数组两层；迭代token先拒绝，树解析/Unicode递归不接收深结构。 */
    private static void requireShallowStructure(byte[] bytes){
        try(JsonParser parser=STRUCTURE_JSON.createParser(bytes)){
            int depth=0;JsonToken token;
            while((token=parser.nextToken())!=null){
                if(token==JsonToken.START_OBJECT||token==JsonToken.START_ARRAY){if(++depth>2)throw invalid();}
                else if(token==JsonToken.END_OBJECT||token==JsonToken.END_ARRAY)depth--;
            }
        }catch(IOException e){throw invalid();}
    }
    private static String text(JsonNode node){if(node==null||!node.isTextual())throw invalid();return node.textValue();}
    private static CohortException invalid(){return new CohortException(400,"INVALID_REQUEST","导出输入无效");}
}
