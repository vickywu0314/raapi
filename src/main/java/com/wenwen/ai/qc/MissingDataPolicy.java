package com.wenwen.ai.qc;

import com.wenwen.ai.clinical.VisitMatcher;
import java.util.*;

/** SQL成功读取原三M，CRP只用共同canonical值；存在性不限制日期。 */
public final class MissingDataPolicy {
    public static final String VERSION="dev-missing-v04";
    private MissingDataPolicy() { }
    public static Map<Long,MissingDataStatus> evaluate(List<Long> ids,Collection<? extends VisitMatcher.Candidate> visits,List<Map<String,Object>> issues) {
        Map<Long,Set<String>> codes=new LinkedHashMap<>();
        for(Long id:ids) codes.put(id,new TreeSet<>());
        for(Map<String,Object> row:issues) {
            long id=((Number)row.get("patient_id")).longValue(); String code=(String)row.get("rule_code");
            if(codes.containsKey(id) && Arrays.asList("M_BASELINE_LAB","M_COMORBIDITY","M_MEDICATION").contains(code)) codes.get(id).add(code);
        }
        Set<Long> valid=new HashSet<>(); for(VisitMatcher.Candidate visit:visits) if(visit.getScore()!=null) valid.add(visit.getPatientId());
        Map<Long,MissingDataStatus> result=new LinkedHashMap<>();
        for(Long id:ids) { if(!valid.contains(id)) codes.get(id).add("M_DAS28"); result.put(id,new MissingDataStatus(codes.get(id))); }
        return Collections.unmodifiableMap(result);
    }
}
