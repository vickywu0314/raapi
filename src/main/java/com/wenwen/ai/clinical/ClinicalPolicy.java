package com.wenwen.ai.clinical;

import com.wenwen.util.IdCardUtil;
import java.time.*;

/** 本次 asOf 的精确临床政策；不从旧整数年龄推估生日。 */
public final class ClinicalPolicy {
    public static final String TRUE="TRUE", FALSE="FALSE", UNKNOWN="UNKNOWN";
    public static String association(Integer sinceYear, LocalDate asOf) {
        return sinceYear != null && sinceYear > asOf.getYear() ? UNKNOWN : TRUE;
    }
    public static String serology(String previous, String status) {
        if (TRUE.equals(previous) || com.wenwen.util.SerologyUtil.isPositive(status)) return TRUE;
        if (FALSE.equals(previous) || com.wenwen.util.SerologyUtil.NEGATIVE.equals(status)) return FALSE;
        return UNKNOWN;
    }
    public static boolean datedAt(String date, LocalDate asOf) {
        LocalDate parsed=date(date); return parsed!=null && !parsed.isAfter(asOf);
    }
    private ClinicalPolicy() { }
    public static String sex(Integer gender) { return gender == null ? null : gender == 1 ? "M" : gender == 2 ? "F" : null; }
    public static Integer duration(String date, LocalDate asOf) {
        LocalDate confirmed=date(date);
        return confirmed == null || confirmed.isAfter(asOf) ? null : Period.between(confirmed,asOf).getYears();
    }
    public static LocalDate date(String date) {
        try { return date == null ? null : LocalDate.parse(date); } catch (java.time.format.DateTimeParseException e) { return null; }
    }
    public static Integer age(String cardNo, LocalDate asOf) {
        LocalDate birth = IdCardUtil.birthDate(cardNo,asOf);
        return birth == null ? null : Period.between(birth,asOf).getYears();
    }
}
