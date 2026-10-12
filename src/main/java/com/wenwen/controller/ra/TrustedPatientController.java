package com.wenwen.controller.ra;

import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.scope.PrincipalProvider;
import com.wenwen.vo.DataResult;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ra/ai")
public class TrustedPatientController {
    private final PrincipalProvider principal;
    private final com.wenwen.ai.scope.AiFeatureAvailability availability;
    private final com.wenwen.service.impl.TrustedPatientServiceImpl service;
    public TrustedPatientController(PrincipalProvider principal,com.wenwen.ai.scope.AiFeatureAvailability availability,com.wenwen.service.impl.TrustedPatientServiceImpl service){this.principal=principal;this.availability=availability;this.service=service;}
    @PostMapping("/patientSearch")
    public ResponseEntity<DataResult<?>> search(HttpServletRequest request){return respond(request,"search");}
    @PostMapping("/patientAuditLogs")
    public ResponseEntity<DataResult<?>> audit(HttpServletRequest request){return respond(request,"audit");}
    @PostMapping("/patientSensitive")
    public ResponseEntity<DataResult<?>> sensitive(HttpServletRequest request){return respond(request,"sensitive");}
    @PostMapping("/visitDetail")
    public ResponseEntity<DataResult<?>> visit(HttpServletRequest request){return respond(request,"visit");}
    @PostMapping("/patientDetail")
    public ResponseEntity<DataResult<?>> detail(HttpServletRequest request){
        return respond(request,"detail");
    }
    private ResponseEntity<DataResult<?>> respond(HttpServletRequest request,String operation){
        String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try{
            com.wenwen.ai.scope.TrustedDoctor doctor=principal.requireCurrent();availability.requireApi();
            Object data;
            if("search".equals(operation))data=service.search(doctor,com.wenwen.ai.query.PatientSearchQuery.read(request.getInputStream()));
            else if("visit".equals(operation))data=service.visit(doctor,com.wenwen.ai.query.VisitDetailQuery.read(request.getInputStream()));
            else{
                com.wenwen.ai.query.PatientDetailQuery query=com.wenwen.ai.query.PatientDetailQuery.read(request.getInputStream());
                data="sensitive".equals(operation)?service.sensitive(doctor,query):"audit".equals(operation)?service.audit(doctor,query):service.detail(doctor,query);
            }
            result.setData(data);result.setSuccess(true);result.setCode("200");result.setMessage("成功");
        }
        catch(CohortException e){status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());}
        catch(java.io.IOException|RuntimeException e){status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("患者资料暂不可用");}
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
}
