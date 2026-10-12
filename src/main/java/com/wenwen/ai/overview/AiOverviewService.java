package com.wenwen.ai.overview;

import com.wenwen.ai.analysis.*;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.source.*;
import com.wenwen.ai.treatment.DrugDictionary;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.wenwen.vo.AiCohortVo.object;

/** 同份本人 RA 源在连接释放后聚合；不创建保留结果。 */
@Service
public final class AiOverviewService {
    private final CohortSourceAdapter source;
    private final Clock clock;
    private final DrugDictionary dictionary;
    public AiOverviewService(CohortSourceAdapter source,Clock clock,DrugDictionary dictionary) {
        this.source=source;this.clock=clock;this.dictionary=dictionary;
    }
    public Map<String,Object> overview(TrustedDoctor doctor,String traceId) {
        Instant started=clock.instant();
        LocalDate asOf=started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        DrugDictionary.Snapshot drugView=dictionary.snapshot();
        SourceBatch batch=source.read(doctor.getId(),asOf);
        CohortComputation.Derivation derived=CohortComputation.derive(batch,"now",asOf,drugView);
        return object("studyTotal",derived.universe.size(),"targetRate",DescriptiveAnalysis.target(derived.universe.values()),
            "modHighN",count(derived,doctor,"act","mod-high"),"fmN",count(derived,doctor,"cm","FM"),
            "missingN",DescriptiveAnalysis.completeness(derived.universe.values()).get("incompleteCount"),
            "meta",object("studyCode","RA","at","now","asOfDate",asOf.toString(),"readStartedAt",started.toString(),
                "readCompletedAt",batch.getReadCompletedAt().toString(),"traceId",traceId,
                "policyVersions",batch.withFmSource(object("descriptive",DescriptiveAnalysis.VERSION,"qc",com.wenwen.ai.qc.MissingDataPolicy.VERSION,
                    "crp","dev-crp-v04","now","dev-now-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04",
                    "treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary",drugView.version))));
    }
    private int count(CohortComputation.Derivation derived,TrustedDoctor doctor,String key,String value) {
        try {
            CohortQuery query=CohortQuery.read(new java.io.ByteArrayInputStream(("{\"filters\":{\""+key+"\":\""+value+"\"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8)),doctor);
            return CohortComputation.select(derived,query).members.size();
        } catch(java.io.IOException e) { throw new IllegalStateException("固定条件无法读取",e); }
    }
}
