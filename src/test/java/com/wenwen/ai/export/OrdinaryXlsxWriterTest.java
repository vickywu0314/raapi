package com.wenwen.ai.export;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.math.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import static org.junit.jupiter.api.Assertions.*;
class OrdinaryXlsxWriterTest {
    @Test void independentDecimalGoldenSurvivesNumericPoiAndRawZipValues()throws Exception {
        ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);JsonNode inputs=json.readTree(getClass().getResourceAsStream("/ai-cohort/p04b/fixed-inputs.json")),oracles=json.readTree(getClass().getResourceAsStream("/ai-cohort/p04b/independent-oracles.json"));assertNotNull(inputs);JsonNode golden=oracles.path("quantileProbes").path("closeBigDecimal");assertEquals(6,golden.path("sorted").size());assertEquals(new BigDecimal(golden.path("sorted").get(0).asText()).doubleValue(),new BigDecimal(golden.path("sorted").get(5).asText()).doubleValue());
        List<BigDecimal> expected=new ArrayList<>();for(String key:Arrays.asList("q1","median","q3","iqr"))expected.add(new BigDecimal(golden.path(key).asText()));expected.add(oracles.path("precisionOrderControl").path("pValue").decimalValue());for(String metric:Arrays.asList("das28","crp"))expected.add(new BigDecimal(oracles.path("mainComparison").path("afterExcludingIntersection").path("continuous").path(metric).path("medianDifferenceAminusB").asText()));byte[] bytes;
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){Row row=book.createSheet("numeric").createRow(0);for(int i=0;i<expected.size();i++)OrdinaryXlsxWriter.cell(row.createCell(i),expected.get(i));OrdinaryXlsxWriter.cell(row.createCell(7),oracles.path("precisionOrderControl").path("displayP").asText());book.write(buffer);bytes=buffer.toByteArray();}
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){for(int i=0;i<expected.size();i++){XSSFCell cell=book.getSheetAt(0).getRow(0).getCell(i);assertEquals(CellType.NUMERIC,cell.getCellTypeEnum());assertEquals(0,expected.get(i).compareTo(new BigDecimal(cell.getRawValue())));assertTrue(Double.isFinite(cell.getNumericCellValue()));assertEquals(expected.get(i).doubleValue(),cell.getNumericCellValue());}assertEquals(CellType.STRING,book.getSheetAt(0).getRow(0).getCell(7).getCellTypeEnum());assertEquals("0.008",book.getSheetAt(0).getRow(0).getCell(7).getStringCellValue());}
        byte[] xml=zip(bytes,"xl/worksheets/sheet1.xml");DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);Document document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));NodeList cells=document.getElementsByTagName("c");for(int i=0;i<expected.size();i++){Element c=(Element)cells.item(i);assertEquals("n",c.getAttribute("t"));assertEquals(0,expected.get(i).compareTo(new BigDecimal(c.getElementsByTagName("v").item(0).getTextContent())));assertEquals(0,c.getElementsByTagName("f").getLength());}
    }
    static byte[] zip(byte[] bytes,String name)throws IOException{try(ZipInputStream stream=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=stream.getNextEntry())!=null)if(name.equals(entry.getName())){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=stream.read(b))>=0)out.write(b,0,n);return out.toByteArray();}}throw new AssertionError("缺ZIP项 "+name);}
    @Test void textFormulaCanariesStayStringsAndInvalidNumericRangeFails()throws Exception {
        String[] values={"=SUM(A1:A2)","+cmd","-123","@formula","9223372036854775807","2026-10-07","synthetic-drug-code"};byte[] bytes;
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){Row row=book.createSheet("text").createRow(0);for(int k=0;k<values.length;k++)OrdinaryXlsxWriter.cell(row.createCell(k),values[k]);OrdinaryXlsxWriter.cell(row.createCell(7),null);for(Number bad:new Number[]{new BigDecimal("1e400"),new BigDecimal("1e-400"),Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->OrdinaryXlsxWriter.cell(row.createCell(8),bad));OrdinaryXlsxWriter.cell(row.createCell(8),BigDecimal.ZERO);book.write(out);bytes=out.toByteArray();}
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){Row row=book.getSheetAt(0).getRow(0);for(int k=0;k<values.length;k++){assertEquals(CellType.STRING,row.getCell(k).getCellTypeEnum());assertEquals(values[k],row.getCell(k).getStringCellValue());}assertEquals(CellType.BLANK,row.getCell(7).getCellTypeEnum());assertEquals("0",((XSSFCell)row.getCell(8)).getRawValue());assertTrue(book.getExternalLinksTable().isEmpty());}
        assertFalse(new String(zip(bytes,"xl/worksheets/sheet1.xml"),"UTF-8").contains("<f"));
    }
}
