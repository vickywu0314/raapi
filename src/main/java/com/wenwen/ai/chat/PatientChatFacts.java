package com.wenwen.ai.chat;
import com.wenwen.ai.source.*;
import com.wenwen.ai.insight.PatientInsightService;
import com.wenwen.ai.query.CohortException;
import java.time.*;
import com.wenwen.ai.treatment.*;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;
/** 授权后的后端事实与明确出站投影；不复制临床算法。 */
public final class PatientChatFacts {
    private PatientChatFacts(){}
    public static Map<String,Object> compute(SourceBatch batch,long patient,Long visit,LocalDate asOf,DrugDictionary.Snapshot view){
        if(!batch.getPatientIds().contains(patient))throw new CohortException(404,"NOT_FOUND","患者不存在或无权访问");
        if(visit==null){
            Map<String,Object> p=PatientInsightService.project(batch,patient,asOf,view),clinical=new LinkedHashMap<>();Map<String,Object> person=map(p.get("patient"));
            for(String k:Arrays.asList("sex","age","diseaseDurationYears","sero","fm","as","clinicalProvenance"))clinical.put(k,person.get(k));
            Map<String,Object> facts=new LinkedHashMap<>();List<Object> refs=new ArrayList<>();for(Object v:(List<?>)p.get("visits"))refs.add(object("patientId",Long.toString(patient),"visitId",map(v).get("visitId")));
            facts.put("PATIENT_COURSE",fact("PATIENT_COURSE","患者全程实测病程",object("visits",p.get("visits")),"完整真实记录；未来和无日期照实保留。",refs,new String[]{"bqpg.result.crpScore","bqpg.result.ytgjs","bqpg.result.zzgjs","bqpg.ztScoreByPatient","bqpg.hqaScore","fzjc.cfydb","bqpg.tjScore","follow_up_date/followUpDate"},"insight","crp"));
            facts.put("PATIENT_CLINICAL",fact("PATIENT_CLINICAL","患者临床摘要",clinical,"未知不补阴性；来源与病程政策一致。",clinicalRefs(clinical,patient),new String[]{"patient_basic_info.gender","patient_basic_info.card_no","patient_basic_info.confirm_date/confirmDate","fzjc.lfsyz/kccpkt",batch.getFmSourceVersion()==null?"patient_comorbidity.disease_code/since_year":"ra.ai.fm-evidence.json.state/observedAt"},"clinical","serology",batch.getFmSourceVersion()==null?"clinical":"fmSource"));
            facts.put("PATIENT_TREATMENT",fact("PATIENT_TREATMENT","患者治疗记录",p.get("treatment"),"可靠与估算起点分开；空记录不表示停药。",medicationRefs(batch,patient),new String[]{"zlfa.xyList/zcyList.drugName/startTime/endTime"},"insight","treatment","drugDictionary"));return facts;
        }
        ScoreVisit target=null;for(ScoreVisit v:batch.getVisits())if(v.getPatientId()==patient&&v.getId()==visit)target=v;
        if(target==null)throw new CohortException(404,"NOT_FOUND","访视不存在或无权访问");
        Map<String,Object> fact=object("key","VISIT_MEASUREMENTS","title","本次访视实测指标","data",PatientInsightService.measurement(target,asOf),"explanation","仅此记录实测指标；缺失不补值，描述不表示疗效因果。","evidence",object("scopeRef","CURRENT_SCOPE","sourceFields",Arrays.asList("bqpg.result.crpScore","bqpg.result.ytgjs","bqpg.result.zzgjs","bqpg.ztScoreByPatient","bqpg.hqaScore","fzjc.cfydb","bqpg.tjScore","follow_up_date/followUpDate"),"policyKeys",Arrays.asList("insight","crp"),"sourceVisits",Collections.singletonList(object("patientId",Long.toString(patient),"visitId",Long.toString(visit)))));
        Map<String,Object> facts=new LinkedHashMap<>();facts.put("VISIT_MEASUREMENTS",fact);facts.put("VISIT_MEDICATION",fact("VISIT_MEDICATION","本次访视记录用药",medication(target,view),"仅目标记录；空模块不表示停药，未知原药名不展示。",Collections.singletonList(object("patientId",Long.toString(patient),"visitId",Long.toString(visit))),new String[]{"zlfa.xyList/zcyList.drugName/startTime/endTime","follow_up_date/followUpDate"},"insight","drugDictionary"));return facts;
    }

