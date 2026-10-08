package com.wenwen.mapper;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AiExportAuditMapper {int insert(Map<String,Object> record);int markGenerationFailed(Map<String,Object> event);int markReady(Map<String,Object> event);int startTransfer(Map<String,Object> event);int rejectTransfer(Map<String,Object> event);int finishWriteFailed(Map<String,Object> event);int finishWritten(Map<String,Object> event);}
