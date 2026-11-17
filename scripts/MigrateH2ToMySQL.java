import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Offline copy with transactional replacement and per-table content verification. */
public class MigrateH2ToMySQL {
    record Column(String name, int type) {}
    record Table(String name, List<Column> columns, List<String> primaryKey) {}
    record Digest(long rows, String sha256) {}

    public static void main(String[] args) throws Exception {
        Properties settings = new Properties();
        settings.load(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try (Connection source = DriverManager.getConnection(settings.getProperty("source"), "sa", "");
             Connection target = DriverManager.getConnection(settings.getProperty("target"),
                     settings.getProperty("username"), settings.getProperty("password"))) {
            if (!source.getMetaData().getDatabaseProductName().equals("H2")
                    || !target.getMetaData().getDatabaseProductName().equals("MySQL")) {
                throw new IllegalStateException("Expected H2 source and MySQL target");
            }
            try (Statement statement = source.createStatement()) {
                String backup = settings.getProperty("backup");
                statement.execute("BACKUP TO '" + backup.replace("'", "''") + "'");
            }
            List<Table> tables = tables(source);
            source.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            source.setAutoCommit(false);
            target.setAutoCommit(false);
            try {
                try (Statement statement = target.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=0");
                    for (Table table : tables) statement.executeUpdate("DELETE FROM " + mysql(table.name()));
                }
                for (Table table : tables) copy(source, target, table);
                for (Table table : tables) verifyForeignKeys(target, table);
                for (Table table : tables) {
                    Digest before = digest(source, table, false);
                    Digest after = digest(target, table, true);
                    if (!before.equals(after)) throw new IllegalStateException("Content differs: " + table.name());
                    System.out.println(table.name() + "\t" + after.rows() + "\t" + after.sha256());
                }
                try (Statement statement = target.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
                target.commit();
                source.rollback();
                System.out.println("COMMITTED");
            } catch (Exception failure) {
                target.rollback();
                try (Statement statement = target.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
                throw failure;
            }
        }
    }

    static List<Table> tables(Connection source) throws SQLException {
        List<Table> result = new ArrayList<>();
        DatabaseMetaData metadata = source.getMetaData();
        try (ResultSet rows = metadata.getTables(null, "public", "%", null)) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                String type = rows.getString("TABLE_TYPE");
                if (name.equals("flyway_schema_history") || !(type.equals("BASE TABLE") || type.equals("TABLE"))) continue;
                List<Column> columns = new ArrayList<>();
                try (ResultSet fields = metadata.getColumns(null, "public", name, "%")) {
                    while (fields.next()) columns.add(new Column(fields.getString("COLUMN_NAME"), fields.getInt("DATA_TYPE")));
                }
                SortedMap<Short, String> keys = new TreeMap<>();
                try (ResultSet primary = metadata.getPrimaryKeys(null, "public", name)) {
                    while (primary.next()) keys.put(primary.getShort("KEY_SEQ"), primary.getString("COLUMN_NAME"));
                }
                if (keys.isEmpty()) throw new IllegalStateException("Missing primary key: " + name);
                result.add(new Table(name, columns, new ArrayList<>(keys.values())));
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("Source has no business tables");
        result.sort(Comparator.comparing(Table::name));
        return result;
    }

    static String mysql(String name) { return "`" + name.replace("`", "``") + "`"; }
    static String h2(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }

    static String select(Table table, boolean target) {
        java.util.function.Function<String, String> quote = target ? MigrateH2ToMySQL::mysql : MigrateH2ToMySQL::h2;
        String fields = String.join(",", table.columns().stream().map(c -> quote.apply(c.name())).toList());
        String keys = String.join(",", table.primaryKey().stream().map(quote).toList());
        return "SELECT " + fields + " FROM " + quote.apply(table.name()) + " ORDER BY " + keys;
    }

    static void copy(Connection source, Connection target, Table table) throws SQLException {
        String fields = String.join(",", table.columns().stream().map(c -> mysql(c.name())).toList());
        String placeholders = String.join(",", Collections.nCopies(table.columns().size(), "?"));
        try (Statement read = source.createStatement();
             ResultSet rows = read.executeQuery(select(table, false));
             PreparedStatement write = target.prepareStatement("INSERT INTO " + mysql(table.name())
                     + "(" + fields + ") VALUES(" + placeholders + ")")) {
            int batch = 0;
            while (rows.next()) {
                for (int i = 0; i < table.columns().size(); i++) {
                    int type = table.columns().get(i).type();
                    Object value;
                    if (type == Types.CLOB || type == Types.NCLOB || type == Types.LONGVARCHAR) value = rows.getString(i + 1);
                    else if (type == Types.TIMESTAMP) value = rows.getTimestamp(i + 1);
                    else value = rows.getObject(i + 1);
                    write.setObject(i + 1, value);
                }
                write.addBatch();
                if (++batch % 100 == 0) write.executeBatch();
            }
            write.executeBatch();
        }
    }

    static Digest digest(Connection connection, Table table, boolean target) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(select(table, target))) {
            while (rows.next()) {
                count++;
                for (int i = 0; i < table.columns().size(); i++) {
                    int column = i + 1;
                    int type = table.columns().get(i).type();
                    String value;
                    if (type == Types.BOOLEAN || type == Types.BIT) value = rows.getBoolean(column) ? "1" : "0";
                    else if (type == Types.TIMESTAMP) {
                        Timestamp timestamp = rows.getTimestamp(column);
                        value = timestamp == null ? null : timestamp.toLocalDateTime().toString();
                    } else if (type == Types.NUMERIC || type == Types.DECIMAL) {
                        BigDecimal decimal = rows.getBigDecimal(column);
                        value = decimal == null ? null : decimal.stripTrailingZeros().toPlainString();
                    } else value = rows.getString(column);
                    if (rows.wasNull()) value = null;
                    byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                    hash.update((value == null ? "-1:" : bytes.length + ":").getBytes(StandardCharsets.UTF_8));
                    hash.update(bytes);
                }
            }
        }
        return new Digest(count, HexFormat.of().formatHex(hash.digest()));
    }

    static void verifyForeignKeys(Connection target, Table table) throws SQLException {
        try (ResultSet keys = target.getMetaData().getImportedKeys(target.getCatalog(), null, table.name())) {
            while (keys.next()) {
                String column = keys.getString("FKCOLUMN_NAME");
                String parent = keys.getString("PKTABLE_NAME");
                String parentColumn = keys.getString("PKCOLUMN_NAME");
                String sql = "SELECT COUNT(*) FROM " + mysql(table.name()) + " c LEFT JOIN "
                        + mysql(parent) + " p ON c." + mysql(column) + "=p." + mysql(parentColumn)
                        + " WHERE c." + mysql(column) + " IS NOT NULL AND p." + mysql(parentColumn) + " IS NULL";
                try (Statement check = target.createStatement(); ResultSet invalid = check.executeQuery(sql)) {
                    invalid.next();
                    if (invalid.getLong(1) != 0) throw new IllegalStateException("Orphan reference: " + table.name() + "." + column);
                }
            }
        }
    }
}
