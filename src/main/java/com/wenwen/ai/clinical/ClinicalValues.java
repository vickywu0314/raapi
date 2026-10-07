package com.wenwen.ai.clinical;

import java.util.*;

/** 来源值的集合边界：复制所有嵌套 map/list，标量保持原精度。 */
public final class ClinicalValues {
    private ClinicalValues() { }
    public static Map<String,Object> copy(Map<String,?> values) {
        Map<String,Object> result=new LinkedHashMap<>();
        values.forEach((key,value) -> result.put(key,freeze(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Object freeze(Object value) {
        if (value instanceof Map) {
            Map<Object,Object> result=new LinkedHashMap<>();
            ((Map<?,?>)value).forEach((key,item) -> result.put(key,freeze(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List) {
            List<Object> result=new ArrayList<>(); for(Object item:(List<?>)value) result.add(freeze(item));
            return Collections.unmodifiableList(result);
        }
        return value;
    }
}
