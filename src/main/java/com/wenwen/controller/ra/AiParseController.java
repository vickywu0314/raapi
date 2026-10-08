package com.wenwen.controller.ra;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.*;
import com.wenwen.service.AiParseService;
import com.wenwen.vo.DataResult;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ra/ai") public final class AiParseController {
    private final PrincipalProvider principal;private final AiParseService service;
    public AiParseController(PrincipalProvider principal,AiParseService service){this.principal=principal;this.service=service;}
    @PostMapping("/parse") public ResponseEntity<DataResult<?>> parse(HttpServletRequest request){return execute(request,false);}
    @PostMapping("/applyParse") public ResponseEntity<DataResult<?>> apply(HttpServletRequest request){return execute(request,true);}
    private ResponseEntity<DataResult<?>> execute(HttpServletRequest request,boolean apply){String trace=UUID.randomUUID().toString();DataResult<Object> result=new DataResult<>();int status=200;
        try{TrustedDoctor doctor=principal.requireCurrent();long started=System.nanoTime();ParseQuery query=apply?ParseQuery.apply(request.getInputStream(),doctor):ParseQuery.parse(request.getInputStream());result.setData(apply?service.apply(doctor,query,trace,started):service.parse(doctor,query,trace,started));result.setSuccess(true);result.setCode("200");result.setMessage("成功");}
        catch(CohortException e){status=e.getStatus();result.setSuccess(false);result.setCode(e.getCode());result.setMessage(e.getMessage());}
        catch(IOException|RuntimeException e){status=503;result.setSuccess(false);result.setCode("SERVICE_UNAVAILABLE");result.setMessage("解析暂不可用");}
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
}
