package com.wenwen.ai.patient;

import java.util.*;

/** 入组访次的读侧政策；不替代评分或治疗 episode 的临床匹配政策。 */
public final class PatientFollowUpPolicy {
    private PatientFollowUpPolicy() {}
    public static Map<Long,Integer> numbers(List<Map<String,Object>> visits) {
        List<Map<String,Object>> ordered=new ArrayList<>(visits);
        ordered.sort(Comparator.comparing((Map<String,Object> v)->(String)v.get("visitTime"),Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(v->((Number)v.get("visitId")).longValue()));
        Map<Long,Integer> numbers=new HashMap<>();
        for(int i=0;i<ordered.size();i++)numbers.put(((Number)ordered.get(i).get("visitId")).longValue(),i);
        return numbers;
    }
    public static boolean suspectedLost(String status,String lastVisitDate,java.time.LocalDate asOf) {
        return !"withdrawn".equals(status)&&lastVisitDate!=null&&java.time.LocalDate.parse(lastVisitDate).isBefore(asOf.minusYears(2));
    }
    public static String visitType(int number) { return number==0?"基线访视":"第 "+number+" 次随访"; }
}
