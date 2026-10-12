package com.wenwen.ai.treatment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.*;
import org.springframework.core.env.Environment;

/** 只读服务器配置；每次分析捕获一份不可变字典，不缓存配置。 */
public final class ConfiguredDrugDictionary implements DrugDictionary {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Environment environment;
    private final DrugDictionary development=new DevelopmentDrugDictionary();
    public ConfiguredDrugDictionary(Environment environment){this.environment=environment;}
    public Snapshot snapshot(){
        String source=environment.getProperty("ra.ai.drug-dictionary.json");
        if(source==null)source=environment.getProperty("RA_AI_DRUG_DICTIONARY_JSON");
        if(source==null)return development.snapshot();
        if(source.length()>1048576||source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576)throw invalid();
        try{
            if(source.trim().isEmpty())throw new IllegalStateException("药品配置无效");
            boundedStructure(source);
            JsonNode root=JSON.readTree(source);
            fields(root,"version","drugs");
            String version=identifier(root.get("version"));
            JsonNode drugs=root.get("drugs");if(!drugs.isArray()||drugs.size()>5000)throw invalid();
            Map<String,Drug> names=new LinkedHashMap<>();
            Set<String> ids=new HashSet<>();
            for(JsonNode item:drugs){
                fields(item,"id","name","category","aliases");
                String category=text(item.get("category"));
                if(!Arrays.asList("csDMARD","TNFi","JAKi","IL-6i","Abatacept").contains(category))throw invalid();
                Drug drug=new Drug(identifier(item.get("id")),name(item.get("name")),category);
                if(!ids.add(drug.id))throw invalid();
                add(names,drug.name,drug);
                JsonNode aliases=item.get("aliases");if(!aliases.isArray()||aliases.size()>20)throw invalid();
                for(JsonNode alias:aliases)add(names,name(alias),drug);
            }
            return new Snapshot(version,names);
        }catch(IOException e){throw invalid();}
    }
    /** 流式token扫描不递归；只有有界结构才能交给树解析器。 */
    private static void boundedStructure(String source)throws IOException{
        try(JsonParser parser=JSON.getFactory().createParser(source)){
            int depth=0;JsonToken token;
            while((token=parser.nextToken())!=null){
                if(token==JsonToken.START_OBJECT||token==JsonToken.START_ARRAY){if(++depth>4)throw invalid();}
                else if(token==JsonToken.END_OBJECT||token==JsonToken.END_ARRAY)depth--;
            }
        }
    }
    private static IllegalStateException invalid(){return new IllegalStateException("药品配置无效");}
    private static void add(Map<String,Drug> names,String name,Drug drug){Drug previous=names.putIfAbsent(name,drug);if(previous!=null&&previous!=drug)throw invalid();}
    private static String text(JsonNode node){if(node==null||!node.isTextual())throw invalid();return node.textValue();}
    private static String identifier(JsonNode node){String value=text(node).trim();if(value.isEmpty()||value.length()>200||!value.matches("[\\p{L}\\p{N}:._\\-]+"))throw invalid();return value;}
    private static String name(JsonNode node){
        String value=text(node).trim();
        if(value.isEmpty()||value.codePointCount(0,value.length())>128)throw invalid();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw invalid();}
            else if(Character.isLowSurrogate(c))throw invalid();
        }
        return value;
    }
    private static void fields(JsonNode node,String... fields){
        if(node==null||!node.isObject()||node.size()!=fields.length)throw invalid();
        for(String field:fields)if(!node.has(field))throw invalid();
    }
}
