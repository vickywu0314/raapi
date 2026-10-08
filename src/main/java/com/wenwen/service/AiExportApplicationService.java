package com.wenwen.service;
import com.wenwen.ai.export.ExportApplicationQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
public interface AiExportApplicationService {
 Map<String,Object> retry(TrustedDoctor doctor,long applicationId);
 Map<String,Object> apply(TrustedDoctor doctor,ExportApplicationQuery query,Instant requestedAt)throws IOException;
}
