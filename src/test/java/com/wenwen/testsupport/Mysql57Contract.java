package com.wenwen.testsupport;

import java.sql.*;
import javax.sql.DataSource;

/** 破坏性合成夹具在建表前验证真实服务器；禁止在8.x/H2上冒充5.7验收。 */
public final class Mysql57Contract {
    private Mysql57Contract() { }
    public static void requireVersion(String product, String version) {
        if (!"MySQL".equals(product) || version == null || !version.matches("5\\.7\\.44(?:[-.][A-Za-z0-9_.-]+)?")
                || version.toLowerCase(java.util.Locale.ROOT).contains("mariadb"))
            throw new IllegalStateException("兼容验收仅允许真实 MySQL 5.7.44");
    }
    public static void requireSyntheticUrl(String url) {
        if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalStateException("需要显式合成MySQL URL");
        java.net.URI uri = java.net.URI.create(url.substring(5));
        if (!"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getUserInfo() != null || !"/ra_synthetic_test".equals(uri.getPath()))
            throw new IllegalStateException("仅允许显式loopback端口/ra_synthetic_test且禁止userinfo");
    }
    public static void verify(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) { verify(connection); }
    }
    public static void verify(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        requireVersion(metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion());
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT @@transaction_isolation,@@max_allowed_packet,@@innodb_large_prefix,@@innodb_file_per_table,@@innodb_file_format,@@innodb_default_row_format,@@sql_mode")) {
            if (!rows.next() || !"REPEATABLE-READ".equals(rows.getString(1)) || rows.getLong(2) < 64L*1024*1024
                    || !rows.getBoolean(3) || !rows.getBoolean(4) || !"Barracuda".equalsIgnoreCase(rows.getString(5))
                    || !"dynamic".equalsIgnoreCase(rows.getString(6)))
                throw new IllegalStateException("MySQL5.7需RR/64MiB packet/Barracuda/large_prefix/file_per_table/DYNAMIC");
            java.util.Set<String> modes=new java.util.HashSet<>(java.util.Arrays.asList(rows.getString(7).split(",")));
            if(!modes.contains("ONLY_FULL_GROUP_BY")||!(modes.contains("STRICT_TRANS_TABLES")||modes.contains("STRICT_ALL_TABLES")))
                throw new IllegalStateException("MySQL5.7验收需严格模式与ONLY_FULL_GROUP_BY");
        }
    }
}
