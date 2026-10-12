package com.wenwen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.patient.PatientDetailProjection;
import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.query.PatientDetailQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.service.PatientService;
import com.wenwen.util.BizException;
import org.springframework.stereotype.Service;

@Service
public class TrustedPatientServiceImpl {
    private final PatientService patients;
    private final com.wenwen.service.VisitService visits;
    public TrustedPatientServiceImpl(PatientService patients,com.wenwen.service.VisitService visits){this.patients=patients;this.visits=visits;}
    public JsonNode search(TrustedDoctor doctor,com.wenwen.ai.query.PatientSearchQuery query){
        com.wenwen.vo.PatientsListVo list=patients.listPatients(doctor.getId(),query.getKeyword(),null,null,query.getPage(),query.getSize());
        com.fasterxml.jackson.databind.node.ObjectNode result=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        result.put("total",list.getTotal());result.put("page",query.getPage());result.put("size",query.getSize());
        com.fasterxml.jackson.databind.node.ArrayNode items=result.putArray("items");
        for(com.wenwen.vo.PatientItemVo patient:list.getItems()){
            com.fasterxml.jackson.databind.node.ObjectNode item=items.addObject();item.put("patientId",String.valueOf(patient.getPatientId()));item.put("name",patient.getName());item.put("studyNo",patient.getStudyNo());
        }
        return result;
    }
    public JsonNode visit(TrustedDoctor doctor,com.wenwen.ai.query.VisitDetailQuery query){
        try{
            com.wenwen.vo.VisitDetailVo detail=visits.getVisitDetail(doctor.getId(),query.getVisitId());
            if(detail.getPatientId()==null||detail.getPatientId()!=query.getPatientId()||detail.getVisitId()==null||detail.getVisitId()!=query.getVisitId())
                throw new CohortException(404,"RESOURCE_NOT_FOUND","患者资料不可用");
            return com.wenwen.ai.patient.TrustedDetailProjection.visit(detail);
        }
        catch(BizException unavailable){throw new CohortException(404,"RESOURCE_NOT_FOUND","患者资料不可用");}
    }
    public JsonNode sensitive(TrustedDoctor doctor,PatientDetailQuery query){
        try{
            return com.wenwen.ai.patient.TrustedDetailProjection.sensitive(query.getPatientId(),patients.getCardNo(doctor.getId(),query.getPatientId()));
        }catch(BizException unavailable){throw new CohortException(404,"RESOURCE_NOT_FOUND","患者资料不可用");}
    }
    public JsonNode audit(TrustedDoctor doctor,PatientDetailQuery query){
        try{return com.wenwen.ai.patient.TrustedDetailProjection.audit(query.getPatientId(),patients.listAuditLogs(doctor.getId(),query.getPatientId()));}
        catch(BizException unavailable){throw new CohortException(404,"RESOURCE_NOT_FOUND","患者资料不可用");}
    }
    public JsonNode detail(TrustedDoctor doctor,PatientDetailQuery query){
        try{return PatientDetailProjection.from(patients.getPatientDetail(doctor.getId(),query.getPatientId()));}
        catch(BizException unavailable){throw new CohortException(404,"RESOURCE_NOT_FOUND","患者资料不可用");}
    }
}
