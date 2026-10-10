# SAP Subscription Billing Crawler, Auto-POJO & Analytics Engine
## Comprehensive System Architecture & Engineering Technical Manual

---

### Table of Contents
1. [Executive Summary & Motivation](#1-executive-summary--motivation)
2. [End-to-End Architecture & Layer Diagram](#2-end-to-end-architecture--layer-diagram)
3. [The 4-Stage Automated Pipeline Lifecycle](#3-the-4-stage-automated-pipeline-lifecycle)
4. [Component-by-Component Deep Dive](#4-component-by-component-deep-dive)
5. [Critical Code Snippets & How They Work](#5-critical-code-snippets--how-they-work)
6. [Step-by-Step Example: Adding Any New SAP Service](#6-step-by-step-example-adding-any-new-sap-service)
7. [Database Architecture & Hybrid Dual-Engine Strategy](#7-database-architecture--hybrid-dual-engine-strategy)
8. [REST API Specification Reference](#8-rest-api-specification-reference)
9. [Operational Cheatsheet: Scripts & Commands](#9-operational-cheatsheet-scripts--commands)

---

## 1. Executive Summary & Motivation

In enterprise ERP environments, **SAP Subscription Billing** exposes microservices with intricate data models (Bills, Subscriptions, Customers, Products, Usage Records). Integrating these microservices typically requires weeks of tedious manual engineering:
- **Wrapper Complexity:** SAP responses are wrapped unpredictably across different endpoints (`bills`, `subscriptions`, `value`, `items`, `d.results`).
- **DTO Maintenance Nightmare:** Manually authoring Java POJO classes for dozens of nested objects leads to human error and breaks whenever SAP updates its schema.
- **Database Schema Management:** Developers must manually create SQL DDL migrations, define types, map relational keys, and handle database upsert logic.

### The Innovation of this Project
This project introduces a **zero-boilerplate, 1-Click Automation Architecture**:
1. **Dynamic Schema Inference:** Reads any arbitrary SAP endpoint response and reverse-engineers a compliant OpenAPI 3.0 specification.
2. **Auto POJO Generation:** Automatically runs OpenAPI Generator to produce strongly-typed Java models.
3. **Universal Jackson Parsing:** Unwraps any SAP payload dynamically without custom parser code.
4. **Dynamic Database Synthesis:** Auto-creates database tables with relational columns in **PostgreSQL** (with automated **H2 Embedded** fallback), preserving 100% data fidelity using `raw_json TEXT`.
5. **Interactive Web Dashboard:** Real-time analytics, Chart.js visualizations, filtering, and manual sync controls.

---

## 2. End-to-End Architecture & Layer Diagram

```
+---------------------------------------------------------------------------------------------------+
|                                   1. SAP CLOUD BACKEND LAYER                                      |
|    SAP API Business Hub Sandbox (https://sandbox.api.sap.com/subscriptionbilling/...)             |
|    Authentication: APIKey Header (wITmrAEUGLzlYWC2my5UOKunJinBNwe4)                               |
+---------------------------------------------------------------------------------------------------+
                                                  |
                                                  | HTTPS GET (JSON / Spec)
                                                  v
+---------------------------------------------------------------------------------------------------+
|                         2. NETWORK INGESTION ENGINE (SapApiClient.java)                           |
|    * Apache HttpClient 5 (Connection Pooling, TLS 1.3)                                            |
|    * RequestConfig Guards: 10s Connect Timeout, 15s Response Timeout                              |
+---------------------------------------------------------------------------------------------------+
             |                                                                 |
             | [Path A: Auto-Setup Pipeline]                                   | [Path B: Live Sync]
             v                                                                 v
+----------------------------------------------------+       +--------------------------------------+
|    3. AUTOMATION ENGINE (SapAutoServiceManager)    |       |    4. UNIVERSAL DESERIALIZER          |
|    * Schema Inferencer: Analyzes live JSON types   |       |       (GenericSapParser.java)        |
|    * Synthesizes specs/<service>-spec.json (OAS 3) |       |    * Jackson Databind + JavaTime     |
|    * Runs openapi-generator-maven-plugin           |       |    * Multi-wrapper unwrapper         |
|    * Generates POJOs in projectsap/sap/<service>   |       |      (bills, subscriptions, items, d)|
|    * Registers descriptor in SapServiceRegistry    |       |    * Zero manual field mapping       |
+----------------------------------------------------+       +--------------------------------------+
             |                                                                 |
             +-------------------------------+---------------------------------+
                                             |
                                             v
+---------------------------------------------------------------------------------------------------+
|                        5. DYNAMIC PERSISTENCE TIER (DatabaseManager.java)                         |
|    * Dynamic DDL Engine: Synthesizes 'CREATE TABLE IF NOT EXISTS <service>s'                      |
|    * Column Extraction: Typed scalars (status, customer_id, market_id) + Full raw_json TEXT      |
|    * ANSI Check-then-Upsert: Guaranteed compatibility across database engines                    |
|    * Dual-Engine Architecture: PostgreSQL (localhost:5432/sap_bills)  -->  H2 Embedded Fallback   |
+---------------------------------------------------------------------------------------------------+
                                             |
                                             v
+---------------------------------------------------------------------------------------------------+
|                     6. PRESENTATION & REST API TIER (BillServer.java / frontend)                  |
|    * Multi-threaded Java HTTP Server (Port 8080)                                                  |
|    * REST Routes: /api/bills, /api/stats, /api/sync, /api/sync/{service}, /api/data/{service}     |
|    * Glassmorphic Dashboard: Chart.js Metrics, Status Indicators, Search & Filter Controls        |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. The 4-Stage Automated Pipeline Lifecycle

When you run `auto_service_setup.bat <serviceName> <serviceUrl>`, the 4 stages execute sequentially:

```
[Service Link] ──> [1. Schema Inference] ──> [2. POJO Generation] ──> [3. Registry] ──> [4. DB Ingestion]
```

1. **Stage 1: Live Payload Ingestion & Schema Inference**
   - Fetches the live endpoint payload via Apache HttpClient 5.
   - If the response is already OpenAPI/Swagger, it is preserved directly.
   - If it is raw SAP data, `SapAutoServiceManager.inferOpenApiSpec()` crawls all JSON properties, classifies their types (integer, float, boolean, nested object, array), and generates a fully-compliant OpenAPI 3.0 JSON specification (`specs/<service>-spec.json`).

2. **Stage 2: Strong-Typed POJO Generation**
   - The engine triggers `openapi-generator-maven-plugin` programmatically.
   - OpenAPI Generator writes typed Java POJOs (with Jackson annotations, getters/setters, equals/hashCode) to `target/generated-sources/`.
   - The classes are copied to `src/main/java/projectsap/sap/<service>/model/`.

3. **Stage 3: Reflection-Safe Dynamic Registration**
   - The service is registered in `SapServiceRegistry` using `registerDynamic()`.
   - Uses `Class.forName()` so that other parts of the system do not require static compile-time class dependencies, ensuring the project remains 100% buildable even if a service model is modified or removed.

4. **Stage 4: Automated Database Table Creation & Upsert**
   - `DatabaseManager.ensureServiceTable()` verifies if the table exists in PostgreSQL.
   - If not, it builds the dynamic SQL DDL with typed columns + `raw_json TEXT`.
   - Live records are ingested using ANSI SQL upsert logic, ensuring both PostgreSQL and H2 Embedded work seamlessly.

---

## 4. Component-by-Component Deep Dive

| Component | File Path | Key Responsibilities |
|---|---|---|
| **`SapApiClient`** | `src/main/java/projectsap/sap/bill/client/SapApiClient.java` | Universal HTTP client using Apache HttpClient 5. Handles timeouts, TLS handshakes, and APIKey authentication headers. |
| **`GenericSapParser`** | `src/main/java/projectsap/sap/common/GenericSapParser.java` | Universal deserializer. Unwraps SAP wrapper keys (`bills`, `subscriptions`, `value`, `items`, `data`, `results`, `d.results`) and converts JSON into strongly-typed POJO lists. |
| **`SapServiceRegistry`** | `src/main/java/projectsap/sap/common/SapServiceRegistry.java` | Central service dictionary. Maintains metadata, default endpoints, and POJO class references dynamically. |
| **`SapAutoServiceManager`** | `src/main/java/projectsap/sap/common/SapAutoServiceManager.java` | 1-Click orchestrator for schema inference, Maven code generation, and immediate database table setup. |
| **`DatabaseManager`** | `src/main/java/projectsap/sap/bill/db/DatabaseManager.java` | Dual-engine database controller. Creates `sap_bills` database, synthesizes tables dynamically, binds parameters, and logs sync operations. |
| **`DatabaseViewer`** | `src/main/java/projectsap/sap/bill/db/DatabaseViewer.java` | Terminal database inspection tool. Discovers user tables and displays formatted record previews. |
| **`BillServer`** | `src/main/java/projectsap/sap/bill/server/BillServer.java` | Multi-threaded HTTP server hosting REST APIs and serving frontend static assets on port 8080. |
| **Frontend UI** | `frontend/index.html`, `app.js`, `styles.css` | Real-time analytics dashboard with Chart.js charts, status indicators, and multi-service controls. |

---

## 5. Critical Code Snippets & How They Work

### 1. Universal SAP Wrapper Extraction (`GenericSapParser.java`)
```java
public static JsonNode extractArrayNode(JsonNode root) {
    if (root == null) return null;
    if (root.isArray()) return root;

    // Detect standard SAP REST wrappers
    String[] wrapperKeys = {"bills", "subscriptions", "value", "items", "data", "results", "content"};
    for (String key : wrapperKeys) {
        if (root.has(key) && root.get(key).isArray()) {
            return root.get(key);
        }
    }

    // Detect SAP OData wrappers { "d": { "results": [ ... ] } }
    if (root.has("d")) {
        JsonNode dNode = root.get("d");
        if (dNode.has("results") && dNode.get("results").isArray()) {
            return dNode.get("results");
        }
        if (dNode.isArray()) return dNode;
    }
    return null;
}
```

### 2. Dynamic Table DDL Synthesis (`DatabaseManager.java`)
```java
public static void ensureServiceTable(Connection conn, String tableName, JsonNode sampleItem) throws SQLException {
    if (tableExists(conn, tableName)) return;

    StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
    ddl.append("    id VARCHAR(255) PRIMARY KEY,\n");

    if (sampleItem != null && sampleItem.isObject()) {
        Iterator<Map.Entry<String, JsonNode>> fields = sampleItem.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String col = toSnakeCase(entry.getKey());
            JsonNode val = entry.getValue();

            if (val.isObject() && val.has("id")) {
                ddl.append("    ").append(col).append("_id VARCHAR(255),\n");
            } else if (val.isBoolean()) {
                ddl.append("    ").append(col).append(" BOOLEAN,\n");
            } else if (val.isInt() || val.isLong()) {
                ddl.append("    ").append(col).append(" BIGINT,\n");
            } else if (val.isFloatingPointNumber() || val.isBigDecimal()) {
                ddl.append("    ").append(col).append(" DECIMAL(18,2),\n");
            } else {
                ddl.append("    ").append(col).append(" VARCHAR(255),\n");
            }
        }
    }
    ddl.append("    raw_json TEXT,\n");
    ddl.append("    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n");
    ddl.append("    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n)");

    conn.createStatement().execute(ddl.toString());
}
```

### 3. Dynamic REST Sync Routing (`BillServer.java`)
```java
server.createContext("/api/sync", exchange -> {
    String path = exchange.getRequestURI().getPath();
    String serviceName = path.length() > "/api/sync/".length() ? 
                         path.substring("/api/sync/".length()).trim() : "bills";

    if ("bills".equalsIgnoreCase(serviceName)) {
        String json = SapApiClient.fetchBillsFromSAP();
        int count = DatabaseManager.saveBills(json);
        sendJson(exchange, 200, "{\"success\":true,\"count\":" + count + "}");
    } else {
        var desc = SapServiceRegistry.getDescriptor(serviceName);
        String json = SapApiClient.fetchFromUrl(desc.getDefaultEndpoint());
        int count = DatabaseManager.saveServiceData(serviceName, json);
        sendJson(exchange, 200, "{\"success\":true,\"service\":\"" + serviceName + "\",\"count\":" + count + "}");
    }
});
```

---

## 6. Step-by-Step Example: Adding Any New SAP Service

### Scenario: Adding SAP "Product" Service
Suppose you want to ingest the SAP Subscription Billing **Products** endpoint:
`https://sandbox.api.sap.com/subscriptionbilling/api/product/v1/products`

#### Command:
```powershell
.\auto_service_setup.bat product "https://sandbox.api.sap.com/subscriptionbilling/api/product/v1/products"
```

#### What Happens in Seconds:
1. `SapAutoServiceManager` calls `SapApiClient` and downloads the live product JSON payload.
2. It discovers fields: `id`, `code`, `name`, `description`, `pricingSchemeId`, `createdAt`.
3. It generates `specs/product-spec.json`.
4. OpenAPI Generator builds `Product.java` in `src/main/java/projectsap/sap/product/model/`.
5. It registers `product` in `SapServiceRegistry`.
6. `DatabaseManager` connects to PostgreSQL and creates the table `products`:
   ```sql
   CREATE TABLE IF NOT EXISTS products (
       id VARCHAR(255) PRIMARY KEY,
       code VARCHAR(255),
       name VARCHAR(255),
       description TEXT,
       pricing_scheme_id VARCHAR(255),
       raw_json TEXT,
       fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );
   ```
7. It upserts the live products into PostgreSQL!
8. View the data immediately by running `.\view_db_data.bat` or opening `http://localhost:8080/api/data/products`.

---

## 7. Database Architecture & Hybrid Dual-Engine Strategy

```
           +---------------------------------------------------+
           |            DatabaseManager.getConnection()        |
           +---------------------------------------------------+
                                     |
                       [1. Try PostgreSQL: 5432]
                                     |
                    +----------------+----------------+
                    |                                 |
              [Success]                            [Fails]
                    v                                 v
     +------------------------------+   +------------------------------+
     |   PostgreSQL Engine Active   |   |   H2 Embedded Database       |
     |   Host: localhost:5432       |   |   File: ./data/sap_bills_db  |
     |   DB:   sap_bills            |   |   Mode: PostgreSQL-compatible|
     |   User: postgres             |   |   Zero External Dependency   |
     +------------------------------+   +------------------------------+
```

### Table Structure Summary
1. **`bills`**: Stores SAP invoice and billing line items.
2. **`subscriptions`**: Stores customer subscription agreements, lifecycles, and billing profiles.
3. **`fetch_log`**: Audit log recording timestamp, record count, and crawling status for every sync run.
4. **`<dynamic_service>s`**: Inferred tables synthesized dynamically for any newly added service.

---

## 8. REST API Specification Reference

| Method | Endpoint | Parameters | Description |
|---|---|---|---|
| `GET` | `/api/bills` | `status`, `type`, `currency` | Filtered list of stored bills |
| `GET` | `/api/bills/{id}` | `id` (Path) | Detailed single bill record by UUID |
| `GET` | `/api/stats` | None | Financial KPI aggregation, currency breakdown, and timeline |
| `GET / POST` | `/api/sync` | `service` (Query) | Crawls SAP for latest bills and persists to PostgreSQL |
| `GET / POST` | `/api/sync/{service}` | `service` (Path) | Crawls any registered service and persists to its dedicated DB table |
| `GET` | `/api/data/{service}` | `service` (Path / Query) | Returns stored records for any service directly from PostgreSQL/H2 |
| `GET` | `/api/services` | None | Lists all registered services with their table name and database record count |
| `GET` | `/api/fetch-log` | None | Audit log of crawling jobs |

---

## 9. Operational Cheatsheet: Scripts & Commands

```powershell
# 1. Run the Dashboard Web Server (Port 8080)
.\run.bat

# 2. Add any new SAP service (1-Click Automation)
.\auto_service_setup.bat <service_name> <service_url_or_spec>

# 3. View all tables and records in terminal
.\view_db_data.bat

# 4. Test Subscription POJO & Database persistence
.\test_subscription_pojo.bat

# 5. Test Bills Auto-POJO conversion
.\test_auto_pojo.bat
```

---
*Document generated as part of the SAP Subscription Billing Automation & Analytics Engineering Project.*
