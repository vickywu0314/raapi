package com.wenwen.ai.export;
import java.util.*;
/** 精确列集合；不记录患者、异常文本或文件bytes。 */
public final class ExportAuditRecord extends LinkedHashMap<String,Object> {
    public ExportAuditRecord(Map<String,Object> values){super(values);}
}
