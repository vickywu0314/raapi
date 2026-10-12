package com.wenwen.mapper;
import com.wenwen.ai.chat.ChatAuditEvent;
@org.apache.ibatis.annotations.Mapper public interface AiChatAuditMapper {int insert(ChatAuditEvent event);}
