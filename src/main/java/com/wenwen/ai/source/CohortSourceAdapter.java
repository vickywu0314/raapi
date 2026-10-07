package com.wenwen.ai.source;

import com.fasterxml.jackson.core.*;
import com.wenwen.mapper.AiCohortSourceMapper;
import com.wenwen.util.Das28Util;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class CohortSourceAdapter {
    private final AiCohortSourceMapper mapper;
    private final TransactionTemplate snapshot;
    private final Clock clock;
    private final JsonFactory json = new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public CohortSourceAdapter(AiCohortSourceMapper mapper, PlatformTransactionManager transactions, Clock clock) {
        this.mapper = mapper; this.clock = clock;
        snapshot = new TransactionTemplate(transactions);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshot.setReadOnly(true);
    }
    public SourceBatch read(long doctorId) {
        // Spring-managed MyBatis 的两条 SELECT 共享本事务。回调仅读取，不解析病例或计算。
        RawBatch raw = snapshot.execute(status -> new RawBatch(mapper.patientIds(doctorId), mapper.visits(doctorId)));
        Instant completed = clock.instant(); // execute 已完成事务及连接释放
        List<ScoreVisit> visits = new ArrayList<>();
        for (VisitRow row : raw.visits) visits.add(parse(row));
        return new SourceBatch(Collections.unmodifiableList(new ArrayList<>(raw.ids)), Collections.unmodifiableList(visits), completed);
    }
    private ScoreVisit parse(VisitRow row) {
        LinkedHashSet<String> quality = new LinkedHashSet<>();
        Map<String,String> assessment = scalars(row.getBqpg(), quality);
        Map<String,String> labs = scalars(row.getFzjc(), quality);
        String raw = assessment.get("/result/crpScore");
        BigDecimal value = Das28Util.canonicalCrp(raw);
        if (value != null) {
            quality.add("LEGACY_UNVERIFIED");
            if (!number(assessment.get("/result/ytgjs")) || !number(assessment.get("/result/zzgjs"))
                    || !number(assessment.get("/ztScoreByPatient")) || !number(labs.get("/cfydb"))) quality.add("COMPONENTS_MISSING");
        }
        LocalDate date = null;
        if (row.getObservedAt() != null) {
            try { date = LocalDate.parse(row.getObservedAt()); }
            catch (DateTimeParseException e) { quality.add("INVALID_DATE"); }
        }
        return new ScoreVisit(row.getId(), row.getPatientId(), date, raw, value, Collections.unmodifiableList(new ArrayList<>(quality)));
    }
    private Map<String,String> scalars(String text, Set<String> quality) {
        Map<String,String> result = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return result;
        try (JsonParser parser = json.createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) { quality.add("INVALID_JSON"); return result; }
            int roots = 0;
            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.END_OBJECT && parser.getParsingContext().inRoot()) roots++;
                if (roots > 1 || (roots == 1 && token != JsonToken.END_OBJECT)) throw new IOException("多余 JSON 值");
                if (token == JsonToken.VALUE_STRING || token.isNumeric()) {
                    String path = parser.getParsingContext().pathAsPointer().toString();
                    if ("/result/crpScore".equals(path) || "/result/ytgjs".equals(path) || "/result/zzgjs".equals(path)
                            || "/ztScoreByPatient".equals(path) || "/cfydb".equals(path)) result.put(path, parser.getText());
                }
            }
        } catch (IOException e) { quality.add("INVALID_JSON"); result.clear(); }
        return result;
    }
    private boolean number(String value) {
        if (value == null) return false;
        try { return new BigDecimal(value.trim()).signum() >= 0; }
        catch (NumberFormatException e) { return false; }
    }
    private static final class RawBatch {
        final List<Long> ids;
        final List<VisitRow> visits;
        RawBatch(List<Long> ids, List<VisitRow> visits) { this.ids = ids; this.visits = visits; }
    }
}
