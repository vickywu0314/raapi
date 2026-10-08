package com.wenwen.ai.result;
import com.wenwen.mapper.AiCohortSourceMapper;
import com.wenwen.ai.source.PatientDisplayRow;
import com.wenwen.ai.query.CohortException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
@Component public final class AnalysisPageReader {
    private final AiCohortSourceMapper mapper;private final TransactionTemplate snapshot;
    public AnalysisPageReader(AiCohortSourceMapper mapper,PlatformTransactionManager transactions){this.mapper=mapper;snapshot=new TransactionTemplate(transactions);snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setReadOnly(true);}
    public Map<Long,PatientDisplayRow> read(long owner,String scope,List<Long> ids){return snapshot.execute(status->{
        if(!AnalysisValues.scope(owner,mapper.patientIds(owner)).equals(scope))throw new CohortException(409,"SCOPE_CHANGED","分析范围已变更，请重新分析");
        Map<Long,PatientDisplayRow> display=new LinkedHashMap<>();for(PatientDisplayRow row:mapper.displays(owner,ids))if(display.put(row.getId(),row)!=null)throw AnalysisSettings.unavailable();
        if(display.size()!=ids.size()||!display.keySet().equals(new HashSet<>(ids)))throw AnalysisSettings.unavailable();return display;
    });}
}
