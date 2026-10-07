package com.wenwen.service.impl;

import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.source.*;
import com.wenwen.service.AiCohortService;
import com.wenwen.util.Das28Util;
import com.wenwen.vo.AiCohortVo;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.wenwen.vo.AiCohortVo.object;

@Service
public class AiCohortServiceImpl implements AiCohortService {
    private final CohortSourceAdapter source;
    private final Clock clock;
    public AiCohortServiceImpl(CohortSourceAdapter source, Clock clock) { this.source = source; this.clock = clock; }
    public AiCohortVo analyze(TrustedDoctor doctor, CohortQuery query, String traceId) {
        Instant started = clock.instant();
        LocalDate asOf = started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        SourceBatch batch = source.read(doctor.getId());
        Map<Long,ScoreVisit> current = new HashMap<>();
        Map<Long,Set<String>> observedQuality = new HashMap<>();
        for (ScoreVisit visit : batch.getVisits()) {
            observedQuality.computeIfAbsent(visit.getPatientId(), key -> new LinkedHashSet<>()).addAll(visit.getQuality());
            if (visit.getScore() == null || visit.getObservedAt() == null || visit.getObservedAt().isAfter(asOf)) continue;
            ScoreVisit old = current.get(visit.getPatientId());
            if (old == null || visit.getObservedAt().isAfter(old.getObservedAt())
                    || (visit.getObservedAt().equals(old.getObservedAt()) && visit.getId() > old.getId())) current.put(visit.getPatientId(),visit);
        }
        String[] levels = {"remission","low","moderate","high"};
        Map<String,Integer> counts = new LinkedHashMap<>();
        for (String level : levels) counts.put(level,0);
        List<Map<String,Object>> items = new ArrayList<>();
        int n = 0, eval = 0;
        Integer effectiveIdsN = query.getIds() == null ? null : 0;
        for (long id : batch.getPatientIds()) {
            if (query.getIds() != null) {
                if (!query.getIds().contains(id)) continue;
                effectiveIdsN++;
            }
            ScoreVisit score = current.get(id);
            String level = score == null ? null : Das28Util.activity(score.getScore());
            if (query.getAct() != null) {
                if (score == null) continue;
                boolean target = score.getScore().compareTo(new java.math.BigDecimal("2.7")) <= 0;
                if (!(query.getAct().equals(level) || ("target".equals(query.getAct()) && target)
                        || ("mod-high".equals(query.getAct()) && !target))) continue;
            }
            n++;
            if (level != null) { counts.put(level,counts.get(level)+1); eval++; }
            if (items.size() < 10) {
                Map<String,Object> provenance = score == null
                    ? object("source",null,"sourceVisitId",null,"sourceField",null,"observedAt",null,"raw",null,
                        "quality",new ArrayList<>(observedQuality.getOrDefault(id,Collections.emptySet())),"missingReason","NO_VALID_CRP")
                    : object("source","LEGACY_STORED","sourceVisitId",Long.toString(score.getId()),"sourceField","bqpg.result.crpScore",
                        "observedAt",score.getObservedAt().toString(),"raw",score.getRaw(),"quality",score.getQuality());
                items.add(object("patientId",Long.toString(id),"das28At",score == null ? null : score.getScore(),"activity",level,"scoreProvenance",provenance));
            }
        }
        List<Map<String,Object>> distribution = new ArrayList<>();
        for (String level : levels) distribution.add(object("level",level,"label",Das28Util.label(level),"count",counts.get(level)));
        return new AiCohortVo(n,batch.getPatientIds().size(),query.getIds() == null ? null : query.getIds().size(),effectiveIdsN,
            object("current",distribution,"evalN",eval,"unknownN",n-eval),object("total",n,"items",items),
            object("asOfDate",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),
                "computedAt",clock.instant().toString(),"policyVersions",object("crp","dev-crp-v04","now","dev-now-v04"),"traceId",traceId,
                "supportedFilters",Arrays.asList("studyCode","at","act","ids"),"completion","P01_TRACER"));
    }
}
