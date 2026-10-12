package com.wenwen.ai.chat;
import com.wenwen.ai.analysis.*;
import com.wenwen.ai.query.*;
import com.wenwen.ai.scope.*;
import com.wenwen.ai.source.*;
import com.wenwen.ai.treatment.DrugDictionary;
import java.time.*;
import java.util.*;
import static com.wenwen.vo.AiCohortVo.object;
/** 每轮全新源事实；网络等待前SQL归还，审计提交后才发布。 */
public final class ChatService {
    private final CohortSourceAdapter source;private final Clock clock;private final DrugDictionary dictionary;private final ChatSettings settings;private final ChatGateway gateway;private final ChatAuditStore audit;
    public ChatService(CohortSourceAdapter source,Clock clock,DrugDictionary dictionary,ChatSettings settings,ChatGateway gateway,ChatAuditStore audit){this.source=source;this.clock=clock;this.dictionary=dictionary;this.settings=settings;this.gateway=gateway;this.audit=audit;}
    public Map<String,Object> chat(TrustedDoctor doctor,ChatQuery query,String trace)throws java.io.IOException {
        settings.require(query.kind);long start=System.nanoTime();Instant started=clock.instant();LocalDate asOf=started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();DrugDictionary.Snapshot view=dictionary.snapshot();
        SourceBatch batch=source.read(doctor.getId(),asOf);Map<String,Object> facts,scope;String at;
        if("PATIENT".equals(query.kind)){facts=PatientChatFacts.compute(batch,query.patientId,query.visitId,asOf,view);scope=object("kind","PATIENT","patientId",Long.toString(query.patientId),"visitId",query.visitId==null?null:Long.toString(query.visitId),"n",1);at="now";}
        else{CohortComputation.Derivation derived=CohortComputation.derive(batch,query.cohort.getAt(),asOf,view);List<DerivedPatient> members=CohortComputation.select(derived,query.cohort).members;facts=ChatFacts.compute(members,batch.getFmSourceVersion());scope=object("kind",query.kind,"filters",query.cohort.normalizedFilters(),"n",members.size());at=query.cohort.getAt();}
        ChatGateway.Result result=gateway.select(query,(Integer)scope.get("n"),facts,asOf.toString());
        if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("chat interrupted");
        if("CONTEXT_TOO_LARGE".equals(result.failure))throw new CohortException(413,result.failure,"问答上下文超出限制，请缩小到本次访视");
        String ref=UUID.randomUUID().toString(),outcome=result.failure==null?result.selection.status:result.failure;
        audit.append(new ChatAuditEvent(ref,trace,doctor.getId(),query.kind,"AI",outcome,settings.modelVersion(),ChatProtocol.PROMPT_VERSION,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),started.toEpochMilli()));
        if(result.failure!=null)throw new CohortException(status(result.failure),result.failure,"问答模型暂不可用");
        List<Object> selected=new ArrayList<>();for(String key:result.selection.keys)selected.add(facts.get(key));
        return object("status",result.selection.status,"engine","AI","reason",result.selection.reason,"scope",scope,"facts",Collections.unmodifiableList(selected),
            "meta",object("traceId",trace,"asOfDate",asOf.toString(),"readStartedAt",started.toString(),"readCompletedAt",batch.getReadCompletedAt().toString(),"at",at,"policyVersions",policies(query.kind,batch,view),"modelVersion",settings.modelVersion(),"promptVersion",ChatProtocol.PROMPT_VERSION),"auditRef",ref);
    }
    private Map<String,Object> policies(String kind,SourceBatch batch,DrugDictionary.Snapshot view){Map<String,Object> versions=new LinkedHashMap<>(batch.withFmSource(object("descriptive",DescriptiveAnalysis.VERSION,"qc",com.wenwen.ai.qc.MissingDataPolicy.VERSION,"crp","dev-crp-v04","now","dev-now-v04","clinical","dev-clinical-v04","serology","dev-ever-serology-v04","treatment","dev-timeline-v04","visitMatcher","dev-visit-match-v04","drugDictionary",view.version,"statistics",com.wenwen.ai.statistics.StatisticalPolicy.VERSION)));if("PATIENT".equals(kind))versions.put("insight","dev-insight-v01");return versions;
    }
    private int status(String code){return "AI_BUSY".equals(code)?429:"AI_TIMEOUT".equals(code)?504:Arrays.asList("AI_INVALID_RESPONSE","AI_HTTP_ERROR").contains(code)?502:503;}
}
