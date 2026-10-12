package com.wenwen.ai.query;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;

/** Both IDs are required; doctor identity is supplied by the trusted session. */
public final class VisitDetailQuery {
    private final long patientId, visitId;
    private VisitDetailQuery(long patientId,long visitId){this.patientId=patientId;this.visitId=visitId;}
    public long getPatientId(){return patientId;}
    public long getVisitId(){return visitId;}
    public static VisitDetailQuery read(InputStream input)throws IOException{
        JsonNode n=PatientDetailQuery.readObject(input);
        if(n==null||!n.isObject()||n.size()!=2||!n.has("patientId")||!n.has("visitId"))
            throw new CohortException(400,"INVALID_REQUEST","访视详情输入无效");
        return new VisitDetailQuery(PatientDetailQuery.readId(n.path("patientId")),PatientDetailQuery.readId(n.path("visitId")));
    }
}
