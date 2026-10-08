package com.wenwen.controller.ra;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.*;
import com.wenwen.service.ComparisonService;
import com.wenwen.vo.DataResult;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ra/ai")
public final class ComparisonController {
    private final PrincipalProvider principal;
    private final AiFeatureAvailability availability;
    private final ComparisonService service;
    private final java.time.Clock clock;
    public ComparisonController(PrincipalProvider principal,ComparisonService service,java.time.Clock clock,AiFeatureAvailability availability){this.principal=principal;this.availability=availability;this.service=service;this.clock=clock;}
    @PostMapping("/compare") public ResponseEntity<DataResult<?>> compare(HttpServletRequest request){
        String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try{TrustedDoctor doctor=principal.requireCurrent();availability.requireApi();ComparisonQuery query=ComparisonQuery.readCompare(request.getInputStream(),doctor);result.setData(service.compare(doctor,query,trace));result.setSuccess(true);result.setCode("200");result.setMessage("成功");}
        catch(CohortException e){status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());}
        catch(IOException|RuntimeException e){status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("比较暂不可用");}
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
    @PostMapping("/compareResult") public ResponseEntity<DataResult<?>> read(HttpServletRequest request){
        String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try{TrustedDoctor doctor=principal.requireCurrent();availability.requireApi();java.time.Instant requestedAt=clock.instant();String id=ComparisonQuery.readResult(request.getInputStream());result.setData(service.read(doctor,id,trace,requestedAt));result.setSuccess(true);result.setCode("200");result.setMessage("成功");}
        catch(CohortException e){status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());}
        catch(IOException|RuntimeException e){status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("比较暂不可用");}
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
}
