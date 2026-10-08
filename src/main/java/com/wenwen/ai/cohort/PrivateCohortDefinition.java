package com.wenwen.ai.cohort;
/** 私有持久事实；不记录临床载荷。 */
public final class PrivateCohortDefinition {
    private String id,studyCode,name,note,filtersSha256;
    public String getId(){
        return id;
    } public void setId(String v){
        id=v;
    }
    public String getStudyCode(){
        return studyCode;
    } public void setStudyCode(String v){
        studyCode=v;
    }
    public String getName(){
        return name;
    } public void setName(String v){
        name=v;
    }
    public String getNote(){
        return note;
    } public void setNote(String v){
        note=v;
    }
    public String getFiltersSha256(){
        return filtersSha256;
    } public void setFiltersSha256(String v){
        filtersSha256=v;
    }
    private long ownerDoctorId,version,createdAtMs,updatedAtMs;
    public long getOwnerDoctorId(){
        return ownerDoctorId;
    } public void setOwnerDoctorId(long v){
        ownerDoctorId=v;
    }
    public long getVersion(){
        return version;
    } public void setVersion(long v){
        version=v;
    }
    public long getCreatedAtMs(){
        return createdAtMs;
    } public void setCreatedAtMs(long v){
        createdAtMs=v;
    }
    public long getUpdatedAtMs(){
        return updatedAtMs;
    } public void setUpdatedAtMs(long v){
        updatedAtMs=v;
    }
    private Long deletedAtMs;
    public Long getDeletedAtMs(){
        return deletedAtMs;
    } public void setDeletedAtMs(Long v){
        deletedAtMs=v;
    }
    private int filtersVersion;
    public int getFiltersVersion(){
        return filtersVersion;
    } public void setFiltersVersion(int v){
        filtersVersion=v;
    }
    private byte[] filtersUtf8;
    public byte[] getFiltersUtf8(){
        return filtersUtf8==null?null:filtersUtf8.clone();
    } public void setFiltersUtf8(byte[] v){
        filtersUtf8=v==null?null:v.clone();
    }
}
