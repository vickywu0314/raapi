package com.wenwen.ai.parse;

import java.util.*;
import java.util.regex.*;

/** 仅有限完整词；消费最长词跨度，不解释任意自由语言。 */
public final class RuleParser {
    private static final class Word {
        final String field,value,text;
        Word(String field,String value,String text){this.field=field;this.value=value;this.text=text.toLowerCase(Locale.ROOT);}
    }
    private static final String[] NEUTRAL={"帮我","筛选","筛出","查找","看看","当前","治疗","年龄","患者","人群","并且","请","的","且","和"};
    private static final String NEUTRAL_PATTERN=String.join("|",NEUTRAL);
    private static final Pattern AGE=Pattern.compile("(?<![0-9])(-?[0-9]+)(?:[-~至](-?[0-9]+)岁?|岁以上)(?![0-9])");
    private static final List<Word> WORDS=new ArrayList<>();
    static {
        words("sex","F","女性","女患者","女");
        words("sex","M","男性","男患者");
        words("tx","TNFi","TNF","肿瘤坏死","阿达木","依那西普","英夫利昔");
        words("tx","JAKi","JAK","托法替布","巴瑞替尼","乌帕替尼");
        words("tx","IL-6i","IL-6","IL6","托珠");
        words("tx","Abatacept","阿巴西普","Abatacept");
        words("tx","bio","生物制剂","靶向","b/tsDMARD");
        words("tx","csDMARD","甲氨蝶呤","MTX","csDMARD","传统");
        words("act","mod-high","中高活动","中高疾病活动","中高","未达标","仍活动","仍有活动","仍疾病活动","仍有疾病活动","仍未达标","仍中高活动","仍有中高活动","仍中高疾病活动","仍有中高疾病活动");
        words("act","high","高活动","高疾病活动","仍高活动","仍有高活动","仍高疾病活动","仍有高疾病活动");
        words("act","remission","缓解");
        words("act","target","已达标","达到目标","低疾病活动");
        words("cm","FM","纤维肌痛","FM");
        words("cm","AS","强直","AS");
        words("sero","1","血清阳性","RF","ACPA","抗CCP");
        words("at","6m","6个月","六个月","半年");
        words("data","missing","缺失","待补全","不完整");
        WORDS.sort((a,b)->Integer.compare(b.text.length(),a.text.length()));
    }
    private static void words(String field,String value,String... texts){for(String text:texts)WORDS.add(new Word(field,value,text));}
    private static boolean asciiWord(char c){return c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_';}
    private static boolean boundary(String input,int start,Word word){int end=start+word.text.length();return !(asciiWord(word.text.charAt(0))&&start>0&&asciiWord(input.charAt(start-1)))&&!(asciiWord(word.text.charAt(word.text.length()-1))&&end<input.length()&&asciiWord(input.charAt(end)));}
    public ParseDecision parse(String question){
        String input=question.replaceAll("(?U)\\s","").toLowerCase(Locale.ROOT);
        List<String> warnings=new ArrayList<>();
        if(input.contains("或")||Pattern.compile("(?i)(?<![a-z])or(?![a-z])").matcher(question).find())warnings.add("UNSUPPORTED_OR");
        if(input.replace("仍未达标","").replace("未达标","").replace("不完整","").matches(".*(?:非|不|未|阴性).*"))warnings.add("UNSUPPORTED_NEGATION");
        if(!warnings.isEmpty())return new ParseDecision("UNSUPPORTED",null,0,warnings);
        boolean[] consumed=new boolean[input.length()];Map<String,Object> candidate=new LinkedHashMap<>();Set<String> conflicts=new HashSet<>();
        for(int i=0;i<input.length();i++)for(Word word:WORDS){if("女".equals(word.text)&&input.contains("男"))continue;int end=i+word.text.length();if(end>input.length()||!input.startsWith(word.text,i)||!boundary(input,i,word))continue;boolean available=true;for(int j=i;j<end;j++)if(consumed[j])available=false;if(!available)continue;collect(candidate,conflicts,word.field,word.value);Arrays.fill(consumed,i,end,true);break;}
        Matcher age=AGE.matcher(input);
        boolean invalidAge=false;
        while(age.find()){
            try{int min=Integer.parseInt(age.group(1)),max=age.group(2)==null?120:Integer.parseInt(age.group(2));
                if(min<0||min>120||max<0||max>120||min>max)invalidAge=true;
                else collect(candidate,conflicts,"age",min+"-"+(age.group(2)==null?"":Integer.toString(max)));
            }catch(NumberFormatException e){invalidAge=true;}
            Arrays.fill(consumed,age.start(),age.end(),true);
        }
        StringBuilder leftover=new StringBuilder();for(int i=0;i<input.length();i++)if(!consumed[i])leftover.append(input.charAt(i));
        String rest=leftover.toString().replaceAll(NEUTRAL_PATTERN+"|[\\p{P}]","");
        String status=input.isEmpty()?"EMPTY":candidate.isEmpty()?"UNRECOGNIZED":rest.isEmpty()?"OK":"PARTIAL";
        if(!conflicts.isEmpty())warnings.add("CONFLICTING_CONDITIONS");if(invalidAge)warnings.add("AGE_OUT_OF_RANGE");
        if(!warnings.isEmpty())return new ParseDecision("UNSUPPORTED",null,0,warnings);
        return new ParseDecision(status,candidate.isEmpty()?null:candidate,rest.codePointCount(0,rest.length()));
    }
    /** 在原始连续跨度上消费；规则结果可用不代表原句获准外发。 */
    public boolean safeForOutbound(String question){
        String input=question.toLowerCase(Locale.ROOT);
        for(int i=0;i<input.length();){
            char c=input.charAt(i);
            if(c==' '||c=='\t'||c=='\r'||c=='\n'||",，、;；:：。.!！?？()（）".indexOf(c)>=0){i++;continue;}
            int end=i;
            for(Word word:WORDS){if("女".equals(word.text)&&input.contains("男"))continue;if(input.startsWith(word.text,i)&&boundary(input,i,word)){end=i+word.text.length();break;}}
            if(end>i){i=end;continue;}
            Matcher age=AGE.matcher(input).region(i,input.length()).useTransparentBounds(true);
            if(age.lookingAt())try{
                int min=Integer.parseInt(age.group(1)),max=age.group(2)==null?120:Integer.parseInt(age.group(2));
                if(min>=0&&min<=max&&max<=120&&age.group(1).equals(Integer.toString(min))&&(age.group(2)==null||age.group(2).equals(Integer.toString(max)))){i=age.end();continue;}
            }catch(NumberFormatException invalid){return false;}
            for(String word:NEUTRAL)if(input.startsWith(word,i)){end=i+word.length();break;}
            if(end>i){i=end;continue;}
            if(input.startsWith("吸烟",i)){i+=2;continue;}
            return false;
        }
        return true;
    }
    private static void collect(Map<String,Object> candidate,Set<String> conflicts,String field,String value){
        if(conflicts.contains(field))return;
        String old=(String)candidate.get(field);
        if(old==null||old.equals(value)){candidate.put(field,value);return;}
        if("act".equals(field)){
            if(("high".equals(old)&&"mod-high".equals(value))||("remission".equals(old)&&"target".equals(value)))return;
            if(("mod-high".equals(old)&&"high".equals(value))||("target".equals(old)&&"remission".equals(value))){candidate.put(field,value);return;}
        }
        if("tx".equals(field) && !"csDMARD".equals(old) && !"csDMARD".equals(value)){
            if("bio".equals(old)){candidate.put(field,value);return;}
            if("bio".equals(value))return;
        }
        candidate.remove(field);conflicts.add(field);
    }

}
