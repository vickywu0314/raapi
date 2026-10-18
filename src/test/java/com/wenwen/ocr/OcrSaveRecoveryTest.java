package com.wenwen.ocr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.auth.AuthService;
import com.wenwen.controller.ra.VisitController;
import com.wenwen.controller.ra.PatientController;
import com.wenwen.controller.ra.OcrController;
import com.wenwen.mapper.OcrMapper;
import com.wenwen.service.VisitService;
import com.wenwen.service.PatientWriteService;
import com.wenwen.vo.*;

/** 存储边界合成故障；实际Controller/Service/JSON，不证明真实MySQL事务。 */
class OcrSaveRecoveryTest {
    final String file=OcrLinkTest.FILE;
    final OcrMapper mapper=mock(OcrMapper.class);
    final OcrService ocr=new OcrService();
    final VisitService writer=mock(VisitService.class);
    final MockHttpSession session=new MockHttpSession();
    final Map<String,Object> record=new HashMap<>();
    final MockMvc mvc;
    OcrSaveRecoveryTest(){
        ReflectionTestUtils.setField(ocr,"mapper",mapper);session.setAttribute(AuthService.SESSION_DOCTOR,11L);
        record.put("doctorId",11L); when(mapper.findRecord(file)).thenAnswer(x->new HashMap<>(record));
        when(mapper.ownsPatient(10L,11L)).thenReturn(1);when(mapper.visitBelongsToPatient(100L,10L)).thenReturn(1);
        VisitCreateResultVo saved=new VisitCreateResultVo();saved.setPatientId(10L);saved.setVisitId(100L);saved.setFilledCount(1);
        when(writer.createVisit(any())).thenReturn(saved);
        VisitController c=new VisitController();ReflectionTestUtils.setField(c,"visitService",writer);ReflectionTestUtils.setField(c,"ocrService",ocr);
        OcrController o=new OcrController();ReflectionTestUtils.setField(o,"ocrService",ocr);mvc=MockMvcBuilders.standaloneSetup(c,o).build();
    }
    JSONObject create(boolean auth) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r=post("/api/ra/visit/createVisit").contentType(MediaType.APPLICATION_JSON).content("{\"doctorId\":22,\"patientId\":10,\"visitDate\":\"2026-10-01\",\"ocrFileIds\":[\""+file+"\"]}");
        if(auth)r.session(session);return JSONObject.parseObject(mvc.perform(r).andReturn().getResponse().getContentAsString());
    }
    JSONObject retry() throws Exception {return JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/linkImages").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":10,\"visitId\":100,\"ocrFileIds\":[\""+file+"\"]}")).andReturn().getResponse().getContentAsString());}
    @Test void rejectsAnonymousAndKnownOwnershipConflictBeforeClinicalWrite() throws Exception {
        assertEquals("401",create(false).getString("code")); verifyZeroInteractions(writer);
        record.put("doctorId",22L); assertEquals("403",create(true).getString("code")); verifyZeroInteractions(writer);
        record.put("doctorId",11L);record.put("patientId",20L);assertEquals("409",create(true).getString("code"));verifyZeroInteractions(writer);
        record.put("patientId",10L);record.put("visitId",99L);assertEquals("409",create(true).getString("code"));verifyZeroInteractions(writer);
    }
    @Test void savedClinicalIdSurvivesLinkFailureAndRetryNeverRecreates() throws Exception {
        AtomicInteger calls=new AtomicInteger();when(mapper.linkRecord(file,11L,10L,100L)).thenAnswer(x->{if(calls.getAndIncrement()==0)throw new DataAccessResourceFailureException("synthetic link outage");record.put("patientId",10L);record.put("visitId",100L);return 1;});
        JSONObject saved=create(true);assertTrue(saved.getBooleanValue("success"));JSONObject data=saved.getJSONObject("data");assertEquals("100",data.getString("visitId"));assertEquals("10",data.getString("patientId"));assertEquals("PENDING",data.getJSONObject("ocrLink").getString("status"));assertEquals(Collections.singletonList(file),data.getJSONObject("ocrLink").getJSONArray("pendingFileIds").toJavaList(String.class));
        assertEquals("LINKED",retry().getJSONObject("data").getString("status"));assertEquals("LINKED",retry().getJSONObject("data").getString("status"));
        verify(writer,times(1)).createVisit(argThat(r->Long.valueOf(11).equals(r.getDoctorId())));assertEquals(100L,record.get("visitId"));
    }
    @Test void acknowledgedLostAfterUpdateIsPendingThenSameTargetIdempotent() throws Exception {
        AtomicInteger calls=new AtomicInteger();when(mapper.linkRecord(file,11L,10L,100L)).thenAnswer(x->{record.put("patientId",10L);record.put("visitId",100L);if(calls.getAndIncrement()==0)throw new DataAccessResourceFailureException("synthetic commit acknowledgement lost");return 0;});
        assertEquals("PENDING",create(true).getJSONObject("data").getJSONObject("ocrLink").getString("status"));
        assertEquals(100L,record.get("visitId"));assertEquals("LINKED",retry().getJSONObject("data").getString("status"));verify(writer,times(1)).createVisit(any());
    }
    @Test void noOcrNormalSessionPathAndPatientIdentityCompatibility() throws Exception {
        JSONObject visit=JSONObject.parseObject(mvc.perform(post("/api/ra/visit/createVisit").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"doctorId\":22,\"patientId\":10}")).andReturn().getResponse().getContentAsString());
        assertTrue(visit.getBooleanValue("success"));assertEquals("NOT_REQUESTED",visit.getJSONObject("data").getJSONObject("ocrLink").getString("status"));verify(writer).createVisit(argThat(r->r.getDoctorId()==11L));
        PatientWriteService patients=mock(PatientWriteService.class);PatientCreateResultVo result=new PatientCreateResultVo();result.setPatientId(9007199254740993L);when(patients.createPatient(any())).thenReturn(result);
        PatientController c=new PatientController();ReflectionTestUtils.setField(c,"patientWriteService",patients);ReflectionTestUtils.setField(c,"ocrService",ocr);MockMvc pm=MockMvcBuilders.standaloneSetup(c).build();
        JSONObject p=JSONObject.parseObject(pm.perform(post("/api/ra/patient/createPatient").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"doctorId\":22}")).andReturn().getResponse().getContentAsString());assertTrue(p.getBooleanValue("success"));assertEquals("9007199254740993",p.getJSONObject("data").get("patientId"));verify(patients).createPatient(argThat(r->r.getDoctorId()==11L));
    }
    @Test void fileSetIsBoundedDeduplicatedAndStrict() {
        assertEquals(Collections.singletonList(file),OcrService.fileIds(file,Arrays.asList(file,file)));assertThrows(RuntimeException.class,()->OcrService.fileIds("../unsafe",null));assertThrows(RuntimeException.class,()->OcrService.fileIds(null,Collections.nCopies(21,file)));
    }

    @Test void mixedPermanentConflictDoesNotBlockRecoverableFileAfterClinicalSave() throws Exception {
        final String second="20261017/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.jpg";
        Map<String,Object> secondRecord=new HashMap<>();secondRecord.put("doctorId",11L);when(mapper.findRecord(second)).thenAnswer(x->new HashMap<>(secondRecord));
        VisitCreateResultVo saved=new VisitCreateResultVo();saved.setPatientId(10L);saved.setVisitId(100L);
        when(writer.createVisit(any())).thenAnswer(x->{record.put("patientId",20L);record.put("visitId",200L);return saved;});
        AtomicInteger calls=new AtomicInteger();when(mapper.linkRecord(second,11L,10L,100L)).thenAnswer(x->{if(calls.getAndIncrement()==0)throw new DataAccessResourceFailureException("synthetic transient failure of second image");secondRecord.put("patientId",10L);secondRecord.put("visitId",100L);return 1;});
        String body="{\"patientId\":10,\"visitDate\":\"2026-10-01\",\"ocrFileIds\":[\""+file+"\",\""+second+"\"]}";
        JSONObject created=JSONObject.parseObject(mvc.perform(post("/api/ra/visit/createVisit").session(session).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString());
        assertTrue(created.getBooleanValue("success"));JSONObject link=created.getJSONObject("data").getJSONObject("ocrLink");
        assertTrue(link.getBooleanValue("retryable"),"one permanent conflict must not disable another recoverable file");
        assertEquals(Arrays.asList(file,second),link.getJSONArray("pendingFileIds").toJavaList(String.class));assertEquals(Collections.singletonList(second),link.getJSONArray("retryableFileIds").toJavaList(String.class));
        JSONObject retried=JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/linkImages").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":10,\"visitId\":100,\"ocrFileIds\":[\""+file+"\",\""+second+"\"]}")).andReturn().getResponse().getContentAsString());
        assertTrue(retried.getBooleanValue("success"),"per-file conflict must not reject the entire recovery request");link=retried.getJSONObject("data");assertEquals("PENDING",link.getString("status"));assertFalse(link.getBooleanValue("retryable"));assertEquals(Collections.singletonList(file),link.getJSONArray("pendingFileIds").toJavaList(String.class));assertEquals(Collections.singletonList(second),link.getJSONArray("linkedFileIds").toJavaList(String.class));assertTrue(link.getJSONArray("retryableFileIds").isEmpty());
        assertEquals(20L,record.get("patientId"));assertEquals(200L,record.get("visitId"));assertEquals(100L,secondRecord.get("visitId"));verify(writer,times(1)).createVisit(any());verify(mapper,never()).linkRecord(eq(file),anyLong(),anyLong(),any());
    }
    @Test void recoveryStillRejectsMissingSessionAndGlobalTargetBeforeAnyFileUpdate() throws Exception {
        String body="{\"patientId\":10,\"visitId\":100,\"ocrFileIds\":[\""+file+"\"]}";
        assertEquals("401",JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/linkImages").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString()).getString("code"));
        when(mapper.ownsPatient(10L,11L)).thenReturn(0);
        assertEquals("403",JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/linkImages").session(session).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString()).getString("code"));verify(mapper,never()).linkRecord(anyString(),anyLong(),anyLong(),any());verifyZeroInteractions(writer);
    }
}
