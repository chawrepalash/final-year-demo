package projectsap.sap.bill.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI utility to inspect and display all tables and stored records in PostgreSQL / H2.
 */
public class DatabaseViewer {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("        SAP DATABASE INSPECTOR & DATA VIEWER                      ");
        System.out.println("==================================================================");

        boolean connected = DatabaseManager.testConnection();
        if (!connected) {
            System.err.println("[ERROR] Unable to connect to database!");
            return;
        }

        System.out.println("[DB] Active Database Engine: " + DatabaseManager.getDbEngineName());
        System.out.println();

        try (Connection conn = DatabaseManager.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> userTables = new ArrayList<>();

            // Discover tables
            try (ResultSet rs = meta.getTables(null, "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    userTables.add(rs.getString("TABLE_NAME"));
                }
            }
            if (userTables.isEmpty()) {
                try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String name = rs.getString("TABLE_NAME");
                        if (!name.startsWith("SYSTEM_") && !name.startsWith("INFORMATION_SCHEMA")) {
                            userTables.add(name);
                        }
                    }
                }
            }

            System.out.println("------------------------------------------------------------------");
            System.out.println("  TABLE SUMMARY IN DATABASE");
            System.out.println("------------------------------------------------------------------");
            for (String tbl : userTables) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tbl)) {
                    if (rs.next()) {
                        long count = rs.getLong(1);
                        System.out.printf("  • %-25s : %d records%n", tbl, count);
                    }
                } catch (Exception ignored) {}
            }
            System.out.println("------------------------------------------------------------------");
            System.out.println();

            // Display Subscriptions data if table exists
            if (containsIgnoreCase(userTables, "subscriptions")) {
                displayTableData(conn, "subscriptions", 10);
            }

            // Display Bills data if table exists
            if (containsIgnoreCase(userTables, "bills")) {
                displayTableData(conn, "bills", 5);
            }

            // Display any other custom service tables
            for (String tbl : userTables) {
                if (!tbl.equalsIgnoreCase("subscriptions") && 
                    !tbl.equalsIgnoreCase("bills") && 
                    !tbl.equalsIgnoreCase("fetch_log")) {
                    displayTableData(conn, tbl, 5);
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Error viewing database data: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("==================================================================");
        System.out.println("  End of Database Inspection");
        System.out.println("==================================================================");
    }

    private static boolean containsIgnoreCase(List<String> list, String target) {
        for (String s : list) {
            if (s.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private static void displayTableData(Connection conn, String tableName, int limit) {
        System.out.println("==================================================================");
        System.out.println("  DATA PREVIEW: Table '" + tableName.toUpperCase() + "' (Top " + limit + " rows)");
        System.out.println("==================================================================");

        String sql = "SELECT * FROM " + tableName + " ORDER BY fetched_at DESC LIMIT " + limit;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int colCount = rsmd.getColumnCount();

            // Collect columns to display (skip raw_json to keep output readable)
            List<Integer> displayCols = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                String colName = rsmd.getColumnName(i);
                if (!colName.equalsIgnoreCase("raw_json")) {
                    displayCols.add(i);
                }
            }

            // Print Header
            StringBuilder header = new StringBuilder("  ");
            for (int colIdx : displayCols) {
                String name = rsmd.getColumnName(colIdx);
                header.append(String.format("%-22s ", truncate(name, 20)));
            }
            System.out.println(header);
            System.out.println("  " + "-".repeat(Math.min(header.length() - 2, 110)));

            int rowNum = 0;
            while (rs.next()) {
                rowNum++;
                StringBuilder row = new StringBuilder("  ");
                for (int colIdx : displayCols) {
                    Object val = rs.getObject(colIdx);
                    String strVal = val != null ? val.toString() : "NULL";
                    row.append(String.format("%-22s ", truncate(strVal, 20)));
                }
                System.out.println(row);
            }

            if (rowNum == 0) {
                System.out.println("  (No rows found)");
            }
            System.out.println();

        } catch (Exception e) {
            System.err.println("  [!] Could not query table " + tableName + ": " + e.getMessage());
        }
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "NULL";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
}
