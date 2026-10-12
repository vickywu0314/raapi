package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wenwen.vo.VisitDetailVo;

/** Keep legacy clinical presentation while preserving identifier precision. */
public final class TrustedDetailProjection {
    private static final ObjectMapper JSON=new ObjectMapper();
    private TrustedDetailProjection(){}
    public static JsonNode sensitive(long patientId,String cardNo){
        ObjectNode data=JSON.createObjectNode();data.put("patientId",Long.toString(patientId));data.put("cardNo",cardNo);return data;
    }
    public static JsonNode audit(long patientId,java.util.List<com.wenwen.vo.AuditLogVo> logs){
        ObjectNode data=JSON.createObjectNode();data.put("patientId",Long.toString(patientId));
        com.fasterxml.jackson.databind.node.ArrayNode list=data.putArray("logs");
        for(com.wenwen.vo.AuditLogVo log:logs){
            ObjectNode item=JSON.valueToTree(log);item.put("id",Long.toString(log.getId()));
            if(log.getVisitId()!=null)item.put("visitId",Long.toString(log.getVisitId()));list.add(item);
        }
        return data;
    }
    public static JsonNode visit(VisitDetailVo detail){
        ObjectNode data=JSON.valueToTree(detail);
        data.put("patientId",Long.toString(detail.getPatientId()));
        data.put("visitId",Long.toString(detail.getVisitId()));
        if(detail.getDoctorId()!=null)data.put("doctorId",Long.toString(detail.getDoctorId()));
        return data;
    }
}
