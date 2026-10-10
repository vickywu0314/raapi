package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

/** 只读实际下载的OOXML；独立于生产writer、policy与统计期望。 */
public final class WebExportWorkbookInspector {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("input.xlsx output.json");
        Path input=Paths.get(args[0]).toRealPath(),output=Paths.get(args[1]).toAbsolutePath().normalize();
        if(!input.getParent().equals(output.getParent())||!"workbook-observation.json".equals(output.getFileName().toString()))throw new IllegalArgumentException("必须在本run目录内输出准确观察文件");
        byte[] bytes=Files.readAllBytes(input);List<String> entries=new ArrayList<>();
        try(ZipFile zip=new ZipFile(input.toFile())) {
            Enumeration<? extends ZipEntry> all=zip.entries();
            while(all.hasMoreElements()){
                String name=all.nextElement().getName();entries.add(name);
                if(name.toLowerCase(Locale.ROOT).contains("vba")||name.startsWith("xl/externalLinks/"))throw new AssertionError("宏或外链");
            }
        }
        Map<String,Object> report=new LinkedHashMap<>();report.put("filename",input.getFileName().toString());report.put("bytes",bytes.length);
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",b&255));report.put("sha256",hash.toString());report.put("zipEntries",entries);
        List<String> names=new ArrayList<>();List<Map<String,Object>> sheets=new ArrayList<>();Map<String,Object> metadata=new LinkedHashMap<>();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if(!book.getExternalLinksTable().isEmpty())throw new AssertionError("外链");
            for(int i=0;i<book.getNumberOfSheets();i++) {
                if(book.isSheetHidden(i)||book.isSheetVeryHidden(i))throw new AssertionError("隐藏sheet");
                XSSFSheet sheet=book.getSheetAt(i);names.add(sheet.getSheetName());List<Map<String,Object>> cells=new ArrayList<>();
                for(Row row:sheet)for(Cell cell:row){
                    if(sheet.isColumnHidden(cell.getColumnIndex()))throw new AssertionError("隐藏列");
                    CellType type=cell.getCellTypeEnum();if(type==CellType.FORMULA)throw new AssertionError("FORMULA");
                    Map<String,Object> observed=new LinkedHashMap<>();observed.put("row",cell.getRowIndex());observed.put("column",cell.getColumnIndex());observed.put("type",type.name());observed.put("raw",((XSSFCell)cell).getRawValue());observed.put("string",type==CellType.STRING?cell.getStringCellValue():null);cells.add(observed);
                }
                Map<String,Object> observed=new LinkedHashMap<>();observed.put("name",sheet.getSheetName());observed.put("rows",sheet.getPhysicalNumberOfRows());observed.put("cells",cells);sheets.add(observed);
                if("metadata".equals(sheet.getSheetName()))for(Row row:sheet){Cell key=row.getCell(0),value=row.getCell(1);if(key!=null&&key.getCellTypeEnum()==CellType.STRING&&value!=null)metadata.put(key.getStringCellValue(),value.getCellTypeEnum()==CellType.STRING?value.getStringCellValue():((XSSFCell)value).getRawValue());}
            }
        }
        report.put("sheetNames",names);report.put("sheets",sheets);report.put("metadata",metadata);
        Files.write(output,(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("WORKBOOK_INSPECTED "+input.getFileName()+" "+hash+" "+bytes.length);
    }
}
