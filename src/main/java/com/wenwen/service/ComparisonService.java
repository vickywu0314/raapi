package com.wenwen.service;
import com.wenwen.ai.query.ComparisonQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import java.util.Map;
public interface ComparisonService {
    Map<String,Object> read(TrustedDoctor doctor,String id,String traceId,java.time.Instant requestedAt);
    Map<String,Object> compare(TrustedDoctor doctor,ComparisonQuery query,String traceId);
}
