package com.wenwen.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 仅显式 loopback 合成库；直接加载真实 MyBatis XML，不启动应用。 */
public class PatientDas28MapperTest {
    private DriverManagerDataSource dataSource;
    private SqlSessionFactory factory;
    private boolean createdTable;

    static void guard(String url, String user, String password) {
        if (url == null || user == null || user.isEmpty() || password == null || password.isEmpty()
                || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("必须显式提供 RA_TEST_MYSQL_URL/USER/PASSWORD");
        }
        URI target = URI.create(url.substring("jdbc:".length()));
        if (!("127.0.0.1".equals(target.getHost()) || "localhost".equals(target.getHost()))
                || !"/ra_synthetic_test".equals(target.getPath()) || target.getUserInfo() != null) {
            throw new IllegalStateException("仅允许 loopback 的 ra_synthetic_test 合成库");
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        String url = System.getenv("RA_TEST_MYSQL_URL");
        String user = System.getenv("RA_TEST_MYSQL_USER");
        String password = System.getenv("RA_TEST_MYSQL_PASSWORD");
        guard(url, user, password);
        dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet database = statement.executeQuery("SELECT DATABASE()")) {
                assertTrue(database.next());
                assertEquals("ra_synthetic_test", database.getString(1));
            }
            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals("MySQL", metadata.getDatabaseProductName());
            System.out.println("synthetic SQL versions: MySQL " + metadata.getDatabaseProductVersion()
                    + "; JDBC " + metadata.getDriverVersion());
            try (InputStream schema = Resources.getResourceAsStream("ai-cohort/p01b/schema.sql")) {
                Scanner text = new Scanner(schema, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
                statement.execute(text.next());
                createdTable = true; // 仅在本测试成功创建后获得 teardown 所有权
            }
        }
        Configuration configuration = new Configuration(new Environment("synthetic", new JdbcTransactionFactory(), dataSource));
        for (String resource : new String[] {"mybatis/ProjectMapper.xml", "mybatis/PatientMapper.xml"}) {
            try (InputStream xml = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    public void teardown() throws SQLException {
        if (createdTable) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE patient_follow_up_history");
            }
        }
    }

    private void insert(long id, long patient, int type, String json, String underscoredDate, String camelDate) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,bqpg,follow_up_date,followUpDate) VALUES (?,?,?,?,?,?)")) {
            statement.setLong(1, id);
            statement.setLong(2, patient);
            statement.setInt(3, type);
            statement.setString(4, json);
            statement.setString(5, underscoredDate);
            statement.setString(6, camelDate);
            statement.executeUpdate();
        }
    }

    private List<Map<String, Object>> query(Long... patients) {
        try (SqlSession session = factory.openSession()) {
            return session.getMapper(PatientMapper.class).listDas28(Arrays.asList(patients));
        }
    }

    private static String scoreJson(String value) {return "{\"result\":{\"crpScore\":"+value+"}}";}

    private static List<String> values(List<Map<String, Object>> rows) {
        List<String> result = new ArrayList<String>();
        for (Map<String, Object> row : rows) {
            result.add((String)row.get("bqpg"));
        }
        return result;
    }

    @Test
    public void legalWhitespaceNumbersAndStringsAreReadable() throws Exception {
        insert(1, 101, 0, "{\"result\":{\"crpScore\":2.295}}", "2026-01-01", null);
        insert(2, 101, 1, "{\"result\":{\"crpScore\":\"4.105\"}}", "2026-01-02", null);
        insert(3, 101, 7, "{\"result\" : {\"crpScore\" : \"2.705\"}}", "2026-01-03", null);
        assertEquals(Arrays.asList("{\"result\" : {\"crpScore\" : \"2.705\"}}",scoreJson("\"4.105\""),scoreJson("2.295")), values(query(101L)));
    }

    @Test
    public void malformedJsonAndEsrNeverBecomeCrpZero() throws Exception {
        insert(1, 101, 0, "{\"result\":{\"crpScore\":0}}", "2026-01-01", null);
        insert(2, 101, 0, "{\"result\":{\"crpScore\":9}", "2026-01-02", null);
        insert(3, 101, 0, "not JSON", "2026-01-03", null);
        insert(4, 101, 0, null, "2026-01-04", null);
        assertEquals(Arrays.asList(null,"not JSON","{\"result\":{\"crpScore\":9}",scoreJson("0")), values(query(101L)));
        insert(5, 102, 0, "{\"result\":{\"esrScore\":4.105}}", "2026-01-05", null);
        List<Map<String, Object>> esrOnly = query(102L);
        assertEquals(1, esrOnly.size());
        assertEquals("{\"result\":{\"esrScore\":4.105}}",esrOnly.get(0).get("bqpg"));
        insert(6, 103, 0, "{\"result\":{\"crpScore\":null}}", null, null);
        insert(7, 104, 0, "{\"result\":{\"crpScore\":{}}}", null, null);
        insert(8, 105, 0, "{\"result\":{\"crpScore\":[]}}", null, null);
        assertEquals(Arrays.asList(scoreJson("null"),scoreJson("{}"),scoreJson("[]")), values(query(103L, 104L, 105L)));
    }

    @Test
    public void onlyRequestedPatientsAndRaResearchRowsAreReturned() throws Exception {
        int[] raTypes = {0, 1, 2, 3, 4, 7};
        for (int type : raTypes) {
            insert(type + 1, 101, type, "{\"result\":{\"crpScore\":\"" + type + "\"}}", "2026-01-01", null);
        }
        for (int type : new int[] {5, 6, 9, 999}) {
            insert(type + 20, 101, type, "{\"result\":{\"crpScore\":999}}", "2026-02-01", null);
        }
        insert(2000, 102, 0, "{\"result\":{\"crpScore\":8}}", "2026-03-01", null);
        assertEquals(Arrays.asList(scoreJson("\"7\""),scoreJson("\"4\""),scoreJson("\"3\""),scoreJson("\"2\""),scoreJson("\"1\""),scoreJson("\"0\"")), values(query(101L)));
        for (Map<String, Object> row : query(101L)) {
            assertEquals(101L, ((Number) row.get("patientId")).longValue());
        }
    }

    @Test
    public void originalDualDateIdAndPatientOrderingIsPreserved() throws Exception {
        insert(10, 101, 0, "{\"result\":{\"crpScore\":10}}", "2026-01-02", "2026-12-31");
        insert(11, 101, 0, "{\"result\":{\"crpScore\":11}}", null, "2026-01-03");
        insert(12, 101, 0, "{\"result\":{\"crpScore\":12}}", "2026-01-03", "2026-01-01");
        insert(9, 101, 0, "{\"result\":{\"crpScore\":9}}", "2026-01-03", null);
        insert(13, 101, 0, "{\"result\":{\"crpScore\":13}}", null, null);
        insert(14, 101, 0, "{\"result\":{\"crpScore\":14}}", "2026-01-01", "2026-12-31");
        insert(99, 102, 0, "{\"result\":{\"crpScore\":99}}", "2026-12-31", null);
        List<Map<String, Object>> rows = query(102L, 101L);
        assertEquals(Arrays.asList(scoreJson("12"),scoreJson("11"),scoreJson("9"),scoreJson("10"),scoreJson("14"),scoreJson("13"),scoreJson("99")), values(rows));
        assertEquals(12L,((Number)rows.get(0).get("visitId")).longValue());
        assertEquals("2026-01-03",rows.get(0).get("visitDate"));
        assertEquals("2026-01-03",rows.get(1).get("visitDate"));
        assertEquals("2026-01-02",rows.get(3).get("visitDate"));
        assertNull(rows.get(5).get("visitDate"));
        List<Long> patients = new ArrayList<Long>();
        for (Map<String, Object> row : rows) {
            patients.add(((Number) row.get("patientId")).longValue());
        }
        assertEquals(Arrays.asList(101L, 101L, 101L, 101L, 101L, 101L, 102L), patients);
    }

    @Test
    public void connectionGuardRejectsMissingRemoteAndOtherSchemas() {
        String synthetic = "jdbc:mysql://127.0.0.1:43306/ra_synthetic_test";
        assertDoesNotThrow(() -> guard(synthetic, "synthetic", "synthetic"));
        assertDoesNotThrow(() -> guard("jdbc:mysql://localhost/ra_synthetic_test", "synthetic", "synthetic"));
        assertThrows(IllegalStateException.class, () -> guard(null, "synthetic", "synthetic"));
        assertThrows(IllegalStateException.class, () -> guard(synthetic, null, "synthetic"));
        assertThrows(IllegalStateException.class, () -> guard(synthetic, "synthetic", null));
        assertThrows(IllegalStateException.class, () -> guard("jdbc:mysql://192.0.2.1/ra_synthetic_test", "synthetic", "synthetic"));
        assertThrows(IllegalStateException.class, () -> guard("jdbc:mysql://127.0.0.1/other_schema", "synthetic", "synthetic"));
        assertThrows(IllegalStateException.class, () -> guard("jdbc:mysql://user@localhost/ra_synthetic_test", "synthetic", "synthetic"));
    }
}
