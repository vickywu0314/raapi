package com.wenwen.mapper;
import com.wenwen.ai.export.ExportApplicationRecord;
import java.util.Map;
public interface AiExportApplicationMapper {
    ExportApplicationRecord find(Map<String,Object> query);
    int reserve(Map<String,Object> record);
    int attach(Map<String,Object> record);
    int claim(Map<String,Object> event);
    int bindRecipient(Map<String,Object> event);
    int accepted(Map<String,Object> event);
    int settle(Map<String,Object> event);
}
