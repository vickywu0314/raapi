package com.wenwen.ai.analysis;
import com.wenwen.ai.statistics.StatisticalPolicy;
import com.wenwen.util.Das28Util;
import java.math.*;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;
/** 同次C的完整比较；只消费已派生事实，不持有数据库或医学替代规则。 */
public final class ComparisonAnalysis {
    public static final String VERSION="dev-compare-v01";
    private ComparisonAnalysis(){ }
    public static List<Map<String,Object>> rows(List<DerivedPatient> a,List<DerivedPatient> b,int overlap,boolean exclude) {
        List<Map<String,Object>> out=new ArrayList<>();out.add(object("metric","n","kind","COUNT","a",object("n",a.size()),"b",object("n",b.size()),"difference",null,"test",null));
        for(String metric:Arrays.asList("age","duration","das28At","crpAt","haqAt")) {
            List<BigDecimal> x=values(a,metric),y=values(b,metric);Map<String,Object> left=continuous(x,a.size()),right=continuous(y,b.size());
            BigDecimal medianA=(BigDecimal)left.get("median"),medianB=(BigDecimal)right.get("median");
            out.add(object("metric",metric,"kind","CONTINUOUS","a",left,"b",right,"difference",medianA==null||medianB==null?null:medianA.subtract(medianB),"test",continuousTest(x,y,overlap,exclude)));
        }
        for(String metric:Arrays.asList("female","sero","target","complete")) {
            Map<String,Object> left=binary(a,metric),right=binary(b,metric);int nx=(Integer)left.get("knownN"),ny=(Integer)right.get("knownN");String den="target".equals(metric)?"EVALUABLE_TARGET":"KNOWN_CATEGORY";
            out.add(object("metric",metric,"kind","BINARY","a",left,"b",right,"difference",percentageDifference(left,right),"test",contingencyTest(new long[][]{{(Integer)left.get("numerator"),nx-(Integer)left.get("numerator")},{(Integer)right.get("numerator"),ny-(Integer)right.get("numerator")}},nx,ny,den,overlap,exclude)));
        }
        for(String metric:Arrays.asList("activity","line")) {
            Map<String,Object> left=distribution(a,metric),right=distribution(b,metric);
            out.add(object("metric",metric,"kind","DISTRIBUTION","a",left,"b",right,"difference",null,"test",contingencyTest(new long[][]{groupCounts(left),groupCounts(right)},(Integer)left.get("knownN"),(Integer)right.get("knownN"),"activity".equals(metric)?"EVALUABLE_ACTIVITY":"KNOWN_LINE",overlap,exclude)));
        }
        return Collections.unmodifiableList(out);
    }
    private static Map<String,Object> blocked(int a,int b,String denominator,boolean contingency,int overlap,boolean exclude){
        String status=overlap>0&&!exclude?"OVERLAP":a<5||b<5?"INSUFFICIENT_SAMPLE":null;
        if(status==null)return null;Map<String,Object> t=new LinkedHashMap<>(StatisticalPolicy.unavailable(status,"OVERLAP".equals(status)?"COHORTS_OVERLAP":"VALID_N_LT_5",a,b,denominator));
        if(contingency){t.put("expectedBelow5Cells",null);t.put("expectedCellCount",null);}return Collections.unmodifiableMap(t);
    }
    static Map<String,Object> continuousTest(List<BigDecimal> a,List<BigDecimal> b,int overlap,boolean exclude){Map<String,Object> blocked=blocked(a.size(),b.size(),"NON_MISSING_METRIC",false,overlap,exclude);return blocked!=null?blocked:StatisticalPolicy.mannWhitney(a,b,"NON_MISSING_METRIC");}
    static Map<String,Object> contingencyTest(long[][] counts,int a,int b,String denominator,int overlap,boolean exclude){Map<String,Object> blocked=blocked(a,b,denominator,true,overlap,exclude);return blocked!=null?blocked:StatisticalPolicy.contingency(counts,a,b,denominator);}
    @SuppressWarnings("unchecked") private static long[] groupCounts(Map<String,Object> side){List<Map<String,Object>> groups=(List<Map<String,Object>>)side.get("groups");long[] out=new long[groups.size()];for(int i=0;i<out.length;i++)out[i]=((Number)groups.get(i).get("count")).longValue();return out;}
    static BigDecimal quantile(List<BigDecimal> sorted,BigDecimal p){
        if(sorted.isEmpty())return null;BigDecimal h=p.multiply(BigDecimal.valueOf(sorted.size()-1));int i=h.intValue();BigDecimal frac=h.subtract(BigDecimal.valueOf(i));return sorted.get(i).add(frac.signum()==0?BigDecimal.ZERO:sorted.get(i+1).subtract(sorted.get(i)).multiply(frac));
    }
    static Map<String,Object> continuous(List<BigDecimal> values,int total){
        List<BigDecimal> sorted=new ArrayList<>(values);Collections.sort(sorted);BigDecimal q1=quantile(sorted,new BigDecimal(".25")),median=quantile(sorted,new BigDecimal(".5")),q3=quantile(sorted,new BigDecimal(".75"));
        return object("validN",sorted.size(),"unknownN",total-sorted.size(),"q1",q1,"median",median,"q3",q3,"iqr",q1==null?null:q3.subtract(q1),"status",sorted.isEmpty()?"NO_DATA":"OK");
    }
    static List<BigDecimal> values(Collection<DerivedPatient> patients,String metric){List<BigDecimal> out=new ArrayList<>();for(DerivedPatient p:patients){BigDecimal v=value(p,metric);if(v!=null)out.add(v);}return out;}
    static BigDecimal value(DerivedPatient p,String metric){
        if("age".equals(metric))return p.clinical.getAge()==null?null:BigDecimal.valueOf(p.clinical.getAge());
        if("duration".equals(metric))return p.clinical.getDiseaseDurationYears()==null?null:BigDecimal.valueOf(p.clinical.getDiseaseDurationYears());
        if(p.evaluation==null)return null;
        if("das28At".equals(metric))return p.evaluation.getScore();
        return (BigDecimal)p.evaluation.getEvaluation().get("crpAt".equals(metric)?"crp":"haq").get("value");
    }
    static int evalN(Collection<DerivedPatient> patients){int n=0;for(DerivedPatient p:patients)if(p.evaluation!=null)n++;return n;}
    private static Map<String,Object> binary(Collection<DerivedPatient> patients,String metric){
        int yes=0,known=0;for(DerivedPatient p:patients){
            if("female".equals(metric)){String s=p.clinical.getSex();if(s!=null)known++;if("F".equals(s))yes++;}
            else if("sero".equals(metric)){String s=p.clinical.getSero();if(!"UNKNOWN".equals(s))known++;if("TRUE".equals(s))yes++;}
            else if("complete".equals(metric)){known++;if("COMPLETE".equals(p.qc.getStatus()))yes++;}
            else if(p.evaluation!=null){known++;if(p.evaluation.getScore().compareTo(new BigDecimal("2.7"))<=0)yes++;}
        }
        int denominator="target".equals(metric)?known:patients.size();
        return object("numerator",yes,"denominator",denominator,"knownN",known,"unknownN",patients.size()-known,"value",DescriptiveAnalysis.ratio(yes,denominator),"status",denominator==0?"NO_DATA":"OK");
    }
    static BigDecimal percentageDifference(Map<String,Object> a,Map<String,Object> b){
        int x=(Integer)a.get("numerator"),y=(Integer)b.get("numerator"),nx=(Integer)a.get("denominator"),ny=(Integer)b.get("denominator");
        if(nx==0||ny==0)return null;return BigDecimal.valueOf(x).multiply(BigDecimal.valueOf(ny)).subtract(BigDecimal.valueOf(y).multiply(BigDecimal.valueOf(nx))).multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(nx).multiply(BigDecimal.valueOf(ny)),20,RoundingMode.HALF_UP);
    }
    private static Map<String,Object> distribution(Collection<DerivedPatient> patients,String metric){
        String[] categories="activity".equals(metric)?new String[]{"remission","low","moderate","high"}:new String[]{"1","2","3"};int[] counts=new int[categories.length];int known=0;
        for(DerivedPatient p:patients){String category="activity".equals(metric)?p.evaluation==null?null:Das28Util.activity(p.evaluation.getScore()):p.treatment.line==null?null:p.treatment.line.toString();if(category!=null)for(int i=0;i<categories.length;i++)if(categories[i].equals(category)){counts[i]++;known++;}}
        int denominator="activity".equals(metric)?known:patients.size();List<Map<String,Object>> groups=new ArrayList<>();for(int i=0;i<categories.length;i++)groups.add(object("category",categories[i],"count",counts[i],"value",DescriptiveAnalysis.ratio(counts[i],denominator)));
        return object("denominator",denominator,"knownN",known,"unknownN",patients.size()-known,"status",denominator==0?"NO_DATA":"OK","groups",groups);
    }
    public static Map<String,Object> coverage(Collection<DerivedPatient> patients){
        Map<String,Object> out=new LinkedHashMap<>();for(String metric:Arrays.asList("age","duration","das28At","crpAt","haqAt","female","sero","target","complete","activity","line")){
            int valid=0,dated=0;java.time.LocalDate min=null,max=null;
            for(DerivedPatient p:patients){boolean known;String date=null;
                if(Arrays.asList("age","duration","das28At","crpAt","haqAt").contains(metric)){
                    known=value(p,metric)!=null;
                    if(known&&"das28At".equals(metric))date=p.evaluation.getObservedAt()==null?null:p.evaluation.getObservedAt().toString();
                    if(known&&("crpAt".equals(metric)||"haqAt".equals(metric)))date=(String)p.evaluation.getEvaluation().get("crpAt".equals(metric)?"crp":"haq").get("observedAt");
                }else if("female".equals(metric))known=p.clinical.getSex()!=null;
                else if("sero".equals(metric))known=!"UNKNOWN".equals(p.clinical.getSero());
                else if("complete".equals(metric))known=true;
                else if("line".equals(metric))known=p.treatment.line!=null;
                else{known=p.evaluation!=null;if(known)date=p.evaluation.getObservedAt()==null?null:p.evaluation.getObservedAt().toString();}
                if(known){valid++;if(date!=null){java.time.LocalDate d=java.time.LocalDate.parse(date);dated++;if(min==null||d.isBefore(min))min=d;if(max==null||d.isAfter(max))max=d;}}
            }
            int undated=valid-dated;out.put(metric,object("validN",valid,"datedN",dated,"undatedN",undated,"missingN",patients.size()-valid,"minDate",min==null?null:min.toString(),"maxDate",max==null?null:max.toString(),"status",valid==0?"NO_DATA":dated==0?"UNKNOWN":undated==0?"KNOWN":"PARTIAL"));
        }return Collections.unmodifiableMap(out);
    }
}
