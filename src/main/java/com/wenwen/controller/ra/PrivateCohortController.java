package com.wenwen.controller.ra;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.*;
import com.wenwen.service.PrivateCohortService;
import com.wenwen.vo.DataResult;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ra/cohort")
public final class PrivateCohortController {
    private final PrincipalProvider principal;
    private final PrivateCohortService service;
    private final AiFeatureAvailability availability;
    public PrivateCohortController(PrincipalProvider principal,PrivateCohortService service,AiFeatureAvailability availability){
        this.principal=principal;
        this.service=service;
        this.availability=availability;
    }
    @PostMapping("/save") public ResponseEntity<DataResult<?>> save(HttpServletRequest request){
        return handle("SAVE",request);
    }
    @PostMapping("/open") public ResponseEntity<DataResult<?>> open(HttpServletRequest request){
        return handle("OPEN",request);
    }
    @PostMapping("/list") public ResponseEntity<DataResult<?>> list(HttpServletRequest request){
        return handle("LIST",request);
    }
    @PostMapping("/rename") public ResponseEntity<DataResult<?>> rename(HttpServletRequest request){
        return handle("RENAME",request);
    }
    @PostMapping("/delete") public ResponseEntity<DataResult<?>> delete(HttpServletRequest request){
        return handle("DELETE",request);
    }
    private ResponseEntity<DataResult<?>> handle(String action,HttpServletRequest request){
        String trace=UUID.randomUUID().toString();
        DataResult<Object> result=new DataResult<>();
        int status=200;
        try{
            TrustedDoctor doctor=principal.requireCurrent();
            availability.requireApi();
            PrivateCohortQuery q=PrivateCohortQuery.read(action,request.getInputStream(),doctor);
            result.setData(service.execute(doctor,q,trace));
            result.setSuccess(true);
            result.setCode("200");
            result.setMessage("成功");
        }
        catch(CohortException e){
            status=e.getStatus();
            result.setSuccess(false);
            result.setCode(e.getCode());
            result.setMessage(e.getMessage());
        }
        catch(IOException|RuntimeException e){
            status=503;
            result.setSuccess(false);
            result.setCode("SERVICE_UNAVAILABLE");
            result.setMessage("队列暂不可用");
        }
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(result);
    }
}
