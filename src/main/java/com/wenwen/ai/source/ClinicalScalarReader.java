package com.wenwen.ai.source;

import com.fasterxml.jackson.core.*;
import java.io.IOException;
import java.util.*;

/** 共同原始标量入口：原数字文本、严格重复键及整份拒绝策略。 */
public final class ClinicalScalarReader {
    private ClinicalScalarReader() { }
    private static final JsonFactory json = new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public static Map<String,String> read(String text, Set<String> quality) {
        Map<String,String> result = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return result;
        try (JsonParser parser = json.createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) { quality.add("INVALID_JSON"); return result; }
            int roots = 0;
            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.END_OBJECT && parser.getParsingContext().inRoot()) roots++;
                if (roots > 1 || (roots == 1 && token != JsonToken.END_OBJECT)) throw new IOException("多余 JSON 值");
                String path = parser.getParsingContext().pathAsPointer().toString();
                if (Arrays.asList("/result/crpScore","/result/ytgjs","/result/zzgjs","/ztScoreByPatient","/cfydb",
                        "/lfsyz","/kccpkt","/hqaScore","/tjScore","/result/hqaScore").contains(path)
                        && token != JsonToken.FIELD_NAME && token != JsonToken.END_OBJECT && token != JsonToken.END_ARRAY) {
                    result.put(path,token.isScalarValue() && token!=JsonToken.VALUE_NULL?parser.getText():null);
                }
            }
        } catch (IOException e) { quality.add("INVALID_JSON"); result.clear(); }
        return result;
    }
}
