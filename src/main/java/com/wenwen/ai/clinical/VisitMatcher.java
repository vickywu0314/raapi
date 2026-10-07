package com.wenwen.ai.clinical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.wenwen.ai.treatment.TreatmentEpisode;
import com.wenwen.util.Das28Util;

/** 无SQL/输入顺序依赖的患者内日期匹配；调用者固定本次上海asOf。 */
public final class VisitMatcher {
    private VisitMatcher() { }
    public interface Candidate {
        long getId(); long getPatientId(); LocalDate getObservedAt(); BigDecimal getScore();
    }
    /** 旧Mapper行的最小不可变适配；值规则仍由canonicalCrp拥有。 */
    public static final class Visit implements Candidate {
        private final long id,patientId; private final LocalDate date; private final BigDecimal score;
        public Visit(long id,long patientId,LocalDate date,String raw) { this.id=id; this.patientId=patientId; this.date=date; this.score=Das28Util.canonicalCrp(raw); }
        public long getId(){return id;} public long getPatientId(){return patientId;}
        public LocalDate getObservedAt(){return date;} public BigDecimal getScore(){return score;}
    }
    public static <T extends Candidate> T now(Collection<T> visits,long patientId,LocalDate asOf) {
        return latest(visits,patientId,asOf,visit -> visit.getScore()!=null);
    }
    /** 最新化验可以来自无DAS的访视；仍复用同一患者/日期/ID顺序。 */
    public static <T extends Candidate> T latest(Collection<T> visits,long patientId,LocalDate asOf,java.util.function.Predicate<T> evaluable) {
        T selected=null;
        for(T visit:visits) {
            if(visit.getPatientId()!=patientId || visit.getObservedAt()==null || visit.getObservedAt().isAfter(asOf) || !evaluable.test(visit)) continue;
            if(selected==null || visit.getObservedAt().isAfter(selected.getObservedAt())
                    || (visit.getObservedAt().equals(selected.getObservedAt()) && visit.getId()>selected.getId())) selected=visit;
        }
        return selected;
    }
    public static boolean reliable(TreatmentEpisode episode) {
        return episode!=null && ("ACTIVE".equals(episode.state)||"CONFLICT".equals(episode.state))
            && episode.start!=null && "EXPLICIT".equals(episode.confidence);
    }
    private static boolean valid(Candidate visit,long patientId,LocalDate asOf) {
        return visit.getPatientId()==patientId && visit.getScore()!=null && visit.getObservedAt()!=null && !visit.getObservedAt().isAfter(asOf);
    }
    public static <T extends Candidate> T sixMonth(Collection<T> visits,long patientId,LocalDate asOf,TreatmentEpisode episode) {
        if(!reliable(episode)) return null;
        LocalDate target=episode.start.plusMonths(6); T selected=null;
        for(T visit:visits) {
            if(!valid(visit,patientId,asOf)) continue;
            LocalDate date=visit.getObservedAt();
            if(date.isBefore(target.minusDays(60)) || date.isAfter(target.plusDays(60)) || date.isBefore(episode.start)
                    || (episode.end!=null && date.isAfter(episode.end))) continue;
            if(selected==null || nearest(visit,selected,target)) selected=visit;
        }
        return selected;
    }
    public static <T extends Candidate> T baseline(Collection<T> visits,long patientId,LocalDate asOf,TreatmentEpisode episode) {
        if(!reliable(episode)) return null;
        T selected=null;
        for(T visit:visits) {
            if(!valid(visit,patientId,asOf)) continue;
            LocalDate date=visit.getObservedAt();
            if(date.isBefore(episode.start.minusDays(90)) || date.isAfter(episode.start.plusDays(14))) continue;
            if(selected==null || nearest(visit,selected,episode.start)) selected=visit;
        }
        return selected;
    }
    /** 两端分别携带所评价episode身份，拒绝跨患者、跨段或逆日期配对。 */
    public static String deltaMissingReason(Candidate baseline,String baselineKey,Candidate evaluation,String evaluationKey,TreatmentEpisode episode) {
        if(!reliable(episode)) return "NO_RELIABLE_START";
        if(baseline==null || baseline.getScore()==null) return "MISSING_BASELINE";
        if(evaluation==null || evaluation.getScore()==null) return "MISSING_EVALUATION";
        if(baseline.getPatientId()!=evaluation.getPatientId()) return "PATIENT_MISMATCH";
        if(!Objects.equals(episode.key,baselineKey) || !Objects.equals(episode.key,evaluationKey)) return "EPISODE_MISMATCH";
        if(baseline.getObservedAt()==null || evaluation.getObservedAt()==null) return "UNDATED_PAIR";
        if(baseline.getObservedAt().isAfter(evaluation.getObservedAt())) return "BASELINE_AFTER_EVALUATION";
        if(evaluation.getObservedAt().isBefore(episode.start)) return "EVAL_BEFORE_SCHEME";
        if(episode.end!=null && evaluation.getObservedAt().isAfter(episode.end)) return "EVAL_AFTER_SCHEME";
        return null;
    }
    public static BigDecimal delta(Candidate baseline,String baselineKey,Candidate evaluation,String evaluationKey,TreatmentEpisode episode) {
        return deltaMissingReason(baseline,baselineKey,evaluation,evaluationKey,episode)==null
            ? baseline.getScore().subtract(evaluation.getScore()).setScale(2,java.math.RoundingMode.HALF_UP) : null;
    }
    public static boolean beforeScheme(Candidate evaluation,TreatmentEpisode episode) {
        LocalDate start=episode==null?null:episode.start==null?episode.estimatedStart:episode.start;
        return start!=null && evaluation!=null && evaluation.getObservedAt()!=null && evaluation.getObservedAt().isBefore(start);
    }
    private static boolean nearest(Candidate candidate,Candidate old,LocalDate target) {
        long a=Math.abs(ChronoUnit.DAYS.between(target,candidate.getObservedAt())), b=Math.abs(ChronoUnit.DAYS.between(target,old.getObservedAt()));
        return a<b || (a==b && (candidate.getObservedAt().isBefore(old.getObservedAt())
            || (candidate.getObservedAt().equals(old.getObservedAt()) && candidate.getId()>old.getId())));
    }
}
