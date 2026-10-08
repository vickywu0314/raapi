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
}
