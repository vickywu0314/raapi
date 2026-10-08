package com.wenwen.ai.parse;
import java.util.*;
/** 交给真实parse caller的安全决定；不持有HTTP对象或原始JSON。 */
public final class AiGatewayResult {
    private final Map<String,Object> candidate;
    private final List<Map<String,String>> rejected;
    private final String status,notice,fallback,model;
    private AiGatewayResult(Map<String,Object> candidate,String status,String notice,String fallback,String model,List<Map<String,String>> rejected){List<Map<String,String>> safe=new ArrayList<>();for(Map<String,String> item:rejected)safe.add(Collections.unmodifiableMap(new LinkedHashMap<>(item)));this.rejected=Collections.unmodifiableList(safe);this.candidate=candidate==null?null:Collections.unmodifiableMap(new LinkedHashMap<>(candidate));this.status=status;this.notice=notice;this.fallback=fallback;this.model=model;}
    static AiGatewayResult success(Map<String,Object> candidate,String status,String notice,String model,List<Map<String,String>> rejected){return new AiGatewayResult(candidate,status,notice,null,model,rejected);}
    static AiGatewayResult failure(String reason,String model){return new AiGatewayResult(null,null,null,reason,model,Collections.emptyList());}
    public List<Map<String,String>> getRejected(){return rejected;}
    public Map<String,Object> getCandidate(){return candidate;} public String getStatus(){return status;} public String getNotice(){return notice;} public String getFallback(){return fallback;} public String getModelVersion(){return model;} public String getPromptVersion(){return model==null?null:AiGatewayProtocol.PROMPT_VERSION;}
}
