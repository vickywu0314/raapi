package com.wenwen.ai.analysis;

import com.wenwen.ai.statistics.StatisticalPolicy;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;

/** 扩充现有描述结果；仅检验C内有效已知治疗，不改变研究基准U。 */
public final class StatisticalAnalysis {
    private StatisticalAnalysis() { }
    public static Map<String,Object> byTreatment(Collection<DerivedPatient> cohort,Collection<DerivedPatient> universe) {
        Map<String,Object> output=new LinkedHashMap<>(DescriptiveAnalysis.byTreatment(cohort,universe));
        List<String> included=new ArrayList<>();List<Map<String,Object>> excluded=new ArrayList<>(),ns=new ArrayList<>();List<long[]> counts=new ArrayList<>();
        for(Map<String,Object> group:(List<Map<String,Object>>)output.get("groups")) {
            String tx=(String)group.get("tx");int eval=(Integer)group.get("evalN"),target=(Integer)group.get("targetN");
            if(eval<5)excluded.add(object("tx",tx,"n",group.get("n"),"evalN",eval,"reason","VALID_N_LT_5"));
            else {included.add(tx);ns.add(object("tx",tx,"n",eval));counts.add(new long[]{target,eval-target});}
        }
        Integer nA=counts.size()==2?(Integer)ns.get(0).get("n"):null,nB=counts.size()==2?(Integer)ns.get(1).get("n"):null;
        String denominator="EVALUABLE_KNOWN_TREATMENT";
        Map<String,Object> test=new LinkedHashMap<>(counts.size()<2?StatisticalPolicy.unavailable("INSUFFICIENT_SAMPLE","ELIGIBLE_GROUPS_LT_2",nA,nB,denominator):StatisticalPolicy.contingency(counts.toArray(new long[0][]),nA,nB,denominator));
        if(counts.size()<2){test.put("expectedBelow5Cells",null);test.put("expectedCellCount",null);}
        test.put("includedGroups",Collections.unmodifiableList(included));test.put("excludedGroups",Collections.unmodifiableList(excluded));test.put("groupNs",Collections.unmodifiableList(ns));
        output.put("test",Collections.unmodifiableMap(test));output.put("comparisonNotice","组间基线不同，差异不代表疗效差异");return Collections.unmodifiableMap(output);
    }
}
