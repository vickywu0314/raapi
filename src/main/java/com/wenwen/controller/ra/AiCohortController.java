package com.wenwen.controller.ra;

import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import com.wenwen.ai.scope.PrincipalProvider;
import com.wenwen.service.AiCohortService;
import com.wenwen.vo.DataResult;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ra/ai")
public class AiCohortController {
    private final PrincipalProvider principal;
    private final AiCohortService service;
    private final java.time.Clock clock;
    public AiCohortController(PrincipalProvider principal, AiCohortService service,java.time.Clock clock) {
        this.principal = principal;
        this.service = service;this.clock=clock;
    }
    @PostMapping("/resolveEntry")
    public ResponseEntity<DataResult<?>> resolveEntry(HttpServletRequest request) {
        String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try {
            TrustedDoctor doctor=principal.requireCurrent();
            java.time.Instant requestedAt=clock.instant();
            com.wenwen.ai.query.SimilarEntryQuery query=com.wenwen.ai.query.SimilarEntryQuery.read(request.getInputStream());
            result.setData(service.resolveEntry(doctor,query,trace,requestedAt));
            result.setSuccess(true);result.setCode("200");result.setMessage("成功");
        } catch(CohortException e) { status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage()); }
        catch(IOException|RuntimeException e) { status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("分析暂不可用"); }
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
    @PostMapping("/cohortPatients")
    public ResponseEntity<DataResult<?>> cohortPatients(HttpServletRequest request) {
        String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try{TrustedDoctor doctor=principal.requireCurrent();java.time.Instant requestedAt=clock.instant();com.wenwen.ai.query.CohortPageQuery query=com.wenwen.ai.query.CohortPageQuery.read(request.getInputStream());result.setData(service.page(doctor,query,trace,requestedAt));result.setSuccess(true);result.setCode("200");result.setMessage("成功");}
        catch(CohortException e){status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());}
        catch(IOException|RuntimeException e){status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("分析暂不可用");}
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
    @PostMapping("/cohort")
    public ResponseEntity<DataResult<?>> cohort(HttpServletRequest request) {
        String trace = UUID.randomUUID().toString();
        DataResult<Object> result = new DataResult<>();
        int status = 200;
        try {
            TrustedDoctor doctor = principal.requireCurrent();
            CohortQuery query = CohortQuery.read(request.getInputStream(), doctor);
            result.setData(service.analyze(doctor, query, trace));
            result.setSuccess(true); result.setCode("200"); result.setMessage("成功");
        } catch (CohortException e) {
            status = e.getStatus(); result.setSuccess(false); result.setCode(e.getCode()); result.setMessage(e.getMessage());
        } catch (IOException | RuntimeException e) {
            status = 503; result.setSuccess(false); result.setCode("SERVICE_UNAVAILABLE"); result.setMessage("分析暂不可用");
        }
        return ResponseEntity.status(status).header("X-Trace-Id", trace).body(result);
    }
}
