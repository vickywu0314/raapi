package com.wenwen.ai.analysis;

import com.wenwen.ai.clinical.PatientClinical;
import com.wenwen.ai.source.ScoreVisit;
import com.wenwen.ai.qc.MissingDataPolicy;
import com.wenwen.ai.treatment.TreatmentEpisode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.wenwen.vo.AiCohortVo.object;

/** 明确合成derived事实验证Service真实消费边界，不冒充真实FM FALSE或疼痛尺度接入。 */
class DescriptiveAnalysisTest {
    @Test void explicitFmAndOtherFactsProduceActualDasMeans() {
        List<DerivedPatient> people=groups(new String[]{"1","2","3"},new String[]{"0","1","2"});
        people.add(patient(7,"UNKNOWN",null,Collections.emptyMap()));
        Map<String,Object> fm=DescriptiveAnalysis.fm(people);
        assertEquals("OK",fm.get("status"));assertNull(fm.get("reason"));assertEquals(3,fm.get("nFM"));assertEquals(3,fm.get("nOther"));assertEquals(1,fm.get("unknownN"));
        List<?> rows=(List<?>)fm.get("rows");assertEquals(6,rows.size());
        Map<?,?> das=(Map<?,?>)rows.get(0);assertEquals("das28",das.get("metric"));decimal(das,"meanFM","2");decimal(das,"meanOther","1");decimal(das,"difference","1");assertEquals(true,das.get("highlight"));assertEquals(3,das.get("nFMValid"));assertEquals(3,das.get("nOtherValid"));assertEquals("2.0",das.get("displayFM"));assertEquals("1.0",das.get("displayDifference"));
    }
    @Test void eachFmMetricUsesItsOwnValidNAndMissingValues() {
        List<DerivedPatient> people=new ArrayList<>();
        String[] tjcA={"1","2",null},tjcB={"1",null,null},crpB={"3","6","9"};
        for(int i=0;i<3;i++) {
            Map<String,String> a=new HashMap<>();a.put("tjc",tjcA[i]);a.put("sjc","1");a.put("pain","15");a.put("haq","1.20");
            Map<String,String> b=new HashMap<>();b.put("tjc",tjcB[i]);b.put("sjc",".50001");b.put("crp",crpB[i]);b.put("pain","10");b.put("haq","1");
            people.add(patient(i+1,"TRUE","2",a));people.add(patient(i+10,"FALSE","1",b));
        }
        Map<?,?> tjc=row(people,"tjc");decimal(tjc,"meanFM","1.5");decimal(tjc,"meanOther","1");decimal(tjc,"difference",".5");assertEquals(2,tjc.get("nFMValid"));assertEquals(1,tjc.get("nOtherValid"));assertEquals(true,tjc.get("highlight"));
        Map<?,?> sjc=row(people,"sjc");decimal(sjc,"difference",".49999");assertEquals("0.5",sjc.get("displayDifference"));assertEquals(false,sjc.get("highlight"));
        Map<?,?> crp=row(people,"crp");assertNull(crp.get("meanFM"));decimal(crp,"meanOther","6");assertEquals(0,crp.get("nFMValid"));assertEquals(3,crp.get("nOtherValid"));assertNull(crp.get("difference"));assertNull(crp.get("highlight"));assertEquals("NO_DATA",crp.get("status"));assertEquals("MISSING_METRIC",crp.get("reason"));assertEquals("—",crp.get("displayFM"));assertEquals("—",crp.get("displayDifference"));
        Map<?,?> pain=row(people,"pain");decimal(pain,"difference","5");assertEquals(true,pain.get("highlight"));assertEquals("15",pain.get("displayFM"));
        Map<?,?> haq=row(people,"haq");decimal(haq,"difference",".20");assertEquals(true,haq.get("highlight"));assertEquals("0.20",haq.get("displayDifference"));
        List<DerivedPatient> unverified=groups(new String[]{"1","1","1"},new String[]{"1","1","1"});assertNull(row(unverified,"pain").get("meanFM"));assertEquals("NO_DATA",row(unverified,"pain").get("status"));
    }
    @Test void rationalMeansAndHaqHighlightIgnoreDisplayRounding() {
        List<DerivedPatient> people=groups(new String[]{"1","0","0"},new String[]{"0","0","0"});
        Map<?,?> das=row(people,"das28");decimal(das,"meanFM",".3333333333333333");decimal(das,"difference",".3333333333333333");assertEquals(3,das.get("nFMValid"));assertEquals(3,das.get("nOtherValid"));assertEquals(false,das.get("highlight"));assertEquals("0.3",das.get("displayDifference"));
        people=new ArrayList<>();for(int i=0;i<3;i++){people.add(patient(i+1,"TRUE","1",Collections.singletonMap("haq","1.19999")));people.add(patient(i+10,"FALSE","1",Collections.singletonMap("haq","1")));}
        Map<?,?> haq=row(people,"haq");decimal(haq,"difference",".19999");assertEquals("0.20",haq.get("displayDifference"));assertEquals(false,haq.get("highlight"));
    }
    @Test void unknownNeverJoinsFalseAndEitherSmallGroupSuppressesAllRows() {
        for(boolean smallFm:Arrays.asList(true,false)) {
            List<DerivedPatient> people=groups(smallFm?new String[]{"1","2"}:new String[]{"1","2","3"},smallFm?new String[]{"0","1","2"}:new String[]{"0","1"});
            people.add(patient(7,"UNKNOWN","9",Collections.emptyMap()));Map<String,Object> fm=DescriptiveAnalysis.fm(people);
            assertEquals(smallFm?2:3,fm.get("nFM"));assertEquals(smallFm?3:2,fm.get("nOther"));assertEquals(1,fm.get("unknownN"));assertEquals("INSUFFICIENT_SAMPLE",fm.get("status"));assertEquals("GROUP_SIZE_LT_3",fm.get("reason"));assertEquals(Collections.emptyList(),fm.get("rows"));
        }
    }
    @Test void fmConsumesEvaluationVisitInsteadOfOtherCurrentOrBaselineVisits() {
        List<DerivedPatient> people=new ArrayList<>();
        for(int i=0;i<6;i++) {
            DerivedPatient eval=patient(i+1,i<3?"TRUE":"FALSE",i<3?"2":"1",new HashMap<String,String>(){{put("crp","3");put("haq","1");}});
            ScoreVisit other=patient(100+i,"UNKNOWN","9",new HashMap<String,String>(){{put("crp","999");put("haq","2.9");}}).evaluation;
            people.add(new DerivedPatient(eval.id,eval.clinical,eval.qc,eval.treatment,other,eval.evaluation,other,eval.evaluation));
        }
        decimal(row(people,"das28"),"meanFM","2");decimal(row(people,"crp"),"meanFM","3");decimal(row(people,"haq"),"meanOther","1");
    }
    Map<?,?> row(List<DerivedPatient> people,String metric) {
        for(Object row:(List<?>)DescriptiveAnalysis.fm(people).get("rows"))if(metric.equals(((Map<?,?>)row).get("metric")))return (Map<?,?>)row;
        fail("缺少行"+metric);return null;
    }
    List<DerivedPatient> groups(String[] a,String[] b) {
        List<DerivedPatient> people=new ArrayList<>();for(int i=0;i<a.length;i++)people.add(patient(i+1,"TRUE",a[i],Collections.emptyMap()));for(int i=0;i<b.length;i++)people.add(patient(10+i,"FALSE",b[i],Collections.emptyMap()));return people;
    }
    DerivedPatient patient(long id,String fm,String das,Map<String,String> metrics) {
        Map<String,Map<String,Object>> evaluation=new LinkedHashMap<>();
        for(String metric:Arrays.asList("tjc","sjc","crp","pain","haq"))evaluation.put(metric,object("value",metrics.get(metric)==null?null:new BigDecimal(metrics.get(metric)),"unit","pain".equals(metric)?"EXPLICIT_SYNTHETIC_CONFIRMED_SCALE":"SYNTHETIC"));
        ScoreVisit visit=das==null?null:new ScoreVisit(id,id,LocalDate.of(2026,10,6),das,new BigDecimal(das),Collections.emptyList(),null,null,evaluation);
        PatientClinical clinical=new PatientClinical(null,null,null,"UNKNOWN",fm,"UNKNOWN",Collections.emptyMap());
        TreatmentEpisode tx=new TreatmentEpisode(null,"UNKNOWN",null,null,0,null,null,null,null,Collections.emptyList(),Collections.emptyMap());
        return new DerivedPatient(id,clinical,MissingDataPolicy.evaluate(Collections.singletonList(id),Collections.emptyList(),Collections.emptyList()).get(id),tx,visit,null,null,visit);
    }
    void decimal(Map<?,?> value,String key,String expected) {assertNotNull(value.get(key),key);assertEquals(0,new BigDecimal(expected).compareTo((BigDecimal)value.get(key)),key);}
}
