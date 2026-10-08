package projectsap.sap.bill.db;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import projectsap.sap.bill.model.*;
import projectsap.sap.common.GenericSapParser;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages database operations for SAP Bill data.
 * Configured for local PostgreSQL database with automatic database creation
 * and fallback to H2 embedded database if PostgreSQL is not reachable.
 */
public class DatabaseManager {

    // ── PostgreSQL Configuration ─────────────────────────────
    private static final String PG_HOST = System.getProperty("db.host", System.getenv().getOrDefault("DB_HOST", "localhost"));
    private static final int PG_PORT = Integer.parseInt(System.getProperty("db.port", System.getenv().getOrDefault("DB_PORT", "5432")));
    private static final String PG_DB = System.getProperty("db.name", System.getenv().getOrDefault("DB_NAME", "sap_bills"));
    private static final String PG_USER = System.getProperty("db.user", System.getenv().getOrDefault("DB_USER", "postgres"));
    private static final String PG_PASS = System.getProperty("db.pass", System.getenv().getOrDefault("DB_PASS", "postgres"));

    private static final String PG_URL = "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/" + PG_DB;

    // ── H2 Fallback Configuration ─────────────────────────────
    private static final String H2_URL = "jdbc:h2:./data/sap_bills_db;MODE=PostgreSQL;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASS = "";

