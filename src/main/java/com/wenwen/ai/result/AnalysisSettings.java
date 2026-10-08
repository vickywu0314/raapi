package com.wenwen.ai.result;
import com.wenwen.ai.query.CohortException;
import java.util.Base64;
/** 部署注入且延迟校验，默认无key不阻止其它应用bean启动。 */
public final class AnalysisSettings {
    private final String ttlSeconds,maxBytes,keyBase64;
    public AnalysisSettings(String ttl,String max,String key){ttlSeconds=ttl;maxBytes=max;keyBase64=key;}
    public long ttlMillis(){try{long t=Long.parseLong(ttlSeconds);if(t<=0)throw new IllegalArgumentException();return Math.multiplyExact(t,1000L);}catch(RuntimeException e){throw unavailable();}}
    public int maxBytes(){try{int n=Integer.parseInt(maxBytes);if(n<=0||n>16777215)throw new IllegalArgumentException();return n;}catch(RuntimeException e){throw unavailable();}}
    public byte[] key(){try{byte[] key=Base64.getDecoder().decode(keyBase64);if(key.length<32)throw new IllegalArgumentException();return key;}catch(RuntimeException e){throw unavailable();}}
    public void validateAt(long now){validate();try{Math.addExact(now,ttlMillis());}catch(ArithmeticException e){throw unavailable();}}
    public void validate(){ttlMillis();maxBytes();key();}
    public static CohortException unavailable(){return new CohortException(503,"SERVICE_UNAVAILABLE","分析暂不可用");}
}
