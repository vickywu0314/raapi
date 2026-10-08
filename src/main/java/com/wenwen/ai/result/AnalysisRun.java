package com.wenwen.ai.result;
/** 有期分析持久行；敏感载荷无日志toString，字节边界防御复制。 */
public final class AnalysisRun {
    private String id,scopeFingerprint,sortKey,payloadSha256;
    private long ownerDoctorId,createdAtMs,expiresAtMs;
    private int payloadVersion;
    private byte[] payload;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getScopeFingerprint(){return scopeFingerprint;} public void setScopeFingerprint(String v){scopeFingerprint=v;}
    public String getSortKey(){return sortKey;} public void setSortKey(String v){sortKey=v;}
    public String getPayloadSha256(){return payloadSha256;} public void setPayloadSha256(String v){payloadSha256=v;}
    public long getOwnerDoctorId(){return ownerDoctorId;} public void setOwnerDoctorId(long v){ownerDoctorId=v;}
    public long getCreatedAtMs(){return createdAtMs;} public void setCreatedAtMs(long v){createdAtMs=v;}
    public long getExpiresAtMs(){return expiresAtMs;} public void setExpiresAtMs(long v){expiresAtMs=v;}
    public int getPayloadVersion(){return payloadVersion;} public void setPayloadVersion(int v){payloadVersion=v;}
    public byte[] getPayload(){return payload==null?null:payload.clone();} public void setPayload(byte[] v){payload=v==null?null:v.clone();}
}
