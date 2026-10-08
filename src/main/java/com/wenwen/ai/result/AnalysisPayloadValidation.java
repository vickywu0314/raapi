package com.wenwen.ai.result;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;
/** 验证耐久格式及必要语义；不重新执行任何医学或统计算法。 */
final class AnalysisPayloadValidation {
    private AnalysisPayloadValidation(){}
    private static Set<String> keys(String text){return new HashSet<>(Arrays.asList(text.split(" ")));}
    private static final Set<String> NUMBERS=keys("das28At das28Base das28Current deltaDas28 value rate targetRate ageMedian durationMedian cohortRate femaleRate seroRate completeRate studyTargetRate meanFM meanOther difference pValue");
    private static final Set<String> INTEGERS=keys("n studyTotal submittedUniqueIdsN effectiveIdsN baseN baseUnknownN evalN unknownN unknownTxN numerator denominator validN incompleteCount qcUnknownN evaluable noCurrentTxN targetN nFM nOther nFMValid nOtherValid unknownLineN count line age diseaseDurationYears targetedDrugHistoryN schemeDurationMonths nA nB expectedBelow5Cells expectedCellCount sinceYear");
    private static final Set<String> NULLABLE_INTEGERS=keys("submittedUniqueIdsN effectiveIdsN line age diseaseDurationYears schemeDurationMonths nA nB expectedBelow5Cells expectedCellCount sinceYear");
    private static final Set<String> BOOLEAN=keys("eligible6m significant highlight");
    private static final Set<String> IDS=keys("patientId sourceVisitId baselineVisitId evalVisitId nowVisitId sourceRowId visitId");
    private static final Set<String> DATES=keys("observedAt baselineDate evalDate nowDate target6mDate startDate estimatedStartDate endDate startTime endTime asOfDate");
    private static final Set<String> INSTANTS=keys("readStartedAt readCompletedAt computedAt expiresAt");
    private static final Set<String> LISTS=keys("quality missingCodes genericDrugIds supportedFilters includedGroups warnings");
    private static final Set<String> NESTED=keys("activity stats metricMeta byTx fm lines meta filters rows current base groups test studyTargetMeta excludedGroups groupNs policyVersions clinical qc selection scoreProvenance baselineProvenance crpAt crpCurrent evaluation clinicalProvenance treatment observations provenance drugFacts sex age diseaseDurationYears sero as tjc sjc gh haq crp pain targetRate ageMedian durationMedian cohortRate femaleRate seroRate completeRate");
    private static final Set<String> CODES=keys("LEGACY_UNVERIFIED COMPONENTS_MISSING INVALID_JSON INVALID_DATE DISCREPANCY SCALE_UNVERIFIED DATE_UNSPECIFIED INVALID_TREATMENT_DATE DATE_CONFLICT CARRIED_FORWARD END_DERIVED_SCHEME_START UNDATED_MEDICATION_OBSERVATION FUTURE_MEDICATION_OBSERVATION FUTURE_TREATMENT_START EVAL_BEFORE_SCHEME NO_MEDICATION_OBSERVATION INVALID_MEDICATION_SOURCE UNMAPPED_DRUG NO_VALID_CRP NO_VALID_BASELINE NO_RELIABLE_START NO_ELIGIBLE_6M MISSING_BASELINE MISSING_EVALUATION PATIENT_MISMATCH EPISODE_MISMATCH UNDATED_PAIR BASELINE_AFTER_EVALUATION EVAL_AFTER_SCHEME NO_EVALUATION NO_VALID_CRP_LAB MISSING_VALUE INVALID_VALUE OUT_OF_RANGE NON_INTEGER UNVERIFIED_SCALE UNRECOGNIZED_GENDER NO_VALID_BIRTH_DATE NO_VALID_CONFIRM_DATE NO_CLASSIFIABLE_DATED_TEST NO_POSITIVE_ASSOCIATION VALID_N_LT_5 ELIGIBLE_GROUPS_LT_2 ZERO_RANK_VARIANCE ZERO_MARGIN GROUP_SIZE_LT_3 MISSING_METRIC SMALL_SAMPLE_TIES SPARSE_EXPECTED_COUNTS M_BASELINE_LAB M_COMORBIDITY M_MEDICATION M_DAS28");
    private static final Set<String> SOURCE_FIELDS=new HashSet<>(Arrays.asList("bqpg.result.crpScore","bqpg.result.ytgjs","bqpg.result.zzgjs","bqpg.ztScoreByPatient","bqpg.hqaScore","bqpg.result.hqaScore","fzjc.cfydb","bqpg.tjScore","patient_basic_info.gender","patient_basic_info.card_no","COALESCE(patient_basic_info.confirm_date,patient_basic_info.confirmDate)","fzjc.lfsyz/fzjc.kccpkt","patient_comorbidity.disease_code/since_year","zlfa","zlfa.xyList","zlfa.zcyList","zlfa.xyList/zlfa.zcyList"));
    @SuppressWarnings("unchecked") private static Map<String,Object> obj(Object value,String allowed,String required){if(!(value instanceof Map))fail();Map<String,Object> m=(Map<String,Object>)value;Set<String> permit=keys(allowed);if(!permit.containsAll(m.keySet())||!m.keySet().containsAll(keys(required)))fail();for(Map.Entry<String,Object> e:m.entrySet())if(!NESTED.contains(e.getKey()))scalar(e.getKey(),e.getValue());return m;}
    private static Map<String,Object> obj(Object value,String fields){return obj(value,fields,fields);}
    private static List<?> list(Object value){if(!(value instanceof List))fail();return (List<?>)value;}
    private static boolean one(String text,String... values){return Arrays.asList(values).contains(text);}
    private static BigDecimal number(Object value){if(!(value instanceof Number))fail();try{BigDecimal n=value instanceof BigDecimal?(BigDecimal)value:new BigDecimal(value.toString());return n;}catch(RuntimeException e){throw AnalysisSettings.unavailable();}}
    private static long integer(Object value){try{return number(value).longValueExact();}catch(ArithmeticException e){throw AnalysisSettings.unavailable();}}
    private static void id(Object value){if(!(value instanceof String)||!((String)value).matches("[1-9][0-9]*"))fail();try{Long.parseLong((String)value);}catch(RuntimeException e){fail();}}
    private static void scalar(String key,Object value){
        if(value==null){if((INTEGERS.contains(key)&&!NULLABLE_INTEGERS.contains(key))||LISTS.contains(key)||one(key,"status","state","at","traceId","asOfDate","readStartedAt","readCompletedAt","computedAt","expiresAt","completion","patientProjection","statisticalDisclosure","comparisonNotice","ruleVersion","policyVersion","dictionaryVersion","statistics","descriptive","qc","crp","now","clinical","serology","treatment","visitMatcher","drugDictionary","eligible6m","rawRetention"))fail();return;}
        if(NUMBERS.contains(key)){number(value);return;}
        if(INTEGERS.contains(key)){integer(value);return;}
        if(BOOLEAN.contains(key)){if(!(value instanceof Boolean))fail();return;}
        if(IDS.contains(key)){id(value);return;}
        if(LISTS.contains(key)){for(Object x:list(value)){
            if(!(x instanceof String))fail();if("quality".equals(key)||"missingCodes".equals(key)||"warnings".equals(key)){if(!CODES.contains(x))fail();}
            else if("genericDrugIds".equals(key))identifier((String)x);
            else if("includedGroups".equals(key)){if(!one((String)x,"csDMARD","TNFi","JAKi","IL-6i","Abatacept"))fail();}
            else if(!one((String)x,"studyCode","at","act","ids","sex","age","sero","cm","tx","data"))fail();
        }return;}
        if(!(value instanceof String))fail();String text=(String)value;
        if(DATES.contains(key)){try{if(!LocalDate.parse(text).toString().equals(text))fail();}catch(RuntimeException e){fail();}return;}
        if(INSTANTS.contains(key)){try{if(!Instant.parse(text).toString().equals(text))fail();}catch(RuntimeException e){fail();}return;}
        switch(key){
            case "raw":if(!text.matches("\\s*[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?\\s*"))fail();number(new BigDecimal(text.trim()));break;
            case "status":if(!one(text,"OK","NO_DATA","INSUFFICIENT_SAMPLE","DEGENERATE","COMPLETE","MISSING","UNKNOWN","negative","low_positive","high_positive","positive"))fail();break;
            case "quality":case "missingCodes":case "missingReason":case "deltaMissingReason":case "reason":case "warnings":if(!CODES.contains(text))fail();break;
            case "source":if(!one(text,"LEGACY_STORED","LEGACY_RECORDED","LEGACY_POSITIVE_ASSOCIATION"))fail();break;
            case "sourceField":case "field":if(!SOURCE_FIELDS.contains(text))fail();break;
            case "state":if(!one(text,"ACTIVE","CONFLICT","NONE","UNKNOWN"))fail();break;
            case "category":case "tx":if(!one(text,"csDMARD","TNFi","JAKi","IL-6i","Abatacept"))fail();break;
            case "activity":case "level":if(!one(text,"remission","low","moderate","high"))fail();break;
            case "label":if(!one(text,"临床缓解","低疾病活动度","中疾病活动度","高疾病活动度"))fail();break;
            case "sex":if(!one(text,"F","M"))fail();break;
            case "sero":case "fm":case "as":if(!one(text,"TRUE","FALSE","UNKNOWN"))fail();break;
            case "at":if(!one(text,"now","6m"))fail();break;
            case "metric":if(!one(text,"das28","tjc","sjc","crp","pain","haq"))fail();break;
            case "unit":if(!one(text,"count","0-100","0-3","mg/L"))fail();break;
            case "startConfidence":if(!one(text,"EXPLICIT","ESTIMATED_FIRST_OBSERVED","ESTIMATED_EXPLICIT_END","UNKNOWN"))fail();break;
            case "method":if(!one(text,"MANN_WHITNEY_EXACT","MANN_WHITNEY_ASYMPTOTIC","FISHER_EXACT","CHI_SQUARE"))fail();break;
            case "testDenominator":if(!one(text,"NON_MISSING_EVALUATION_METRIC","EVALUABLE_KNOWN_TREATMENT"))fail();break;
            case "displayText":case "displayFM":case "displayOther":case "displayDifference":case "displayP":if(!one(text,"—","<0.001")&&!text.matches("-?[0-9]+(?:\\.[0-9]+)?%?"))fail();break;
            case "traceId":if(!AnalysisCursor.canonicalId(text))fail();break;
            case "completion":if(!"P03_RESULT".equals(text))fail();break;
            case "patientProjection":if(!"LIVE_SOURCE_V04".equals(text))fail();break;
            case "rawRetention":if(!"OMITTED".equals(text))fail();break;
            case "statisticalDisclosure":if(!"探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系".equals(text))fail();break;
            case "comparisonNotice":if(!"组间基线不同，差异不代表疗效差异".equals(text))fail();break;
            case "episodeKey":if(!text.matches("visit:[1-9][0-9]*:[\\p{L}\\p{N}:._+\\-]+"))fail();break;
            case "genericDrugId":identifier(text);break;
            case "ruleVersion":case "policyVersion":case "dictionaryVersion":case "statistics":case "descriptive":case "qc":case "crp":case "now":case "clinical":case "serology":case "treatment":case "visitMatcher":case "drugDictionary":identifier(text);break;
            default:fail();
        }
    }
    private static void identifier(String v){if(v.isEmpty()||v.length()>200||!v.matches("[\\p{L}\\p{N}:._\\-]+"))fail();}
    private static void fail(){throw AnalysisSettings.unavailable();}
    static void validate(Map<String,Object> root){
        obj(root,"n studyTotal submittedUniqueIdsN effectiveIdsN activity stats metricMeta byTx fm lines meta filters rows");
        for(String k:Arrays.asList("n","studyTotal"))if(root.get(k)==null||integer(root.get(k))<0)fail();if(integer(root.get("n"))>integer(root.get("studyTotal")))fail();
        Map<String,Object> activity=obj(root.get("activity"),"current base baseN baseUnknownN evalN unknownN unknownTxN");for(String k:Arrays.asList("current","base"))for(Object r:list(activity.get(k)))obj(r,"level count value status displayText label");
        Map<String,Object> stats=obj(root.get("stats"),"targetRate ageMedian durationMedian cohortRate femaleRate seroRate completeRate incompleteCount qcUnknownN evaluable");for(String k:stats.keySet())scalar(k,stats.get(k));
        Map<String,Object> metrics=obj(root.get("metricMeta"),"targetRate ageMedian durationMedian cohortRate femaleRate seroRate completeRate");for(String k:metrics.keySet())obj(metrics.get(k),"ageMedian".equals(k)||"durationMedian".equals(k)?"validN unknownN status displayText":"numerator denominator unknownN status displayText");
        Map<String,Object> by=obj(root.get("byTx"),"groups unknownTxN noCurrentTxN studyTargetRate studyTargetMeta test comparisonNotice");for(Object r:list(by.get("groups")))obj(r,"tx n evalN targetN rate status displayText");obj(by.get("studyTargetMeta"),"numerator denominator unknownN status displayText");test(by.get("test"),true);
        Map<String,Object> fm=obj(root.get("fm"),"nFM nOther unknownN status reason rows");for(Object r:list(fm.get("rows"))){Map<String,Object> row=obj(r,"metric meanFM meanOther nFMValid nOtherValid difference highlight status reason displayFM displayOther displayDifference test");test(row.get("test"),false);}
        Map<String,Object> lines=obj(root.get("lines"),"groups unknownLineN");for(Object r:list(lines.get("groups")))obj(r,"line count value status displayText");
        Map<String,Object> meta=obj(root.get("meta"),"at asOfDate readStartedAt readCompletedAt computedAt traceId expiresAt supportedFilters completion patientProjection statisticalDisclosure policyVersions");Map<String,Object> policies=obj(meta.get("policyVersions"),"statistics descriptive qc crp now clinical serology treatment visitMatcher drugDictionary");for(String k:policies.keySet())scalar(k,policies.get(k));
        filters(root.get("filters"));List<?> rows=list(root.get("rows"));if(integer(root.get("n"))!=rows.size())fail();Set<String> ids=new HashSet<>();Map<String,Object> previous=null;
        for(Object value:rows){Map<String,Object> row=row(value);Object id=row.get("patientId");id(id);if(!ids.add((String)id))fail();if(previous!=null&&AnalysisValues.ORDER.compare(previous,row)>0)fail();previous=row;}
    }
    private static void test(Object value,boolean byTx){String base="method status pValue displayP significant nA nB reason warnings policyVersion testDenominator";Map<String,Object> t=obj(value,byTx?base+" expectedBelow5Cells expectedCellCount includedGroups excludedGroups groupNs":base);if(byTx){for(Object r:list(t.get("excludedGroups")))obj(r,"tx n evalN reason");for(Object r:list(t.get("groupNs")))obj(r,"tx n");}}
    @SuppressWarnings("unchecked") private static void filters(Object value){if(!(value instanceof Map))fail();Map<String,Object> m=(Map<String,Object>)value;if(!m.keySet().equals(keys("studyCode at act ids sex age sero cm tx data")))fail();if(!"RA".equals(m.get("studyCode"))||!one((String)m.get("at"),"now","6m"))fail();Object ids=m.get("ids");if(ids!=null){long previous=0;for(Object id:list(ids)){id(id);long next=Long.parseLong((String)id);if(next<=previous)fail();previous=next;}}
        for(String k:Arrays.asList("act","sex","age","sero","cm","tx","data"))if(m.get(k)!=null&&!(m.get(k) instanceof String))fail();
        for(String k:Arrays.asList("act","sex","sero","cm","tx","data"))if(m.get(k)!=null){String v=(String)m.get(k);boolean valid="act".equals(k)?one(v,"target","mod-high","remission","low","moderate","high"):"sex".equals(k)?one(v,"F","M"):"sero".equals(k)?"1".equals(v):"cm".equals(k)?one(v,"FM","AS","none"):"tx".equals(k)?one(v,"csDMARD","bio","TNFi","JAKi","IL-6i","Abatacept"):one(v,"complete","missing");if(!valid)fail();}
        if(m.get("age")!=null){String valueAge=(String)m.get("age");if(!valueAge.matches("[0-9]{1,3}-[0-9]{0,3}"))fail();String[] range=valueAge.split("-",-1);int min=Integer.parseInt(range[0]),max=range[1].isEmpty()?120:Integer.parseInt(range[1]);if(min>max||min>120||max>120)fail();}
    }
    private static Map<String,Object> row(Object value){
        Map<String,Object> r=obj(value,"patientId das28At das28Base das28Current deltaDas28 activity clinical qc selection scoreProvenance baselineProvenance crpAt crpCurrent evaluation clinicalProvenance treatment");for(String k:Arrays.asList("das28At","das28Base","das28Current","deltaDas28"))scalar(k,r.get(k));scalar("activity",r.get("activity"));
        Map<String,Object> c=obj(r.get("clinical"),"sex age diseaseDurationYears sero fm as");for(String k:c.keySet())scalar(k,c.get(k));for(String k:Arrays.asList("sero","fm","as"))if(c.get(k)==null)fail();obj(r.get("qc"),"status missingCodes ruleVersion");
        obj(r.get("selection"),"at episodeKey baselineVisitId baselineDate evalVisitId evalDate nowVisitId nowDate target6mDate eligible6m quality missingReason deltaMissingReason");
        for(String k:Arrays.asList("scoreProvenance","baselineProvenance")){Map<String,Object> p=obj(r.get(k),"source sourceVisitId sourceField observedAt quality missingReason raw","source sourceVisitId sourceField observedAt quality raw");if(p.get("raw")!=null&&r.get("scoreProvenance".equals(k)?"das28At":"das28Base")==null)fail();}
        for(String k:Arrays.asList("crpAt","crpCurrent"))metric(r.get(k),false);
        Map<String,Object> evaluation=obj(r.get("evaluation"),"tjc sjc gh haq crp pain");for(String k:evaluation.keySet())metric(evaluation.get(k),"pain".equals(k));
        Map<String,Object> provenance=obj(r.get("clinicalProvenance"),"sex age diseaseDurationYears sero fm as");for(String k:provenance.keySet()){boolean observed=one(k,"sero","fm","as");Map<String,Object> p=obj(provenance.get(k),observed?"source sourceField quality missingReason observations":"source sourceField quality missingReason");if(p.get("source")==null||p.get("sourceField")==null)fail();if(observed)for(Object o:list(p.get("observations")))obj(o,"sero".equals(k)?"sourceVisitId observedAt status":"sourceRowId sinceYear");}
        Map<String,Object> tx=obj(r.get("treatment"),"state category line targetedDrugHistoryN schemeDurationMonths startDate estimatedStartDate endDate startConfidence episodeKey genericDrugIds provenance");Map<String,Object> p=obj(tx.get("provenance"),"visitId field observedAt quality dictionaryVersion missingReason drugFacts");for(Object f:list(p.get("drugFacts")))obj(f,"genericDrugId visitId field observedAt startTime endTime");return r;
    }
    private static void metric(Object value,boolean pain){Map<String,Object> m=obj(value,pain?"value sourceVisitId sourceField observedAt unit quality missingReason raw rawRetention":"value sourceVisitId sourceField observedAt unit quality missingReason");if(pain&&(m.get("value")!=null||m.get("unit")!=null||m.get("raw")!=null))fail();}
}
