package com.wenwen.ai.export;
import java.util.*;
/** MyBatis本片记录；公开响应只由固定投影构造。 */
public final class ExportApplicationRecord extends LinkedHashMap<String,Object> {
    public ExportApplicationRecord(){}
    public ExportApplicationRecord(Map<String,Object> map){super(map);}
    public String text(String key){Object value=get(key);return value==null?null:String.valueOf(value);}
    public long number(String key){Object value=get(key);if(!(value instanceof Number))throw new IllegalStateException("申请记录损坏");return ((Number)value).longValue();}
    public Map<String,Object> response(){return response(true,"SENT".equals(text("mailState"))?Boolean.TRUE:"FAILED".equals(text("mailState"))?Boolean.FALSE:null,text("lastError"));}
    public Map<String,Object> response(boolean confirmed,Boolean accepted,String reason){Map<String,Object> n=new LinkedHashMap<>();String state=confirmed?text("mailState"):"UNKNOWN";n.put("mailStatus",state);n.put("attemptId",text("attemptId"));n.put("retryable",confirmed&&Arrays.asList("PENDING","FAILED").contains(state));n.put("smtpAccepted",accepted);n.put("statusConfirmed",confirmed);n.put("reason",reason);n.put("deliveryStatus","UNKNOWN");Map<String,Object> out=new LinkedHashMap<>();out.put("applicationId",text("applicationId"));out.put("status","APPLICATION_RECORDED");out.put("notification",n);return out;}
}
