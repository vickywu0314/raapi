package com.wenwen.ocr;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.wenwen.mapper.OcrMapper;
import com.wenwen.util.BizException;
import java.util.*;

class OcrLinkTest {
    static final String FILE="20261017/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg";
    OcrMapper mapper=mock(OcrMapper.class);
    OcrService service=new OcrService();
    OcrLinkTest(){ReflectionTestUtils.setField(service,"mapper",mapper);when(mapper.ownsPatient(anyLong(),eq(11L))).thenReturn(1);when(mapper.visitBelongsToPatient(100L,10L)).thenReturn(1);}
    Map<String,Object> record(Long patient,Long visit){Map<String,Object> r=new HashMap<>();r.put("doctorId",11L);r.put("patientId",patient);r.put("visitId",visit);return r;}
    @Test void cannotMoveImageAcrossPatients(){when(mapper.findRecord(FILE)).thenReturn(record(10L,100L));assertThrows(BizException.class,()->service.link(FILE,11L,20L,null));verify(mapper,never()).linkRecord(anyString(),anyLong(),anyLong(),any());}
    @Test void cannotMoveImageAcrossVisits(){when(mapper.findRecord(FILE)).thenReturn(record(10L,100L));when(mapper.visitBelongsToPatient(101L,10L)).thenReturn(1);assertThrows(BizException.class,()->service.link(FILE,11L,10L,101L));verify(mapper,never()).linkRecord(anyString(),anyLong(),anyLong(),any());}
    @Test void targetVisitMustActuallyBelongToPatient(){when(mapper.findRecord(FILE)).thenReturn(record(null,null));assertThrows(BizException.class,()->service.link(FILE,11L,20L,100L));verify(mapper,never()).linkRecord(anyString(),anyLong(),anyLong(),any());}
    @Test void zeroRowUpdateIsVisibleFailure(){when(mapper.findRecord(FILE)).thenReturn(record(null,null));assertThrows(BizException.class,()->service.link(FILE,11L,10L,100L));}
    @Test void legacyInconsistentPairCannotBeRetainedByNullVisitLink(){when(mapper.findRecord(FILE)).thenReturn(record(10L,999L));assertThrows(BizException.class,()->service.link(FILE,11L,10L,null));verify(mapper,never()).linkRecord(anyString(),anyLong(),anyLong(),any());}
}
