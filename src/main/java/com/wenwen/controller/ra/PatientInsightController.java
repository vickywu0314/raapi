package com.wenwen.controller.ra;

import com.wenwen.ai.insight.PatientInsightService;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.*;
import com.wenwen.vo.DataResult;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ra/ai")
public final class PatientInsightController {
    private final PrincipalProvider principal;
    private final AiFeatureAvailability availability;
    private final PatientInsightService service;
    public PatientInsightController(PrincipalProvider principal,AiFeatureAvailability availability,PatientInsightService service){this.principal=principal;this.availability=availability;this.service=service;}
    @PostMapping("/patientInsight")
    public ResponseEntity<DataResult<?>> insight(HttpServletRequest request){
        String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try{TrustedDoctor doctor=principal.requireCurrent();availability.requireApi();result.setData(service.insight(doctor,PatientDetailQuery.read(request.getInputStream()),trace));result.setSuccess(true);result.setCode("200");result.setMessage("成功");}
        catch(CohortException e){status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());}
        catch(java.io.IOException|RuntimeException e){status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("患者病程暂不可用");}
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
}