    private static String recordedDate(String raw,String label,List<String> missing){if(raw==null){missing.add("MISSING_"+label);return null;}try{if(!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new java.time.DateTimeException("shape");return LocalDate.parse(raw).toString();}catch(java.time.DateTimeException invalid){missing.add("INVALID_"+label);return null;}}
    private static Map<String,Object> medication(ScoreVisit visit,DrugDictionary.Snapshot view){MedicationObservation observed=visit.getMedication();List<Object> drugs=new ArrayList<>();int unknown=0;for(MedicationObservation.Row row:observed.rows){DrugDictionary.Drug drug=view.identify(row.name);if(drug==null||!drug.recognized()){unknown++;continue;}List<String> missing=new ArrayList<>();String start=recordedDate(row.start,"START",missing),end=recordedDate(row.end,"END",missing);drugs.add(object("name",drug.name,"category",drug.category,"startDate",start,"endDate",end,"sourceField",row.field,"missingReasons",missing));}return object("visitId",Long.toString(visit.getId()),"observedAt",visit.getObservedAt()==null?null:visit.getObservedAt().toString(),"status",observed.invalid?"INVALID":observed.rows.isEmpty()?"NOT_RECORDED":"RECORDED","drugs",drugs,"unknownN",unknown);}
    private static Map<String,Object> fact(String key,String title,Object data,String explanation,List<Object> refs,String[] fields,String... policies){return object("key",key,"title",title,"data",data,"explanation",explanation+" 描述不代表疗效因果。","evidence",object("scopeRef","CURRENT_SCOPE","sourceFields",Arrays.asList(fields),"policyKeys",Arrays.asList(policies),"sourceVisits",refs));}
    private static List<Object> medicationRefs(SourceBatch batch,long patient){List<Object> refs=new ArrayList<>();for(ScoreVisit v:batch.getVisits())if(v.getPatientId()==patient&&(v.getMedication().invalid||!v.getMedication().rows.isEmpty()))refs.add(object("patientId",Long.toString(patient),"visitId",Long.toString(v.getId())));return refs;}
    private static List<Object> clinicalRefs(Map<String,Object> clinical,long patient){Set<String> ids=new LinkedHashSet<>();for(Object p:map(clinical.get("clinicalProvenance")).values()){Object observations=map(p).get("observations");if(observations instanceof List)for(Object o:(List<?>)observations){Object id=map(o).get("sourceVisitId");if(id!=null)ids.add(id.toString());}}List<Object> refs=new ArrayList<>();for(String id:ids)refs.add(object("patientId",Long.toString(patient),"visitId",id));return refs;}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static Map<String,Object> pick(Map<String,Object> value,String... keys){Map<String,Object> result=new LinkedHashMap<>();for(String key:keys)result.put(key,value.get(key));return result;}
    private static Map<String,Object> safeVisit(Map<String,Object> data,String ref){
        Map<String,Object> metrics=map(data.get("metrics")),safe=new LinkedHashMap<>();
        for(String k:Arrays.asList("das28Crp","tjc","sjc","gh","haq","crp","pain"))safe.put(k,pick(map(metrics.get(k)),"value","unit","observedAt","quality","missingReason"));
        return object("visitRef",ref,"observedAt",data.get("observedAt"),"temporalStatus",data.get("temporalStatus"),"metrics",safe);
    }
    private static Map<String,Object> safeEpisode(Map<String,Object> episode,Map<String,Object> names){Map<String,Object> safe=pick(episode,"state","category","line","targetedDrugHistoryN","startDate","estimatedStartDate","endDate","startConfidence");if(episode.containsKey("schemeDurationMonths"))safe.put("schemeDurationMonths",episode.get("schemeDurationMonths"));List<Object> drugs=new ArrayList<>();for(Object id:(List<?>)episode.get("genericDrugIds"))if(names.containsKey(id))drugs.add(names.get(id));safe.put("drugs",drugs);safe.put("quality",map(episode.get("provenance")).get("quality"));safe.put("missingReason",map(episode.get("provenance")).get("missingReason"));return safe;}
    public static Map<String,Object> outbound(Map<String,Object> facts){
        Map<String,Object> safe=new LinkedHashMap<>();for(Map.Entry<String,Object> item:facts.entrySet()){
            String key=item.getKey();Map<String,Object> data=map(map(item.getValue()).get("data"));
            if("VISIT_MEASUREMENTS".equals(key))safe.put(key,safeVisit(data,"v1"));
            else if("VISIT_MEDICATION".equals(key)){List<Object> drugs=new ArrayList<>();for(Object drug:(List<?>)data.get("drugs"))drugs.add(pick(map(drug),"name","category","startDate","endDate","missingReasons"));safe.put(key,object("visitRef","v1","observedAt",data.get("observedAt"),"status",data.get("status"),"drugs",drugs,"unknownN",data.get("unknownN")));}
            else if("PATIENT_COURSE".equals(key)){List<Object> visits=new ArrayList<>();int n=0;for(Object v:(List<?>)data.get("visits"))visits.add(safeVisit(map(v),"v"+(++n)));safe.put(key,object("visits",visits));}
            else if("PATIENT_CLINICAL".equals(key))safe.put(key,pick(data,"sex","age","diseaseDurationYears","sero","fm","as"));
            else if("PATIENT_TREATMENT".equals(key)){Map<String,Object> names=new LinkedHashMap<>();for(Object d:(List<?>)data.get("drugNames")){Map<String,Object> name=map(d);names.put((String)name.get("id"),pick(name,"name","category"));}List<Object> history=new ArrayList<>();for(Object e:(List<?>)data.get("history"))history.add(safeEpisode(map(e),names));safe.put(key,object("current",safeEpisode(map(data.get("current")),names),"history",history,"drugNames",new ArrayList<>(names.values())));}
        }return safe;
    }
}
