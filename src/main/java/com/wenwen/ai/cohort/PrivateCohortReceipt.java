package com.wenwen.ai.cohort;
/** 私有持久事实；不记录临床载荷。 */
public final class PrivateCohortReceipt {
    private String action,requestId,cohortId,requestSha256,traceId;
    public String getAction(){
        return action;
    } public void setAction(String v){
        action=v;
    }
    public String getRequestId(){
        return requestId;
    } public void setRequestId(String v){
        requestId=v;
    }
    public String getCohortId(){
        return cohortId;
    } public void setCohortId(String v){
        cohortId=v;
    }
    public String getRequestSha256(){
        return requestSha256;
    } public void setRequestSha256(String v){
        requestSha256=v;
    }
    public String getTraceId(){
        return traceId;
    } public void setTraceId(String v){
        traceId=v;
    }
    private long ownerDoctorId,resultVersion,createdAtMs;
    public long getOwnerDoctorId(){
        return ownerDoctorId;
    } public void setOwnerDoctorId(long v){
        ownerDoctorId=v;
    }
    public long getResultVersion(){
        return resultVersion;
    } public void setResultVersion(long v){
        resultVersion=v;
    }
    public long getCreatedAtMs(){
        return createdAtMs;
    } public void setCreatedAtMs(long v){
        createdAtMs=v;
    }
    private int protocolVersion;
    public int getProtocolVersion(){
        return protocolVersion;
    } public void setProtocolVersion(int v){
        protocolVersion=v;
    }
}
