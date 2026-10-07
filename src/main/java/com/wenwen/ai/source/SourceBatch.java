package com.wenwen.ai.source;

import java.time.Instant;
import java.util.List;
import lombok.Value;

/** 离开源事务后返回的必要值；不携带连接、session 或原 JSON。 */
@Value
public class SourceBatch {
    List<Long> patientIds;
    List<ScoreVisit> visits;
    Instant readCompletedAt;
}
