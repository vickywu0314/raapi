package com.wenwen.ai.export;

import com.fasterxml.jackson.databind.*;
import com.wenwen.service.impl.AiExportServiceImpl;
import java.io.*;
import java.util.*;

/** 保存已验证引用的安全投影，永不保存rows或自由说明。 */
public final class ExportApplicationSummary {
    public static final String VERSION="dev-export-apply-v01";
    private static final ObjectMapper JSON=new ObjectMapper();
    private ExportApplicationSummary(){}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    public static String build(ExportApplicationQuery q,ExportContentReader.Content c,ExportFieldPolicy.Window w,List<Map<String,Object>> blocked)throws IOException {
        Map<String,Object> s=new LinkedHashMap<>();s.put("schemaVersion",VERSION);s.put("source",q.export.source);s.put("referenceId",q.export.referenceId);s.put("payloadSha256",c.hash);s.put("scopeFingerprint",c.fingerprint);s.put("selectedItems",q.export.requested);s.put("conditions",AiExportServiceImpl.conditions(q.export,c.payload));Map<String,Object> counts=new LinkedHashMap<>();boolean cohort="COHORT".equals(q.export.source);counts.put("cohortN",cohort?c.payload.get("n"):null);counts.put("aN",cohort?null:map(c.payload.get("a")).get("n"));counts.put("bN",cohort?null:map(c.payload.get("b")).get("n"));s.put("counts",counts);s.put("blocked",blocked);s.put("freeStart",w.freeStart.toString());s.put("today",w.today.toString());s.put("sourcePolicyVersions",map(c.payload.get("meta")).get("policyVersions"));String encoded=JSON.writeValueAsString(s);read(encoded);return encoded;
    }
    public static JsonNode read(String text)throws IOException {
        if(text==null||text.getBytes("UTF-8").length>16384)throw bad();JsonNode n;
        try(com.fasterxml.jackson.core.JsonParser parser=new com.fasterxml.jackson.core.JsonFactory().createParser(text)){int depth=0;com.fasterxml.jackson.core.JsonToken token;while((token=parser.nextToken())!=null){if(token==com.fasterxml.jackson.core.JsonToken.START_ARRAY||token==com.fasterxml.jackson.core.JsonToken.START_OBJECT){if(++depth>4)throw bad();}else if(token==com.fasterxml.jackson.core.JsonToken.END_ARRAY||token==com.fasterxml.jackson.core.JsonToken.END_OBJECT)depth--;}}
        try{n=com.wenwen.ai.query.PrivateCohortQuery.strict(text.getBytes("UTF-8"));}catch(RuntimeException e){throw bad();}
        ExportApplicationQuery.keys(n,"schemaVersion","source","referenceId","payloadSha256","scopeFingerprint","selectedItems","conditions","counts","blocked","freeStart","today","sourcePolicyVersions");
        if(!VERSION.equals(n.path("schemaVersion").asText())||!Arrays.asList("COHORT","COMPARE").contains(n.path("source").asText())||!com.wenwen.ai.result.AnalysisCursor.canonicalId(n.path("referenceId").asText()))throw bad();
        for(String hash:Arrays.asList("payloadSha256","scopeFingerprint"))if(!n.path(hash).isTextual()||!n.path(hash).asText().matches("[0-9a-f]{64}"))throw bad();
        List<String> catalogue="COHORT".equals(n.path("source").asText())?AiExportQuery.FIELDS:AiExportQuery.METRICS;JsonNode selected=n.get("selectedItems");if(!selected.isArray()||selected.size()==0)throw bad();int position=-1;for(JsonNode v:selected){int p=catalogue.indexOf(v.asText());if(!v.isTextual()||p<=position)throw bad();position=p;}
        JsonNode counts=n.get("counts");ExportApplicationQuery.keys(counts,"cohortN","aN","bN");boolean cohort="COHORT".equals(n.path("source").asText());for(String k:Arrays.asList("cohortN","aN","bN")){JsonNode v=counts.get(k);if(cohort=="cohortN".equals(k)){if(!v.isIntegralNumber()||!v.canConvertToInt()||v.intValue()<0)throw bad();}else if(!v.isNull())throw bad();}
        if(cohort)conditions(n.get("conditions"),false);else {JsonNode c=n.get("conditions");ExportApplicationQuery.keys(c,"a","b","excludeOverlap","overlapN");conditions(c.get("a"),true);conditions(c.get("b"),true);if(!c.path("excludeOverlap").isBoolean())throw bad();count(c.get("overlapN"));}
        JsonNode versions=n.get("sourcePolicyVersions");String[] v=cohort?new String[]{"statistics","descriptive","qc","crp","now","clinical","serology","treatment","visitMatcher","drugDictionary"}:new String[]{"statistics","descriptive","qc","crp","now","clinical","serology","treatment","visitMatcher","drugDictionary","comparison"};ExportApplicationQuery.keys(versions,v);for(String key:v){JsonNode value=versions.get(key);if(!value.isTextual()||value.asText().isEmpty()||value.asText().length()>256)throw bad();}
        ExportApplicationQuery.date(n.path("freeStart").asText());ExportApplicationQuery.date(n.path("today").asText());if(n.path("freeStart").asText().compareTo(n.path("today").asText())>0)throw bad();
        JsonNode blocks=n.get("blocked");if(!blocks.isArray()||blocks.size()==0)throw bad();for(JsonNode b:blocks){ExportApplicationQuery.keys(b,"field","side","reason","affectedN");if(!catalogue.contains(b.path("field").asText())||!Arrays.asList("INCOMPLETE_SOURCE_EVIDENCE","MISSING_SOURCE_DATE","SOURCE_BEFORE_WINDOW","SOURCE_AFTER_TODAY","NO_VALID_CONTRIBUTION").contains(b.path("reason").asText())||!b.path("affectedN").isIntegralNumber()||!b.path("affectedN").canConvertToInt()||b.path("affectedN").asInt()<0)throw bad();if(cohort?!b.get("side").isNull():!Arrays.asList("A","B").contains(b.path("side").asText()))throw bad();}
        return n;
    }
    private static void conditions(JsonNode c,boolean side){
        ExportApplicationQuery.keys(c,side?new String[]{"source","cohortId","cohortVersion","filters","ids"}:new String[]{"filters","ids"});
        if(side){String source=c.path("source").asText();if("FILTERS".equals(source)){if(!c.get("cohortId").isNull()||!c.get("cohortVersion").isNull())throw bad();}else if("SAVED".equals(source)){if(!com.wenwen.ai.result.AnalysisCursor.canonicalId(c.path("cohortId").asText())||!c.get("cohortVersion").isIntegralNumber()||!c.get("cohortVersion").canConvertToLong()||c.get("cohortVersion").asLong()<1)throw bad();}else throw bad();}
        JsonNode f=c.get("filters");ExportApplicationQuery.keys(f,"studyCode","at","act","sex","age","sero","cm","tx","data");if(!"RA".equals(f.path("studyCode").asText())||!Arrays.asList("now","6m").contains(f.path("at").asText()))throw bad();
        optional(f,"act","target","mod-high","remission","low","moderate","high");optional(f,"sex","F","M");optional(f,"sero","1");optional(f,"cm","FM","AS","none");optional(f,"tx","csDMARD","bio","TNFi","JAKi","IL-6i","Abatacept");optional(f,"data","complete","missing");JsonNode age=f.get("age");if(!age.isNull()){if(!age.isTextual()||!age.asText().matches("[0-9]{1,3}-[0-9]{0,3}"))throw bad();String[] bounds=age.asText().split("-",-1);int min=Integer.parseInt(bounds[0]),max=bounds[1].isEmpty()?120:Integer.parseInt(bounds[1]);if(min>max||max>120)throw bad();}
        JsonNode ids=c.get("ids");ExportApplicationQuery.keys(ids,"mode","submittedUniqueIdsN","effectiveIdsN");if("ALL".equals(ids.path("mode").asText())){if(!ids.get("submittedUniqueIdsN").isNull()||!ids.get("effectiveIdsN").isNull())throw bad();}else if("EXPLICIT".equals(ids.path("mode").asText())){count(ids.get("submittedUniqueIdsN"));count(ids.get("effectiveIdsN"));if(ids.path("effectiveIdsN").asInt()>ids.path("submittedUniqueIdsN").asInt())throw bad();}else throw bad();
    }
    private static void optional(JsonNode n,String key,String...values){JsonNode v=n.get(key);if(!v.isNull()&&(!v.isTextual()||!Arrays.asList(values).contains(v.asText())))throw bad();}
    private static void count(JsonNode n){if(n==null||!n.isIntegralNumber()||!n.canConvertToInt()||n.asInt()<0)throw bad();}
    public static String scope(String summary)throws IOException {JsonNode s=read(summary);Map<String,Object> out=new LinkedHashMap<>();for(String key:Arrays.asList("schemaVersion","source","referenceId","selectedItems","counts"))out.put(key,s.get(key));String v=JSON.writeValueAsString(out);if(v.codePointCount(0,v.length())>4000)throw bad();return v;}
    private static IllegalStateException bad(){return new IllegalStateException("申请摘要损坏");}
}
