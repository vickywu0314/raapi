package com.wenwen.util;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdCardUtilTest {
    @Test void datedExtractionIsCalendarValidAndKeepsFifteenAndEighteenDigitFormats() {
        LocalDate asOf=LocalDate.of(2026,10,7);
        assertEquals(LocalDate.of(1986,10,7),IdCardUtil.birthDate("110101198610070011",asOf));
        assertEquals(LocalDate.of(1986,10,7),IdCardUtil.birthDate("110101861007001",asOf));
        assertEquals(LocalDate.of(2000,2,29),IdCardUtil.birthDate("11010120000229001X",asOf));
        for(String invalid:new String[]{null,"bad","110101198602300011","110101189910070011","110101202610080011"}) assertNull(IdCardUtil.birthDate(invalid,asOf));
        assertEquals(LocalDate.of(1986,10,7),IdCardUtil.birthDate("110101198610070011"));
    }
}
