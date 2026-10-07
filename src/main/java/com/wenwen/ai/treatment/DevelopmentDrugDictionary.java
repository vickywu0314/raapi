package com.wenwen.ai.treatment;

import java.util.*;

/** 明示开发映射；dev:身份不冒充尚未提供的真实字典主键。 */
public final class DevelopmentDrugDictionary implements DrugDictionary {
    private final Snapshot view;
    public DevelopmentDrugDictionary() {
        Map<String,Drug> names=new LinkedHashMap<>();
        add(names,"csDMARD","甲氨蝶呤","来氟米特","柳氮磺吡啶","羟氯喹");
        add(names,"TNFi","阿达木单抗","依那西普","英夫利昔单抗","戈利木单抗","赛妥珠单抗");
        add(names,"JAKi","托法替布","巴瑞替尼","乌帕替尼");
        add(names,"IL-6i","托珠单抗","沙利鲁单抗");
        add(names,"Abatacept","阿巴西普");
        view=new Snapshot("dev-drug-v04",names);
    }
    private static void add(Map<String,Drug> names,String category,String... generics) {
        for(String name:generics) names.put(name,new Drug("dev:"+name,name,category));
    }
    public Snapshot snapshot() { return view; }
}
