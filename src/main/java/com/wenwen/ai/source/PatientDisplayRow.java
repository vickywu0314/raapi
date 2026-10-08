package com.wenwen.ai.source;
/** 后页授权批量显示，绝不查询身份卡或病例。 */
public final class PatientDisplayRow {
    private long id;private String name,studyNo;
    public long getId(){return id;}public void setId(long v){id=v;}
    public String getName(){return name;}public void setName(String v){name=v;}
    public String getStudyNo(){return studyNo;}public void setStudyNo(String v){studyNo=v;}
}
