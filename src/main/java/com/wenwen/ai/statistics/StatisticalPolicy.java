package com.wenwen.ai.statistics;

import java.math.*;
import java.util.*;
import org.apache.commons.statistics.inference.*;
import static com.wenwen.vo.AiCohortVo.object;

/** 不可变开发默认；库异常向实际Controller传播，不伪装业务缺失。 */
public final class StatisticalPolicy {
    public static final String VERSION="dev-stats-v04";
    private StatisticalPolicy() { }
    public static Map<String,Object> result(String method,double p,Integer nA,Integer nB,String denominator,List<String> warnings) {
        if(!Double.isFinite(p)||p<0||p>1)throw new IllegalStateException("无效统计概率");
        return object("method",method,"status","OK","pValue",p,"displayP",p<.001?"<0.001":BigDecimal.valueOf(p).setScale(3,RoundingMode.HALF_UP).toPlainString(),
            "significant",p<.05,"nA",nA,"nB",nB,"reason",null,"warnings",Collections.unmodifiableList(new ArrayList<>(warnings)),"policyVersion",VERSION,"testDenominator",denominator);
    }
    public static Map<String,Object> unavailable(String status,String reason,Integer nA,Integer nB,String denominator) {
        return object("method",null,"status",status,"pValue",null,"displayP","—","significant",null,"nA",nA,"nB",nB,"reason",reason,
            "warnings",Collections.emptyList(),"policyVersion",VERSION,"testDenominator",denominator);
    }
    public static Map<String,Object> mannWhitney(List<BigDecimal> a,List<BigDecimal> b,String denominator) {
        if(a.size()<5||b.size()<5)return unavailable("INSUFFICIENT_SAMPLE","VALID_N_LT_5",a.size(),b.size(),denominator);
        // compareTo既保全极近小数的秩，又把不同scale的相等数视为真实ties。
        SortedMap<BigDecimal,Double> codes=new TreeMap<>();for(BigDecimal value:a)codes.put(value,null);for(BigDecimal value:b)codes.put(value,null);
        if(codes.size()==1)return unavailable("DEGENERATE","ZERO_RANK_VARIANCE",a.size(),b.size(),denominator);
        int rank=0;for(Map.Entry<BigDecimal,Double> entry:codes.entrySet())entry.setValue((double)++rank);
        double[] x=a.stream().mapToDouble(codes::get).toArray(),y=b.stream().mapToDouble(codes::get).toArray();
        boolean ties=codes.size()<a.size()+b.size();
        boolean exact=!ties&&Math.min(a.size(),b.size())<=8&&Math.max(a.size(),b.size())<=50;
        double p=MannWhitneyUTest.withDefaults().with(AlternativeHypothesis.TWO_SIDED).with(exact?PValueMethod.EXACT:PValueMethod.ASYMPTOTIC).with(ContinuityCorrection.ENABLED).test(x,y).getPValue();
        return result(exact?"MANN_WHITNEY_EXACT":"MANN_WHITNEY_ASYMPTOTIC",p,a.size(),b.size(),denominator,ties&&(a.size()<10||b.size()<10)?Collections.singletonList("SMALL_SAMPLE_TIES"):Collections.emptyList());
    }
    public static Map<String,Object> contingency(long[][] table,Integer nA,Integer nB,String denominator) {
        if(table.length==0||table[0]==null||table[0].length==0)throw new IllegalArgumentException("无效列联表形状");
        for(long[] row:table){if(row==null||row.length!=table[0].length)throw new IllegalArgumentException("无效列联表形状");for(long value:row)if(value<0)throw new IllegalArgumentException("无效列联表频数");}
        long total=0;long[] rows=new long[table.length],cols=new long[table[0].length];
        for(int r=0;r<table.length;r++)for(int c=0;c<cols.length;c++){rows[r]=Math.addExact(rows[r],table[r][c]);cols[c]=Math.addExact(cols[c],table[r][c]);total=Math.addExact(total,table[r][c]);}
        if(total==0||Arrays.stream(rows).filter(n->n>0).count()<2||Arrays.stream(cols).filter(n->n>0).count()<2) {
            Map<String,Object> test=new LinkedHashMap<>(unavailable("DEGENERATE","ZERO_MARGIN",nA,nB,denominator));
            test.put("expectedBelow5Cells",null);test.put("expectedCellCount",null);return Collections.unmodifiableMap(test);
        }
        int rowCount=(int)Arrays.stream(rows).filter(n->n>0).count(),colCount=(int)Arrays.stream(cols).filter(n->n>0).count();
        long[][] pruned=new long[rowCount][colCount];int rr=0;
        for(int r=0;r<rows.length;r++)if(rows[r]>0){int cc=0;for(int c=0;c<cols.length;c++)if(cols[c]>0)pruned[rr][cc++]=table[r][c];rr++;}
        table=pruned;rows=Arrays.stream(rows).filter(n->n>0).toArray();cols=Arrays.stream(cols).filter(n->n>0).toArray();
        if(Arrays.stream(rows).anyMatch(n->n<5)) {
            Map<String,Object> test=new LinkedHashMap<>(unavailable("INSUFFICIENT_SAMPLE","VALID_N_LT_5",nA,nB,denominator));
            test.put("expectedBelow5Cells",null);test.put("expectedCellCount",null);return Collections.unmodifiableMap(test);
        }
        int below=0,cells=table.length*cols.length;
        // 只比较数学期望与5的大小：整数交叉乘避免除法误差和long乘法溢出。
        BigInteger fiveTotal=BigInteger.valueOf(total).multiply(BigInteger.valueOf(5));
        for(long row:rows)for(long col:cols)if(BigInteger.valueOf(row).multiply(BigInteger.valueOf(col)).compareTo(fiveTotal)<0)below++;
        boolean fisher=table.length==2&&cols.length==2&&below>0;double p;
        if(fisher) {
            int[][] counts={{Math.toIntExact(table[0][0]),Math.toIntExact(table[0][1])},{Math.toIntExact(table[1][0]),Math.toIntExact(table[1][1])}};
            p=FisherExactTest.withDefaults().with(AlternativeHypothesis.TWO_SIDED).test(counts).getPValue();
        } else p=ChiSquareTest.withDefaults().test(table).getPValue();
        Map<String,Object> test=new LinkedHashMap<>(result(fisher?"FISHER_EXACT":"CHI_SQUARE",p,nA,nB,denominator,!fisher&&cells>4&&below/(double)cells>.2?Collections.singletonList("SPARSE_EXPECTED_COUNTS"):Collections.emptyList()));
        test.put("expectedBelow5Cells",below);test.put("expectedCellCount",cells);return Collections.unmodifiableMap(test);
    }
}
