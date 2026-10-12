package com.wenwen.ai.treatment;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredDrugDictionaryTest {
    static final String KEY="ra.ai.drug-dictionary.json", ENV="RA_AI_DRUG_DICTIONARY_JSON";
    static final String VALID="{\"version\":\"synthetic-v1\",\"drugs\":[{\"id\":\"synthetic:g1\",\"name\":\"generic\",\"category\":\"TNFi\",\"aliases\":[\"brand\"]}]}";
    final Map<String,Object> properties=new HashMap<>();
    final ConfiguredDrugDictionary dictionary;
    ConfiguredDrugDictionaryTest(){StandardEnvironment env=new StandardEnvironment();env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);env.getPropertySources().addFirst(new MapPropertySource("synthetic",properties));dictionary=new ConfiguredDrugDictionary(env);}
    DrugDictionary.Snapshot view(String source){properties.put(KEY,source);return dictionary.snapshot();}
    void invalid(String source){IllegalStateException e=assertThrows(IllegalStateException.class,()->view(source));assertEquals("药品配置无效",e.getMessage());assertNull(e.getCause());}
    @Test void absencePriorityAndPresentInvalidNeverFallBack(){
        assertEquals("dev-drug-v04",dictionary.snapshot().version);
        properties.put(ENV,VALID);assertEquals("synthetic-v1",dictionary.snapshot().version);
        properties.put(KEY,VALID.replace("synthetic-v1","synthetic-primary"));assertEquals("synthetic-primary",dictionary.snapshot().version);
        invalid("");invalid(" ");invalid("not-json-private-canary");
        properties.remove(KEY);assertEquals("synthetic-v1",dictionary.snapshot().version);
        properties.put(ENV,"");invalid(null);
    }
    @Test void canonicalSchemaRejectsMissingUnknownDuplicateTrailingNullAndWrongTypes(){
        assertEquals("empty-v1",view("{\"version\":\"empty-v1\",\"drugs\":[]}").version);
        for(String source:Arrays.asList("{}","null","[]","{\"version\":\"v\"}","{\"drugs\":[]}",
            VALID.replace("\"version\":\"synthetic-v1\"","\"version\":null"),VALID.replace("\"drugs\":[", "\"drugs\":null,\"unused\":["),
            VALID.replace("\"version\":", "\"extra\":1,\"version\":"),VALID.replace("\"id\":", "\"extra\":1,\"id\":"),
            VALID.replace("\"version\":", "\"version\":\"duplicate\",\"version\":"),VALID.replace("\"id\":", "\"id\":\"duplicate\",\"id\":"),
            VALID+" {}",VALID+" trailing",VALID.replace("\"id\":\"synthetic:g1\",", ""),VALID.replace("\"name\":\"generic\",", ""),
            VALID.replace("\"category\":\"TNFi\",", ""),VALID.replace(",\"aliases\":[\"brand\"]", ""),
            VALID.replace("\"drugs\":[{", "\"drugs\":[null,{"),VALID.replace("\"id\":\"synthetic:g1\"","\"id\":1"),
            VALID.replace("\"name\":\"generic\"","\"name\":false"),VALID.replace("\"category\":\"TNFi\"","\"category\":null"),
            VALID.replace("[\"brand\"]","{}"),VALID.replace("[\"brand\"]","[null]"),VALID.replace("[\"brand\"]","[1]")))invalid(source);
    }
    String repeat(String value,int n){return String.join("",Collections.nCopies(n,value));}
    @Test void identifiersTrimRespectRetainedLengthAndCategoriesAreExact(){
        String id="合成:"+repeat("a",197),version=repeat("v",200);
        DrugDictionary.Snapshot s=view(VALID.replace("synthetic-v1"," "+version+" ").replace("synthetic:g1"," "+id+" "));
        assertEquals(version,s.version);assertEquals(id,s.identify("generic").id);
        assertEquals("synthetic:9007199254740993",view(VALID.replace("synthetic:g1","synthetic:9007199254740993")).identify("generic").id);
        for(String value:Arrays.asList(""," ",repeat("a",201),"bad space","bad/+","bad\u0301","\\ud800")){
            invalid(VALID.replace("synthetic-v1",value));invalid(VALID.replace("synthetic:g1",value));
        }
        for(String category:Arrays.asList("csDMARD","TNFi","JAKi","IL-6i","Abatacept"))assertEquals(category,view(VALID.replace("TNFi",category)).identify("brand").category);
        for(String category:Arrays.asList("tnfi","TNFi ","", "OTHER"))invalid(VALID.replace("TNFi",category));
    }
    @Test void namesAreTrimmedExactPairedUnicodeAndBoundedByCodePoints(){
        DrugDictionary.Snapshot s=view(VALID.replace("\"generic\"","\" generic \"").replace("[\"brand\"]","[\" brand \",\"generic\",\"brand\"]"));
        assertEquals("generic",s.identify(" generic ").name);assertSame(s.identify("generic"),s.identify("brand"));
        assertNull(s.identify("BRAND"));assertNull(s.identify("ｂｒａｎｄ"));assertNull(s.identify("brand effective"));
        String exact=repeat("\uD83D\uDE00",128);
        assertNotNull(view(VALID.replace("generic",exact)).identify(exact));assertNotNull(view(VALID.replace("brand",exact)).identify(exact));
        for(String name:Arrays.asList(""," ",repeat("a",129),repeat("\uD83D\uDE00",129),"\\ud800","\\udc00","\\ud800x")){
            invalid(VALID.replace("generic",name));invalid(VALID.replace("brand",name));
        }
    }
    @Test void duplicateIdsAndCrossEntityNormalizedNamesRejectWholeConfiguration(){
        String drug="{\"id\":\"synthetic:g2\",\"name\":\"other\",\"category\":\"TNFi\",\"aliases\":[\"other-brand\"]}";
        String two=VALID.substring(0,VALID.length()-2)+","+drug+"]}";
        DrugDictionary.Snapshot s=view(two);assertNotEquals(s.identify("generic").id,s.identify("other").id);
        invalid(two.replace("synthetic:g2","synthetic:g1"));invalid(two.replace("synthetic:g2"," synthetic:g1 "));
        invalid(VALID.substring(0,VALID.length()-2)+","+VALID.substring(VALID.indexOf("[{" )+1,VALID.length()-2)+"]}");
        invalid(two.replace("\"other\"","\" generic \""));invalid(two.replace("\"other\"","\" brand \""));
        invalid(two.replace("other-brand"," generic "));invalid(two.replace("other-brand"," brand "));
    }
    String drugs(int count){List<String> rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add("{\"id\":\"g:"+i+"\",\"name\":\"name"+i+"\",\"category\":\"TNFi\",\"aliases\":[]}");return "{\"version\":\"count-v1\",\"drugs\":["+String.join(",",rows)+"]}";}
    @Test void drugAndAliasCountsAcceptLimitsAndRejectOneBeyond(){
        assertNull(view(drugs(0)).identify("name0"));assertEquals("g:4999",view(drugs(5000)).identify("name4999").id);
        invalid(drugs(5001));
        assertNotNull(view(VALID.replace("[\"brand\"]","[]")).identify("generic"));
        assertNotNull(view(VALID.replace("[\"brand\"]","["+String.join(",",Collections.nCopies(20,"\"brand\""))+"]")).identify("brand"));
        invalid(VALID.replace("[\"brand\"]","["+String.join(",",Collections.nCopies(21,"\"brand\""))+"]"));
    }
    @Test void utf8ByteLimitAcceptsExactAndRejectsBeyondBeforeBuilding(){
        String source=VALID.replace("generic","合成名");int bytes=source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String exact=source+repeat(" ",1048576-bytes);
        assertEquals(1048576,exact.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals("synthetic-v1",view(exact).version);invalid(exact+" ");
        // UTF-8字节超限而Java长度未超限；所有单个字段和数量均合法。
        List<String> rows=new ArrayList<>();for(int i=0;i<2500;i++)rows.add("{\"id\":\"g:"+i+"\",\"name\":\""+repeat("药",120)+i+"\",\"category\":\"TNFi\",\"aliases\":[]}");
        String multi="{\"version\":\"bytes-v1\",\"drugs\":["+String.join(",",rows)+"]}";
        assertTrue(multi.length()<1048576);assertTrue(multi.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576);invalid(multi);
    }
    @Test void depthIsBoundedBeforeRecursiveTreeConstruction(){
        invalid("{\"version\":\"depth-canary\",\"drugs\":"+repeat("[",10000)+"0"+repeat("]",10000)+"}");
        assertNotNull(view(VALID).identify("brand")); // 根/药品列表/药品/别名列表，深度4。
        invalid(VALID.replace("[\"brand\"]","[[\"brand\"]]")); // 深度5。
        assertNotNull(view(VALID.replace("brand","{}[] braces are text")).identify("{}[] braces are text"));
    }
    @Test void oneCapturedStringAndOldSnapshotStayIndependentOfLaterChanges(){
        java.util.concurrent.atomic.AtomicInteger primaryReads=new java.util.concurrent.atomic.AtomicInteger(),fallbackReads=new java.util.concurrent.atomic.AtomicInteger();
        StandardEnvironment env=new StandardEnvironment(){@Override public String getProperty(String key){if(KEY.equals(key)){primaryReads.incrementAndGet();return (String)properties.get(KEY);}if(ENV.equals(key)){fallbackReads.incrementAndGet();return (String)properties.get(ENV);}throw new AssertionError("unexpected property "+key);}};
        ConfiguredDrugDictionary d=new ConfiguredDrugDictionary(env);
        String original=VALID;properties.put(KEY,original);DrugDictionary.Snapshot old=d.snapshot();
        assertEquals(1,primaryReads.get());assertEquals(0,fallbackReads.get());
        original=original.replace("synthetic-v1","synthetic-v2").replace("TNFi","JAKi");properties.put(KEY,original);
        assertEquals("synthetic-v1",old.version);assertEquals("TNFi",old.identify("brand").category);
        DrugDictionary.Snapshot next=d.snapshot();assertEquals("synthetic-v2",next.version);assertEquals("JAKi",next.identify("brand").category);
        assertEquals(2,primaryReads.get());assertEquals(0,fallbackReads.get());properties.clear();assertEquals("synthetic:g1",old.identify("generic").id);
        Map<String,DrugDictionary.Drug> caller=new HashMap<>();caller.put("caller",new DrugDictionary.Drug("g:1","caller","TNFi"));
        DrugDictionary.Snapshot immutable=new DrugDictionary.Snapshot("v",caller);caller.clear();assertNotNull(immutable.identify("caller"));
    }
}
