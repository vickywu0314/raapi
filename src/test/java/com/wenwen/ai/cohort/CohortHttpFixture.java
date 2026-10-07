package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.*;
import com.wenwen.ai.scope.*;
import com.wenwen.ai.source.CohortSourceAdapter;
import com.wenwen.mapper.AiCohortSourceMapper;
import org.mybatis.spring.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import com.wenwen.config.AiCohortConfiguration;
import com.wenwen.controller.ra.AiCohortController;
import com.wenwen.service.impl.AiCohortServiceImpl;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 真实 DispatcherServlet 和真实服务，仅替换可信身份及时钟；绝不启动 RaApplication。 */
abstract class CohortHttpFixture {
    AnnotationConfigWebApplicationContext context;
    MockMvc http;
    CountingBodyFilter bodyRead;
    DriverManagerDataSource raw;
    MutableClock clock;
    ObservedDataSource observed;
    final ObjectMapper json = new ObjectMapper();
    final List<String> ownedTables = new ArrayList<>();

    static void guard(String url, String user, String password) {
        if (url == null || user == null || user.isEmpty() || password == null || password.isEmpty()
                || !url.startsWith("jdbc:mysql://")) throw new IllegalStateException("缺少显式合成 MySQL 环境");
        URI uri = URI.create(url.substring(5));
        if (!("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                || !"/ra_synthetic_test".equals(uri.getPath()) || uri.getUserInfo() != null)
            throw new IllegalStateException("仅允许 loopback/ra_synthetic_test");
    }
    @Configuration @EnableWebMvc
    @Import({AiCohortConfiguration.class, AiCohortController.class, AiCohortServiceImpl.class, CohortSourceAdapter.class})
    static class Infrastructure {
        @Bean public DriverManagerDataSource dataSource() {
            String url = System.getenv("RA_TEST_MYSQL_URL"), user = System.getenv("RA_TEST_MYSQL_USER"), password = System.getenv("RA_TEST_MYSQL_PASSWORD");
            guard(url, user, password);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver"); return ds;
        }
        @Bean @Primary public ObservedDataSource observedDataSource(DriverManagerDataSource raw) { return new ObservedDataSource(raw); }
        @Bean public PlatformTransactionManager transactions(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.Resource[] {new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/PatientMapper.xml")});
            return factory.getObject();
        }
        @Bean public AiCohortSourceMapper sourceMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(AiCohortSourceMapper.class);
        }
        @Bean @Primary public MutableClock testClock() { return new MutableClock(); }
    }
    @Configuration static class TrustedIdentity {
        @Bean @Primary public FakePrincipalProvider trustedProvider() { return new FakePrincipalProvider(); }
    }
    static final class FakePrincipalProvider implements PrincipalProvider {
        long doctor = 101;
        public TrustedDoctor requireCurrent() { return new TrustedDoctor(doctor); }
    }
    static final class MutableClock extends Clock {
        Runnable onInstant;
        Instant now = Instant.parse("2026-10-07T02:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { if (onInstant != null) onInstant.run(); return now; }
    }
    void buildContext(boolean trusted) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Infrastructure.class);
        if (trusted) context.register(TrustedIdentity.class);
        beforeRefresh();
        context.refresh();
        bodyRead = new CountingBodyFilter();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        raw = context.getBean(DriverManagerDataSource.class);
        clock = context.getBean(MutableClock.class);
        observed = context.getBean(ObservedDataSource.class);
    }
    void beforeRefresh() { }
    @BeforeEach void setup() throws Exception {
        buildContext(true);
        try (Connection c = raw.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT DATABASE(), @@transaction_isolation, VERSION()")) {
                assertTrue(rs.next()); assertEquals("ra_synthetic_test", rs.getString(1));
                assertEquals("REPEATABLE-READ", rs.getString(2));
                System.out.println("P01c MySQL " + rs.getString(3) + " REPEATABLE-READ; JDBC " + c.getMetaData().getDriverVersion());
            }
            try (InputStream in = getClass().getResourceAsStream("/ai-cohort/p01c/schema.sql")) {
                String ddl = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
                String[] tables = {"patient_basic_info", "patient_relation_doctor", "patient_follow_up_history", "patient_comorbidity"};
                int i = 0;
                for (String sql : ddl.split(";")) if (!sql.trim().isEmpty()) { s.execute(sql); ownedTables.add(tables[i++]); }
            }
            try (ResultSet engines = s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('patient_basic_info','patient_relation_doctor','patient_follow_up_history','patient_comorbidity')")) {
                int count=0; while(engines.next()) { assertEquals("InnoDB",engines.getString(1)); count++; } assertEquals(4,count);
            }
            for (int i = 1; i <= 7; i++) s.execute("INSERT INTO patient_basic_info (id,name) VALUES (" + i + ",'synthetic-same-name')");
            s.execute("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,1,1,0),(202,1,5,0),(101,2,6,0),(202,2,2,0),(101,3,3,1),(202,4,4,0),(101,5,7,0),(101,6,2,0),(101,7,4,0),(101,8,0,0)");
        }
        visit(10,1,0,"{\"result\":{\"crpScore\":9}}","2026-10-07",null,null);
        visit(11,1,1,"{\"result\":{\"crpScore\":2.295}}","2026-10-07",null,null);
        visit(30,3,3,"{\"result\":{\"crpScore\":\"2.705\"}}",null,"2026-10-06",null);
        visit(50,5,7,"{\"result\":{\"crpScore\":4.105}}","2026-10-07",null,null);
        visit(60,6,2,"{\"result\":{\"esrScore\":8}}","2026-10-07",null,null);
        visit(70,7,4,"{\"result\":{\"crpScore\":0}}","2026-10-07",null,null);
    }
    @AfterEach void teardown() throws Exception {
        if (raw != null) for (int i = ownedTables.size()-1; i >= 0; i--) sql("DROP TABLE " + ownedTables.get(i));
        if (context != null) context.close();
    }
    void sql(String sql) throws SQLException {
        try (Connection c = raw.getConnection(); Statement s = c.createStatement()) { s.execute(sql); }
    }
    void visit(long id, long patient, int type, String bqpg, String date, String camel, String fzjc) throws SQLException {
        try (Connection c = raw.getConnection(); PreparedStatement s = c.prepareStatement("INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,follow_up_date,followUpDate,bqpg,fzjc) VALUES (?,?,?,?,?,?,?)")) {
            s.setLong(1,id); s.setLong(2,patient); s.setInt(3,type); s.setString(4,date); s.setString(5,camel); s.setString(6,bqpg); s.setString(7,fzjc); s.executeUpdate();
        }
    }
    MvcResult request(String body) throws Exception {
        return http.perform(post("/api/ra/ai/cohort").contentType("application/json").content(body)).andReturn();
    }
    void error(String body, int status, String code) throws Exception {
        MvcResult response = request(body);
        assertEquals(status,response.getResponse().getStatus(),response.getResponse().getContentAsString());
        JsonNode envelope = json.readTree(response.getResponse().getContentAsByteArray());
        assertFalse(envelope.path("success").asBoolean()); assertEquals(code,envelope.path("code").asText());
        assertTrue(envelope.path("data").isNull()); assertNotNull(response.getResponse().getHeader("X-Trace-Id"));
    }
    JsonNode success(String body) throws Exception {
        MvcResult result = request(body);
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode envelope = json.readTree(result.getResponse().getContentAsByteArray());
        assertTrue(envelope.path("success").asBoolean()); assertEquals("200",envelope.path("code").asText());
        assertNotNull(result.getResponse().getHeader("X-Trace-Id"));
        assertEquals(result.getResponse().getHeader("X-Trace-Id"),envelope.path("data").path("meta").path("traceId").asText());
        return envelope.path("data");
    }
    List<String> ids(JsonNode data) {
        List<String> ids = new ArrayList<>();
        data.path("patients").path("items").forEach(x -> ids.add(x.path("patientId").asText()));
        return ids;
    }
}
