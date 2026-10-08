package com.wenwen.ai.result;
import com.wenwen.ai.query.CohortException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnalysisCursorTest {
    static final String KEY="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    static final String ID="12345678-1234-1234-1234-123456789abc";
    final AnalysisCursor cursors=new AnalysisCursor(new AnalysisSettings("900","8388608",KEY));
    String sign(String value)throws Exception {return sign(value.getBytes(StandardCharsets.UTF_8));}
    String sign(byte[] bytes)throws Exception {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY),"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(bytes));}
    String payload(String version){return "{\"v\":"+version+",\"analysisId\":\""+ID+"\",\"doctorId\":101,\"nextOffset\":10,\"sort\":\"DAS28_AT_DESC_NULL_LAST_ID_ASC\",\"expiresAtMs\":2000000000000}";}
    void invalid(String c){CohortException e=assertThrows(CohortException.class,()->cursors.verify(ID,c));assertEquals(400,e.getStatus());assertEquals("CURSOR_INVALID",e.getCode());}
    @Test void signedPayloadMustBeStrictUtf8WithoutAlternateEncodingAutodetection() throws Exception {
        String value=payload("1");
        AnalysisCursor.Token valid=cursors.verify(ID,sign(value.getBytes(StandardCharsets.UTF_8)));
        assertEquals(101,valid.owner);assertEquals(10,valid.offset);
        for(String encoding:new String[]{"UTF-16LE","UTF-16BE","UTF-32LE","UTF-32BE"})
            invalid(sign(value.getBytes(encoding)));
        byte[] prefix=value.substring(0,value.length()-1).getBytes(StandardCharsets.UTF_8);
        for(byte[] malformed:new byte[][]{{(byte)0xc3,0x28},{(byte)0xc0,(byte)0xaf},{(byte)0x80}}){
            byte[] bytes=Arrays.copyOf(prefix,prefix.length+malformed.length+1);
            System.arraycopy(malformed,0,bytes,prefix.length,malformed.length);bytes[bytes.length-1]='}';
            invalid(sign(bytes));
        }
    }
    @Test void signedVersionMustBeExactIntegerOneWithoutNarrowingOverflow() throws Exception {
        for(String version:new String[]{"4294967297","9223372036854775809","1.0","\"1\"","true","2","null"})invalid(sign(payload(version)));
        AnalysisCursor.Token valid=cursors.verify(ID,sign(payload("1")));assertEquals(10,valid.offset);assertEquals(101,valid.owner);
    }
    @Test void exactSignedJsonTypesKeysEncodingAndMacAreRequired() throws Exception {
        String valid=payload("1");
        for(String bad:Arrays.asList(valid+" {}",valid.replace("\"v\":1","\"v\":1,\"v\":1"),valid.replace("\"v\":1","\"v\":1,\"extra\":null"),valid.replace("\"v\":1,",""),valid.replace("123456789abc","123456789ABC"),valid.replace("\"doctorId\":101","\"doctorId\":\"101\""),valid.replace("\"doctorId\":101","\"doctorId\":-1"),valid.replace("\"doctorId\":101","\"doctorId\":9223372036854775808"),valid.replace("\"nextOffset\":10","\"nextOffset\":11"),valid.replace("\"nextOffset\":10","\"nextOffset\":0"),valid.replace("\"nextOffset\":10","\"nextOffset\":10.0"),valid.replace("\"nextOffset\":10","\"nextOffset\":2147483650"),valid.replace("DESC_NULL","ASC_NULL"),valid.replace("2000000000000","\"2000000000000\"")))invalid(sign(bad));
        String token=cursors.issue(ID,101,10,2000000000000L);String[] parts=token.split("\\.");assertEquals(valid,new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8));
        for(String bad:new String[]{"", "x",token+"=",parts[0]+".."+parts[1],"!"+token,token+String.join("",Collections.nCopies(2048,"a")),parts[0]+"."+parts[1].substring(1)})invalid(bad);
        for(String changed:Arrays.asList(valid.replace("\"v\":1","\"v\":2"),valid.replace(ID,"22345678-1234-1234-1234-123456789abc"),valid.replace("101","202"),valid.replace("\"nextOffset\":10","\"nextOffset\":30"),valid.replace("DESC_NULL","ASC_NULL"),valid.replace("2000000000000","2000000000001")))invalid(Base64.getUrlEncoder().withoutPadding().encodeToString(changed.getBytes(StandardCharsets.UTF_8))+"."+parts[1]);
        String modifiedMac=parts[1].charAt(0)=='A'?"B":"A";invalid(parts[0]+"."+modifiedMac+parts[1].substring(1));
        CohortException mismatch=assertThrows(CohortException.class,()->cursors.verify("22345678-1234-1234-1234-123456789abc",token));assertEquals("CURSOR_INVALID",mismatch.getCode());
    }
}
