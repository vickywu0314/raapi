package com.wenwen.controller.ra;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.ai.export.ExportApplicationQuery;
import com.wenwen.ai.scope.*;
import com.wenwen.ai.query.CohortException;
import com.wenwen.service.AiExportApplicationService;
import com.wenwen.vo.DataResult;
import java.io.*;
import java.time.Clock;
import java.util.*;
import javax.servlet.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ra/export") public final class AiExportApplicationController {
 private final AiFeatureAvailability availability;private final PrincipalProvider principal;private final AiExportApplicationService service;private final Clock clock;private final ObjectMapper json=new ObjectMapper();
 public AiExportApplicationController(PrincipalProvider p,AiExportApplicationService s,Clock c,AiFeatureAvailability availability){this.availability=availability;principal=p;service=s;clock=c;}
 @PostMapping("/applyAnalysis") public void apply(HttpServletRequest request,HttpServletResponse response)throws IOException {handle(request,response,false);}
 @PostMapping("/retryNotification") public void retry(HttpServletRequest request,HttpServletResponse response)throws IOException {handle(request,response,true);}
 private void handle(HttpServletRequest request,HttpServletResponse response,boolean retry)throws IOException {response.setHeader("X-Trace-Id",UUID.randomUUID().toString());DataResult<Object> result=new DataResult<>();int status=200;try{TrustedDoctor doctor=principal.requireCurrent();availability.requireApi();java.time.Instant at=clock.instant();Map<String,Object> data=retry?service.retry(doctor,ExportApplicationQuery.retry(request.getInputStream())):service.apply(doctor,ExportApplicationQuery.read(request.getInputStream()),at);result.setSuccess(true);result.setCode("200");result.setMessage("申请已记录");result.setData(data);}catch(CohortException e){status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());}catch(IOException|RuntimeException e){status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("申请暂不可用");}response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");json.writeValue(response.getOutputStream(),result);}
}
