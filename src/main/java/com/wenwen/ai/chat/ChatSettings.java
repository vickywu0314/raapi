package com.wenwen.ai.chat;
import org.springframework.core.env.Environment;
import com.wenwen.ai.query.CohortException;
import java.util.regex.*;
/** 独立chat许可；拒绝时不读取key。每轮scope选择对应许可后才lazy读取凭据。 */
public final class ChatSettings {
    private final Environment env;private final String enabled,aggregate,clinical;final String endpoint,model,proxyMode;volatile String key;private String credentialFailure;private boolean resolved;
    public ChatSettings(Environment env){
        this.env=env;enabled=env.getProperty("ra.ai.chat.enabled","false");aggregate=env.getProperty("ra.ai.chat.aggregate-outbound-approved","false");clinical=env.getProperty("ra.ai.chat.clinical-outbound-approved","false");
        endpoint=env.getProperty("ra.ai.chat.endpoint","https://api.deepseek.com/chat/completions");model=env.getProperty("ra.ai.chat.model","deepseek-flash");proxyMode=env.getProperty("ra.ai.chat.proxy-mode","direct");
    }
    private synchronized void credentials(){
        if(resolved)return;
        String configured=env.getProperty("ra.ai.chat.api-key");key=configured==null?env.getProperty("RA_AI_API_KEY",""):configured;
        String synthetic=env.getProperty("ra.ai.chat.synthetic-loopback","false");boolean valid=flag(synthetic)&&("direct".equals(proxyMode)||"system".equals(proxyMode))&&key.matches("[!-~]{1,512}");
        if("true".equals(synthetic)){Matcher m=Pattern.compile("http://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/chat/completions").matcher(endpoint);valid=valid&&m.matches()&&Integer.parseInt(m.group(1))<=65535&&"p05a-synthetic-model".equals(model)&&"p05a-loopback-key".equals(key);}
        else valid=valid&&"https://api.deepseek.com/chat/completions".equals(endpoint)&&"deepseek-flash".equals(model);
        credentialFailure=valid?null:"AI_CONFIG_UNAVAILABLE";resolved=true;
    }
    private static boolean flag(String v){return "true".equals(v)||"false".equals(v);}
    public void require(){require("ALL");}
    public void require(String kind){
        String approved="PATIENT".equals(kind)?clinical:aggregate;
        String failure=!flag(enabled)||!flag(approved)?"AI_CONFIG_UNAVAILABLE":"false".equals(enabled)?"CHAT_DISABLED":"false".equals(approved)?"CHAT_OUTBOUND_NOT_APPROVED":null;
        if(failure!=null)throw unavailable(failure);
        credentials();if(credentialFailure!=null)throw unavailable(credentialFailure);
    }
    private static CohortException unavailable(String code){return new CohortException("CHAT_OUTBOUND_NOT_APPROVED".equals(code)?403:503,code,"问答配置或许可不可用");}
    public String modelVersion(){return model;}
}
