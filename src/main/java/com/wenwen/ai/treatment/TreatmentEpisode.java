package com.wenwen.ai.treatment;

import java.time.LocalDate;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;

/** 本次分析的不可变episode，同时是当前类别/线数/时长的消费来源。 */
public final class TreatmentEpisode {
    public final String key,state,category,confidence;
    public final Integer line;
    public final int historyN;
    public final LocalDate start,estimatedStart,end;
    public final List<String> genericIds;
    public final Map<String,Object> provenance;
    public TreatmentEpisode(String key,String state,String category,Integer line,int historyN,LocalDate start,LocalDate estimatedStart,LocalDate end,String confidence,List<String> ids,Map<String,Object> provenance) {
        this.key=key; this.state=state; this.category=category; this.line=line; this.historyN=historyN; this.start=start; this.estimatedStart=estimatedStart; this.end=end; this.confidence=confidence;
        this.genericIds=Collections.unmodifiableList(new ArrayList<>(ids)); this.provenance=com.wenwen.ai.clinical.ClinicalValues.copy(provenance);
    }
    public Map<String,Object> summary(LocalDate asOf) {
        LocalDate effective=start==null?estimatedStart:start;
        return object("state",state,"category",category,"line",line,"targetedDrugHistoryN",historyN,
            "schemeDurationMonths",effective==null?null:java.time.temporal.ChronoUnit.MONTHS.between(effective,asOf),
            "startDate",start==null?null:start.toString(),"estimatedStartDate",estimatedStart==null?null:estimatedStart.toString(),
            "endDate",end==null?null:end.toString(),"startConfidence",confidence,"episodeKey",key,"genericDrugIds",genericIds,"provenance",provenance);
    }
    public boolean matches(String tx) { return tx==null || ("ACTIVE".equals(state) && (tx.equals(category) || ("bio".equals(tx) && Arrays.asList("TNFi","JAKi","IL-6i","Abatacept").contains(category)))); }
}
