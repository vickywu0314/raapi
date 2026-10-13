package com.wenwen.auth;

import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.scope.AuthenticatedDoctorSession;
import com.wenwen.ai.scope.SessionPrincipalProvider;
import com.wenwen.util.BizException;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.junit.Assert.*;

public class AuthLoginPrincipalTest {
    @After public void clearContext(){RequestContextHolder.resetRequestAttributes();}
    private AuthService service(){AuthService s=new AuthService();ReflectionTestUtils.setField(s,"accounts","test:secret:101");return s;}
    private void bind(MockHttpServletRequest r){RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(r));}
    @Test public void loginRotatesSessionInstallsTypedIdentityAndLogoutRevokesIt(){
        AuthService s=service();MockHttpServletRequest r=new MockHttpServletRequest();MockHttpSession old=new MockHttpSession();r.setSession(old);
        s.login("test","secret",false,r);assertTrue(old.isInvalid());assertNotSame(old,r.getSession(false));
        assertTrue(r.getSession(false).getAttribute(AuthenticatedDoctorSession.ATTRIBUTE) instanceof AuthenticatedDoctorSession);
        assertEquals(Long.valueOf(101),AuthService.currentDoctorId(r));bind(r);
        assertEquals(101,new SessionPrincipalProvider().requireCurrent().getId());
        s.logout(r);try{new SessionPrincipalProvider().requireCurrent();fail("logged out identity accepted");}catch(CohortException expected){}
    }
    @Test public void legacyAttributeAloneCannotAuthenticateAi(){
        MockHttpServletRequest r=new MockHttpServletRequest();r.getSession(true).setAttribute(AuthService.SESSION_DOCTOR,Long.valueOf(101));bind(r);
        try{new SessionPrincipalProvider().requireCurrent();fail("untyped identity accepted");}catch(CohortException expected){}
    }
    @Test public void rejectedCredentialsDoNotInstallIdentity(){
        MockHttpServletRequest r=new MockHttpServletRequest();try{service().login("test","wrong",false,r);fail("bad credentials accepted");}catch(BizException expected){}
        assertNull(r.getSession(false));
    }
}
