package com.wenwen.ai.clinical;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.env.Environment;
import static com.wenwen.vo.AiCohortVo.object;

/** 本次read捕获的完整FM来源；不缓存、不授予患者范围。 */
public final class ConfiguredFmEvidenceSource {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Environment environment;
    public ConfiguredFmEvidenceSource(Environment environment){this.environment=environment;}
    public View snapshot(){
        String source=environment==null?null:environment.getProperty("ra.ai.fm-evidence.json");
        if(source==null&&environment!=null)source=environment.getProperty("RA_AI_FM_EVIDENCE_JSON");
        if(source==null)return null;
        if(source.length()>1048576||source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576)throw invalid();
        try{
            try(JsonParser parser=JSON.getFactory().createParser(source)){
                int depth=0;JsonToken token;
                while((token=parser.nextToken())!=null){
                    if(token==JsonToken.START_OBJECT||token==JsonToken.START_ARRAY){if(++depth>3)throw invalid();}
                    else if(token==JsonToken.END_OBJECT||token==JsonToken.END_ARRAY)depth--;
                }
            }
            JsonNode root=JSON.readTree(source);fields(root,"version","records");String version=identifier(root.get("version"));
            JsonNode rows=root.get("records");if(!rows.isArray()||rows.size()>5000)throw invalid();
            Map<Long,Evidence> records=new LinkedHashMap<>();
            for(JsonNode row:rows){
                fields(row,"patientId","state","evidenceId","observedAt");String patient=text(row.get("patientId"));
                if(!patient.matches("[1-9][0-9]*"))throw invalid();long id=Long.parseLong(patient);
                String state=text(row.get("state"));if(!Arrays.asList("TRUE","FALSE","UNKNOWN").contains(state))throw invalid();
                String date=text(row.get("observedAt"));LocalDate observed=LocalDate.parse(date);if(!observed.toString().equals(date))throw invalid();
                if(records.put(id,new Evidence(state,identifier(row.get("evidenceId")),observed))!=null)throw invalid();
            }
            return new View(version,records);
        }catch(IOException|RuntimeException e){throw invalid();}
    }
    private static String text(JsonNode n){if(n==null||!n.isTextual())throw invalid();return n.textValue();}
    private static String identifier(JsonNode n){String s=text(n).trim();if(s.isEmpty()||s.length()>200||!s.matches("[\\p{L}\\p{N}:._\\-]+"))throw invalid();return s;}
    private static void fields(JsonNode n,String...fields){if(n==null||!n.isObject()||n.size()!=fields.length)throw invalid();for(String f:fields)if(!n.has(f))throw invalid();}
    private static IllegalStateException invalid(){return new IllegalStateException("FM配置无效");}
    private static final class Evidence {
        final String state,id;final LocalDate observed;
        Evidence(String state,String id,LocalDate observed){this.state=state;this.id=id;this.observed=observed;}
    }
    public static final class View {
        public final String version;
        private final Map<Long,Evidence> records;
        private View(String version,Map<Long,Evidence> records){this.version=version;this.records=Collections.unmodifiableMap(new LinkedHashMap<>(records));}
        private Evidence effective(long patient,LocalDate asOf){Evidence e=records.get(patient);return e!=null&&!e.observed.isAfter(asOf)?e:null;}
        public String state(long patient,LocalDate asOf){Evidence e=effective(patient,asOf);return e==null?"UNKNOWN":e.state;}
        public Map<String,Object> provenance(long patient,LocalDate asOf){
            Evidence e=effective(patient,asOf);
            List<Map<String,Object>> observations=e==null?Collections.emptyList():Collections.singletonList(Collections.unmodifiableMap(object("evidenceId",e.id,"observedAt",e.observed.toString(),"fmState",e.state)));
            return Collections.unmodifiableMap(object("source","CONFIGURED_FM_EVIDENCE","sourceField","ra.ai.fm-evidence.json","quality",Collections.singletonList("SOURCE_UNVERIFIED"),"missingReason",e==null?"NO_EFFECTIVE_FM_EVIDENCE":"UNKNOWN".equals(e.state)?"EXPLICIT_UNKNOWN":null,"observations",observations));
        }
    }
}
