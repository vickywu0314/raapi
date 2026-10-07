package com.wenwen.ai.qc;

import com.wenwen.ai.clinical.VisitMatcher;
import java.util.*;

/** 离开短RR后派生的当前源视图；不持有连接或原JSON。 */
public final class QcSnapshot {
    private final int issueCount,issuePatients;
    public int getIssueCount(){return issueCount;}
    public int getIssuePatients(){return issuePatients;}
    private final List<Long> patientIds;
    private final List<Map<String,Object>> rows;
    private final List<VisitMatcher.Visit> visits;
    private final Map<Long,MissingDataStatus> qc;
    QcSnapshot(List<Long> ids,List<Map<String,Object>> rows,List<VisitMatcher.Visit> visits,Map<Long,MissingDataStatus> qc,int issueCount,int issuePatients) {
        this.issueCount=issueCount;this.issuePatients=issuePatients;
        this.patientIds=Collections.unmodifiableList(new ArrayList<>(ids));
        List<Map<String,Object>> copies=new ArrayList<>(); for(Map<String,Object> row:rows) copies.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        this.rows=Collections.unmodifiableList(copies); this.visits=Collections.unmodifiableList(new ArrayList<>(visits));
        this.qc=Collections.unmodifiableMap(new LinkedHashMap<>(qc));
    }
    public List<Long> getPatientIds(){return patientIds;}
    public List<Map<String,Object>> getRows(){return rows;}
    public List<VisitMatcher.Visit> getVisits(){return visits;}
    public Map<Long,MissingDataStatus> getQc(){return qc;}
    public int incompleteCount(){int n=0;for(MissingDataStatus status:qc.values()) if(status.matches("missing")) n++;return n;}
}
