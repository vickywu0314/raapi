package com.wenwen.ai.source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Value;

@Value
public class ScoreVisit {
    long id;
    long patientId;
    LocalDate observedAt;
    String raw;
    BigDecimal score;
    List<String> quality;
}
