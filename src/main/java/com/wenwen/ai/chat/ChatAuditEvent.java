package com.wenwen.ai.chat;
@lombok.Value
public class ChatAuditEvent {
    String eventRef,traceId;long doctorId;String scopeKind,engine,outcome,modelVersion,promptVersion;long elapsedMs,createdAt;
}
