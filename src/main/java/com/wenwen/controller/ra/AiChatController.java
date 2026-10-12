package com.wenwen.controller.ra;
import com.wenwen.ai.chat.ChatService;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.*;
import com.wenwen.vo.DataResult;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ra/ai")
public final class AiChatController {
    private final PrincipalProvider principal;private final AiFeatureAvailability availability;private final ChatService service;
    public AiChatController(PrincipalProvider p,AiFeatureAvailability a,ChatService s){principal=p;availability=a;service=s;}
    @PostMapping("/chat") public ResponseEntity<DataResult<?>> chat(HttpServletRequest request){
        String trace=UUID.randomUUID().toString();DataResult<Object> out=new DataResult<>();int status=200;
        try{TrustedDoctor doctor=principal.requireCurrent();availability.requireApi();ChatQuery query=ChatQuery.read(request.getInputStream(),doctor);out.setData(service.chat(doctor,query,trace));out.setSuccess(true);out.setCode("200");out.setMessage("成功");}
        catch(CohortException e){status=e.getStatus();out.setSuccess(false);out.setCode(e.getCode());out.setMessage(e.getMessage());}
        catch(java.io.IOException|RuntimeException e){status=503;out.setSuccess(false);out.setCode("SERVICE_UNAVAILABLE");out.setMessage("问答暂不可用");}
        return ResponseEntity.status(status).header("X-Trace-Id",trace).body(out);
    }
}
