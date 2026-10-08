package com.wenwen.service.impl;
import com.wenwen.ai.cohort.*;
import com.wenwen.ai.query.PrivateCohortQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.service.PrivateCohortService;
import java.util.Map;
import org.springframework.stereotype.Service;
import static com.wenwen.vo.AiCohortVo.object;
@Service public final class PrivateCohortServiceImpl implements PrivateCohortService {
    private final DefinitionStore store;
    private final com.wenwen.service.AiCohortService analysis;
    public PrivateCohortServiceImpl(DefinitionStore store,com.wenwen.service.AiCohortService analysis){
        this.store=store;
        this.analysis=analysis;
    }
    public Map<String,Object> execute(TrustedDoctor doctor,PrivateCohortQuery q,String trace){
        if("LIST".equals(q.action)){
            Map<String,Object> list=new java.util.LinkedHashMap<>(store.list(doctor.getId(),q.page,q.size));
            list.put("meta",object("traceId",trace));
            return list;
        }
        if("OPEN".equals(q.action)){
            PrivateCohortDefinition d=store.open(doctor.getId(),q.cohortId);
            return object("cohort",DefinitionStore.item(d),"analysis",analysis.analyze(doctor,PrivateCohortCodec.decode(d),trace),"meta",object("traceId",trace));
        }
        PrivateCohortReceipt r="DELETE".equals(q.action)?store.delete(doctor.getId(),q,trace):"RENAME".equals(q.action)?store.rename(doctor.getId(),q,trace):store.save(doctor.getId(),q,trace);
        return object("cohortId",r.getCohortId(),"version",r.getResultVersion(),"meta",object("traceId",trace));
    }
}
