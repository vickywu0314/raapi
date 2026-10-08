package com.wenwen.ai.result;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
public final class AnalysisValues {
    public static final String SORT="DAS28_AT_DESC_NULL_LAST_ID_ASC";
    private AnalysisValues(){}
    public static String sha256(byte[] bytes){try{StringBuilder b=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(bytes))b.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return b.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static String scope(long owner,Collection<Long> ids){StringBuilder b=new StringBuilder().append(owner).append('\n').append("RA\n");for(long id:new TreeSet<>(ids))b.append(id).append('\n');return sha256(b.toString().getBytes(StandardCharsets.UTF_8));}
    public static final Comparator<Map<String,Object>> ORDER=(a,b)->{
        java.math.BigDecimal x=a.get("das28At")==null?null:new java.math.BigDecimal(a.get("das28At").toString()),y=b.get("das28At")==null?null:new java.math.BigDecimal(b.get("das28At").toString());
        int c=x==null?(y==null?0:1):y==null?-1:y.compareTo(x);return c!=0?c:Long.compare(Long.parseLong((String)a.get("patientId")),Long.parseLong((String)b.get("patientId")));
    };
}
