package com.wenwen.ai.source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Value;

@Value
public class ScoreVisit implements com.wenwen.ai.clinical.VisitMatcher.Candidate {
    long id;
    long patientId;
    LocalDate observedAt;
    String raw;
    BigDecimal score;
    List<String> quality;
    String rf;
    String ccp;
    java.util.Map<String,java.util.Map<String,Object>> evaluation;
    com.wenwen.ai.treatment.MedicationObservation medication;
    public ScoreVisit(long id,long patientId,LocalDate observedAt,String raw,BigDecimal score,List<String> quality,String rf,String ccp,
            java.util.Map<String,java.util.Map<String,Object>> evaluation) {
        this(id,patientId,observedAt,raw,score,quality,rf,ccp,evaluation,com.wenwen.ai.treatment.MedicationObservation.empty());
    }
    public ScoreVisit(long id,long patientId,LocalDate observedAt,String raw,BigDecimal score,List<String> quality,String rf,String ccp,
            java.util.Map<String,java.util.Map<String,Object>> evaluation,com.wenwen.ai.treatment.MedicationObservation medication) {
        this.medication=medication;
        this.id=id; this.patientId=patientId; this.observedAt=observedAt; this.raw=raw; this.score=score;
        this.quality=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(quality)); this.rf=rf; this.ccp=ccp;
        java.util.Map<String,java.util.Map<String,Object>> copied=new java.util.LinkedHashMap<>();
        evaluation.forEach((key,value) -> copied.put(key,com.wenwen.ai.clinical.ClinicalValues.copy(value)));
        this.evaluation=java.util.Collections.unmodifiableMap(copied);
    }
}
