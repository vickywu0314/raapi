package com.wenwen.ai.chat;
import com.wenwen.ai.analysis.*;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;
/** 数值来自既有确定性政策，模型只选择key。 */
public final class ChatFacts {
    private ChatFacts(){}
    public static Map<String,Object> compute(List<DerivedPatient> members,String fmSourceVersion){
        Map<String,Object> facts=new LinkedHashMap<>();
        add(facts,"TARGET_RATE","DAS28-CRP达标率",DescriptiveAnalysis.target(members),"以有效DAS28-CRP评估为分母；未知评分不作达标。",new String[]{"bqpg.result.crpScore","follow_up_date/followUpDate"},"crp","descriptive");
        add(facts,"ACTIVITY_DISTRIBUTION","疾病活动分布",activity(members),"基线和评估分别使用各自有效人数；两个分布不是成对变化。",new String[]{"bqpg.result.crpScore","follow_up_date/followUpDate","zlfa.xyList/zcyList.startTime/endTime"},"crp","visitMatcher","descriptive");
        add(facts,"COHORT_SIZE","本轮范围人数",object("n",members.size()),"人数以当前医生本人RA去重范围及完整条件为准。",new String[]{"patient_relation_doctor.doctor_id/research_type/patient_id"},"clinical");
        add(facts,"DATA_COMPLETENESS","资料完整性",DescriptiveAnalysis.completeness(members),"缺失来自既有质控政策；未知与读取失败分别处理。",new String[]{"patient_basic_info.jws","patient_follow_up_history.bqpg/fzjc"},"qc");
        add(facts,"CLINICAL_SUMMARY","临床摘要",object("female",DescriptiveAnalysis.clinicalRatio(members,true),"serology",DescriptiveAnalysis.clinicalRatio(members,false),"age",DescriptiveAnalysis.median(members,true),"diseaseDuration",DescriptiveAnalysis.median(members,false)),"中位数只计算已知值；血清学为截至评估日曾经阳性，未知不补阴性。",new String[]{"patient_basic_info.gender","patient_basic_info.card_no","patient_basic_info.confirm_date/confirmDate","fzjc.lfsyz/kccpkt"},"clinical","serology","descriptive");
        Map<String,Object> treatment=new LinkedHashMap<>(StatisticalAnalysis.byTreatment(members,members));treatment.remove("studyTargetRate");treatment.remove("studyTargetMeta");
        add(facts,"BY_TREATMENT","按治疗分组",treatment,"只比较本轮范围的有效已知治疗；检验门槛与原统计政策一致，未作多重比较校正。",new String[]{"zlfa.xyList/zcyList.drugName/startTime/endTime","bqpg.result.crpScore"},"treatment","drugDictionary","statistics");
        add(facts,"FM_COMPARISON","FM分组比较",DescriptiveAnalysis.fm(members),"未记录FM不补阴性；每组至少3人才展示，指标检验各组有效人数至少5。空指标显示无数据，未作多重比较校正。",new String[]{fmSourceVersion==null?"patient_comorbidity.disease_code/since_year":"ra.ai.fm-evidence.json.state/observedAt","bqpg.result.crpScore/ytgjs/zzgjs","bqpg.hqaScore","fzjc.cfydb"},fmSourceVersion==null?new String[]{"clinical","descriptive","statistics"}:new String[]{"fmSource","descriptive","statistics"});
        add(facts,"TREATMENT_LINES","治疗线数",DescriptiveAnalysis.lines(members),"三桶沿已有治疗历史政策；未知治疗线数独立显示，不补为一线。",new String[]{"zlfa.xyList/zcyList.drugName/startTime/endTime"},"treatment","drugDictionary","descriptive");
        add(facts,"PAIRED_DAS28_CHANGE","成对DAS28-CRP变化",paired(members),"仅同人同方案的可靠基线与所选评估配对；基线减评估的正值表示分数下降，不代表疗效因果。无可靠配对时无数据。",new String[]{"bqpg.result.crpScore","follow_up_date/followUpDate","zlfa.xyList/zcyList.startTime/endTime"},"crp","treatment","visitMatcher");
        return Collections.unmodifiableMap(facts);
    }
    private static void add(Map<String,Object> facts,String key,String title,Object data,String explanation,String[] fields,String... policies){facts.put(key,object("key",key,"title",title,"data",data,"explanation",explanation+" 描述性结果不代表疗效因果。","evidence",object("scopeRef","CURRENT_SCOPE","sourceFields",Arrays.asList(fields),"policyKeys",Arrays.asList(policies))));}
    private static Map<String,Object> paired(List<DerivedPatient> members){
        int n=0;java.math.BigDecimal sum=java.math.BigDecimal.ZERO;Map<String,Integer> missing=new LinkedHashMap<>();
        for(DerivedPatient p:members){String reason=com.wenwen.ai.clinical.VisitMatcher.deltaMissingReason(p.baseline,p.treatment.key,p.evaluation,p.treatment.key,p.treatment);if(reason==null){sum=sum.add(com.wenwen.ai.clinical.VisitMatcher.delta(p.baseline,p.treatment.key,p.evaluation,p.treatment.key,p.treatment));n++;}else missing.put(reason,missing.getOrDefault(reason,0)+1);}
        java.math.BigDecimal mean=n==0?null:sum.divide(java.math.BigDecimal.valueOf(n),16,java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return object("pairedN",n,"unknownN",members.size()-n,"meanDelta",mean,"status",n==0?"NO_DATA":"OK","missingReasons",Collections.unmodifiableMap(missing));
    }
    private static Map<String,Object> activity(List<DerivedPatient> members){
        int[] base=new int[4],evaluation=new int[4];int bn=0,en=0;List<String> levels=Arrays.asList("remission","low","moderate","high");
        for(DerivedPatient p:members){if(p.baseline!=null){base[levels.indexOf(com.wenwen.util.Das28Util.activity(p.baseline.getScore()))]++;bn++;}if(p.evaluation!=null){evaluation[levels.indexOf(com.wenwen.util.Das28Util.activity(p.evaluation.getScore()))]++;en++;}}
        List<Object> b=new ArrayList<>(),e=new ArrayList<>();for(int i=0;i<4;i++){b.add(DescriptiveAnalysis.bucket("level",levels.get(i),base[i],bn));e.add(DescriptiveAnalysis.bucket("level",levels.get(i),evaluation[i],en));}
        return object("base",b,"current",e,"baseN",bn,"baseUnknownN",members.size()-bn,"evalN",en,"unknownN",members.size()-en);
    }
}
