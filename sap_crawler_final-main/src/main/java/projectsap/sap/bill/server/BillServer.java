package projectsap.sap.bill.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import projectsap.sap.bill.client.SapApiClient;
import projectsap.sap.bill.db.DatabaseManager;
import projectsap.sap.common.GenericSapParser;
import projectsap.sap.common.SapServiceRegistry;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Web Server and API gateway for SAP Bill Dashboard.
 * Embeds Sun HttpServer with dynamic port fallback (8080 -> 8085 -> 8081...)
 */
public class BillServer {

    private static final int[] PREFERRED_PORTS = {8080, 8085, 8081, 8082, 8888};
    private static final String FRONTEND_DIR = resolveFrontendDir();

    public static void main(String[] args) throws Exception {
        System.out.println("=============================================");
        System.out.println("  SAP Subscription Billing Dashboard Server  ");
        System.out.println("=============================================");

        // ── 1. Initialize Database ────────────────────────────────
        System.out.println("[Server] Initializing database connection...");
        if (DatabaseManager.testConnection()) {
            DatabaseManager.initDatabase();
            System.out.println("[Server] Database initialized using: " + DatabaseManager.getDbEngineName());
        } else {
            System.err.println("[Server] ERROR: Database connection failed!");
        }

        // ── 2. Create HTTP Server on first available port ─────────
        HttpServer server = null;
        int activePort = 8080;

        for (int port : PREFERRED_PORTS) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                activePort = port;
                break;
            } catch (IOException e) {
                System.out.println("[Server] Port " + port + " is in use, trying next port...");
            }
        }

        if (server == null) {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            activePort = server.getAddress().getPort();
        }

        // ── REST API: Get Bills ────────────────────────────────────
        server.createContext("/api/bills", exchange -> {
            addCorsHeaders(exchange);
            if (handlePreflight(exchange)) return;

            try {
                String path = exchange.getRequestURI().getPath();

                // Single bill by ID
                if (path.length() > "/api/bills/".length()) {
                    String id = path.substring("/api/bills/".length());
                    String json = DatabaseManager.getBillById(id);
                    if (json != null) {
                        sendJson(exchange, 200, json);
                    } else {
                        sendJson(exchange, 404, "{\"error\":\"Bill not found with ID: " + escapeJson(id) + "\"}");
                    }
                    return;
                }

                // Filtered bill list
                Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getQuery());
                String status = queryParams.get("status");
                String type = queryParams.get("type");
                String currency = queryParams.get("currency");

                String json = DatabaseManager.getAllBills(status, type, currency);
                sendJson(exchange, 200, json);

            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // ── REST API: Statistics ───────────────────────────────────
        server.createContext("/api/stats", exchange -> {
            addCorsHeaders(exchange);
            if (handlePreflight(exchange)) return;

            try {
                String json = DatabaseManager.getStats();
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // ── REST API: Sync from SAP (Apache HttpClient 5) ─────────
        server.createContext("/api/sync", exchange -> {
            addCorsHeaders(exchange);
            if (handlePreflight(exchange)) return;

            try {
                System.out.println("[Sync] Fetching latest bills from SAP API via Apache HttpClient...");
                String json = SapApiClient.fetchBillsFromSAP();
                int count = DatabaseManager.saveBills(json);
                sendJson(exchange, 200,
                    "{\"success\":true,\"message\":\"Successfully synced " + count + " bills via Auto POJO Conversion (Zero Manual Mapping)\",\"count\":" + count + "}");
            } catch (Exception e) {
                e.printStackTrace();
                DatabaseManager.logFetchError(e.getMessage());
                sendJson(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // ── REST API: Fetch Log History ────────────────────────────
        server.createContext("/api/fetch-log", exchange -> {
            addCorsHeaders(exchange);
            if (handlePreflight(exchange)) return;

            try {
                String json = DatabaseManager.getFetchLog();
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // ── REST API: Registered SAP Services & Auto POJO Registry ─
        server.createContext("/api/services", exchange -> {
            addCorsHeaders(exchange);
            if (handlePreflight(exchange)) return;

            try {
                List<Map<String, Object>> serviceList = new ArrayList<>();
                for (SapServiceRegistry.ServiceDescriptor<?> desc : SapServiceRegistry.getAllDescriptors().values()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("serviceId", desc.getServiceId());
                    item.put("displayName", desc.getDisplayName());
                    item.put("defaultEndpoint", desc.getDefaultEndpoint());
                    item.put("pojoClass", desc.getPojoClass().getName());
                    item.put("description", desc.getDescription());
                    serviceList.add(item);
                }
                sendJson(exchange, 200, GenericSapParser.toJson(serviceList));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // ── Legacy Proxy Route ────────────────────────────────────
        server.createContext("/bills", exchange -> {
            addCorsHeaders(exchange);
            if (handlePreflight(exchange)) return;

            try {
                String json = SapApiClient.fetchBillsFromSAP();
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // ── Static Files: Frontend assets ──────────────────────────
        server.createContext("/frontend/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String filePath = path.substring("/frontend/".length());
            serveFile(exchange, filePath);
        });

        // ── Root: Main Dashboard ──────────────────────────────────
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                serveFile(exchange, "index.html");
            } else {
                serveFile(exchange, path.startsWith("/") ? path.substring(1) : path);
            }
        });

        server.setExecutor(null);
        server.start();

        System.out.println("[Server] Server successfully started!");
        System.out.println("[Server] Dashboard URL : http://localhost:" + activePort);
        System.out.println("[Server] Active Database: " + DatabaseManager.getDbEngineName());
        System.out.println("=============================================");
    }

    private static void serveFile(HttpExchange exchange, String relativePath) throws IOException {
        File file = new File(FRONTEND_DIR, relativePath);
        if (file.exists() && file.isFile()) {
            String contentType = getContentType(relativePath);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            byte[] bytes = Files.readAllBytes(file.toPath());
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().close();
                return;
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            String notFound = "404 File Not Found: " + relativePath;
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(404, bytes.length);
                exchange.getResponseBody().close();
                return;
            }
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().close();
            return;
        }
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception e) {
                    params.put(kv[0], kv[1]);
                }
            }
        }
        return params;
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }

    private static String resolveFrontendDir() {
        String[] candidates = {
            "frontend",
            "../frontend",
            "../../frontend"
        };
        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (dir.exists() && dir.isDirectory()) {
                try {
                    return dir.getCanonicalPath();
                } catch (IOException e) {
                    return dir.getAbsolutePath();
                }
            }
        }
        return "frontend";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
