package com.wenwen.ai.clinical;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredFmEvidenceSourceTest {
    static final String VALID="{\"version\":\" synthetic:来源-v1 \",\"records\":[{\"patientId\":\"9223372036854775807\",\"state\":\"FALSE\",\"evidenceId\":\" 证据:opaque-1 \",\"observedAt\":\"2026-10-06\"}]}";
    @Test void strictBoundedConfigurationRejectsWholeSourceWithoutEchoingIt(){
        List<String> bad=new ArrayList<>(Arrays.asList("","null","[]","{}",VALID+" {}",VALID.replace("\"version\":","\"unknown\":0,\"version\":"),VALID.replace("\"version\":","\"version\":\"duplicate\",\"version\":"),VALID.replace("\"records\":","\"records\":null,\"records\":"),VALID.replace("\"state\":\"FALSE\"","\"state\":\"false\""),VALID.replace("\"observedAt\":\"2026-10-06\"","\"observedAt\":\"2026-2-30\""),VALID.replace("\"evidenceId\":\" 证据:opaque-1 \"","\"evidenceId\":null")));
        for(String id:Arrays.asList("01","0","-1"," 1","1 ","9223372036854775808","1.0"))bad.add(VALID.replace("9223372036854775807",id));
        bad.add(VALID.replace("\"9223372036854775807\"","1"));
        String record="{\"patientId\":\"1\",\"state\":\"TRUE\",\"evidenceId\":\"synthetic:e1\",\"observedAt\":\"2026-10-06\"}";
        bad.add("{\"version\":\"v1\",\"records\":["+record+","+record+"]}");
        bad.add("{\"version\":\"v1\",\"records\":[[[[]]]]}");
        bad.add(VALID.replace(" synthetic:来源-v1 ",String.join("",Collections.nCopies(201,"a"))));
        bad.add(VALID.replace(" 证据:opaque-1 ","private canary"));
        bad.add(String.join("",Collections.nCopies(1048577," ")));
        bad.add("{\"version\":\"v1\",\"records\":["+String.join(",",Collections.nCopies(5001,record))+"]}");
        for(String source:bad){IllegalStateException e=assertThrows(IllegalStateException.class,()->new ConfiguredFmEvidenceSource(new MockEnvironment().withProperty("ra.ai.fm-evidence.json",source)).snapshot());assertEquals("FM配置无效",e.getMessage());}
        ConfiguredFmEvidenceSource.View v=new ConfiguredFmEvidenceSource(new MockEnvironment().withProperty("ra.ai.fm-evidence.json",VALID)).snapshot();
        assertEquals("synthetic:来源-v1",v.version);assertEquals("FALSE",v.state(Long.MAX_VALUE,LocalDate.parse("2026-10-07")));assertEquals("UNKNOWN",v.state(Long.MAX_VALUE-1,LocalDate.parse("2026-10-07")));
        assertThrows(UnsupportedOperationException.class,()->v.provenance(Long.MAX_VALUE,LocalDate.parse("2026-10-07")).put("source","bad"));
    }
    @Test void propertyPriorityImmutableViewsAndDateEffectivenessAreExact(){
        MockEnvironment env=new MockEnvironment();ConfiguredFmEvidenceSource source=new ConfiguredFmEvidenceSource(env);assertNull(source.snapshot());env.withProperty("RA_AI_FM_EVIDENCE_JSON",VALID);ConfiguredFmEvidenceSource.View first=source.snapshot();assertEquals("synthetic:来源-v1",first.version);
        LocalDate day=LocalDate.parse("2026-10-07");assertEquals("UNKNOWN",first.state(Long.MAX_VALUE,LocalDate.parse("2026-10-05")));assertEquals("NO_EFFECTIVE_FM_EVIDENCE",first.provenance(Long.MAX_VALUE,LocalDate.parse("2026-10-05")).get("missingReason"));
        env.withProperty("ra.ai.fm-evidence.json",VALID.replace("来源-v1","来源-v2").replace("FALSE","UNKNOWN"));ConfiguredFmEvidenceSource.View second=source.snapshot();assertEquals("synthetic:来源-v2",second.version);assertEquals("UNKNOWN",second.state(Long.MAX_VALUE,day));assertEquals("EXPLICIT_UNKNOWN",second.provenance(Long.MAX_VALUE,day).get("missingReason"));assertEquals("FALSE",first.state(Long.MAX_VALUE,day));
        env.withProperty("ra.ai.fm-evidence.json","");assertThrows(IllegalStateException.class,source::snapshot);assertEquals("FALSE",first.state(Long.MAX_VALUE,day));
        env.withProperty("ra.ai.fm-evidence.json",VALID.replace(" synthetic:来源-v1 ",String.join("",Collections.nCopies(200,"界"))));assertEquals(200,source.snapshot().version.length());
        StringBuilder rows=new StringBuilder("{\"version\":\"limit\",\"records\":[");for(int i=1;i<=5000;i++){if(i>1)rows.append(',');rows.append("{\"patientId\":\"").append(i).append("\",\"state\":\"TRUE\",\"evidenceId\":\"e:").append(i).append("\",\"observedAt\":\"2026-10-07\"}");}rows.append("]}");env.withProperty("ra.ai.fm-evidence.json",rows.toString());assertEquals("TRUE",source.snapshot().state(5000,day));
        env.withProperty("ra.ai.fm-evidence.json",String.join("",Collections.nCopies(400000,"界")));assertThrows(IllegalStateException.class,source::snapshot);
    }

}
