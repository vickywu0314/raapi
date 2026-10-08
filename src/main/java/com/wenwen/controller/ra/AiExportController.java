package com.wenwen.controller.ra;
import com.wenwen.ai.export.*;
import com.wenwen.ai.scope.*;
import com.wenwen.ai.query.CohortException;
import com.wenwen.service.AiExportService;
import com.wenwen.vo.DataResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.*;
import java.io.*;
import java.time.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ra/export")
public final class AiExportController {
    private final AiFeatureAvailability availability;private final PrincipalProvider principal;private final AiExportService service;private final Clock clock;private final ObjectMapper json=new ObjectMapper();
    public AiExportController(PrincipalProvider p,AiExportService s,Clock c,AiFeatureAvailability availability){this.availability=availability;principal=p;service=s;clock=c;}
    @PostMapping("/xlsx") public void export(HttpServletRequest request,HttpServletResponse response)throws IOException {
        String trace=UUID.randomUUID().toString();boolean binaryPhase=false;long owner=0;response.setHeader("X-Trace-Id",trace);
        try{TrustedDoctor doctor=principal.requireCurrent();availability.requireXlsx();owner=doctor.getId();Instant at=clock.instant();AiExportQuery query=AiExportQuery.read(request.getInputStream());OrdinaryXlsxWriter.File file=service.prepare(doctor,query,trace,at);binaryPhase=true;response.setStatus(200);response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Cache-Control","no-store");response.setHeader("Content-Disposition","attachment; filename=\"ra-export-"+query.referenceId+"-"+at.toEpochMilli()+".xlsx\"");response.getOutputStream().write(file.bytes);response.getOutputStream().flush();service.finishWritten(doctor.getId(),trace);}
        catch(ExportFieldPolicy.NeedApply e){if(binaryPhase)throw e;error(response,409,"NEED_APPLY",e.getMessage(),e.data);}
        catch(CohortException e){if(binaryPhase){service.finishWriteFailed(owner,trace);throw e;}error(response,e.getStatus(),e.getCode(),e.getMessage());}
        catch(IOException|RuntimeException e){if(binaryPhase){service.finishWriteFailed(owner,trace);throw e;}error(response,503,"SERVICE_UNAVAILABLE","导出暂不可用");}
    }
    private void error(HttpServletResponse r,int status,String code,String message)throws IOException{error(r,status,code,message,null);}
    private void error(HttpServletResponse r,int status,String code,String message,Object data)throws IOException{r.setStatus(status);r.setContentType("application/json");r.setCharacterEncoding("UTF-8");DataResult<Object> result=new DataResult<>();result.setSuccess(false);result.setCode(code);result.setMessage(message);result.setData(data);json.writeValue(r.getOutputStream(),result);}
}
