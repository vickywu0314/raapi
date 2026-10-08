package com.wenwen.ai.parse;
import java.util.*;
public final class ParseDecision {
    private final String status;
    private final int unmapped;
    private final List<String> warnings;
    private final Map<String,Object> candidate;
    public ParseDecision(String status,Map<String,Object> candidate,int unmapped){this(status,candidate,unmapped,Collections.emptyList());}
    public ParseDecision(String status,Map<String,Object> candidate,int unmapped,List<String> warnings){this.status=status;this.candidate=candidate;this.unmapped=unmapped;this.warnings=warnings;}
    public List<String> getWarnings(){return warnings;}
    public int getUnmapped(){return unmapped;}
    public String getStatus(){return status;} public Map<String,Object> getCandidate(){return candidate;}
    public static String mode(String status){return "OK".equals(status)?"REPLACE_EXCEPT_IDS":"PARTIAL".equals(status)?"REQUIRE_CONFIRMATION":"KEEP_CURRENT";}
}
