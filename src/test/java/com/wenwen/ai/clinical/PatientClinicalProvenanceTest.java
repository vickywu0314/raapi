package com.wenwen.ai.clinical;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PatientClinicalProvenanceTest {
    @Test void concurrentFirstAccessFreezesExactlyOnceAndReturnsImmutableEvidence()throws Exception {
        AtomicInteger built=new AtomicInteger();Map<String,Object> input=new LinkedHashMap<>();List<Object> facts=new ArrayList<>();facts.add(new LinkedHashMap<>(Collections.singletonMap("sourceRowId","1")));input.put("observations",facts);
        PatientClinical patient=new PatientClinical("F",40,10,"TRUE","TRUE","UNKNOWN",()->{built.incrementAndGet();return input;});assertEquals(0,built.get());
        ExecutorService callers=Executors.newFixedThreadPool(8);try{List<Future<Map<String,Object>>> reads=new ArrayList<>();for(int i=0;i<24;i++)reads.add(callers.submit(patient::getProvenance));Map<String,Object> first=reads.get(0).get(5,TimeUnit.SECONDS);for(Future<Map<String,Object>> read:reads)assertSame(first,read.get(5,TimeUnit.SECONDS));assertEquals(1,built.get());facts.clear();assertEquals(1,((List<?>)first.get("observations")).size());assertThrows(UnsupportedOperationException.class,()->((List<Object>)first.get("observations")).clear());}finally{callers.shutdownNow();assertTrue(callers.awaitTermination(5,TimeUnit.SECONDS));}
    }
}