    private static String activeUrl = PG_URL;
    private static String activeUser = PG_USER;
    private static String activePass = PG_PASS;
    private static String dbEngineName = "PostgreSQL";

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(activeUrl, activeUser, activePass);
    }

    /**
     * Attempts to connect to PostgreSQL (auto-creating the database if needed).
     * If PostgreSQL is unavailable or credentials fail, falls back to H2.
     */
    public static boolean testConnection() {
        // 1. Try PostgreSQL
        try {
            ensurePostgresDatabaseExists();
            try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS)) {
                if (conn.isValid(3)) {
                    activeUrl = PG_URL;
                    activeUser = PG_USER;
                    activePass = PG_PASS;
                    dbEngineName = "PostgreSQL (localhost:5432/" + PG_DB + ")";
                    System.out.println("[DB] Successfully connected to PostgreSQL database: " + PG_DB);
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("[DB] PostgreSQL connection attempt failed: " + e.getMessage());
        }

        // 2. Fallback to H2 Embedded
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASS)) {
            if (conn.isValid(3)) {
                activeUrl = H2_URL;
                activeUser = H2_USER;
                activePass = H2_PASS;
                dbEngineName = "H2 Embedded Database";
                System.out.println("[DB] Connected to H2 Embedded Database fallback.");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DB] H2 connection test failed: " + e.getMessage());
        }

        return false;
    }

    /**
     * Auto-creates the 'sap_bills' database in PostgreSQL if it doesn't exist.
     */
    private static void ensurePostgresDatabaseExists() {
        String maintenanceUrl = "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/postgres";
        try (Connection conn = DriverManager.getConnection(maintenanceUrl, PG_USER, PG_PASS);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + PG_DB + "'");
            if (!rs.next()) {
                System.out.println("[DB] PostgreSQL database '" + PG_DB + "' does not exist. Creating it now...");
                stmt.execute("CREATE DATABASE " + PG_DB);
                System.out.println("[DB] Database '" + PG_DB + "' created successfully!");
            }
        } catch (Exception e) {
            System.out.println("[DB] Maintenance check note: " + e.getMessage());
        }
    }

    public static String getDbEngineName() {
        return dbEngineName;
    }

    public static boolean isPostgresActive() {
        return activeUrl.startsWith("jdbc:postgresql:");
    }

    /**
     * Initialize database tables if they don't exist.
     */
    public static void initDatabase() throws SQLException {
        String createBillsTable = """
            CREATE TABLE IF NOT EXISTS bills (
                id                 VARCHAR(255) PRIMARY KEY,
                document_number    BIGINT,
                billing_type       VARCHAR(50),
                bill_status        VARCHAR(50),
                transfer_status    VARCHAR(50),
                currency           VARCHAR(10),
                due_date           DATE,
                billing_date       DATE,
                billing_profile_id VARCHAR(255),
                time_zone          VARCHAR(100),
                split_element      VARCHAR(255),
                contract_account   VARCHAR(255),
                create_invoice     BOOLEAN,
                customer_id        VARCHAR(255),
                customer_type      VARCHAR(50),
                customer_country   VARCHAR(100),
                market_id          VARCHAR(255),
                net_amount         DECIMAL(18,2),
                net_currency       VARCHAR(10),
                gross_amount       DECIMAL(18,2),
                gross_currency     VARCHAR(10),
                raw_json           TEXT,
                fetched_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String createFetchLogTable = isPostgresActive() ? """
            CREATE TABLE IF NOT EXISTS fetch_log (
                id          SERIAL PRIMARY KEY,
                fetched_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                bill_count  INT,
                status      VARCHAR(50),
                message     TEXT
            )
            """ : """
            CREATE TABLE IF NOT EXISTS fetch_log (
                id          INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                fetched_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                bill_count  INT,
                status      VARCHAR(50),
                message     CLOB
            )
            """;

        String createSubscriptionsTable = """
            CREATE TABLE IF NOT EXISTS subscriptions (
                id                 VARCHAR(255) PRIMARY KEY,
                subscription_id    VARCHAR(255),
                status             VARCHAR(100),
                customer_id        VARCHAR(255),
                market_id          VARCHAR(100),
                currency           VARCHAR(10),
                valid_from         VARCHAR(100),
                valid_until        VARCHAR(100),
                billing_profile_id VARCHAR(100),
                raw_json           TEXT,
                fetched_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createBillsTable);
            stmt.execute(createFetchLogTable);
            stmt.execute(createSubscriptionsTable);
            System.out.println("[DB] Database tables ('bills', 'subscriptions', 'fetch_log') verified/initialized in " + dbEngineName + ".");
        }
    }

    /**
     * Save bills to the database by automatically converting JSON to Bill POJOs (Auto POJO conversion).
     *
     * @param jsonArrayString Raw JSON from SAP API.
     * @return Number of bills saved/updated.
     */
    public static int saveBills(String jsonArrayString) throws Exception {
        System.out.println("[DB] Auto-converting SAP API JSON response to List<Bill> POJOs...");
        List<Bill> bills = GenericSapParser.parseList(jsonArrayString, Bill.class);
        System.out.println("[DB] Successfully auto-converted " + bills.size() + " Bill POJO objects (zero manual mapping).");
        return saveBills(bills);
    }

    /**
     * Save strongly-typed Bill POJOs to the database (upserting existing records).
     *
     * @param bills List of Bill POJO instances.
     * @return Number of bills saved/updated.
     */
    public static int saveBills(List<Bill> bills) throws Exception {
        if (bills == null || bills.isEmpty()) {
            return 0;
        }

        String checkSql = "SELECT id FROM bills WHERE id = ?";

        String insertSql = """
            INSERT INTO bills (id, document_number, billing_type, bill_status, transfer_status,
                               currency, due_date, billing_date, billing_profile_id, time_zone,
                               split_element, contract_account, create_invoice,
                               customer_id, customer_type, customer_country,
                               market_id, net_amount, net_currency, gross_amount, gross_currency,
                               raw_json, fetched_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;

        String updateSql = """
            UPDATE bills SET
                document_number = ?, billing_type = ?, bill_status = ?, transfer_status = ?,
                currency = ?, due_date = ?, billing_date = ?, billing_profile_id = ?, time_zone = ?,
                split_element = ?, contract_account = ?, create_invoice = ?,
                customer_id = ?, customer_type = ?, customer_country = ?,
                market_id = ?, net_amount = ?, net_currency = ?, gross_amount = ?, gross_currency = ?,
                raw_json = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        int count = 0;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psCheck = conn.prepareStatement(checkSql);
                 PreparedStatement psInsert = conn.prepareStatement(insertSql);
                 PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {

                for (Bill bill : bills) {
                    String id = bill.getId();
                    if (id == null) continue;

                    psCheck.setString(1, id);
                    ResultSet rs = psCheck.executeQuery();
                    boolean exists = rs.next();
                    rs.close();

                    String rawJson = GenericSapParser.toJson(bill);

                    if (exists) {
                        setBillParams(psUpdate, bill, rawJson, false);
                        psUpdate.setString(22, id);
                        psUpdate.executeUpdate();
                    } else {
                        psInsert.setString(1, id);
                        setBillParams(psInsert, bill, rawJson, true);
                        psInsert.executeUpdate();
                    }
                    count++;
                }

                conn.commit();
                System.out.println("[DB] Saved/Updated " + count + " bills in " + dbEngineName + " using Bill POJOs.");
                logFetch(conn, count, "SUCCESS", "Fetched and saved " + count + " bills using auto POJO conversion");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        return count;
    }

    /**
     * Binds typed Bill POJO properties to the SQL PreparedStatement.
     */
    private static void setBillParams(PreparedStatement ps, Bill bill, String rawJson, boolean isInsert) throws SQLException {
        int idx = isInsert ? 2 : 1;

        ps.setObject(idx++, bill.getDocumentNumber());
        ps.setString(idx++, bill.getBillingType() != null ? bill.getBillingType().getValue() : null);
        ps.setString(idx++, bill.getBillStatus() != null ? bill.getBillStatus().getValue() : null);
        ps.setString(idx++, bill.getTransferStatus() != null ? bill.getTransferStatus().getValue() : null);
        ps.setString(idx++, bill.getCurrency());

        LocalDate dueDate = bill.getDueDate();
        LocalDate billingDate = bill.getBillingDate();
        ps.setObject(idx++, dueDate);
        ps.setObject(idx++, billingDate);

        ps.setString(idx++, bill.getBillingProfileId());
        ps.setString(idx++, bill.getTimeZone());
        ps.setString(idx++, bill.getSplitElement());
        ps.setString(idx++, bill.getContractAccount());

        if (bill.getCreateInvoice() != null) {
            ps.setBoolean(idx++, bill.getCreateInvoice());
        } else {
            ps.setNull(idx++, Types.BOOLEAN);
        }

        Customer customer = bill.getCustomer();
        if (customer != null) {
            ps.setString(idx++, customer.getId());
            ps.setString(idx++, customer.getType() != null ? customer.getType().getValue() : null);
            ps.setString(idx++, customer.getCountry());
        } else {
            ps.setNull(idx++, Types.VARCHAR);
            ps.setNull(idx++, Types.VARCHAR);
            ps.setNull(idx++, Types.VARCHAR);
        }

        Market market = bill.getMarket();
        ps.setString(idx++, market != null ? market.getId() : null);

        MonetaryAmount netAmount = bill.getNetAmount();
        if (netAmount != null && netAmount.getAmount() != null) {
            ps.setBigDecimal(idx++, netAmount.getAmount());
            ps.setString(idx++, netAmount.getCurrency());
        } else {
            ps.setNull(idx++, Types.DECIMAL);
            ps.setNull(idx++, Types.VARCHAR);
        }

        MonetaryAmount grossAmount = bill.getGrossAmount();
        if (grossAmount != null && grossAmount.getAmount() != null) {
            ps.setBigDecimal(idx++, grossAmount.getAmount());
            ps.setString(idx++, grossAmount.getCurrency());
        } else {
            ps.setNull(idx++, Types.DECIMAL);
            ps.setNull(idx++, Types.VARCHAR);
        }

        ps.setString(idx++, rawJson);
    }

    /**
     * Query bills with optional filters.
     */
    public static String getAllBills(String status, String type, String currency) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT raw_json FROM bills WHERE 1=1");
        List<String> params = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            sql.append(" AND bill_status = ?");
            params.add(status);
        }
        if (type != null && !type.isEmpty()) {
            sql.append(" AND billing_type = ?");
            params.add(type);
        }
        if (currency != null && !currency.isEmpty()) {
            sql.append(" AND currency = ?");
            params.add(currency);
        }

        sql.append(" ORDER BY billing_date DESC, document_number DESC");

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }

            ResultSet rs = ps.executeQuery();
            StringBuilder jsonArray = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) jsonArray.append(",");
                jsonArray.append(rs.getString("raw_json"));
                first = false;
            }

            jsonArray.append("]");
            return jsonArray.toString();
        }
    }

    /**
     * Get a single bill by ID.
     */
    public static String getBillById(String id) throws SQLException {
        String sql = "SELECT raw_json FROM bills WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("raw_json");
            }
            return null;
        }
    }

    /**
     * Get aggregated statistics.
     */
    public static String getStats() throws SQLException {
        StringBuilder json = new StringBuilder("{");

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            json.append("\"dbEngine\":\"").append(escapeJson(dbEngineName)).append("\",");

            // Total count
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as total FROM bills");
            rs.next();
            json.append("\"totalBills\":").append(rs.getInt("total")).append(",");

            // By status
            rs = stmt.executeQuery(
                "SELECT bill_status, COUNT(*) as cnt FROM bills GROUP BY bill_status ORDER BY bill_status");
            json.append("\"byStatus\":{");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                String status = rs.getString("bill_status");
                json.append("\"").append(status != null ? status : "UNKNOWN").append("\":")
                    .append(rs.getInt("cnt"));
                first = false;
            }
            json.append("},");

            // By type
            rs = stmt.executeQuery(
                "SELECT billing_type, COUNT(*) as cnt FROM bills GROUP BY billing_type ORDER BY billing_type");
            json.append("\"byType\":{");
            first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                String type = rs.getString("billing_type");
                json.append("\"").append(type != null ? type : "UNKNOWN").append("\":")
                    .append(rs.getInt("cnt"));
                first = false;
            }
            json.append("},");

            // By currency
            rs = stmt.executeQuery(
                "SELECT currency, COUNT(*) as cnt FROM bills GROUP BY currency ORDER BY currency");
            json.append("\"byCurrency\":{");
            first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                String cur = rs.getString("currency");
                json.append("\"").append(cur != null ? cur : "UNKNOWN").append("\":")
                    .append(rs.getInt("cnt"));
                first = false;
            }
            json.append("},");

            // Total amounts
            rs = stmt.executeQuery(
                "SELECT COALESCE(SUM(net_amount), 0) as total_net, COALESCE(SUM(gross_amount), 0) as total_gross FROM bills");
            rs.next();
            json.append("\"totalNetAmount\":").append(rs.getBigDecimal("total_net")).append(",");
            json.append("\"totalGrossAmount\":").append(rs.getBigDecimal("total_gross")).append(",");

            // Currencies list
            rs = stmt.executeQuery("SELECT DISTINCT currency FROM bills WHERE currency IS NOT NULL ORDER BY currency");
            json.append("\"currencies\":[");
            first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("\"").append(rs.getString("currency")).append("\"");
                first = false;
            }
            json.append("],");

            // Last fetch info
            rs = stmt.executeQuery(
                "SELECT fetched_at, bill_count, status, message FROM fetch_log ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                json.append("\"lastSync\":{");
                json.append("\"at\":\"").append(rs.getTimestamp("fetched_at")).append("\",");
                json.append("\"count\":").append(rs.getInt("bill_count")).append(",");
                json.append("\"status\":\"").append(rs.getString("status")).append("\",");
                json.append("\"message\":\"").append(escapeJson(rs.getString("message"))).append("\"");
                json.append("}");
            } else {
                json.append("\"lastSync\":null");
            }

            json.append("}");
        }

        return json.toString();
    }

    /**
     * Get fetch log history.
     */
    public static String getFetchLog() throws SQLException {
        String sql = "SELECT id, fetched_at, bill_count, status, message FROM fetch_log ORDER BY id DESC LIMIT 50";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"fetchedAt\":\"").append(rs.getTimestamp("fetched_at")).append("\",");
                json.append("\"billCount\":").append(rs.getInt("bill_count")).append(",");
                json.append("\"status\":\"").append(rs.getString("status")).append("\",");
                json.append("\"message\":\"").append(escapeJson(rs.getString("message"))).append("\"");
                json.append("}");
                first = false;
            }
            json.append("]");
            return json.toString();
        }
    }

    private static void logFetch(Connection conn, int count, String status, String message) {
        String sql = "INSERT INTO fetch_log (bill_count, status, message) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setString(2, status);
            ps.setString(3, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Failed to log fetch: " + e.getMessage());
        }
    }

    public static void logFetchError(String message) {
        try (Connection conn = getConnection()) {
            logFetch(conn, 0, "ERROR", message);
        } catch (SQLException e) {
            System.err.println("[DB] Failed to log fetch error: " + e.getMessage());
        }
    }

    private static String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
