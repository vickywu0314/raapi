package com.wenwen.controller.ra;

import com.wenwen.ai.overview.AiOverviewService;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.*;
import com.wenwen.vo.DataResult;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ra/ai")
public final class AiOverviewController {
    private final PrincipalProvider principal;
    private final AiFeatureAvailability availability;
    private final AiOverviewService service;
    public AiOverviewController(PrincipalProvider principal,AiFeatureAvailability availability,AiOverviewService service) {
        this.principal=principal;this.availability=availability;this.service=service;
    }
    @PostMapping("/overview")
    public ResponseEntity<DataResult<?>> overview(HttpServletRequest request) {
        String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try {
            TrustedDoctor doctor=principal.requireCurrent();availability.requireApi();
            OverviewQuery.read(request.getInputStream());result.setData(service.overview(doctor,trace));
            result.setSuccess(true);result.setCode("200");result.setMessage("成功");
        } catch(CohortException e) {
            status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());
        } catch(java.io.IOException|RuntimeException e) {
            status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("研究库概览暂不可用");
        }
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
}
