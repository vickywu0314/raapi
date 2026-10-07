package com.wenwen.ai.treatment;

import com.wenwen.ai.source.ScoreVisit;
import java.time.LocalDate;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;

/** 先接收观察，再重放已知起止事件；当前选择不删除已结束的历史集合。 */
public final class TreatmentTimeline {
    public final List<TreatmentEpisode> episodes;
    private final TreatmentEpisode current;
    private TreatmentTimeline(List<TreatmentEpisode> episodes,TreatmentEpisode current) {
        this.episodes=Collections.unmodifiableList(new ArrayList<>(episodes)); this.current=current;
    }
    public TreatmentEpisode current() { return current; }
    public static TreatmentTimeline build(List<ScoreVisit> visits,LocalDate asOf,DrugDictionary.Snapshot dictionary) {
        List<ScoreVisit> ordered=new ArrayList<>(); Set<String> excluded=new TreeSet<>();
        for(ScoreVisit visit:visits) {
            if(visit.getObservedAt()==null) excluded.add("UNDATED_MEDICATION_OBSERVATION");
            else if(visit.getObservedAt().isAfter(asOf)) excluded.add("FUTURE_MEDICATION_OBSERVATION");
            else ordered.add(visit);
        }
        ordered.sort(Comparator.comparing(ScoreVisit::getObservedAt).thenComparingLong(ScoreVisit::getId));
        Engine engine=new Engine(dictionary);
        for(ScoreVisit visit:ordered) {
            engine.advance(visit.getObservedAt());
            if(visit.getMedication().rows.isEmpty() && !visit.getMedication().invalid) { engine.carry(); continue; }
            engine.observe(new Plan(visit,dictionary));
        }
        engine.advance(asOf);
        if(engine.pending!=null || (engine.plan!=null && engine.plan.hasFutureStart(asOf))) excluded.add("FUTURE_TREATMENT_START");
        if(engine.current==null) engine.current=unknown(dictionary,engine.history,null,"NO_MEDICATION_OBSERVATION",Collections.emptyList());
        if(!excluded.isEmpty()) engine.current=withQuality(engine.current,excluded);
        if(engine.episodes.isEmpty()) engine.episodes.add(engine.current);
        else if(!"NONE".equals(engine.current.state)) engine.episodes.set(engine.episodes.size()-1,engine.current);
        return new TreatmentTimeline(engine.episodes,engine.current);
    }
    private static final class Engine {
        final DrugDictionary.Snapshot dictionary;
        final List<TreatmentEpisode> episodes=new ArrayList<>();
        final Set<String> history=new TreeSet<>();
        TreatmentEpisode current;
        Plan plan,pending;
        LocalDate cursor;
        Engine(DrugDictionary.Snapshot dictionary) { this.dictionary=dictionary; }
        void advance(LocalDate limit) {
            if(cursor==null) { cursor=limit; return; }
            LocalDate event;
            while((event=nextEvent(limit))!=null) {
                cursor=event;
                if(pending!=null && !pending.active(event).isEmpty()) { plan=pending; pending=null; accept(plan,event,false); }
                else if(plan!=null) accept(plan,event,true);
            }
            cursor=limit;
        }
        LocalDate nextEvent(LocalDate limit) {
            LocalDate next=null;
            for(Plan candidate:Arrays.asList(plan,pending)) if(candidate!=null) for(Fact fact:candidate.facts.values()) {
                for(LocalDate date:Arrays.asList(fact.begin(),fact.finish()==null?null:fact.finish().plusDays(1)))
                    if(date!=null && date.isAfter(cursor) && !date.isAfter(limit) && (next==null || date.isBefore(next))) next=date;
            }
            return next;
        }
        void observe(Plan next) {
            addHistory(next,cursor);
            if(next.invalid || next.unmapped) {
                current=unknown(dictionary,history,next.visit,next.invalid?"INVALID_MEDICATION_SOURCE":"UNMAPPED_DRUG",new ArrayList<>(next.facts.keySet()));
                episodes.add(current); plan=pending=null; return;
            }
            if(next.active(cursor).isEmpty() && next.hasFutureStart(cursor)) { pending=next; return; }
            if(plan!=null && current!=null && !"NONE".equals(current.state) && !"UNKNOWN".equals(current.state)
                    && definingIds(next.active(cursor)).equals(current.genericIds)) next=next.merge(plan,current);
            plan=next; pending=null; accept(plan,cursor,false);
        }
        void addHistory(Plan source,LocalDate time) {
            for(Fact fact:source.facts.values()) if(fact.drug.targeted() && (fact.begin()==null || !fact.begin().isAfter(time))) history.add(fact.drug.id);
        }
        void accept(Plan source,LocalDate time,boolean event) {
            addHistory(source,time);
            SortedMap<String,Fact> active=source.active(time);
            List<String> ids=definingIds(active);
            if(ids.isEmpty()) {
                if(current!=null && !"UNKNOWN".equals(current.state)) {
                    LocalDate end=lastEnd(source);
                    current=new TreatmentEpisode(current.key,"NONE",null,null,history.size(),null,null,end,null,current.genericIds,current.provenance);
                } else if(current==null) {
                    // 首次接收已结束证据仍保留历史实体与明确日期，不以当前剪枝抹去。
                    TreatmentEpisode past=source.episode(source.facts,time,history.size());
                    if(past!=null) { episodes.add(past); current=new TreatmentEpisode(past.key,"NONE",null,null,history.size(),null,null,lastEnd(source),null,past.genericIds,past.provenance); }
                }
                return;
            }
            TreatmentEpisode next=source.episode(active,time,history.size());
            boolean same=current!=null && !"NONE".equals(current.state) && !"UNKNOWN".equals(current.state) && current.genericIds.equals(ids);
            if(same) {
                Set<String> quality=qualities(current); quality.addAll(qualities(next));
                boolean conflict=(current.start!=null && next.start!=null && !current.start.equals(next.start))
                    || (current.end!=null && next.end!=null && !current.end.equals(next.end));
                if(conflict) quality.add("DATE_CONFLICT");
                boolean unreliable=conflict || "UNKNOWN".equals(current.confidence) || "UNKNOWN".equals(next.confidence);
                LocalDate start=unreliable?null:next.start==null?current.start:next.start;
                LocalDate estimated=unreliable?null:start==null?current.estimatedStart:null;
                String confidence=unreliable?"UNKNOWN":start==null?current.confidence:"EXPLICIT";
                if("ESTIMATED_EXPLICIT_END".equals(current.confidence) && !unreliable
                        && (next.start==null || next.start.isBefore(current.estimatedStart))) {
                    start=null; estimated=current.estimatedStart; confidence=current.confidence;
                }
                Map<String,Object> provenance=new LinkedHashMap<>(next.provenance);
                provenance.put("quality",new ArrayList<>(quality));
                if(unreliable) provenance.put("missingReason",quality.contains("DATE_CONFLICT")?"DATE_CONFLICT":"INVALID_TREATMENT_DATE");
                next=new TreatmentEpisode(current.key,next.state,next.category,next.line,next.historyN,start,estimated,next.end,confidence,next.genericIds,provenance);
                episodes.set(episodes.size()-1,next);
            } else {
                boolean endedDefinition=event && current!=null && current.end!=null && current.end.equals(time.minusDays(1));
                if(endedDefinition) {
                    Map<String,Object> provenance=new LinkedHashMap<>(next.provenance); Set<String> quality=qualities(next); quality.add("END_DERIVED_SCHEME_START"); provenance.put("quality",new ArrayList<>(quality));
                    next=new TreatmentEpisode(next.key+":end:"+time,next.state,next.category,next.line,next.historyN,null,time,next.end,"ESTIMATED_EXPLICIT_END",next.genericIds,provenance);
                }
                LocalDate boundary=effectiveStart(next);
                if(current!=null && !"NONE".equals(current.state) && !"UNKNOWN".equals(current.state) && boundary!=null) closePrevious(boundary.minusDays(1));
                episodes.add(next);
            }
            current=next;
        }
        void closePrevious(LocalDate end) {
            TreatmentEpisode old=episodes.get(episodes.size()-1);
            if(old.end!=null && old.end.isBefore(end)) return;
            episodes.set(episodes.size()-1,new TreatmentEpisode(old.key,old.state,old.category,old.line,old.historyN,old.start,old.estimatedStart,end,old.confidence,old.genericIds,old.provenance));
        }
        void carry() {
            if(current!=null && ("ACTIVE".equals(current.state) || "CONFLICT".equals(current.state))) {
                current=withQuality(current,Collections.singleton("CARRIED_FORWARD")); episodes.set(episodes.size()-1,current);
            }
        }
    }
    private static final class Plan {
        final ScoreVisit visit;
        final DrugDictionary.Snapshot dictionary;
        final SortedMap<String,Fact> facts=new TreeMap<>();
        final List<Map<String,Object>> originals=new ArrayList<>();
        final boolean invalid;
        boolean unmapped;
        Plan(ScoreVisit visit,DrugDictionary.Snapshot dictionary) {
            this.visit=visit; this.dictionary=dictionary; this.invalid=visit.getMedication().invalid;
            for(MedicationObservation.Row row:visit.getMedication().rows) {
                DrugDictionary.Drug drug=dictionary.identify(row.name);
                if(drug==null || !drug.recognized()) { unmapped=true; continue; }
                Fact fact=new Fact(drug,row.start,row.end,false,false);
                originals.add(object("genericDrugId",drug.id,"startTime",row.start,"endTime",row.end,"visitId",Long.toString(visit.getId()),"field",row.field,"observedAt",visit.getObservedAt().toString()));
                Fact old=facts.get(drug.id); facts.put(drug.id,old==null?fact:fact.merge(old));
            }
        }
        Plan merge(Plan old,TreatmentEpisode current) {
            for(String id:new ArrayList<>(facts.keySet())) if(old.facts.containsKey(id)) {
                Fact fact=facts.get(id);
                boolean newOrigin="ESTIMATED_EXPLICIT_END".equals(current.confidence) && fact.begin()!=null && !fact.begin().isBefore(current.estimatedStart);
                if(!newOrigin) facts.put(id,fact.merge(old.facts.get(id)));
            }
            List<Map<String,Object>> combined=new ArrayList<>(old.originals); combined.addAll(originals); originals.clear(); originals.addAll(combined);
            return this;
        }
        SortedMap<String,Fact> active(LocalDate time) {
            SortedMap<String,Fact> active=new TreeMap<>();
            for(Fact fact:facts.values()) if((fact.begin()==null || !fact.begin().isAfter(time)) && (fact.finish()==null || !fact.finish().isBefore(time))) active.put(fact.drug.id,fact);
            return active;
        }
        boolean hasFutureStart(LocalDate time) { for(Fact fact:facts.values()) if(fact.begin()!=null && fact.begin().isAfter(time)) return true; return false; }
        TreatmentEpisode episode(SortedMap<String,Fact> active,LocalDate time,int historyN) {
            List<String> ids=definingIds(active); if(ids.isEmpty()) return null;
            LocalDate start=null,end=null; boolean explicit=true,invalidDate=false,conflicting=false;
            Set<String> quality=new LinkedHashSet<>(); quality.add("LEGACY_UNVERIFIED");
            for(Fact fact:facts.values()) { if(fact.invalid) quality.add("INVALID_TREATMENT_DATE"); if(fact.conflict) quality.add("DATE_CONFLICT"); }
            for(String id:ids) {
                Fact fact=active.get(id); invalidDate|=fact.invalid; conflicting|=fact.conflict;
                if(fact.begin()==null) explicit=false; else if(start==null || fact.begin().isAfter(start)) start=fact.begin();
                if(fact.finish()!=null && (end==null || fact.finish().isBefore(end))) end=fact.finish();
            }
            if(!explicit || invalidDate || conflicting) start=null;
            if(invalidDate || conflicting) end=null;
            boolean conflict=ids.size()>1 && active.get(ids.get(0)).drug.targeted();
            originals.sort(Comparator.comparing(fact -> String.valueOf(fact.get("genericDrugId"))+"/"+fact.get("visitId")+"/"+fact.get("field")+"/"+fact.get("startTime")+"/"+fact.get("endTime")));
            Map<String,Object> provenance=object("visitId",Long.toString(visit.getId()),"field",sourceField(visit),"observedAt",visit.getObservedAt().toString(),"quality",new ArrayList<>(quality),
                "missingReason",invalidDate?"INVALID_TREATMENT_DATE":conflicting?"DATE_CONFLICT":null,"dictionaryVersion",dictionary.version,"drugFacts",originals);
            return new TreatmentEpisode("visit:"+visit.getId()+":"+String.join("+",ids),conflict?"CONFLICT":"ACTIVE",conflict?null:active.get(ids.get(0)).drug.category,
                conflict?null:active.get(ids.get(0)).drug.targeted()?Math.min(historyN+1,3):1,historyN,start,start==null && !invalidDate && !conflicting?visit.getObservedAt():null,end,
                invalidDate || conflicting?"UNKNOWN":start==null?"ESTIMATED_FIRST_OBSERVED":"EXPLICIT",ids,provenance);
        }
    }
    private static final class Fact {
        final DrugDictionary.Drug drug;
        final String start,end;
        final boolean conflict,invalid;
        Fact(DrugDictionary.Drug drug,String start,String end,boolean conflict,boolean invalid) {
            this.drug=drug; this.start=start; this.end=end; this.conflict=conflict;
            LocalDate begin=date(start),finish=date(end);
            this.invalid=invalid || (start!=null && begin==null) || (end!=null && finish==null) || (begin!=null && finish!=null && begin.isAfter(finish));
        }
        Fact merge(Fact old) {
            boolean differing=(old.start!=null && start!=null && !old.start.equals(start)) || (old.end!=null && end!=null && !old.end.equals(end));
            return new Fact(drug,first(old.start,start),first(old.end,end),conflict || old.conflict || differing,invalid || old.invalid);
        }
        LocalDate begin() { return invalid || conflict?null:date(start); }
        LocalDate finish() { return invalid || conflict?null:date(end); }
    }
    private static List<String> definingIds(SortedMap<String,Fact> active) {
        List<String> targeted=new ArrayList<>(); for(Fact fact:active.values()) if(fact.drug.targeted()) targeted.add(fact.drug.id);
        return targeted.isEmpty()?new ArrayList<>(active.keySet()):targeted;
    }
    private static LocalDate lastEnd(Plan plan) { LocalDate end=null; for(Fact fact:plan.facts.values()) if(fact.finish()!=null && (end==null || fact.finish().isAfter(end))) end=fact.finish(); return end; }
    private static Set<String> qualities(TreatmentEpisode value) { return new LinkedHashSet<>((List<String>)value.provenance.get("quality")); }
    private static TreatmentEpisode withQuality(TreatmentEpisode value,Collection<String> extra) {
        Map<String,Object> provenance=new LinkedHashMap<>(value.provenance); Set<String> quality=qualities(value); quality.addAll(extra); provenance.put("quality",new ArrayList<>(quality));
        return new TreatmentEpisode(value.key,value.state,value.category,value.line,value.historyN,value.start,value.estimatedStart,value.end,value.confidence,value.genericIds,provenance);
    }
    private static LocalDate effectiveStart(TreatmentEpisode episode) { return episode.start==null?episode.estimatedStart:episode.start; }
    private static TreatmentEpisode unknown(DrugDictionary.Snapshot dictionary,Set<String> history,ScoreVisit visit,String reason,List<String> ids) {
        return new TreatmentEpisode(null,"UNKNOWN",null,null,history.size(),null,null,null,null,ids,object("visitId",visit==null?null:Long.toString(visit.getId()),"field",visit==null?"zlfa":sourceField(visit),"observedAt",visit==null?null:visit.getObservedAt().toString(),"dictionaryVersion",dictionary.version,"quality",Collections.singletonList(reason),"missingReason",reason));
    }
    private static String sourceField(ScoreVisit visit) { Set<String> fields=new TreeSet<>(); for(MedicationObservation.Row row:visit.getMedication().rows) fields.add(row.field); return fields.isEmpty()?"zlfa":String.join("/",fields); }
    private static String first(String a,String b) { return a==null?b:b==null?a:a.compareTo(b)<=0?a:b; }
    private static LocalDate date(String raw) {
        if(raw==null || !raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try { return LocalDate.parse(raw); } catch(java.time.format.DateTimeParseException e) { return null; }
    }
}
