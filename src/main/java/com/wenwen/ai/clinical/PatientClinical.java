package com.wenwen.ai.clinical;
import java.util.Map;
import java.util.function.Supplier;
/** 请求私有临床值；大来源明细仅在本页/导出真正访问时冻结，不跨请求缓存。 */
public final class PatientClinical {
    private final String sex,sero,fm,as;
    private final Integer age,diseaseDurationYears;
    private volatile Map<String,Object> provenance;
    private Supplier<Map<String,Object>> provenanceSource;
    public PatientClinical(String sex,Integer age,Integer duration,String sero,String fm,String as,Map<String,Object> provenance){this(sex,age,duration,sero,fm,as,()->provenance);this.provenance=ClinicalValues.copy(provenance);this.provenanceSource=null;}
    public PatientClinical(String sex,Integer age,Integer duration,String sero,String fm,String as,Supplier<Map<String,Object>> source){this.sex=sex;this.age=age;this.diseaseDurationYears=duration;this.sero=sero;this.fm=fm;this.as=as;this.provenanceSource=java.util.Objects.requireNonNull(source);}
    public String getSex(){return sex;}public Integer getAge(){return age;}public Integer getDiseaseDurationYears(){return diseaseDurationYears;}public String getSero(){return sero;}public String getFm(){return fm;}public String getAs(){return as;}
    public Map<String,Object> getProvenance(){Map<String,Object> ready=provenance;if(ready==null)synchronized(this){ready=provenance;if(ready==null){ready=ClinicalValues.copy(provenanceSource.get());provenance=ready;provenanceSource=null;}}return ready;}
}
