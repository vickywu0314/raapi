package com.wenwen.ai.source;

import lombok.Data;

/** 仅源读取所需列；不包含身份信息。 */
@Data
public final class VisitRow {
    private long id;
    private long patientId;
    private String observedAt;
    private String bqpg;
    private String fzjc;
}
