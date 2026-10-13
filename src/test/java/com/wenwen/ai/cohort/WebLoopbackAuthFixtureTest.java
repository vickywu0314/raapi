package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Servlet/TCP auth bootstrap；拒绝数据库连接，绝不把拒绝源替换成 me 成功假响应。 */
class WebLoopbackAuthFixtureTest {
    @Test void realMeReadsSyntheticCookieProfile() throws Exception {
        AtomicInteger attempts=new AtomicInteger();
        WebLoopbackApiFixture fixture=fixture(attempts);
        try {
            int port=fixture.start("synthetic",false,new File(System.getProperty("java.io.tmpdir"),"auth-fixture-"+System.nanoTime()));
            Response login=request(port,"POST","/api/ra/test/session",null);
            assertEquals(204,login.status);assertNotNull(login.cookie);
            Response me=request(port,"POST","/api/ra/auth/me",login.cookie);
            assertEquals(200,me.status);
            JsonNode envelope=WebLoopbackApiFixture.JSON.readTree(me.body);
            assertTrue(envelope.path("success").asBoolean());assertEquals("200",envelope.path("code").asText());
            JsonNode user=envelope.path("data");assertEquals(101,user.path("doctorId").longValue());
            assertEquals("synthetic",user.path("username").textValue());assertEquals("医生 101",user.path("name").textValue());assertEquals("研究者",user.path("role").textValue());
            assertEquals(1,attempts.get());
            assertEquals(101,typedDoctor(fixture,login.cookie));
            assertEquals(Long.valueOf(101),session(fixture,login.cookie).getAttribute(com.wenwen.auth.AuthService.SESSION_DOCTOR));
            assertEquals("synthetic",session(fixture,login.cookie).getAttribute(com.wenwen.auth.AuthService.SESSION_USER));
            assertEquals(com.wenwen.auth.AuthService.class,fixture.application.getBean(com.wenwen.auth.AuthService.class).getClass());
            assertEquals(com.wenwen.controller.ra.AuthController.class,fixture.application.getBean(com.wenwen.controller.ra.AuthController.class).getClass());
        } finally {fixture.close();}
        assertTrue(fixture.closed);assertEquals(0,fixture.observed.active.get());
    }
    @Test void anonymousAndLoggedOutCookieNeverReviveEitherIdentity() throws Exception {
        AtomicInteger attempts=new AtomicInteger();WebLoopbackApiFixture f=fixture(attempts);
        try {
            int port=f.start("synthetic",false,new File(System.getProperty("java.io.tmpdir"),"auth-anonymous-"+System.nanoTime()));
            assertAnonymous(request(port,"POST","/api/ra/auth/me",null));assertEquals(0,attempts.get());
            Response login=request(port,"POST","/api/ra/test/session",null);assertEquals(101,typedDoctor(f,login.cookie));
            assertEquals(204,request(port,"POST","/api/ra/test/logout",login.cookie).status);
            assertNull(session(f,login.cookie));assertAnonymous(request(port,"POST","/api/ra/auth/me",login.cookie));
            assertEquals(401,request(port,"POST","/api/ra/patient/patientDetail",login.cookie).status);
            Response next=request(port,"POST","/api/ra/test/session",null);assertNotEquals(login.cookie,next.cookie);assertEquals(101,typedDoctor(f,next.cookie));
            assertAnonymous(request(port,"POST","/api/ra/auth/me",login.cookie));assertEquals(0,attempts.get());
        } finally {f.close();}
    }
    @Test void establishingNewCookieInvalidatesPreviousSessionBeforeReplacingIdentity() throws Exception {
        AtomicInteger attempts=new AtomicInteger();WebLoopbackApiFixture f=fixture(attempts);
        try {
            int port=f.start("synthetic",false,new File(System.getProperty("java.io.tmpdir"),"auth-rotation-"+System.nanoTime()));
            Response first=request(port,"POST","/api/ra/test/session",null);
            javax.servlet.http.HttpSession previous=session(f,first.cookie);
            // 模拟另一可信登录身份；只改测试拥有的合成 servlet session，不新增网络选择医生入口。
            previous.setAttribute(com.wenwen.auth.AuthService.SESSION_DOCTOR,Long.valueOf(202));
            previous.setAttribute(com.wenwen.auth.AuthService.SESSION_USER,"synthetic-202");
            previous.setAttribute(com.wenwen.ai.scope.AuthenticatedDoctorSession.ATTRIBUTE,new com.wenwen.ai.scope.AuthenticatedDoctorSession(new com.wenwen.ai.scope.TrustedDoctor(202)));
            assertEquals(202,typedDoctor(f,first.cookie));
            assertEquals(202,WebLoopbackApiFixture.JSON.readTree(request(port,"POST","/api/ra/auth/me",first.cookie).body).path("data").path("doctorId").longValue());
            Response next=request(port,"POST","/api/ra/test/session",first.cookie);
            assertNotEquals(first.cookie,next.cookie);assertNull(session(f,first.cookie));assertEquals(101,typedDoctor(f,next.cookie));
            assertAnonymous(request(port,"POST","/api/ra/auth/me",first.cookie));
            assertEquals(101,WebLoopbackApiFixture.JSON.readTree(request(port,"POST","/api/ra/auth/me",next.cookie).body).path("data").path("doctorId").longValue());
            assertEquals(2,attempts.get());
        } finally {f.close();}
    }
    @Test void authBootstrapIsExactPostOnlyInTypedScenarios() throws Exception {
        String path="/api/ra/auth/me";
        for(String scenario:new String[]{"issue1-detail","p00-trusted","p01-overview","p03-sources","p05-chat","p06-current","p07-current"}) {
            assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,path));
            for(String invalid:new String[]{path+"/",path+"?x=1","/api/ra/auth/login","/api/ra/auth/logout","/api/ra/test/chooseDoctor"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,invalid));
        }
        for(String scenario:new String[]{"default","p01b","p07-composition","p07-composition-time","p04","p07-entry"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path));
        AtomicInteger attempts=new AtomicInteger();WebLoopbackApiFixture f=fixture(attempts);
        try {
            int port=f.start("synthetic",false,new File(System.getProperty("java.io.tmpdir"),"auth-exact-"+System.nanoTime()));
            Response login=request(port,"POST","/api/ra/test/session",null);
            for(String method:new String[]{"GET","HEAD","PUT","DELETE","OPTIONS"})assertEquals(405,request(port,method,path,login.cookie).status,method);
            for(String invalid:new String[]{path+"/",path+"?x=1","/api/ra/auth/login","/api/ra/auth/logout","/api/ra/test/chooseDoctor"})assertEquals(404,request(port,"POST",invalid,login.cookie).status,invalid);
            assertEquals(101,typedDoctor(f,login.cookie));assertEquals(0,attempts.get());
        } finally {f.close();}
    }
    static void assertAnonymous(Response response) throws Exception {
        assertEquals(200,response.status);JsonNode envelope=WebLoopbackApiFixture.JSON.readTree(response.body);
        assertEquals("401",envelope.path("code").textValue());assertFalse(envelope.path("success").asBoolean());assertTrue(envelope.path("data").isNull());assertNull(response.cookie);
    }
    static javax.servlet.http.HttpSession session(WebLoopbackApiFixture f,String cookie) throws Exception {
        org.apache.catalina.Context context=(org.apache.catalina.Context)f.tomcat.getHost().findChildren()[0];
        org.apache.catalina.Session session=context.getManager().findSession(cookie.substring(cookie.indexOf('=')+1));
        return session==null?null:session.getSession();
    }
    static long typedDoctor(WebLoopbackApiFixture f,String cookie) throws Exception {
        javax.servlet.http.HttpServletRequest request=org.mockito.Mockito.mock(javax.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getSession(false)).thenReturn(session(f,cookie));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {return f.application.getBean(com.wenwen.ai.scope.PrincipalProvider.class).requireCurrent().getId();}
        finally {org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    static WebLoopbackApiFixture fixture(AtomicInteger attempts) {
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="issue1-detail";
        f.raw=new DriverManagerDataSource(){
            @Override public Connection getConnection() throws SQLException {attempts.incrementAndGet();throw new SQLException("auth-fixture: database forbidden");}
            @Override public Connection getConnection(String user,String password) throws SQLException {return getConnection();}
        };
        return f;
    }
    static class Response {int status;String cookie;String body;}
    static Response request(int port,String method,String path,String cookie) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        try {
            Response r=new Response();r.status=c.getResponseCode();String setCookie=c.getHeaderField("Set-Cookie");r.cookie=setCookie==null?null:setCookie.split(";",2)[0];
            InputStream input=r.status>=400?c.getErrorStream():c.getInputStream();r.body="";
            if(input!=null)try(Scanner scanner=new Scanner(input,"UTF-8").useDelimiter("\\A")){if(scanner.hasNext())r.body=scanner.next();}
            return r;
        } finally {c.disconnect();}
    }
}
