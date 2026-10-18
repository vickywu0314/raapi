package com.wenwen.ocr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.dao.DataAccessResourceFailureException;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.auth.AuthService;
import com.wenwen.controller.ra.OcrController;
import com.wenwen.mapper.OcrMapper;
import java.util.*;

class OcrAccessTest {
    private OcrService service(OcrMapper mapper) {
        OcrService s = new OcrService(); ReflectionTestUtils.setField(s, "mapper", mapper); return s;
    }
    @Test void anonymousCannotEnumerateImages() throws Exception {
        OcrMapper mapper = mock(OcrMapper.class);
        OcrController c = new OcrController(); ReflectionTestUtils.setField(c, "ocrService", service(mapper));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(c).build();
        JSONObject r = JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/visitImages").param("doctorId","22").param("patientId","220").param("visitId","990")).andReturn().getResponse().getContentAsString());
        assertFalse(r.getBooleanValue("success")); assertEquals("401",r.getString("code"));
        verifyZeroInteractions(mapper);
    }
    @Test void imageAuthorizationFailsClosedOnMissingRecordAndDatabaseFailure() {
        OcrMapper mapper=mock(OcrMapper.class); OcrService s=service(mapper);
        assertFalse(s.canView("20261017/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg", 11L));
        when(mapper.findRecord(anyString())).thenThrow(new DataAccessResourceFailureException("synthetic"));
        assertFalse(s.canView("20261017/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg",11L));
    }

    @Test void sessionDoctorOverridesSpoofedParameterAndRequiresActualVisit() throws Exception {
        OcrMapper mapper=mock(OcrMapper.class);OcrService service=service(mapper);OcrController c=new OcrController();ReflectionTestUtils.setField(c,"ocrService",service);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(c).build();MockHttpSession session=new MockHttpSession();session.setAttribute(AuthService.SESSION_DOCTOR,11L);
        when(mapper.ownsPatient(220L,22L)).thenReturn(1);
        JSONObject denied=JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/visitImages").session(session).param("doctorId","22").param("patientId","220").param("visitId","990")).andReturn().getResponse().getContentAsString());
        assertEquals("403",denied.getString("code"));verify(mapper,never()).listVisitRecords(anyLong(),anyLong(),anyLong());
        when(mapper.ownsPatient(110L,11L)).thenReturn(1);when(mapper.visitBelongsToPatient(900L,110L)).thenReturn(1);
        Map<String,Object> row=new HashMap<>();row.put("fileId",OcrLinkTest.FILE);when(mapper.listVisitRecords(11L,110L,900L)).thenReturn(Collections.singletonList(row));
        JSONObject own=JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/visitImages").session(session).param("doctorId","22").param("patientId","110").param("visitId","900")).andReturn().getResponse().getContentAsString());assertTrue(own.getBooleanValue("success"));assertEquals(1,own.getJSONArray("data").size());verify(mapper).listVisitRecords(11L,110L,900L);
    }
    @Test void recognizeDeniesBeforeFileReadOrExternalOcr() throws Exception {
        OcrMapper mapper=mock(OcrMapper.class);OcrService service=service(mapper);BaiduOcrClient baidu=mock(BaiduOcrClient.class);ReflectionTestUtils.setField(service,"baidu",baidu);
        assertThrows(com.wenwen.util.BizException.class,()->service.recognize(OcrLinkTest.FILE,null));verifyZeroInteractions(mapper,baidu);
        assertThrows(com.wenwen.util.BizException.class,()->service.recognize(OcrLinkTest.FILE,11L));verifyZeroInteractions(baidu);
    }
    @Test void aliasesUseSessionAndAnonymousEmptyListStillFails() throws Exception {
        OcrMapper mapper=mock(OcrMapper.class);OcrController c=new OcrController();ReflectionTestUtils.setField(c,"ocrService",service(mapper));MockMvc mvc=MockMvcBuilders.standaloneSetup(c).build();
        String body="{\"doctorId\":22,\"mappings\":[{\"alias\":\"SYNTHETIC\",\"field\":\"cfydb\",\"name\":\"Synthetic\"}]}";
        assertEquals("401",JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/saveAliases").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString()).getString("code"));verifyZeroInteractions(mapper);
        MockHttpSession session=new MockHttpSession();session.setAttribute(AuthService.SESSION_DOCTOR,11L);assertTrue(JSONObject.parseObject(mvc.perform(post("/api/ra/ocr/saveAliases").session(session).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString()).getBooleanValue("success"));verify(mapper).upsertAlias("SYNTHETIC","cfydb","Synthetic",11L);
    }
    @Test void failedUploadRecordIsVisibleAndDoesNotLeaveUnownedFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        OcrMapper mapper=mock(OcrMapper.class);OcrService service=service(mapper);ReflectionTestUtils.setField(service,"uploadDir",dir.toString());
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        when(mapper.insertRecord(anyString(),eq(11L))).thenThrow(new DataAccessResourceFailureException("synthetic missing table"));
        assertThrows(com.wenwen.util.BizException.class,()->service.upload(new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray()),11L));
        try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(dir)){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }

    @Test void clinicalImageSuccessMustNotBeStoredInBrowserCache(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        OcrMapper mapper=mock(OcrMapper.class);OcrService service=service(mapper);ReflectionTestUtils.setField(service,"uploadDir",dir.toString());
        Map<String,Object> row=new HashMap<>();row.put("doctorId",11L);when(mapper.findRecord(OcrLinkTest.FILE)).thenReturn(row);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        java.nio.file.Path path=dir.resolve("ocr").resolve(OcrLinkTest.FILE);java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,bytes.toByteArray());
        OcrController c=new OcrController();ReflectionTestUtils.setField(c,"ocrService",service);MockMvc mvc=MockMvcBuilders.standaloneSetup(c).build();MockHttpSession session=new MockHttpSession();session.setAttribute(AuthService.SESSION_DOCTOR,11L);
        org.springframework.mock.web.MockHttpServletResponse response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ra/files/ocr/"+OcrLinkTest.FILE).session(session)).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertArrayEquals(bytes.toByteArray(),response.getContentAsByteArray());assertEquals("no-store",response.getHeader("Cache-Control"));
    }
}
