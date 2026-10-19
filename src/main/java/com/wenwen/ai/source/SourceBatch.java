package com.wenwen.ai.source;

import java.time.Instant;
import java.util.List;
import lombok.Value;

/** 离开源事务后返回的必要值；不携带连接、session 或原 JSON。 */
@Value
public class SourceBatch {
    List<Long> patientIds;
    List<ScoreVisit> visits;
    Instant readCompletedAt;
    java.util.Map<Long,com.wenwen.ai.clinical.PatientClinical> clinical;
    java.util.Map<Long,com.wenwen.ai.qc.MissingDataStatus> qc;
    java.util.Map<Long,PatientDisplay> display;
    String fmSourceVersion;
    String clinicalRulesFingerprint;
    public SourceBatch(List<Long> patientIds,List<ScoreVisit> visits,Instant readCompletedAt,java.util.Map<Long,com.wenwen.ai.clinical.PatientClinical> clinical,java.util.Map<Long,com.wenwen.ai.qc.MissingDataStatus> qc,java.util.Map<Long,PatientDisplay> display) {
        this(patientIds,visits,readCompletedAt,clinical,qc,display,null);
    }
    public SourceBatch(List<Long> patientIds,List<ScoreVisit> visits,Instant readCompletedAt,java.util.Map<Long,com.wenwen.ai.clinical.PatientClinical> clinical,java.util.Map<Long,com.wenwen.ai.qc.MissingDataStatus> qc,java.util.Map<Long,PatientDisplay> display,String fmSourceVersion) {
        this(patientIds,visits,readCompletedAt,clinical,qc,display,fmSourceVersion,"default");
    }
    public SourceBatch(List<Long> patientIds,List<ScoreVisit> visits,Instant readCompletedAt,java.util.Map<Long,com.wenwen.ai.clinical.PatientClinical> clinical,java.util.Map<Long,com.wenwen.ai.qc.MissingDataStatus> qc,java.util.Map<Long,PatientDisplay> display,String fmSourceVersion,String clinicalRulesFingerprint) {
        this.clinicalRulesFingerprint=clinicalRulesFingerprint;
        this.fmSourceVersion=fmSourceVersion;
        this.patientIds=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(patientIds));
        this.visits=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(visits));
        this.readCompletedAt=readCompletedAt;
        this.display=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(display));
        this.qc=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(qc));
        this.clinical=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(clinical));
    }
    public java.util.Map<String,Object> withFmSource(java.util.Map<String,Object> policies) {
        if(fmSourceVersion==null)return policies;
        java.util.Map<String,Object> result=new java.util.LinkedHashMap<>(policies);
        result.put("fmSource",fmSourceVersion);return java.util.Collections.unmodifiableMap(result);
    }
}
