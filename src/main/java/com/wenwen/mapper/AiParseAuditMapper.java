package com.wenwen.mapper;
import com.wenwen.ai.parse.ParseAuditEvent;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AiParseAuditMapper {int insert(ParseAuditEvent event);}
