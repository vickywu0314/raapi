package com.wenwen.ai.result;
import com.wenwen.mapper.AiCohortSourceMapper;
import com.wenwen.ai.query.CohortException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** 只读取当前本人RA完整U，不显示患者或重新读取临床事实。 */
@Component public final class ComparisonScopeReader {
    private final AiCohortSourceMapper mapper;private final TransactionTemplate snapshot;
    public ComparisonScopeReader(AiCohortSourceMapper mapper,PlatformTransactionManager transactions){this.mapper=mapper;snapshot=new TransactionTemplate(transactions);snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setReadOnly(true);}
    public void require(long owner,String fingerprint){snapshot.execute(status->{if(!AnalysisValues.scope(owner,mapper.patientIds(owner)).equals(fingerprint))throw new CohortException(409,"SCOPE_CHANGED","比较范围已变更，请重新比较");return null;});}
}
