package com.wenwen.ai.source;

import com.wenwen.mapper.AiCohortSourceMapper;
import com.wenwen.util.Das28Util;
import com.wenwen.util.SerologyUtil;
import com.wenwen.ai.clinical.*;
import static com.wenwen.vo.AiCohortVo.object;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class CohortSourceAdapter {
    private final AiCohortSourceMapper mapper;
    private final TransactionTemplate snapshot;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Value("${ra.serology.rf-uln:20}")
    private double rfUln=20;
    @org.springframework.beans.factory.annotation.Value("${ra.serology.ccp-uln:25}")
    private double ccpUln=25;

    public CohortSourceAdapter(AiCohortSourceMapper mapper, PlatformTransactionManager transactions, Clock clock) {
        this.mapper = mapper; this.clock = clock;
        snapshot = new TransactionTemplate(transactions);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshot.setReadOnly(true);
    }
    public SourceBatch read(long doctorId, LocalDate asOf) {
        // Spring-managed MyBatis 的五条批量 SELECT 共享本事务。回调仅读取，不解析病例或计算。
        Map<String,Object> scope=new HashMap<>(); scope.put("doctorId",doctorId); scope.put("patientId",null);
        RawBatch raw = snapshot.execute(status -> new RawBatch(mapper.patients(doctorId),mapper.patientIds(doctorId),mapper.visits(doctorId),mapper.comorbidities(doctorId),mapper.missingIssues(scope)));
        Instant completed = clock.instant(); // execute 已完成事务及连接释放
        List<ScoreVisit> visits = new ArrayList<>();
        for (VisitRow row : raw.visits) visits.add(parse(row));
        Map<Long,PatientClinical> clinical = new LinkedHashMap<>();
        Map<Long,String> serology = new HashMap<>();
        for (ScoreVisit visit : visits) {
            if (visit.getObservedAt()==null || visit.getObservedAt().isAfter(asOf)) continue;
            String state=ClinicalPolicy.serology(serology.get(visit.getPatientId()),com.wenwen.util.SerologyUtil.classify(visit.getRf(),rfUln));
            serology.put(visit.getPatientId(),ClinicalPolicy.serology(state,com.wenwen.util.SerologyUtil.classify(visit.getCcp(),ccpUln)));
        }
        Map<Long,Set<String>> associations=new HashMap<>();
        for (ComorbidityRow row : raw.comorbidities) {
            if ("TRUE".equals(ClinicalPolicy.association(row.getSinceYear(),asOf)))
                associations.computeIfAbsent(row.getPatientId(),key -> new HashSet<>()).add(row.getCode());
        }
        Map<Long,PatientDisplay> display=new LinkedHashMap<>();
        for (PatientRow row : raw.patients) {
            display.put(row.getId(),new PatientDisplay(row.getName(),row.getStudyNo()));
            String sex=ClinicalPolicy.sex(row.getGender()); Integer age=ClinicalPolicy.age(row.getCardNo(),asOf);
            Integer duration=ClinicalPolicy.duration(row.getConfirmDate(),asOf);
            String sero=serology.getOrDefault(row.getId(),"UNKNOWN");
            String fm=associations.getOrDefault(row.getId(),Collections.emptySet()).contains("FM")?"TRUE":"UNKNOWN";
            String as=associations.getOrDefault(row.getId(),Collections.emptySet()).contains("AS")?"TRUE":"UNKNOWN";
            List<Map<String,Object>> tests=new ArrayList<>();
            for (ScoreVisit visit : visits) if (visit.getPatientId()==row.getId() && visit.getObservedAt()!=null && !visit.getObservedAt().isAfter(asOf)) {
                addTest(tests,visit,"fzjc.lfsyz",SerologyUtil.classify(visit.getRf(),rfUln));
                addTest(tests,visit,"fzjc.kccpkt",SerologyUtil.classify(visit.getCcp(),ccpUln));
            }
            Map<String,Object> provenance=object(
                "sex",fact("patient_basic_info.gender",sex==null?"UNRECOGNIZED_GENDER":null),
                "age",fact("patient_basic_info.card_no",age==null?"NO_VALID_BIRTH_DATE":null),
                "diseaseDurationYears",fact("COALESCE(patient_basic_info.confirm_date,patient_basic_info.confirmDate)",duration==null?"NO_VALID_CONFIRM_DATE":null),
                "sero",object("source","LEGACY_RECORDED","sourceField","fzjc.lfsyz/fzjc.kccpkt","observations",Collections.unmodifiableList(tests),
                    "quality",Collections.singletonList("LEGACY_UNVERIFIED"),"missingReason","UNKNOWN".equals(sero)?"NO_CLASSIFIABLE_DATED_TEST":null),
                "fm",associationProvenance(row.getId(),"FM",raw.comorbidities,asOf),
                "as",associationProvenance(row.getId(),"AS",raw.comorbidities,asOf));
            clinical.put(row.getId(),new PatientClinical(sex,age,duration,sero,fm,as,provenance));
        }
        return new SourceBatch(Collections.unmodifiableList(new ArrayList<>(raw.ids)), Collections.unmodifiableList(visits), completed, Collections.unmodifiableMap(clinical),com.wenwen.ai.qc.MissingDataPolicy.evaluate(raw.ids,visits,raw.missing),display);
    }
    private static Map<String,Object> fact(String field,String missing) {
        return object("source","LEGACY_RECORDED","sourceField",field,"quality",Collections.singletonList("LEGACY_UNVERIFIED"),"missingReason",missing);
    }
    private static void addTest(List<Map<String,Object>> tests,ScoreVisit visit,String field,String status) {
        if (status!=null) tests.add(object("sourceVisitId",Long.toString(visit.getId()),"sourceField",field,"observedAt",visit.getObservedAt().toString(),"status",status));
    }
    private static Map<String,Object> associationProvenance(long patient,String code,List<ComorbidityRow> rows,LocalDate asOf) {
        List<Map<String,Object>> evidence=new ArrayList<>(); Set<String> quality=new LinkedHashSet<>(); quality.add("LEGACY_UNVERIFIED");
        for (ComorbidityRow row : rows) if (row.getPatientId()==patient && code.equals(row.getCode()) && "TRUE".equals(ClinicalPolicy.association(row.getSinceYear(),asOf))) {
            evidence.add(object("sourceRowId",Long.toString(row.getId()),"sinceYear",row.getSinceYear()));
            if (row.getSinceYear()==null) quality.add("DATE_UNSPECIFIED");
        }
        return object("source","LEGACY_POSITIVE_ASSOCIATION","sourceField","patient_comorbidity.disease_code/since_year", "observations",Collections.unmodifiableList(evidence),
            "quality",Collections.unmodifiableList(new ArrayList<>(quality)),"missingReason",evidence.isEmpty()?"NO_POSITIVE_ASSOCIATION":null);
    }
    private ScoreVisit parse(VisitRow row) {
        LinkedHashSet<String> quality = new LinkedHashSet<>();
        Map<String,String> assessment = ClinicalScalarReader.read(row.getBqpg(), quality);
        Map<String,String> labs = ClinicalScalarReader.read(row.getFzjc(), quality);
        String raw = assessment.get("/result/crpScore");
        BigDecimal value = Das28Util.canonicalCrp(raw);
        if (value != null) {
            quality.add("LEGACY_UNVERIFIED");
            if (!number(assessment.get("/result/ytgjs")) || !number(assessment.get("/result/zzgjs"))
                    || !number(assessment.get("/ztScoreByPatient")) || !number(labs.get("/cfydb"))) quality.add("COMPONENTS_MISSING");
        }
        LocalDate date = null;
        if (row.getObservedAt() != null) {
            try { date = LocalDate.parse(row.getObservedAt()); }
            catch (DateTimeParseException e) { quality.add("INVALID_DATE"); }
        }
        return new ScoreVisit(row.getId(), row.getPatientId(), date, raw, value, Collections.unmodifiableList(new ArrayList<>(quality)), labs.get("/lfsyz"),labs.get("/kccpkt"),EvaluationPolicy.read(row.getId(),date,assessment,labs),com.wenwen.ai.treatment.MedicationObservation.parse(row.getZlfa()));
    }
    private boolean number(String value) {
        if (value == null) return false;
        try { return new BigDecimal(value.trim()).signum() >= 0; }
        catch (NumberFormatException e) { return false; }
    }
    private static final class RawBatch {
        final List<Map<String,Object>> missing;
        final List<Long> ids;
        final List<VisitRow> visits;
        final List<PatientRow> patients;
        final List<ComorbidityRow> comorbidities;
        RawBatch(List<PatientRow> patients, List<Long> ids, List<VisitRow> visits, List<ComorbidityRow> comorbidities,List<Map<String,Object>> missing) { this.missing=missing; this.ids = ids; this.visits = visits; this.patients=patients; this.comorbidities=comorbidities; }
    }
}
