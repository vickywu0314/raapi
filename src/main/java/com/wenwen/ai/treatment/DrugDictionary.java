package com.wenwen.ai.treatment;

import java.util.*;

/** 每次请求先固定不可变字典视图；不是旧库字典表定义。 */
public interface DrugDictionary {
    Snapshot snapshot();
    final class Drug {
        public final String id, name, category;
        public Drug(String id,String name,String category) { this.id=id; this.name=name; this.category=category; }
        public boolean recognized() { return "csDMARD".equals(category) || targeted(); }
        public boolean targeted() { return Arrays.asList("TNFi","JAKi","IL-6i","Abatacept").contains(category); }
    }
    class Snapshot {
        public final String version;
        private final Map<String,Drug> names;
        public Snapshot(String version,Map<String,Drug> names) { this.version=version; this.names=Collections.unmodifiableMap(new LinkedHashMap<>(names)); }
        public Drug identify(String name) { return name==null?null:names.get(name.trim()); }
    }
}
