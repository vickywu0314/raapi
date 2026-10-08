package com.wenwen.ai.result;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import java.math.*;
import java.time.*;
import java.util.*;
/** 比较v1独立allowlist及计数/数值语义校验；损坏不回源或补缺。 */
public final class ComparisonPayloadValidation {
    private static final String[] METRICS={"n","age","duration","das28At","crpAt","haqAt","female","sero","target","complete","activity","line"};
    private static final String TEST="method status pValue displayP significant nA nB reason warnings policyVersion testDenominator";
    private static final ObjectMapper JSON=new ObjectMapper();
    private ComparisonPayloadValidation(){ }
    public static void validate(Map<String,Object> payload){
        try{validateBody(payload);}catch(com.wenwen.ai.query.CohortException e){throw AnalysisSettings.unavailable();}catch(RuntimeException e){throw AnalysisSettings.unavailable();}
    }
    private static void validateBody(Map<String,Object> p){
        keys(p,"studyTotal a b overlapN excludeOverlap rows meta");int total=integer(p.get("studyTotal")),overlap=integer(p.get("overlapN"));boolean exclude=bool(p.get("excludeOverlap"));
        Map<String,Object> a=map(p.get("a")),b=map(p.get("b")),meta=map(p.get("meta"));int[] left=side(a,total,overlap,exclude),right=side(b,total,overlap,exclude);
        require((long)left[1]+right[1]-overlap<=total);
        keys(meta,"at asOfDate readStartedAt readCompletedAt computedAt expiresAt traceId policyVersions sourceDateCoverage completion statisticalDisclosure");
        String at=text(meta.get("at"));require(Arrays.asList("now","6m").contains(at));require(at.equals(map(a.get("filters")).get("at"))&&at.equals(map(b.get("filters")).get("at")));
        LocalDate asOf=date(meta.get("asOfDate"));Instant started=instant(meta.get("readStartedAt"));instant(meta.get("readCompletedAt"));Instant computed=instant(meta.get("computedAt")),expires=instant(meta.get("expiresAt"));require(expires.toEpochMilli()>computed.toEpochMilli());require(asOf.equals(started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()));uuid(meta.get("traceId"));
        require("P04_COMPARE".equals(meta.get("completion")));require("探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系".equals(meta.get("statisticalDisclosure")));
        Map<String,Object> versions=map(meta.get("policyVersions"));keys(versions,"statistics descriptive qc crp now clinical serology treatment visitMatcher drugDictionary comparison");
        String[] versionKeys={"statistics","descriptive","qc","crp","now","clinical","serology","treatment","visitMatcher","comparison"};String[] versionValues={"dev-stats-v04","dev-descriptive-v04","dev-missing-v04","dev-crp-v04","dev-now-v04","dev-clinical-v04","dev-ever-serology-v04","dev-timeline-v04","dev-visit-match-v04","dev-compare-v01"};for(int i=0;i<versionKeys.length;i++)require(versionValues[i].equals(versions.get(versionKeys[i])));require(!text(versions.get("drugDictionary")).isEmpty());
        Map<String,Object> coverage=map(meta.get("sourceDateCoverage"));keys(coverage,"a b");Map<String,Object> ca=map(coverage.get("a")),cb=map(coverage.get("b"));String metrics=String.join(" ",Arrays.copyOfRange(METRICS,1,METRICS.length));keys(ca,metrics);keys(cb,metrics);
        List<?> rows=list(p.get("rows"));require(rows.size()==12);
        for(int i=0;i<METRICS.length;i++){
            Map<String,Object> row=map(rows.get(i));keys(row,"metric kind a b difference test");String metric=METRICS[i];require(metric.equals(row.get("metric")));String kind=i==0?"COUNT":i<=5?"CONTINUOUS":i<=9?"BINARY":"DISTRIBUTION";require(kind.equals(row.get("kind")));
            Map<String,Object> x=map(row.get("a")),y=map(row.get("b"));
            if(i==0){keys(x,"n");keys(y,"n");require(integer(x.get("n"))==left[0]&&integer(y.get("n"))==right[0]);require(row.get("difference")==null&&row.get("test")==null);continue;}
            int nx,ny;long[][] table=null;String denominator;
            if(i<=5){nx=continuous(x,left[0]);ny=continuous(y,right[0]);sameDecimal(row.get("difference"),x.get("median")==null||y.get("median")==null?null:decimal(x.get("median")).subtract(decimal(y.get("median"))));denominator="NON_MISSING_METRIC";if("das28At".equals(metric))require(nx==left[2]&&ny==right[2]);if("crpAt".equals(metric)||"haqAt".equals(metric))require(nx<=left[2]&&ny<=right[2]);}
            else if(i<=9){nx=binary(x,left[0],left[2],metric);ny=binary(y,right[0],right[2],metric);sameDecimal(row.get("difference"),percentageDifference(x,y));table=new long[][]{{integer(x.get("numerator")),nx-integer(x.get("numerator"))},{integer(y.get("numerator")),ny-integer(y.get("numerator"))}};denominator="target".equals(metric)?"EVALUABLE_TARGET":"KNOWN_CATEGORY";}
            else{nx=distribution(x,left[0],left[2],metric);ny=distribution(y,right[0],right[2],metric);require(row.get("difference")==null);table=new long[][]{groupCounts(x),groupCounts(y)};denominator="activity".equals(metric)?"EVALUABLE_ACTIVITY":"KNOWN_LINE";}
            test(map(row.get("test")),nx,ny,denominator,overlap,exclude,table);
            coverage(map(ca.get(metric)),left[0],nx,asOf,metric);coverage(map(cb.get(metric)),right[0],ny,asOf,metric);
        }
        for(String sideName:Arrays.asList("a","b")){
            Map<String,Object> target=map(map(rows.get(8)).get(sideName));List<?> activities=list(map(map(rows.get(10)).get(sideName)).get("groups"));
            require(integer(target.get("numerator"))==(long)integer(map(activities.get(0)).get("count"))+integer(map(activities.get(1)).get("count")));
        }
        unicode(p);
    }
    private static int[] side(Map<String,Object> side,int total,int overlap,boolean exclude){
        keys(side,"source filters cohortId cohortVersion nBeforeExclusion n evalN submittedUniqueIdsN effectiveIdsN");int n=integer(side.get("n")),before=integer(side.get("nBeforeExclusion")),eval=integer(side.get("evalN"));require(eval<=n&&n<=before&&before<=total&&overlap<=before&&n==before-(exclude?overlap:0));
        String source=text(side.get("source"));if("FILTERS".equals(source))require(side.get("cohortId")==null&&side.get("cohortVersion")==null);else{require("SAVED".equals(source));uuid(side.get("cohortId"));require(positiveLong(side.get("cohortVersion"))>0);}
        Map<String,Object> filters=map(side.get("filters"));keys(filters,"studyCode at act ids sex age sero cm tx data");
        try{CohortQuery query=CohortQuery.read(new java.io.ByteArrayInputStream(JSON.writeValueAsBytes(Collections.singletonMap("filters",filters))),new TrustedDoctor(1));require(JSON.valueToTree(filters).equals(JSON.valueToTree(query.normalizedFilters())));if(query.getIds()==null)require(side.get("submittedUniqueIdsN")==null&&side.get("effectiveIdsN")==null);else{int submitted=integer(side.get("submittedUniqueIdsN")),effective=integer(side.get("effectiveIdsN"));require(submitted==query.getIds().size()&&effective<=submitted&&effective<=total&&before<=effective);}}
        catch(java.io.IOException e){throw AnalysisSettings.unavailable();}
        return new int[]{n,before,eval};
    }
    private static int continuous(Map<String,Object> side,int total){
        keys(side,"validN unknownN q1 median q3 iqr status");int n=integer(side.get("validN"));require((long)n+integer(side.get("unknownN"))==total);if(n==0){require("NO_DATA".equals(side.get("status")));for(String key:Arrays.asList("q1","median","q3","iqr"))require(side.get(key)==null);}
        else{require("OK".equals(side.get("status")));BigDecimal q1=decimal(side.get("q1")),median=decimal(side.get("median")),q3=decimal(side.get("q3")),iqr=decimal(side.get("iqr"));require(q1.signum()>=0&&q1.compareTo(median)<=0&&median.compareTo(q3)<=0&&q3.subtract(q1).compareTo(iqr)==0);if(n==1)require(q1.compareTo(q3)==0);}
        return n;
    }
    private static int binary(Map<String,Object> side,int n,int eval,String metric){
        keys(side,"numerator denominator knownN unknownN value status");int yes=integer(side.get("numerator")),denominator=integer(side.get("denominator")),known=integer(side.get("knownN"));require((long)known+integer(side.get("unknownN"))==n&&yes<=known&&known<=denominator&&denominator==("target".equals(metric)?eval:n));if("target".equals(metric))require(known==eval);if("complete".equals(metric))require(known==n);ratio(side.get("value"),yes,denominator);status(side,denominator);return known;
    }
    private static int distribution(Map<String,Object> side,int n,int eval,String metric){
        keys(side,"denominator knownN unknownN status groups");int known=integer(side.get("knownN")),denominator=integer(side.get("denominator"));require((long)known+integer(side.get("unknownN"))==n&&denominator==("activity".equals(metric)?eval:n));if("activity".equals(metric))require(known==eval);status(side,denominator);
        String[] categories="activity".equals(metric)?new String[]{"remission","low","moderate","high"}:new String[]{"1","2","3"};List<?> groups=list(side.get("groups"));require(groups.size()==categories.length);long sum=0;for(int i=0;i<categories.length;i++){Map<String,Object> group=map(groups.get(i));keys(group,"category count value");require(categories[i].equals(group.get("category")));int count=integer(group.get("count"));sum+=count;ratio(group.get("value"),count,denominator);}require(sum==known);return known;
    }
    private static void test(Map<String,Object> t,int nA,int nB,String denominator,int overlap,boolean exclude,long[][] table){
        keys(t,TEST+(table==null?"":" expectedBelow5Cells expectedCellCount"));require(integer(t.get("nA"))==nA&&integer(t.get("nB"))==nB);require(denominator.equals(t.get("testDenominator"))&&"dev-stats-v04".equals(t.get("policyVersion")));String status=text(t.get("status"));List<?> warnings=list(t.get("warnings"));Set<Object> unique=new HashSet<>();for(Object warning:warnings){require(unique.add(warning));require((table==null?"SMALL_SAMPLE_TIES":"SPARSE_EXPECTED_COUNTS").equals(warning));}
        String blocked=overlap>0&&!exclude?"OVERLAP":nA<5||nB<5?"INSUFFICIENT_SAMPLE":null;if(blocked!=null)require(blocked.equals(status));
        if(!"OK".equals(status)){
            require(Arrays.asList("OVERLAP","INSUFFICIENT_SAMPLE","DEGENERATE").contains(status));require(blocked!=null||"DEGENERATE".equals(status));String reason="OVERLAP".equals(status)?"COHORTS_OVERLAP":"INSUFFICIENT_SAMPLE".equals(status)?"VALID_N_LT_5":table==null?"ZERO_RANK_VARIANCE":"ZERO_MARGIN";require(reason.equals(t.get("reason"))&&t.get("method")==null&&t.get("pValue")==null&&t.get("significant")==null&&"—".equals(t.get("displayP"))&&warnings.isEmpty());if(table!=null){require(t.get("expectedBelow5Cells")==null&&t.get("expectedCellCount")==null);if("DEGENERATE".equals(status))require(nonzeroColumns(table)<2);}return;
        }
        require(blocked==null&&t.get("reason")==null);String method=text(t.get("method"));BigDecimal p=decimal(t.get("pValue"));require(p.signum()>=0&&p.compareTo(BigDecimal.ONE)<=0);require(bool(t.get("significant"))==(p.compareTo(new BigDecimal(".05"))<0));String display=p.compareTo(new BigDecimal(".001"))<0?"<0.001":p.setScale(3,RoundingMode.HALF_UP).toPlainString();require(display.equals(t.get("displayP")));
        if(table==null){require(Arrays.asList("MANN_WHITNEY_EXACT","MANN_WHITNEY_ASYMPTOTIC").contains(method));if("MANN_WHITNEY_EXACT".equals(method))require(Math.min(nA,nB)<=8&&Math.max(nA,nB)<=50&&warnings.isEmpty());if(!warnings.isEmpty())require(Math.min(nA,nB)<10);}
        else{int columns=nonzeroColumns(table);require(columns>=2);int cells=columns*2,below=0;long total=(long)nA+nB;BigInteger five=BigInteger.valueOf(total).multiply(BigInteger.valueOf(5));for(int c=0;c<table[0].length;c++){long sum=table[0][c]+table[1][c];if(sum==0)continue;for(int n:new int[]{nA,nB})if(BigInteger.valueOf(n).multiply(BigInteger.valueOf(sum)).compareTo(five)<0)below++;}require(integer(t.get("expectedBelow5Cells"))==below&&integer(t.get("expectedCellCount"))==cells);boolean fisher=columns==2&&below>0;require((fisher?"FISHER_EXACT":"CHI_SQUARE").equals(method));require((!fisher&&cells>4&&below/(double)cells>.2)==!warnings.isEmpty());}
    }
    private static void coverage(Map<String,Object> c,int n,int valid,LocalDate asOf,String metric){
        keys(c,"validN datedN undatedN missingN minDate maxDate status");int dated=integer(c.get("datedN")),undated=integer(c.get("undatedN"));require(integer(c.get("validN"))==valid&&(long)dated+undated==valid&&(long)valid+integer(c.get("missingN"))==n);String status=valid==0?"NO_DATA":dated==0?"UNKNOWN":undated==0?"KNOWN":"PARTIAL";require(status.equals(c.get("status")));if(dated==0)require(c.get("minDate")==null&&c.get("maxDate")==null);else{LocalDate min=date(c.get("minDate")),max=date(c.get("maxDate"));require(!min.isAfter(max)&&!max.isAfter(asOf));}if(!Arrays.asList("das28At","crpAt","haqAt","target","activity").contains(metric))require(dated==0);
    }
    private static long[] groupCounts(Map<String,Object> side){List<?> groups=list(side.get("groups"));long[] counts=new long[groups.size()];for(int i=0;i<counts.length;i++)counts[i]=integer(map(groups.get(i)).get("count"));return counts;}
    private static int nonzeroColumns(long[][] table){int n=0;for(int i=0;i<table[0].length;i++)if(table[0][i]+table[1][i]>0)n++;return n;}
    private static BigDecimal percentageDifference(Map<String,Object> a,Map<String,Object> b){int nx=integer(a.get("denominator")),ny=integer(b.get("denominator"));if(nx==0||ny==0)return null;return BigDecimal.valueOf(integer(a.get("numerator"))).multiply(BigDecimal.valueOf(ny)).subtract(BigDecimal.valueOf(integer(b.get("numerator"))).multiply(BigDecimal.valueOf(nx))).multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(nx).multiply(BigDecimal.valueOf(ny)),20,RoundingMode.HALF_UP);}
    private static void ratio(Object value,int n,int d){BigDecimal expected=d==0?BigDecimal.ZERO:BigDecimal.valueOf(n).divide(BigDecimal.valueOf(d),16,RoundingMode.HALF_UP);require(decimal(value).compareTo(expected)==0);}
    private static void status(Map<String,Object> side,int denominator){require((denominator==0?"NO_DATA":"OK").equals(side.get("status")));}
    private static void sameDecimal(Object actual,BigDecimal expected){if(expected==null)require(actual==null);else require(decimal(actual).compareTo(expected)==0);}
    private static BigDecimal decimal(Object value){require(value instanceof Number);if(value instanceof Double)require(Double.isFinite((Double)value));if(value instanceof Float)require(Float.isFinite((Float)value));return value instanceof BigDecimal?(BigDecimal)value:new BigDecimal(value.toString());}
    private static int integer(Object value){long v=integral(value);require(v>=0&&v<=Integer.MAX_VALUE);return (int)v;}
    private static long positiveLong(Object value){long v=integral(value);require(v>0);return v;}
    private static long integral(Object value){require(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long||value instanceof BigInteger);return new BigInteger(value.toString()).longValueExact();}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){require(value instanceof Map);return (Map<String,Object>)value;}
    private static List<?> list(Object value){require(value instanceof List);return (List<?>)value;}
    private static String text(Object value){require(value instanceof String);return (String)value;}
    private static boolean bool(Object value){require(value instanceof Boolean);return (Boolean)value;}
    private static LocalDate date(Object value){String s=text(value);LocalDate d=LocalDate.parse(s);require(d.toString().equals(s));return d;}
    private static Instant instant(Object value){String s=text(value);Instant d=Instant.parse(s);require(d.toString().equals(s));return d;}
    private static void uuid(Object value){require(AnalysisCursor.canonicalId(text(value)));}
    private static void keys(Map<String,Object> map,String keys){require(map.keySet().equals(new HashSet<>(Arrays.asList(keys.split(" ")))));}
    private static void require(boolean valid){if(!valid)throw AnalysisSettings.unavailable();}
    private static void unicode(Object value){if(value instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()){unicode(entry.getKey());unicode(entry.getValue());}else if(value instanceof Collection)for(Object item:(Collection<?>)value)unicode(item);else if(value instanceof String){String text=(String)value;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){require(++i<text.length()&&Character.isLowSurrogate(text.charAt(i)));}else require(!Character.isLowSurrogate(c));}}}
}
