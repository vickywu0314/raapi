package com.wenwen.ai.result;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.vo.AiCohortVo;
import java.math.BigDecimal;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.wenwen.vo.AiCohortVo.object;
class AnalysisResultCodecTest {
    private Map<String,Object> metric(){return object("value",null,"sourceVisitId",null,"sourceField",null,"observedAt",null,"unit",null,"quality",Collections.emptyList(),"missingReason","NO_VALID_CRP");}
    private Map<String,Object> row(String id,String score){
        Map<String,Object> fact=object("source","LEGACY_RECORDED","sourceField","patient_basic_info.gender","quality",Collections.emptyList(),"missingReason",null);
        Map<String,Object> observed=new LinkedHashMap<>(fact);observed.put("observations",Collections.emptyList());
        return object("patientId",id,"das28At",new BigDecimal(score),"das28Base",null,"das28Current",new BigDecimal(score),"deltaDas28",null,"activity","moderate","name","synthetic-private-name","studyNo","synthetic-private-study",
            "clinical",object("sex",null,"age",null,"diseaseDurationYears",null,"sero","UNKNOWN","fm","UNKNOWN","as","UNKNOWN"),"qc",object("status","MISSING","missingCodes",Collections.singletonList("M_DAS28"),"ruleVersion","dev-missing-v04"),
            "selection",object("at","now","episodeKey",null,"baselineVisitId",null,"baselineDate",null,"evalVisitId","10","evalDate","2026-10-07","nowVisitId","10","nowDate","2026-10-07","target6mDate",null,"eligible6m",false,"quality",Collections.emptyList(),"missingReason",null,"deltaMissingReason","NO_RELIABLE_START"),
            "scoreProvenance",object("source","LEGACY_STORED","sourceVisitId","10","sourceField","bqpg.result.crpScore","observedAt","2026-10-07","raw",score,"quality",Collections.emptyList()),
            "baselineProvenance",object("source",null,"sourceVisitId",null,"sourceField",null,"observedAt",null,"raw",null,"quality",Collections.emptyList(),"missingReason","NO_RELIABLE_START"),"crpAt",metric(),"crpCurrent",metric(),"evaluation",object("tjc",metric(),"sjc",metric(),"gh",metric(),"haq",metric(),"crp",metric(),"pain",metric()),
            "clinicalProvenance",object("sex",fact,"age",fact,"diseaseDurationYears",fact,"sero",observed,"fm",observed,"as",observed),
            "treatment",object("state","UNKNOWN","category",null,"line",null,"targetedDrugHistoryN",0,"schemeDurationMonths",null,"startDate",null,"estimatedStartDate",null,"endDate",null,"startConfidence",null,"episodeKey",null,"genericDrugIds",Collections.emptyList(),"provenance",object("visitId",null,"field","zlfa","observedAt",null,"quality",Collections.emptyList(),"dictionaryVersion","dev-drug-v04","missingReason","NO_MEDICATION_OBSERVATION")));
    }
    private AiCohortVo candidate(int n){Map<String,Object> ratio=object("numerator",0,"denominator",0,"unknownN",0,"status","NO_DATA","displayText","0%");Map<String,Object> median=object("validN",0,"unknownN",0,"status","NO_DATA","displayText","—");
        return new AiCohortVo("12345678-1234-1234-1234-123456789abc",n,n,null,null,
            object("current",Collections.emptyList(),"base",Collections.emptyList(),"baseN",0,"baseUnknownN",n,"evalN",n,"unknownN",0,"unknownTxN",n),object("total",n,"items",Collections.emptyList()),
            object("at","now","asOfDate","2026-10-07","readStartedAt","2026-10-07T02:00:00Z","readCompletedAt","2026-10-07T02:00:00Z","computedAt","2026-10-07T02:00:00Z","traceId","12345678-1234-1234-1234-123456789abc","expiresAt","2026-10-07T02:15:00Z","supportedFilters",Arrays.asList("studyCode","at","act","ids","sex","age","sero","cm","tx","data"),"completion","P03_RESULT","patientProjection","LIVE_SOURCE_V04","statisticalDisclosure","探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系","policyVersions",object("statistics","dev-stats-v04","descriptive","dev-descriptive-v04","qc","dev-missing-v04","crp","dev-crp-v04","now","dev-now-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04","treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary","dev-drug-v04")),
            object("targetRate",BigDecimal.ZERO,"ageMedian",null,"durationMedian",null,"cohortRate",BigDecimal.ZERO,"femaleRate",BigDecimal.ZERO,"seroRate",BigDecimal.ZERO,"completeRate",BigDecimal.ZERO,"incompleteCount",n,"qcUnknownN",0,"evaluable",n),object("targetRate",ratio,"ageMedian",median,"durationMedian",median,"cohortRate",ratio,"femaleRate",ratio,"seroRate",ratio,"completeRate",ratio),
            object("groups",Collections.emptyList(),"unknownTxN",n,"noCurrentTxN",0,"studyTargetRate",BigDecimal.ZERO,"studyTargetMeta",ratio,"comparisonNotice","组间基线不同，差异不代表疗效差异","test",object("method",null,"status","INSUFFICIENT_SAMPLE","pValue",null,"displayP","—","significant",null,"nA",null,"nB",null,"reason","ELIGIBLE_GROUPS_LT_2","warnings",Collections.emptyList(),"policyVersion","dev-stats-v04","testDenominator","EVALUABLE_KNOWN_TREATMENT","expectedBelow5Cells",null,"expectedCellCount",null,"includedGroups",Collections.emptyList(),"excludedGroups",Collections.emptyList(),"groupNs",Collections.emptyList())),
            object("nFM",0,"nOther",0,"unknownN",n,"status","INSUFFICIENT_SAMPLE","reason","GROUP_SIZE_LT_3","rows",Collections.emptyList()),object("groups",Collections.emptyList(),"unknownLineN",n));
    }
    @Test void rejectsInvalidCandidateCountDuplicateIdsAndWrongOrderBeforeEncoding() throws Exception {
        AnalysisResultCodec codec=new AnalysisResultCodec();CohortQuery query=CohortQuery.read(new ByteArrayInputStream("{}".getBytes("UTF-8")),new TrustedDoctor(101));
        assertThrows(CohortException.class,()->codec.encode(candidate(1),Collections.emptyList(),query));
        assertThrows(CohortException.class,()->codec.encode(candidate(2),Arrays.asList(row("1","3.0"),row("1","3.0")),query));
        assertThrows(CohortException.class,()->codec.encode(candidate(2),Arrays.asList(row("1","3.0"),row("2","4.0")),query));
        assertThrows(CohortException.class,()->codec.encode(candidate(1),Collections.singletonList(row("01","3.0")),query));
        byte[] bytes=codec.encode(candidate(2),Arrays.asList(row("2","4.0"),row("1","3.0")),query);assertFalse(new String(bytes,"UTF-8").contains("synthetic-private"));assertTrue(bytes.length>0);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    private AiCohortVo configuredCandidate(int n){
        AiCohortVo c=candidate(n);Map<String,Object> meta=new LinkedHashMap<>(c.getMeta()),policies=new LinkedHashMap<>(map(meta.get("policyVersions")));policies.put("fmSource","synthetic:fm-v1");meta.put("policyVersions",policies);
        return new AiCohortVo(c.getAnalysisId(),c.getN(),c.getStudyTotal(),c.getSubmittedUniqueIdsN(),c.getEffectiveIdsN(),c.getActivity(),c.getPatients(),meta,c.getStats(),c.getMetricMeta(),c.getByTx(),c.getFm(),c.getLines());
    }
    private Map<String,Object> configuredRow(String state){
        Map<String,Object> r=json.convertValue(row("1","3.0"),Map.class);map(r.get("clinical")).put("fm",state);
        map(r.get("clinicalProvenance")).put("fm",object("source","CONFIGURED_FM_EVIDENCE","sourceField","ra.ai.fm-evidence.json","quality",Collections.singletonList("SOURCE_UNVERIFIED"),"missingReason","UNKNOWN".equals(state)?"EXPLICIT_UNKNOWN":null,"observations",Collections.singletonList(object("evidenceId","synthetic:e1","observedAt","2026-10-06","fmState",state))));return r;
    }
    private AnalysisRun run(byte[] bytes){AnalysisRun r=new AnalysisRun();r.setPayloadVersion(1);r.setSortKey(AnalysisValues.SORT);r.setPayload(bytes);r.setPayloadSha256(AnalysisValues.sha256(bytes));r.setExpiresAtMs(java.time.Instant.parse("2026-10-07T02:15:00Z").toEpochMilli());return r;}
    private CohortQuery query()throws Exception{return CohortQuery.read(new ByteArrayInputStream("{}".getBytes("UTF-8")),new TrustedDoctor(101));}
    @Test void oldAndConfiguredShapesRemainClosedAndTreatmentStatesStayStrict()throws Exception{
        AnalysisResultCodec codec=new AnalysisResultCodec();CohortQuery q=query();
        Map<String,Object> old=json.convertValue(row("1","3.0"),Map.class);map(old.get("clinical")).put("fm","FALSE");
        assertFalse(map(codec.decode(run(codec.encode(candidate(1),Collections.singletonList(old),q))).get("meta")).toString().contains("fmSource"));
        for(String state:Arrays.asList("TRUE","FALSE","UNKNOWN")){Map<String,Object> decoded=codec.decode(run(codec.encode(configuredCandidate(1),Collections.singletonList(configuredRow(state)),q)));assertEquals(state,map(map(((List<?>)decoded.get("rows")).get(0)).get("clinical")).get("fm"));}
        byte[] valid=codec.encode(configuredCandidate(1),Collections.singletonList(configuredRow("FALSE")),q);
        for(String mutation:Arrays.asList("unknown","future","missing-version","mixed","bad-treatment","null-version","bad-evidence","extra-observation")){
            Map<String,Object> payload=json.readValue(valid,Map.class);Map<String,Object> r=map(((List<?>)payload.get("rows")).get(0)),fm=map(map(r.get("clinicalProvenance")).get("fm")),o=map(((List<?>)fm.get("observations")).get(0));
            if("unknown".equals(mutation))payload.put("privateCanary","bad");else if("future".equals(mutation))o.put("observedAt","2026-10-08");else if("missing-version".equals(mutation))map(map(payload.get("meta")).get("policyVersions")).remove("fmSource");else if("mixed".equals(mutation))fm.put("source","LEGACY_POSITIVE_ASSOCIATION");else if("bad-treatment".equals(mutation))map(r.get("treatment")).put("state","FALSE");else if("null-version".equals(mutation))map(map(payload.get("meta")).get("policyVersions")).put("fmSource",null);else if("bad-evidence".equals(mutation))o.put("evidenceId","private canary");else o.put("sourceRowId","1");
            assertThrows(CohortException.class,()->codec.decode(run(json.writeValueAsBytes(payload))),mutation);
        }
        Map<String,Object> unknown=configuredRow("UNKNOWN");map(unknown.get("clinicalProvenance")).put("fm",object("source","CONFIGURED_FM_EVIDENCE","sourceField","ra.ai.fm-evidence.json","quality",Collections.singletonList("SOURCE_UNVERIFIED"),"missingReason","NO_EFFECTIVE_FM_EVIDENCE","observations",Collections.emptyList()));assertNotNull(codec.decode(run(codec.encode(configuredCandidate(1),Collections.singletonList(unknown),q))));
        assertThrows(CohortException.class,()->codec.encode(candidate(1),Collections.singletonList(configuredRow("FALSE")),q));
    }

    @Test void correctHashConfiguredObservationMustAgreeWithClinicalFm()throws Exception{
        AnalysisResultCodec codec=new AnalysisResultCodec();byte[] bytes=codec.encode(configuredCandidate(1),Collections.singletonList(configuredRow("FALSE")),query());
        AnalysisRun valid=run(bytes);assertEquals(AnalysisValues.sha256(bytes),valid.getPayloadSha256());Map<String,Object> decoded=codec.decode(valid);assertEquals("FALSE",map(map(((List<?>)decoded.get("rows")).get(0)).get("clinical")).get("fm"));
        Map<String,Object> altered=json.readValue(bytes,Map.class),r=map(((List<?>)altered.get("rows")).get(0)),fm=map(map(r.get("clinicalProvenance")).get("fm"));map(((List<?>)fm.get("observations")).get(0)).put("fmState","TRUE");
        AnalysisRun corrupt=run(json.writeValueAsBytes(altered));assertEquals(1,corrupt.getPayloadVersion());assertEquals(AnalysisValues.SORT,corrupt.getSortKey());assertEquals(AnalysisValues.sha256(corrupt.getPayload()),corrupt.getPayloadSha256());assertEquals("2026-10-06",map(((List<?>)fm.get("observations")).get(0)).get("observedAt"));
        CohortException e=assertThrows(CohortException.class,()->codec.decode(corrupt));assertEquals(503,e.getStatus());assertEquals("SERVICE_UNAVAILABLE",e.getCode());
        Map<String,Object> badCandidate=json.convertValue(configuredRow("FALSE"),Map.class);map(((List<?>)map(map(badCandidate.get("clinicalProvenance")).get("fm")).get("observations")).get(0)).put("fmState","TRUE");assertThrows(CohortException.class,()->codec.encode(configuredCandidate(1),Collections.singletonList(badCandidate),query()));
        assertNotNull(codec.decode(valid));
    }

    @Test void configuredExtensionCannotLeakIntoOtherFactShapes()throws Exception{
        AnalysisResultCodec codec=new AnalysisResultCodec();byte[] valid=codec.encode(configuredCandidate(1),Collections.singletonList(configuredRow("FALSE")),query());
        for(String key:Arrays.asList("sex","sero","scoreProvenance","crpAt","treatment")){
            Map<String,Object> p=json.readValue(valid,Map.class),r=map(((List<?>)p.get("rows")).get(0));
            if(Arrays.asList("sex","sero").contains(key))map(map(r.get("clinicalProvenance")).get(key)).put("source","CONFIGURED_FM_EVIDENCE");else if("scoreProvenance".equals(key))map(r.get(key)).put("source","CONFIGURED_FM_EVIDENCE");else if("crpAt".equals(key))map(r.get(key)).put("sourceField","ra.ai.fm-evidence.json");else map(map(r.get("treatment")).get("provenance")).put("field","ra.ai.fm-evidence.json");
            assertThrows(CohortException.class,()->codec.decode(run(json.writeValueAsBytes(p))),key);
        }
    }

    @Test void configuredFmFieldRequiresItsSourceVersion()throws Exception{
        AnalysisResultCodec codec=new AnalysisResultCodec();byte[] valid=codec.encode(candidate(1),Collections.singletonList(row("1","3.0")),query());Map<String,Object> p=json.readValue(valid,Map.class),r=map(((List<?>)p.get("rows")).get(0));map(map(r.get("clinicalProvenance")).get("fm")).put("sourceField","ra.ai.fm-evidence.json");assertThrows(CohortException.class,()->codec.decode(run(json.writeValueAsBytes(p))));
    }

}
