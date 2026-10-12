package com.wenwen.ai.parse;
import org.springframework.core.env.Environment;
import java.util.regex.*;
/** 不回显配置原值；关闭或未批准时不读取密钥。 */
public final class AiGatewaySettings {
    private final String failure;
    final String endpoint,model,key,proxyMode;
    public AiGatewaySettings(Environment environment) {
        String enabled=environment.getProperty("ra.ai.gateway.enabled","false"),approved=environment.getProperty("ra.ai.gateway.outbound-approved","false");
        String reason=!flag(enabled)?"AI_CONFIG_UNAVAILABLE":"false".equals(enabled)?"AI_NOT_ENABLED":!flag(approved)?"AI_CONFIG_UNAVAILABLE":"false".equals(approved)?"AI_NOT_APPROVED":null;
        if(reason!=null){endpoint=model=key=proxyMode="";failure=reason;return;}
        proxyMode=environment.getProperty("ra.ai.gateway.proxy-mode","direct");
        endpoint=environment.getProperty("ra.ai.gateway.endpoint","https://api.deepseek.com/chat/completions");
        model=environment.getProperty("ra.ai.gateway.model","");
        String configured=environment.getProperty("ra.ai.gateway.api-key");
        key=configured==null?environment.getProperty("RA_AI_API_KEY",""):configured;
        String synthetic=environment.getProperty("ra.ai.gateway.synthetic-loopback","false");
        boolean valid=("direct".equals(proxyMode)||"system".equals(proxyMode))&&flag(synthetic)&&model.matches("[A-Za-z0-9._-]{1,64}")&&key.matches("[!-~]{1,512}");
        if("true".equals(synthetic)){
            Matcher literal=Pattern.compile("http://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/chat/completions").matcher(endpoint);
            valid=valid&&literal.matches()&&Integer.parseInt(literal.group(1))<=65535&&"p05b-synthetic-model".equals(model)&&"p05b-loopback-key".equals(key);
        }else valid=valid&&"https://api.deepseek.com/chat/completions".equals(endpoint);
        failure=valid?null:"AI_CONFIG_UNAVAILABLE";
    }
    private static boolean flag(String value){return "true".equals(value)||"false".equals(value);}
    String failure(){return failure;}
}
