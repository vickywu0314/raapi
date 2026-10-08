package com.wenwen.ai.cohort;

import java.sql.*;

/** 六人手写真值；药史访视不附加隐式评分。 */
abstract class DescriptiveHttpFixture extends TreatmentHttpFixture {
    void sixPatients() throws Exception {
        sql("DELETE FROM patient_relation_doctor"); sql("DELETE FROM patient_follow_up_history");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,1,1,0),(101,2,0,0),(101,3,0,1),(101,4,0,0),(101,5,0,0),(101,6,0,0),(202,7,0,0)");
        String[] births={"19861007","19661007",null,"19761007","19961007","19561007"};
        String[] confirmed={"2025-10-07","2024-10-07","2023-10-07",null,"2021-10-07","2020-10-07"};
        int[] sex={2,1,2,1,1,2};
        for(int p=1;p<=6;p++) {
            try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE patient_basic_info SET name=?,gender=?,card_no=?,confirm_date=?,jws=? WHERE id=?")) {
                s.setString(1,"合成患者"+p);s.setInt(2,sex[p-1]);s.setString(3,births[p-1]==null?null:"110101"+births[p-1]+"0011");s.setString(4,confirmed[p-1]);s.setString(5,p==6?null:"{\"record\":1}");s.setInt(6,p);s.executeUpdate();
            }
        }
        for(int p=1;p<=6;p++)sql("UPDATE patient_basic_info SET study_no='RA-P03-0000"+p+"' WHERE id="+p);
        sql("UPDATE patient_basic_info SET name='另一医生专有姓名',study_no='OTHER-SECRET' WHERE id=7");
        String[] current={"甲氨蝶呤","依那西普","托法替布","托珠单抗","阿巴西普"};
        String[][] history={{},{},{"阿达木单抗"},{"阿达木单抗"},{}};
        String[] bases={"4.5","4.2","3.1","2.5","2.2"}, scores={"2","2.70","2.71","4.10","4.11"};
        String[] rf={"35","30",null,"20","20"},ccp={null,null,null,"25","25"};
        for(int p=1;p<=5;p++) {
            for(int h=0;h<history[p-1].length;h++) {
                String start="2024-0"+(h+1)+"-01",end="2024-0"+(h+1)+"-20";
                rawVisit(p*100+h,p,start,null,p==3?null:"{\"record\":1}",drugs(drug(history[p-1][h],start,end)));
            }
            rawVisit(p*100+10,p,"2026-04-05",bases[p-1],p==3?null:"{\"record\":1}",drugs(drug(current[p-1],"2026-04-06",null)));
            String labs="{\"cfydb\":6"+(rf[p-1]==null?"":",\"lfsyz\":"+rf[p-1])+(ccp[p-1]==null?"":",\"kccpkt\":"+ccp[p-1])+"}";
            rawVisit(p*100+20,p,"2026-10-06",scores[p-1],labs,drugs(drug(current[p-1],"2026-04-06",null)));
        }
        rawVisit(620,6,"2026-10-06",null,null,null);
        rawVisit(720,7,"2026-10-06","1","{\"lfsyz\":35}",drugs(drug("甲氨蝶呤","2026-04-06",null)));
        sql("INSERT INTO patient_comorbidity VALUES (1,1,'FM',null),(2,2,'FM',null),(3,3,'FM',null)");
        observed.reset();
    }
    void rawVisit(long id,long patient,String date,String score,String labs,String meds) throws Exception {
        visit(id,patient,0,score==null?null:"{\"result\":{\"crpScore\":"+score+",\"ytgjs\":2,\"zzgjs\":1},\"hqaScore\":1.2,\"tjScore\":15}",date,null,labs);
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE id=?")) {s.setString(1,meds);s.setLong(2,id);s.executeUpdate();}
    }
}
