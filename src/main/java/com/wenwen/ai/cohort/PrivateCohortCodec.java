package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.ai.query.PrivateCohortQuery;
import java.security.MessageDigest;
import static com.wenwen.vo.AiCohortVo.object;
/** v1仅编码显式规范条件及命令hash，绝不接收计算成员。 */
public final class PrivateCohortCodec {
    private static final ObjectMapper JSON=new ObjectMapper();
    public static byte[] encode(Object value){
        try{
            return JSON.writeValueAsBytes(value);
        }catch(java.io.IOException e){
            throw new IllegalStateException("编码失败",e);
        }
    }
    public static String sha(byte[] bytes){
        try{
            StringBuilder s=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));
            return s.toString();
        }catch(java.security.NoSuchAlgorithmException e){
            throw new IllegalStateException(e);
        }
    }
    public static com.wenwen.ai.query.CohortQuery decode(PrivateCohortDefinition d){
        String name=d.getName();
        if(name==null||!name.equals(name.trim())||name.codePointCount(0,name.length())<1||name.codePointCount(0,name.length())>30||d.getNote()!=null&&d.getNote().codePointCount(0,d.getNote().length())>200||d.getId()==null||!d.getId().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")||d.getOwnerDoctorId()<1||!"RA".equals(d.getStudyCode()))throw unavailable();
        byte[] bytes=d.getFiltersUtf8();
        if(d.getVersion()<1||d.getFiltersVersion()!=1||bytes==null||bytes.length>128*1024||!sha(bytes).equals(d.getFiltersSha256()))throw unavailable();
        try{
            com.fasterxml.jackson.databind.JsonNode root=PrivateCohortQuery.strict(bytes);
            java.util.Set<String> keys=new java.util.HashSet<>(java.util.Arrays.asList("studyCode","at","act","ids","sex","age","sero","cm","tx","data"));
            if(root.size()!=keys.size())throw unavailable();
            root.fieldNames().forEachRemaining(k->{
                if(!keys.contains(k))throw unavailable();
            });
            com.wenwen.ai.query.CohortQuery q=com.wenwen.ai.query.CohortQuery.read(new java.io.ByteArrayInputStream(encode(java.util.Collections.singletonMap("filters",root))),new com.wenwen.ai.scope.TrustedDoctor(d.getOwnerDoctorId()));
            if(!java.util.Arrays.equals(bytes,encode(q.normalizedFilters())))throw unavailable();
            return q;
        }catch(java.io.IOException|com.wenwen.ai.query.CohortException e){
            throw unavailable();
        }
    }
    private static com.wenwen.ai.query.CohortException unavailable(){
        return new com.wenwen.ai.query.CohortException(503,"SERVICE_UNAVAILABLE","定义暂不可用");
    }
    public static String commandHash(PrivateCohortQuery q){
        if("DELETE".equals(q.action))return sha(encode(object("protocolVersion",1,"action",q.action,"cohortId",q.cohortId,"version",q.version)));
        if("RENAME".equals(q.action))return sha(encode(object("protocolVersion",1,"action",q.action,"cohortId",q.cohortId,"version",q.version,"namePresent",q.namePresent,"name",q.name,"notePresent",q.notePresent,"note",q.note)));
        return sha(encode(object("protocolVersion",1,"action",q.action,"name",q.name,"note",q.note,"filters",q.filters.normalizedFilters())));
    }
}
