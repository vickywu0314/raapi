package com.wenwen.service;
import com.wenwen.ai.export.*;
import com.wenwen.ai.scope.TrustedDoctor;
import java.time.Instant;
import java.io.IOException;
public interface AiExportService {OrdinaryXlsxWriter.File prepare(TrustedDoctor doctor,AiExportQuery query,String trace,Instant requestedAt)throws IOException;void finishWriteFailed(long owner,String trace);void finishWritten(long owner,String trace);}
