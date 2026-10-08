package com.wenwen.ai.analysis;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.source.*;
import com.wenwen.ai.clinical.VisitMatcher;
import com.wenwen.ai.treatment.DrugDictionary;
import com.wenwen.util.Das28Util;
import java.time.LocalDate;
import java.util.*;
/** 原analyze的U派生和AND筛选机械提取；两个真实消费者共享，无连接/Clock。 */
public final class CohortComputation {
    private CohortComputation() { }
    public static final class Derivation {
        public final Map<Long,DerivedPatient> universe;
        public final Map<Long,List<ScoreVisit>> visitsByPatient;
        public final Map<Long,Set<String>> observedQuality;
        private Derivation(Map<Long,DerivedPatient> u,Map<Long,List<ScoreVisit>> v,Map<Long,Set<String>> q) {
            universe=Collections.unmodifiableMap(new LinkedHashMap<>(u));
            Map<Long,List<ScoreVisit>> vv=new LinkedHashMap<>();v.forEach((id,rows)->vv.put(id,Collections.unmodifiableList(new ArrayList<>(rows))));visitsByPatient=Collections.unmodifiableMap(vv);
            Map<Long,Set<String>> qq=new LinkedHashMap<>();q.forEach((id,rows)->qq.put(id,Collections.unmodifiableSet(new LinkedHashSet<>(rows))));observedQuality=Collections.unmodifiableMap(qq);
        }
    }
    public static final class Selection {
        public final List<DerivedPatient> members;
        public final Integer submittedUniqueIdsN,effectiveIdsN;
        private Selection(List<DerivedPatient> members,Integer submitted,Integer effective){this.members=Collections.unmodifiableList(new ArrayList<>(members));submittedUniqueIdsN=submitted;effectiveIdsN=effective;}
    }
    public static Derivation derive(SourceBatch batch,String at,LocalDate asOf,DrugDictionary.Snapshot drugView) {
        Map<Long,List<ScoreVisit>> visitsByPatient=new HashMap<>();
        Map<Long,Set<String>> observedQuality = new HashMap<>();
        for (ScoreVisit visit : batch.getVisits()) {
            visitsByPatient.computeIfAbsent(visit.getPatientId(),key -> new ArrayList<>()).add(visit);
            observedQuality.computeIfAbsent(visit.getPatientId(),key -> new LinkedHashSet<>()).addAll(visit.getQuality());
        }
        Map<Long,DerivedPatient> universe=new LinkedHashMap<>();
        for(long id:batch.getPatientIds()) {
            List<ScoreVisit> records=visitsByPatient.getOrDefault(id,Collections.emptyList());
            com.wenwen.ai.treatment.TreatmentEpisode tx=com.wenwen.ai.treatment.TreatmentTimeline.build(records,asOf,drugView).current();
            ScoreVisit now=VisitMatcher.now(records,id,asOf),six=VisitMatcher.sixMonth(records,id,asOf,tx),base=VisitMatcher.baseline(records,id,asOf,tx);
            universe.put(id,new DerivedPatient(id,batch.getClinical().get(id),batch.getQc().get(id),tx,now,six,base,"6m".equals(at)?six:now));
        }
        return new Derivation(universe,visitsByPatient,observedQuality);
    }
    public static Selection select(Derivation derivation,CohortQuery query) {
        List<DerivedPatient> members=new ArrayList<>();
        Integer effectiveIdsN=query.getIds()==null?null:0;
        for (long id : derivation.universe.keySet()) {
            if (query.getIds() != null) {
                if (!query.getIds().contains(id)) continue;
                effectiveIdsN++;
            }
            DerivedPatient derived=derivation.universe.get(id);
            com.wenwen.ai.qc.MissingDataStatus qc=derived.qc;
            if (!qc.matches(query.getData())) continue;
            com.wenwen.ai.clinical.PatientClinical clinical=derived.clinical;
            if (query.getSex()!=null && !query.getSex().equals(clinical.getSex())) continue;
            if (query.getSero()!=null && !"TRUE".equals(clinical.getSero())) continue;
            if (query.getCm()!=null && !("FM".equals(query.getCm()) && "TRUE".equals(clinical.getFm()))
                    && !("AS".equals(query.getCm()) && "TRUE".equals(clinical.getAs()))
                    && !("none".equals(query.getCm()) && "FALSE".equals(clinical.getFm()) && "FALSE".equals(clinical.getAs()))) continue;
            if (query.getAge()!=null) {
                String[] bounds=query.getAge().split("-",-1);
                int min=Integer.parseInt(bounds[0]), max=bounds[1].isEmpty()?120:Integer.parseInt(bounds[1]);
                if (clinical.getAge()==null || clinical.getAge()<min || clinical.getAge()>max) continue;
            }
            List<ScoreVisit> medicationVisits=derivation.visitsByPatient.getOrDefault(id,Collections.emptyList());
            com.wenwen.ai.treatment.TreatmentEpisode treatment=derived.treatment;
            if(!treatment.matches(query.getTx())) continue;
            ScoreVisit now=derived.now,six=derived.six,baseline=derived.baseline,score=derived.evaluation;
            if("6m".equals(query.getAt()) && score==null) continue;
            String level = score == null ? null : Das28Util.activity(score.getScore());
            if (query.getAct() != null) {
                if (score == null) continue;
                boolean target = score.getScore().compareTo(new java.math.BigDecimal("2.7")) <= 0;
                if (!(query.getAct().equals(level) || ("target".equals(query.getAct()) && target)
                        || ("mod-high".equals(query.getAct()) && !target))) continue;
            }
            members.add(derived);
        }
        return new Selection(members,query.getIds()==null?null:query.getIds().size(),effectiveIdsN);
    }
}
