package com.wenwen.ai.qc;

import com.wenwen.mapper.*;
import com.wenwen.ai.clinical.VisitMatcher;
import com.wenwen.ai.source.ClinicalScalarReader;
import com.wenwen.util.Das28Util;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** 旧公开消费者的单次短RR；回调仅读源，退出后才解析/计算。 */
@Component
public final class QcSnapshotReader {
    private final PatientMapper patients;
    private final ProjectMapper projects;
    private final TransactionTemplate snapshot;
    public QcSnapshotReader(PatientMapper patients,ProjectMapper projects,PlatformTransactionManager transactions) {
        this.patients=patients;this.projects=projects;
        snapshot=new TransactionTemplate(transactions);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);snapshot.setReadOnly(true);
    }
    public QcSnapshot readPatients(Map<String,Object> params) {
        Map<String,Object> scope=new HashMap<>(params);scope.putIfAbsent("patientId",null);scope.putIfAbsent("doctorId",null);
        Raw raw=snapshot.execute(status -> {
            List<Long> ids=projects.qcPatientIds(scope);
            List<Map<String,Object>> issues=projects.remainingQcIssues(scope);
            List<Map<String,Object>> scores=ids.isEmpty()?Collections.emptyList():patients.listDas28(ids);
            return new Raw(ids,issues,scores,patients.listPatients(scope));
        });
        return compute(raw);
    }
    public QcSnapshot readProject() {
        Map<String,Object> scope=new HashMap<>();scope.put("doctorId",null);scope.put("patientId",null);
        Raw raw=snapshot.execute(status -> {
            List<Long> ids=projects.qcPatientIds(scope);
            List<Map<String,Object>> issues=projects.remainingQcIssues(scope);
            List<Map<String,Object>> scores=ids.isEmpty()?Collections.emptyList():patients.listQcDas28(scope);
            return new Raw(ids,issues,scores,Collections.emptyList());
        });
        Set<Long> valid=new HashSet<>();
        for(Map<String,Object> row:raw.scores) {
            long patientId=((Number)row.get("patientId")).longValue();
            if(valid.contains(patientId)) continue;
            // 必须完整读完当前JSON，才可接纳其canonical值；仅在文档之间跳过已满足的患者。
            String score=ClinicalScalarReader.read(text(row.get("bqpg")),new HashSet<>()).get("/result/crpScore");
            if(Das28Util.canonicalCrp(score)!=null) valid.add(patientId);
        }
        return finish(raw,Collections.emptyList(),MissingDataPolicy.evaluateValidDas28(raw.ids,valid,raw.issues));
    }
    private QcSnapshot compute(Raw raw) {
        List<VisitMatcher.Visit> visits=new ArrayList<>();
        for(Map<String,Object> row:raw.scores) {
            String date=text(row.get("visitDate"));
            visits.add(new VisitMatcher.Visit(((Number)row.get("visitId")).longValue(),((Number)row.get("patientId")).longValue(),
                date==null?null:LocalDate.parse(date),ClinicalScalarReader.read(text(row.get("bqpg")),new LinkedHashSet<>()).get("/result/crpScore")));
        }
        Map<Long,MissingDataStatus> qc=MissingDataPolicy.evaluate(raw.ids,visits,raw.issues);
        return finish(raw,visits,qc);
    }
    private QcSnapshot finish(Raw raw,List<VisitMatcher.Visit> visits,Map<Long,MissingDataStatus> qc) {
        int issueCount=0;Set<Long> issuePatients=new HashSet<>();
        for(Map.Entry<Long,MissingDataStatus> entry:qc.entrySet()) if(!entry.getValue().getMissingCodes().isEmpty()) {
            issueCount+=entry.getValue().getMissingCodes().size();issuePatients.add(entry.getKey());
        }
        for(Map<String,Object> issue:raw.issues) if(((String)issue.get("rule_code")).startsWith("L_")) {
            long id=((Number)issue.get("patient_id")).longValue();
            if(qc.containsKey(id)) { issueCount++;issuePatients.add(id); }
        }
        return new QcSnapshot(raw.ids,raw.rows,visits,qc,issueCount,issuePatients.size());
    }
    private static String text(Object value){return value==null?null:value.toString();}
    private static final class Raw {
        final List<Long> ids;final List<Map<String,Object>> issues,scores,rows;
        Raw(List<Long> ids,List<Map<String,Object>> issues,List<Map<String,Object>> scores,List<Map<String,Object>> rows){this.ids=ids;this.issues=issues;this.scores=scores;this.rows=rows;}
    }
}
