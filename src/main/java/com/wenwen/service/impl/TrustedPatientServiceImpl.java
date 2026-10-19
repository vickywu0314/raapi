package com.wenwen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.patient.PatientDetailProjection;
import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.query.PatientDetailQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.mapper.PatientMapper;
import com.wenwen.service.PatientService;
import com.wenwen.util.BizException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TrustedPatientServiceImpl {
    private final PatientService patients;
    private final com.wenwen.service.VisitService visits;
    private final PatientMapper mapper;
    private final TransactionTemplate searchSnapshot;
    private static final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    public TrustedPatientServiceImpl(PatientService patients,com.wenwen.service.VisitService visits,PatientMapper mapper,PlatformTransactionManager transactions){
        this.patients=patients;this.visits=visits;this.mapper=mapper;
        searchSnapshot=new TransactionTemplate(transactions);searchSnapshot.setReadOnly(true);
        searchSnapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);searchSnapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public JsonNode search(TrustedDoctor doctor,com.wenwen.ai.query.PatientSearchQuery query){
        Map<String,Object> scope=new HashMap<>();scope.put("doctorId",doctor.getId());
        String keyword=query.getKeyword().trim();
        String literal=keyword.isEmpty()?null:keyword;String escape=PatientServiceImpl.likeEscape(literal);
        scope.put("keyword",PatientServiceImpl.escapeLike(literal,escape));scope.put("keywordEscape",escape);
        long offset=((long)query.getPage()-1)*query.getSize();scope.put("offset",offset);scope.put("size",query.getSize());
        return searchSnapshot.execute(status -> {
            int total=mapper.countSearchPatients(scope);
            List<Map<String,Object>> rows=offset>=total?Collections.emptyList():mapper.searchPatients(scope);
            com.fasterxml.jackson.databind.node.ObjectNode result=json.createObjectNode();
            result.put("total",total);result.put("page",query.getPage());result.put("size",query.getSize());
            com.fasterxml.jackson.databind.node.ArrayNode items=result.putArray("items");
            for(Map<String,Object> patient:rows){
                com.fasterxml.jackson.databind.node.ObjectNode item=items.addObject();item.put("patientId",String.valueOf(patient.get("patientId")));
                item.put("name",(String)patient.get("name"));item.put("studyNo",(String)patient.get("studyNo"));
            }
            return result;
        });
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
