package com.wenwen.ai.cohort;

/** P02b 独立固定临床期望样本，不从政策结果生成期望。 */
abstract class ClinicalHttpFixture extends CohortHttpFixture {
    void clinicalFixture() throws Exception {
        sql("DELETE FROM patient_relation_doctor"); sql("DELETE FROM patient_follow_up_history");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,1,1,0),(101,2,2,0),(101,3,3,1),(101,4,4,0),(202,5,0,0)");
        sql("UPDATE patient_basic_info SET gender=2,card_no='110101198610070011',confirm_date='2016-10-08' WHERE id=1");
        sql("UPDATE patient_basic_info SET gender=1,card_no='110101198610080011' WHERE id=2");
        sql("UPDATE patient_basic_info SET gender=0,card_no='invalid',age=55 WHERE id=3");
        sql("UPDATE patient_basic_info SET gender=2,card_no='110101194610070011' WHERE id=4");
        for(int i=1;i<=4;i++) visit(i*100,i,0,"{\"result\":{\"crpScore\":3}}","2026-10-07",null,null);
    }
}
