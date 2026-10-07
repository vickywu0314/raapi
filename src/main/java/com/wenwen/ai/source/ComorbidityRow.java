package com.wenwen.ai.source;

import lombok.Data;

@Data
public final class ComorbidityRow {
    private long id;
    private long patientId;
    private String code;
    private Integer sinceYear;
}
