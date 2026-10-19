package com.wenwen.ai.result;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnalysisQuerySessionTest {
    @Test void legacyBlobCapacityDoesNotGateQuerySessions() {
        for(String oldLimit:new String[]{"1","0","invalid","16777216"})
            assertDoesNotThrow(()->new AnalysisSettings("900",oldLimit,"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=").validateAt(1));
    }
}
