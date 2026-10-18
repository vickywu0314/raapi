package com.wenwen.vo;
import java.util.List;
public class OcrLinkRequest {
    private String ocrFileId;
    private List<String> ocrFileIds;
    private Long patientId;
    private Long visitId;
    public String getOcrFileId(){return ocrFileId;}
    public void setOcrFileId(String v){ocrFileId=v;}
    public List<String> getOcrFileIds(){return ocrFileIds;}
    public void setOcrFileIds(List<String> v){ocrFileIds=v;}
    public Long getPatientId(){return patientId;}
    public void setPatientId(Long v){patientId=v;}
    public Long getVisitId(){return visitId;}
    public void setVisitId(Long v){visitId=v;}
}
