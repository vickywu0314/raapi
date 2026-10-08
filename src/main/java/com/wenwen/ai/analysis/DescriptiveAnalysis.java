package com.wenwen.ai.analysis;

import java.math.*;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;

/** HTTP实际消费的内存描述聚合，不读库或改变临床政策。 */
public final class DescriptiveAnalysis {
    public static final String VERSION="dev-descriptive-v04";
    private DescriptiveAnalysis() { }
    public static Map<String,Object> target(Collection<DerivedPatient> patients) {
        int evaluable=0,target=0;
        for(DerivedPatient p:patients) if(p.evaluation!=null) {
            evaluable++; if(p.evaluation.getScore().compareTo(new BigDecimal("2.7"))<=0) target++;
        }
        return proportion(target,evaluable,patients.size()-evaluable);
    }
    public static Map<String,Object> proportion(int numerator,int denominator,int unknown) {
        return object("value",ratio(numerator,denominator),"meta",object("numerator",numerator,"denominator",denominator,"unknownN",unknown,
            "status",denominator==0?"NO_DATA":"OK","displayText",percent(numerator,denominator)));
    }
    public static Map<String,Object> cohortShare(int cohortN,int studyN) {
        Map<String,Object> result=proportion(cohortN,studyN,0);
        if(cohortN>0)return result;
        Map<String,Object> meta=new LinkedHashMap<>((Map<String,Object>)result.get("meta"));meta.put("status","NO_DATA");
        return object("value",result.get("value"),"meta",Collections.unmodifiableMap(meta));
    }
    public static Map<String,Object> clinicalRatio(Collection<DerivedPatient> patients,boolean female) {
        int numerator=0,unknown=0;
        for(DerivedPatient p:patients) {
            String state=female?p.clinical.getSex():p.clinical.getSero();
            if((female?"F":"TRUE").equals(state))numerator++;
            if(female?state==null:"UNKNOWN".equals(state))unknown++;
        }
        return proportion(numerator,patients.size(),unknown);
    }
    public static Map<String,Object> completeness(Collection<DerivedPatient> patients) {
        int complete=0,missing=0,unknown=0;
        for(DerivedPatient p:patients) {
            switch(p.qc.getStatus()) {
                case "COMPLETE":complete++;break;case "MISSING":missing++;break;case "UNKNOWN":unknown++;break;
                default:throw new IllegalStateException("未覆盖的QC状态");
            }
        }
        return object("ratio",proportion(complete,patients.size(),unknown),"incompleteCount",missing,"qcUnknownN",unknown);
    }
    public static Map<String,Object> median(Collection<DerivedPatient> patients,boolean age) {
        List<Integer> values=new ArrayList<>();
        for(DerivedPatient p:patients) {Integer value=age?p.clinical.getAge():p.clinical.getDiseaseDurationYears();if(value!=null)values.add(value);}
        Collections.sort(values);int n=values.size();BigDecimal value=null;
        if(n>0) value=n%2==1?BigDecimal.valueOf(values.get(n/2)):BigDecimal.valueOf(values.get(n/2-1)).add(BigDecimal.valueOf(values.get(n/2))).divide(new BigDecimal("2"));
        return object("value",value,"meta",object("validN",n,"unknownN",patients.size()-n,"status",n==0?"NO_DATA":"OK","displayText",value==null?"—":value.setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()));
    }
    public static BigDecimal ratio(int numerator,int denominator) {
        if(denominator==0)return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator),16,RoundingMode.HALF_UP);
    }
    public static Map<String,Object> byTreatment(Collection<DerivedPatient> cohort,Collection<DerivedPatient> universe) {
        List<Map<String,Object>> groups=new ArrayList<>();int unknown=0,none=0;
        for(DerivedPatient p:cohort) {
            if("UNKNOWN".equals(p.treatment.state)||"CONFLICT".equals(p.treatment.state))unknown++;
            if("NONE".equals(p.treatment.state))none++;
        }
        for(String category:Arrays.asList("csDMARD","TNFi","JAKi","IL-6i","Abatacept")) {
            List<DerivedPatient> members=new ArrayList<>();
            for(DerivedPatient p:cohort)if("ACTIVE".equals(p.treatment.state)&&category.equals(p.treatment.category))members.add(p);
            if(members.isEmpty())continue;
            Map<String,Object> target=target(members);Map<?,?> meta=(Map<?,?>)target.get("meta");
            groups.add(object("tx",category,"n",members.size(),"evalN",meta.get("denominator"),"targetN",meta.get("numerator"),"rate",target.get("value"),"status",meta.get("status"),"displayText",meta.get("displayText")));
        }
        Map<String,Object> study=target(universe);
        return object("groups",Collections.unmodifiableList(groups),"unknownTxN",unknown,"noCurrentTxN",none,"studyTargetRate",study.get("value"),"studyTargetMeta",study.get("meta"));
    }
    public static Map<String,Object> fm(Collection<DerivedPatient> cohort) {
        List<DerivedPatient> yes=new ArrayList<>(),no=new ArrayList<>();int unknown=0;
        for(DerivedPatient p:cohort) {
            if("TRUE".equals(p.clinical.getFm()))yes.add(p);else if("FALSE".equals(p.clinical.getFm()))no.add(p);else unknown++;
        }
        boolean enough=yes.size()>=3&&no.size()>=3;List<Map<String,Object>> rows=new ArrayList<>();
        if(enough)for(String metric:Arrays.asList("das28","tjc","sjc","crp","pain","haq"))rows.add(fmRow(metric,yes,no));
        return object("nFM",yes.size(),"nOther",no.size(),"unknownN",unknown,"status",enough?"OK":"INSUFFICIENT_SAMPLE","reason",enough?null:"GROUP_SIZE_LT_3","rows",Collections.unmodifiableList(rows));
    }
    private static final class Sample {
        BigDecimal sum=BigDecimal.ZERO;int n;final List<BigDecimal> values=new ArrayList<>();
        BigDecimal mean(){return n==0?null:divide(sum,BigDecimal.valueOf(n));}
    }
    private static Sample sample(String metric,Collection<DerivedPatient> patients) {
        Sample sample=new Sample();
        for(DerivedPatient p:patients) {
            if(p.evaluation==null)continue;
            BigDecimal value="das28".equals(metric)?p.evaluation.getScore():(BigDecimal)p.evaluation.getEvaluation().get(metric).get("value");
            if(value!=null){sample.sum=sample.sum.add(value);sample.n++;sample.values.add(value);}
        }
        return sample;
    }
    private static BigDecimal divide(BigDecimal numerator,BigDecimal denominator) {
        try{return numerator.divide(denominator);}catch(ArithmeticException nonTerminating){return numerator.divide(denominator,Math.max(16,numerator.scale()+16),RoundingMode.HALF_UP);}
    }
    private static Map<String,Object> fmRow(String metric,Collection<DerivedPatient> yes,Collection<DerivedPatient> no) {
        Sample a=sample(metric,yes),b=sample(metric,no);BigDecimal meanA=a.mean(),meanB=b.mean(),difference=null;Boolean highlight=null;
        if(a.n>0&&b.n>0) {
            // 用未舍入总和的有理数交叉乘，显示舍入不能升级阈值。
            BigDecimal numerator=a.sum.multiply(BigDecimal.valueOf(b.n)).subtract(b.sum.multiply(BigDecimal.valueOf(a.n)));
            BigDecimal denominator=BigDecimal.valueOf(a.n).multiply(BigDecimal.valueOf(b.n));
            difference=divide(numerator,denominator);
            BigDecimal threshold=new BigDecimal("pain".equals(metric)?"5":"haq".equals(metric)?".2":".5");
            highlight=numerator.abs().compareTo(threshold.multiply(denominator))>=0;
        }
        int decimals="pain".equals(metric)?0:"haq".equals(metric)?2:1;
        return object("metric",metric,"meanFM",meanA,"meanOther",meanB,"nFMValid",a.n,"nOtherValid",b.n,"difference",difference,"highlight",highlight,
            "status",difference==null?"NO_DATA":"OK","reason",difference==null?"MISSING_METRIC":null,
            "displayFM",display(meanA,decimals),"displayOther",display(meanB,decimals),"displayDifference",display(difference,decimals),"test",com.wenwen.ai.statistics.StatisticalPolicy.mannWhitney(a.values,b.values,"NON_MISSING_EVALUATION_METRIC"));
    }
    private static String display(BigDecimal value,int decimals){return value==null?"—":value.setScale(decimals,RoundingMode.HALF_UP).toPlainString();}
    public static Map<String,Object> lines(Collection<DerivedPatient> cohort) {
        int[] counts=new int[3];int unknown=0;
        for(DerivedPatient p:cohort) {
            Integer line=p.treatment.line;
            if(line==null)unknown++;else if(line>=1&&line<=3)counts[line-1]++;else throw new IllegalStateException("未覆盖的治疗线");
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=0;i<3;i++)rows.add(bucket("line",i+1,counts[i],cohort.size()));
        return object("groups",Collections.unmodifiableList(rows),"unknownLineN",unknown);
    }
    public static Map<String,Object> bucket(String key,Object label,int count,int denominator) {
        return object(key,label,"count",count,"value",ratio(count,denominator),"status",denominator==0?"NO_DATA":"OK","displayText",percent(count,denominator));
    }
    private static String percent(int numerator,int denominator) {
        return denominator==0?"0%":BigDecimal.valueOf(numerator).multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(denominator),0,RoundingMode.HALF_UP).toPlainString()+"%";
    }
}
