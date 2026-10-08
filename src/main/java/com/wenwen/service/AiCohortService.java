package com.wenwen.service;

import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.vo.AiCohortVo;

public interface AiCohortService {
    com.wenwen.vo.AiCohortPatientsVo page(TrustedDoctor doctor,com.wenwen.ai.query.CohortPageQuery query,String traceId,java.time.Instant requestedAt);
    java.util.Map<String,Object> resolveEntry(TrustedDoctor doctor,com.wenwen.ai.query.SimilarEntryQuery query,String traceId,java.time.Instant requestedAt);
    AiCohortVo analyze(TrustedDoctor doctor, CohortQuery query, String traceId);
}
