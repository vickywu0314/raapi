package com.wenwen.ai.clinical;

import lombok.Value;

/** 离开源适配器的必要派生值，不含身份证或生日。 */
@Value
public class PatientClinical {
    String sex;
    Integer age;
    Integer diseaseDurationYears;
    String sero;
    String fm;
    String as;
    java.util.Map<String,Object> provenance;
    public PatientClinical(String sex,Integer age,Integer diseaseDurationYears,String sero,String fm,String as,java.util.Map<String,Object> provenance) {
        this.sex=sex; this.age=age; this.diseaseDurationYears=diseaseDurationYears; this.sero=sero; this.fm=fm; this.as=as;
        this.provenance=ClinicalValues.copy(provenance);
    }
}
