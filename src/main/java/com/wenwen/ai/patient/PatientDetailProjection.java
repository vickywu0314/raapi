package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wenwen.vo.PatientDetailVo;
import com.wenwen.vo.VisitItemVo;

/** Preserve legacy clinical fields while projecting every ID before JSON serialization. */
public final class PatientDetailProjection {
    private static final ObjectMapper JSON=new ObjectMapper();
    private PatientDetailProjection(){}
    public static JsonNode from(PatientDetailVo detail){
        ObjectNode data=JSON.valueToTree(detail);data.put("patientId",Long.toString(detail.getPatientId()));
        for(int i=0;i<detail.getVisits().size();i++){
            VisitItemVo visit=detail.getVisits().get(i);ObjectNode projected=(ObjectNode)data.path("visits").get(i);
            projected.put("visitId",Long.toString(visit.getVisitId()));
            if(visit.getDoctorId()!=null)projected.put("doctorId",Long.toString(visit.getDoctorId()));
        }
        return data;
    }
}
