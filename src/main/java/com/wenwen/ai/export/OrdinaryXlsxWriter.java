package com.wenwen.ai.export;
import java.util.*;
import java.time.*;
import java.math.*;
import java.io.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;
import org.springframework.stereotype.Component;
import com.wenwen.ai.query.CohortException;
/** 请求私有的内存Workbook；不从原源加载显示或隐藏标识。 */
@Component public class OrdinaryXlsxWriter {
    @org.springframework.beans.factory.annotation.Value("${ra.export.xlsx.max-rows:100000}") private int maxRows=100000;
    @org.springframework.beans.factory.annotation.Value("${ra.export.xlsx.max-cells:1000000}") private int maxCells=1000000;
    public void validateLimits(){if(maxRows<=0||maxRows>1000000||maxCells<=0||maxCells>10000000)throw new CohortException(503,"SERVICE_UNAVAILABLE","导出暂不可用");}
    public static final class File {public final byte[] bytes;public final int rows;File(byte[] b,int n){bytes=b;rows=n;}}
    public File write(AiExportQuery q,ExportContentReader.Content content,ExportFieldPolicy.Window window,Map<String,Object> metadata,int maxBytes)throws IOException {
        validateLimits();long rows=((List<?>)content.payload.get("rows")).size(),columns="COMPARE".equals(q.source)?COMPARISON_COLUMNS.size():q.requested.size();
        long upperRows="COMPARE".equals(q.source)?comparisonRowCount(q,content.payload):rows;
        if(upperRows>maxRows||(upperRows+1)*columns+(metadata.size()+1)*2>maxCells)throw new CohortException(413,"RESULT_TOO_LARGE","导出行数或单元格数超出限制");
        try(XSSFWorkbook book=createWorkbook();BoundedBytes bytes=new BoundedBytes(maxBytes)){
            int index;
            if("COMPARE".equals(q.source))index=comparison(book,q,content.payload)+1;
            else{Sheet sheet=book.createSheet("cohort");Row header=sheet.createRow(0);for(int i=0;i<q.requested.size();i++)cell(header.createCell(i),title(q.requested.get(i),content.payload));index=1;for(Object item:(List<?>)content.payload.get("rows")){interrupted();Map<String,Object> row=map(item);Row output=sheet.createRow(index++);for(int i=0;i<q.requested.size();i++)cell(output.createCell(i),value(row,q.requested.get(i)));}}
            Sheet meta=book.createSheet("metadata");int n=0;for(Map.Entry<String,Object> entry:metadata.entrySet()){Row r=meta.createRow(n++);cell(r.createCell(0),entry.getKey());cell(r.createCell(1),entry.getValue());}Row r=meta.createRow(n);cell(r.createCell(0),"rowN");cell(r.createCell(1),index-1);interrupted();try{book.write(bytes);}catch(IOException|RuntimeException failure){if(bytes.exceeded)throw new CohortException(413,"RESULT_TOO_LARGE","导出文件超出限制");throw failure;}interrupted();return new File(bytes.toByteArray(),index-1);
        }
    }
    private static long comparisonRowCount(AiExportQuery q,Map<String,Object> payload){long rows=0;for(Object item:(List<?>)payload.get("rows")){Map<String,Object> row=map(item);if(q.requested.contains(row.get("metric")))rows+="DISTRIBUTION".equals(row.get("kind"))?((List<?>)map(row.get("a")).get("groups")).size():1;}return rows;}
    protected XSSFWorkbook createWorkbook(){return new XSSFWorkbook();}
    private static final List<String> COMPARISON_COLUMNS=Collections.unmodifiableList(Arrays.asList("metric","kind","category","aN","bN","aValidN","bValidN","aUnknownN","bUnknownN","aDenominator","bDenominator","aKnownN","bKnownN","aNumerator","bNumerator","aQ1","aMedian","aQ3","aIqr","bQ1","bMedian","bQ3","bIqr","aValue","bValue","aCount","bCount","difference","pValue","displayP","method","testStatus","reason","nA","nB","testDenominator","warnings"));
    private static int comparison(XSSFWorkbook book,AiExportQuery q,Map<String,Object> payload){Sheet sheet=book.createSheet("comparison");Row header=sheet.createRow(0);for(int i=0;i<COMPARISON_COLUMNS.size();i++)cell(header.createCell(i),COMPARISON_COLUMNS.get(i));int index=1;for(Object item:(List<?>)payload.get("rows")){interrupted();Map<String,Object> r=map(item);if(!q.requested.contains(r.get("metric")))continue;boolean distribution="DISTRIBUTION".equals(r.get("kind"));int count=distribution?((List<?>)map(r.get("a")).get("groups")).size():1;for(int k=0;k<count;k++){Map<String,Object> values=new LinkedHashMap<>();values.put("metric",r.get("metric"));values.put("kind",r.get("kind"));for(String side:Arrays.asList("a","b")){Map<String,Object> group=map(r.get(side));List<String> fields="COUNT".equals(r.get("kind"))?Arrays.asList("n"):"CONTINUOUS".equals(r.get("kind"))?Arrays.asList("validN","unknownN","q1","median","q3","iqr"):distribution?Arrays.asList("denominator","knownN","unknownN"):Arrays.asList("numerator","denominator","knownN","unknownN","value");for(String f:fields)values.put(side+Character.toUpperCase(f.charAt(0))+f.substring(1),group.get(f));if(distribution){Map<String,Object> bucket=map(((List<?>)group.get("groups")).get(k));values.put("category",bucket.get("category"));values.put(side+"Value",bucket.get("value"));values.put(side+"Count",bucket.get("count"));}}
                values.put("difference",r.get("difference"));if(k==0&&r.get("test")!=null){Map<String,Object> test=map(r.get("test"));for(String field:Arrays.asList("pValue","displayP","method","reason","nA","nB","testDenominator"))values.put(field,test.get(field));values.put("testStatus",test.get("status"));values.put("warnings",String.join(",",(List<String>)test.get("warnings")));}Row row=sheet.createRow(index++);for(int i=0;i<COMPARISON_COLUMNS.size();i++)cell(row.createCell(i),values.get(COMPARISON_COLUMNS.get(i)));}}return index-1;}
    static void interrupted(){if(Thread.currentThread().isInterrupted())throw new CohortException(503,"SERVICE_UNAVAILABLE","导出暂不可用");}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    private static String title(String field,Map<String,Object> payload){return "das28At".equals(field)?"评估 DAS28（"+map(payload.get("meta")).get("at")+"）":field;}
    private static Object value(Map<String,Object> row,String field){if("crpCurrent".equals(field))return map(row.get(field)).get("value");return row.get(field);}
    /** HTTP和局部精度/文本验收共用的真实写格路径。 */
    public static void cell(Cell cell,Object value){if(value==null){cell.setCellType(CellType.BLANK);return;}if(value instanceof Number){BigDecimal decimal=new BigDecimal(value.toString());double approximation=decimal.doubleValue();if(!Double.isFinite(approximation)||(approximation==0&&decimal.signum()!=0))throw new IllegalArgumentException("导出数字不可表示");XSSFCell x=(XSSFCell)cell;x.getCTCell().setT(STCellType.N);x.getCTCell().setV(decimal.signum()==0?"0":decimal.stripTrailingZeros().toPlainString());return;}cell.setCellValue(value.toString());cell.setCellType(CellType.STRING);}
    private static final class BoundedBytes extends ByteArrayOutputStream {private final int max;boolean exceeded;BoundedBytes(int m){max=m;}private void check(int n){if(n>max-count){exceeded=true;throw new CohortException(413,"RESULT_TOO_LARGE","导出文件超出限制");}}@Override public synchronized void write(int b){check(1);super.write(b);}@Override public synchronized void write(byte[] b,int off,int len){check(len);super.write(b,off,len);}}
}
