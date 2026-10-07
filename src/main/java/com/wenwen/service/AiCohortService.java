package com.wenwen.service;

import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.vo.AiCohortVo;

public interface AiCohortService {
    AiCohortVo analyze(TrustedDoctor doctor, CohortQuery query, String traceId);
}
