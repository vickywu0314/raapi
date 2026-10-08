package com.wenwen.ai.cohort;

/** 独立写定列联表经真实RA关系、药物起点及同访视评分进入HTTP。 */
abstract class StatisticalHttpFixture extends DescriptiveHttpFixture {
    void treatmentTable(int[][] counts) throws Exception {
        sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_follow_up_history");sql("DELETE FROM patient_comorbidity");sql("DELETE FROM patient_basic_info");
        String[] meds={"甲氨蝶呤","依那西普","托法替布","托珠单抗","阿巴西普"};int id=0;
        for(int g=0;g<counts.length;g++)for(int target=0;target<2;target++)for(int i=0;i<counts[g][target];i++) {
            id++;sql("INSERT INTO patient_basic_info(id,name) VALUES ("+id+",'P03b合成患者')");
            sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES (101,"+id+",0,0)");
            rawVisit(id*100L+1,id,"2026-10-06",target==0?"2.0":"4.0","{\"cfydb\":6}",drugs(drug(meds[g],"2026-04-06",null)));
        }
        observed.reset();
    }
}
