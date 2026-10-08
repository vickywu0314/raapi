package com.wenwen.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.query.ParseQuery;
import com.wenwen.ai.parse.*;
import com.wenwen.service.AiParseService;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;
@Service public final class AiParseServiceImpl implements AiParseService {
    public static final String RULE="dev-parse-rules-v01",SCHEMA="dev-parse-schema-v01";
    private final AiParseGateway gateway;private final ParseAuditStore audit;private final Clock clock;private final RuleParser rules=new RuleParser();private final ObjectMapper json=new ObjectMapper();
    public AiParseServiceImpl(ParseAuditStore audit,Clock clock,AiParseGateway gateway){this.audit=audit;this.clock=clock;this.gateway=gateway;}
    public Map<String,Object> parse(TrustedDoctor doctor,ParseQuery query,String trace,long started) throws IOException {
        ParseDecision baseline=rules.parse(query.getQuestion());AiGatewayResult result=gateway.parse(query.getQuestion(),baseline,doctor);ParseDecision decision=result.getFallback()==null?new ParseDecision(result.getStatus(),result.getCandidate(),baseline.getUnmapped()):baseline;String ref=UUID.randomUUID().toString();
        Map<String,Object> data=new LinkedHashMap<>(object("questionRef",ref,"candidateFilters",decision.getCandidate(),"parseStatus",decision.getStatus(),"applyMode",ParseDecision.mode(decision.getStatus())));
        data.put("engine",result.getFallback()==null?"AI":"RULE");data.put("fallbackReason",result.getFallback());
        List<Object> matched=new ArrayList<>();for(String field:Arrays.asList("tx","at","act","sex","age","cm","sero","data"))if(decision.getCandidate()!=null&&decision.getCandidate().containsKey(field))matched.add(object("field",field,"value",decision.getCandidate().get(field)));
        data.put("matched",matched);data.put("unrecognized",decision.getUnmapped()==0?Collections.emptyList():Collections.singletonList(object("reason","UNMAPPED_TEXT","count",decision.getUnmapped())));data.put("rejectedFields",result.getRejected());
        List<String> warnings=new ArrayList<>();if(result.getFallback()!=null)warnings.add(result.getFallback());if(!result.getRejected().isEmpty())warnings.add("MODEL_FIELDS_REJECTED");if("PARTIAL".equals(decision.getStatus()))warnings.add("PARTIAL_REQUIRES_CONFIRMATION");warnings.addAll(decision.getWarnings());data.put("warnings",warnings);
        data.put("notice",result.getFallback()==null?result.getNotice():("AI_NOT_ENABLED".equals(result.getFallback())?"AI未启用，已使用规则解析。":"AI_SAFETY_BLOCKED".equals(result.getFallback())?"该问题未发送至AI，已使用规则解析。":"AI解析未完成，已使用规则解析。")+("UNSUPPORTED".equals(decision.getStatus())?"当前条件无法完整表示，请改写为可用筛选字段的并且条件。":"EMPTY".equals(decision.getStatus())?"问题为空，保留当前条件。":"UNRECOGNIZED".equals(decision.getStatus())?"未识别出可用条件，请改写，例如：女性45至65岁。":"PARTIAL".equals(decision.getStatus())?"部分内容无法转换，需明确确认后才应用可识别条件。可用问法：女性45至65岁。":"已生成候选条件，尚未应用。"));
        append(doctor,ref,trace,"PARSE",decision.getCandidate(),null,decision.getStatus(),false,started,data,result.getModelVersion(),result.getPromptVersion());return data;
    }
    public Map<String,Object> apply(TrustedDoctor doctor,ParseQuery query,String trace,long started) throws IOException {
        boolean applied="OK".equals(query.getStatus()) || ("PARTIAL".equals(query.getStatus()) && query.isConfirm());
        Map<String,Object> effective=query.getCurrent();
        if(applied){effective=new LinkedHashMap<>(query.getCandidate());effective.put("ids",query.getCurrent().get("ids"));effective=ParseQuery.normalize(effective,doctor);}
        Map<String,Object> data=new LinkedHashMap<>(object("questionRef",query.getQuestionRef(),"parseStatus",query.getStatus(),"applyMode",ParseDecision.mode(query.getStatus()),"applied",applied,"filters",effective));
        append(doctor,query.getQuestionRef(),trace,"APPLY",query.getCandidate(),ordinary(effective),query.getStatus(),applied,started,data,null,null);return data;
    }
    private Map<String,Object> ordinary(Map<String,Object> filters){Map<String,Object> out=new LinkedHashMap<>();for(String key:Arrays.asList("sex","age","sero","tx","at","act","cm","data"))if(filters.containsKey(key))out.put(key,filters.get(key));return out;}
    private void append(TrustedDoctor doctor,String ref,String trace,String action,Map<String,Object> candidate,Map<String,Object> effective,String status,boolean applied,long started,Map<String,Object> data,String model,String prompt) throws IOException {
        String summary=json.writeValueAsString(object("candidate",candidate==null?null:ordinary(candidate),"effective",effective));
        long duration=Math.max(0,(System.nanoTime()-started)/1000000);
        data.put("meta",object("traceId",trace,"ruleVersion",RULE,"schemaVersion",SCHEMA,"modelVersion",model,"promptVersion",prompt,"durationMs",duration));
        ParseAuditEvent event=new ParseAuditEvent(UUID.randomUUID().toString(),ref,trace,doctor.getId(),action,summary,"PARSE".equals(action)?(String)data.get("engine"):"NONE",status,applied,"PARSE".equals(action)?(String)data.get("fallbackReason"):null,duration,SCHEMA,RULE,clock.millis(),model,prompt);
        audit.append(event);
    }
}
