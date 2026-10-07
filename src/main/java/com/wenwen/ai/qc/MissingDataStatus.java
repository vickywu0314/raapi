package com.wenwen.ai.qc;

import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;

/** 四M均已覆盖的不可变结果；读源/计算失败由调用方传播。 */
public final class MissingDataStatus {
    private final List<String> missingCodes;
    MissingDataStatus(Collection<String> codes) { missingCodes=Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(codes))); }
    public List<String> getMissingCodes() { return missingCodes; }
    public String getStatus() { return missingCodes.isEmpty()?"COMPLETE":"MISSING"; }
    public boolean matches(String filter) { return filter==null || ("complete".equals(filter) && missingCodes.isEmpty()) || ("missing".equals(filter) && !missingCodes.isEmpty()); }
    public Map<String,Object> asMap() { return Collections.unmodifiableMap(object("status",getStatus(),"missingCodes",missingCodes,"ruleVersion",MissingDataPolicy.VERSION)); }
}
