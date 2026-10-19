package com.wenwen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenwen.auth.AuthService;
import com.wenwen.controller.ra.VisitController;
import com.wenwen.service.VisitService;
import com.wenwen.vo.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class VisitEntryAuthTest {
    @Test void entryRoutesUseSessionDoctorAndRejectAnonymousBeforeService() throws Exception {
        VisitService service = mock(VisitService.class);
        AtomicReference<Long> actor = new AtomicReference<>();
        when(service.updateVisit(any())).thenAnswer(call -> {
            actor.set(((VisitUpdateRequest) call.getArgument(0)).getDoctorId());
            VisitUpdateResultVo result = new VisitUpdateResultVo(); result.setChangedCount(0); return result;
        });
        when(service.getEditForm(anyLong(), anyLong())).thenAnswer(call -> { actor.set(call.getArgument(0)); return new VisitEditFormVo(); });
        when(service.getCreateForm(anyLong(), anyLong())).thenAnswer(call -> { actor.set(call.getArgument(0)); return new VisitEditFormVo(); });
        VisitController controller = new VisitController(); ReflectionTestUtils.setField(controller, "visitService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession(); session.setAttribute(AuthService.SESSION_DOCTOR, 101L);
        for (String route : new String[]{"updateVisit", "visitEditForm", "visitCreateForm"}) {
            MockHttpServletRequestBuilder request = post("/api/ra/visit/" + route);
            if (route.equals("updateVisit")) request.contentType("application/json").content("{\"doctorId\":202,\"visitId\":10}");
            else request.param("doctorId", "202").param(route.equals("visitEditForm") ? "visitId" : "patientId", "10");
            String body = mvc.perform(request.session(session)).andReturn().getResponse().getContentAsString();
            assertEquals("200", new ObjectMapper().readTree(body).path("code").asText());
            assertEquals(Long.valueOf(101), actor.get(), route + " must use the server session");
            clearInvocations(service); actor.set(null);
            MockHttpServletRequestBuilder anonymous = post("/api/ra/visit/" + route);
            if (route.equals("updateVisit")) anonymous.contentType("application/json").content("{\"doctorId\":101,\"visitId\":10}");
            else anonymous.param("doctorId", "101").param(route.equals("visitEditForm") ? "visitId" : "patientId", "10");
            body = mvc.perform(anonymous).andReturn().getResponse().getContentAsString();
            assertEquals("401", new ObjectMapper().readTree(body).path("code").asText());
            verifyNoInteractions(service);
        }
    }
}
