package com.wenwen.ai.result;
import com.fasterxml.jackson.databind.*;
import com.wenwen.vo.AiCohortVo;
import com.wenwen.ai.query.CohortQuery;
import java.util.*;
/** 显式字段投影，绝不序列化源对象或通过删除几个敏感键建立载荷。 */
public final class AnalysisResultCodec {
    private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v){return (Map<String,Object>)v;}
    private static Map<String,Object> fields(Object v,String names){Map<String,Object> out=new LinkedHashMap<>();if(v==null)return out;Map<String,Object> m=map(v);for(String k:names.split(" "))if(m.containsKey(k))out.put(k,m.get(k));return out;}
    private static List<Map<String,Object>> list(Object v,String names){List<Map<String,Object>> out=new ArrayList<>();if(v!=null)for(Object x:(Collection<?>)v)out.add(fields(x,names));return out;}
    private static final String METRIC="value sourceVisitId sourceField observedAt unit quality missingReason";
    private static final String TEST="method status pValue displayP significant nA nB reason warnings policyVersion testDenominator alternative alpha continuityCorrection tiesCorrection pValueMethod expectedBelow5Cells expectedCellCount";
    private static final String PROPORTION="numerator denominator unknownN status displayText";
    public byte[] encode(AiCohortVo vo,List<Map<String,Object>> rows,CohortQuery query){
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("n",vo.getN());out.put("studyTotal",vo.getStudyTotal());out.put("submittedUniqueIdsN",vo.getSubmittedUniqueIdsN());out.put("effectiveIdsN",vo.getEffectiveIdsN());
        Map<String,Object> activity=fields(vo.getActivity(),"baseN baseUnknownN evalN unknownN unknownTxN");
        for(String k:Arrays.asList("current","base"))activity.put(k,list(vo.getActivity().get(k),"level count value status displayText label"));out.put("activity",activity);
        out.put("stats",fields(vo.getStats(),"targetRate ageMedian durationMedian cohortRate femaleRate seroRate completeRate incompleteCount qcUnknownN evaluable"));
        Map<String,Object> metrics=new LinkedHashMap<>();for(String k:Arrays.asList("targetRate","ageMedian","durationMedian","cohortRate","femaleRate","seroRate","completeRate"))metrics.put(k,fields(vo.getMetricMeta().get(k),PROPORTION+" validN"));out.put("metricMeta",metrics);
        Map<String,Object> by=fields(vo.getByTx(),"unknownTxN noCurrentTxN studyTargetRate comparisonNotice");by.put("groups",list(vo.getByTx().get("groups"),"tx n evalN targetN rate status displayText"));by.put("studyTargetMeta",fields(vo.getByTx().get("studyTargetMeta"),PROPORTION));
        Map<String,Object> test=fields(vo.getByTx().get("test"),TEST);Map<String,Object> originalTest=map(vo.getByTx().get("test"));test.put("includedGroups",originalTest.get("includedGroups"));test.put("excludedGroups",list(originalTest.get("excludedGroups"),"tx n evalN reason"));test.put("groupNs",list(originalTest.get("groupNs"),"tx n"));by.put("test",test);out.put("byTx",by);
        Map<String,Object> fm=fields(vo.getFm(),"nFM nOther unknownN status reason");List<Map<String,Object>> fmRows=new ArrayList<>();for(Object x:(Collection<?>)vo.getFm().get("rows")){Map<String,Object> r=fields(x,"metric meanFM meanOther nFMValid nOtherValid difference highlight status reason displayFM displayOther displayDifference");r.put("test",fields(map(x).get("test"),TEST));fmRows.add(r);}fm.put("rows",fmRows);out.put("fm",fm);
        Map<String,Object> lines=fields(vo.getLines(),"unknownLineN");lines.put("groups",list(vo.getLines().get("groups"),"line count value status displayText"));out.put("lines",lines);
        Map<String,Object> meta=fields(vo.getMeta(),"at asOfDate readStartedAt readCompletedAt computedAt traceId expiresAt supportedFilters completion patientProjection statisticalDisclosure");meta.put("policyVersions",fields(vo.getMeta().get("policyVersions"),"statistics descriptive qc crp now clinical serology treatment visitMatcher drugDictionary"));out.put("meta",meta);out.put("filters",query.normalizedFilters());
        List<Map<String,Object>> projected=new ArrayList<>();for(Map<String,Object> r:rows)projected.add(row(r));out.put("rows",projected);
        AnalysisPayloadValidation.validate(out);
        try{return json.writeValueAsBytes(out);}catch(java.io.IOException e){throw new IllegalStateException("结果编码失败",e);}
    }
    @SuppressWarnings("unchecked") public Map<String,Object> decode(AnalysisRun run) {
        if(run.getPayloadVersion()!=1||!AnalysisValues.SORT.equals(run.getSortKey())||!AnalysisValues.sha256(run.getPayload()).equals(run.getPayloadSha256()))throw AnalysisSettings.unavailable();
        try{Map<String,Object> payload=json.readValue(run.getPayload(),Map.class);AnalysisPayloadValidation.validate(payload);if(!java.time.Instant.ofEpochMilli(run.getExpiresAtMs()).toString().equals(((Map<?,?>)payload.get("meta")).get("expiresAt")))throw AnalysisSettings.unavailable();return payload;}catch(java.io.IOException e){throw AnalysisSettings.unavailable();}
    }
    private static Map<String,Object> provenance(Object v,Object score){Map<String,Object> out=fields(v,"source sourceVisitId sourceField observedAt quality missingReason");Object raw=map(v).get("raw");out.put("raw",score!=null&&raw instanceof String&&com.wenwen.ai.clinical.EvaluationPolicy.number((String)raw)!=null?raw:null);return out;}
    private static Map<String,Object> row(Map<String,Object> r){
        Map<String,Object> out=fields(r,"patientId das28At das28Base das28Current deltaDas28 activity");out.put("clinical",fields(r.get("clinical"),"sex age diseaseDurationYears sero fm as"));out.put("qc",fields(r.get("qc"),"status missingCodes ruleVersion"));
        out.put("selection",fields(r.get("selection"),"at episodeKey baselineVisitId baselineDate evalVisitId evalDate nowVisitId nowDate target6mDate eligible6m quality missingReason deltaMissingReason"));
        out.put("scoreProvenance",provenance(r.get("scoreProvenance"),r.get("das28At")));out.put("baselineProvenance",provenance(r.get("baselineProvenance"),r.get("das28Base")));
        for(String k:Arrays.asList("crpAt","crpCurrent"))out.put(k,fields(r.get(k),METRIC));
        Map<String,Object> evaluation=new LinkedHashMap<>();for(String k:Arrays.asList("tjc","sjc","gh","haq","crp","pain")){Map<String,Object> m=fields(map(r.get("evaluation")).get(k),METRIC);if("pain".equals(k)){m.put("raw",null);m.put("rawRetention","OMITTED");}evaluation.put(k,m);}out.put("evaluation",evaluation);
        Map<String,Object> clinical=new LinkedHashMap<>();for(String k:Arrays.asList("sex","age","diseaseDurationYears","sero","fm","as")){Map<String,Object> original=map(map(r.get("clinicalProvenance")).get(k));Map<String,Object> p=fields(original,"source sourceField quality missingReason");if(original.containsKey("observations"))p.put("observations",list(original.get("observations"),"sourceVisitId observedAt status sourceRowId sinceYear"));clinical.put(k,p);}out.put("clinicalProvenance",clinical);
        Map<String,Object> tx=fields(r.get("treatment"),"state category line targetedDrugHistoryN schemeDurationMonths startDate estimatedStartDate endDate startConfidence episodeKey genericDrugIds");Map<String,Object> original=map(map(r.get("treatment")).get("provenance"));Map<String,Object> p=fields(original,"visitId field observedAt quality dictionaryVersion missingReason");List<Map<String,Object>> facts=list(original.get("drugFacts"),"genericDrugId visitId field observedAt startTime endTime");for(Map<String,Object> f:facts)for(String k:Arrays.asList("startTime","endTime"))if(f.containsKey(k))try{if(f.get(k)!=null)java.time.LocalDate.parse((String)f.get(k));}catch(RuntimeException e){f.put(k,null);}p.put("drugFacts",facts);tx.put("provenance",p);out.put("treatment",tx);return out;
    }
}
