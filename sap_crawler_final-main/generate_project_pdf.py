import os
import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether, Preformatted, HRFlowable
)
from reportlab.pdfgen import canvas

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super(NumberedCanvas, self).__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_number(num_pages)
            canvas.Canvas.showPage(self)
        canvas.Canvas.save(self)

    def draw_page_number(self, page_count):
        if self._pageNumber == 1:
            # Skip page number on cover page
            return
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#64748B"))
        
        # Header rule & text
        self.setStrokeColor(colors.HexColor("#CBD5E1"))
        self.setLineWidth(0.5)
        self.line(40, letter[1] - 40, letter[0] - 40, letter[1] - 40)
        self.drawString(40, letter[1] - 35, "SAP Subscription Billing Crawler & Automation Engine — System Architecture")
        
        # Footer rule & text
        self.line(40, 45, letter[0] - 40, 45)
        page_text = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(letter[0] - 40, 32, page_text)
        self.drawString(40, 32, "Confidential — Internal Engineering & Academic Reference")
        self.restoreState()

def build_pdf(filename="SAP_Project_Architecture_Document.pdf"):
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=40,
        rightMargin=40,
        topMargin=50,
        bottomMargin=55
    )

    styles = getSampleStyleSheet()
    
    # Custom styles
    c_primary = colors.HexColor("#0F172A")    # Dark slate
    c_secondary = colors.HexColor("#1E3A8A")  # Royal blue
    c_accent = colors.HexColor("#0284C7")     # Ocean cyan
    c_dark = colors.HexColor("#334155")       # Text dark
    c_code_bg = colors.HexColor("#F8FAFC")    # Light grey bg
    c_code_border = colors.HexColor("#E2E8F0")

    title_style = ParagraphStyle(
        'CoverTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=26,
        leading=32,
        textColor=c_primary,
        alignment=0,
        spaceAfter=10
    )

    subtitle_style = ParagraphStyle(
        'CoverSubTitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=13,
        leading=18,
        textColor=c_accent,
        spaceAfter=20
    )

    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=17,
        leading=22,
        textColor=c_secondary,
        spaceBefore=14,
        spaceAfter=8,
        keepWithNext=True
    )

    h2_style = ParagraphStyle(
        'Heading2_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=12,
        leading=16,
        textColor=c_primary,
        spaceBefore=10,
        spaceAfter=6,
        keepWithNext=True
    )

    body_style = ParagraphStyle(
        'Body_Custom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        leading=14,
        textColor=c_dark,
        spaceAfter=7
    )

    bullet_style = ParagraphStyle(
        'Bullet_Custom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        leading=13.5,
        textColor=c_dark,
        leftIndent=15,
        spaceAfter=4
    )

    callout_style = ParagraphStyle(
        'Callout_Text',
        parent=styles['Normal'],
        fontName='Helvetica-Oblique',
        fontSize=9,
        leading=13,
        textColor=colors.HexColor("#1E293B")
    )

    code_style = ParagraphStyle(
        'Code_Block',
        parent=styles['Normal'],
        fontName='Courier',
        fontSize=8,
        leading=10.5,
        textColor=colors.HexColor("#0F172A")
    )

    story = []

    # ─────────────────────────────────────────────────────────────
    # COVER / HEADER BANNER
    # ─────────────────────────────────────────────────────────────
    story.append(Spacer(1, 15))
    story.append(Paragraph("SAP Subscription Billing Crawler & Automation Engine", title_style))
    story.append(Paragraph("End-to-End System Architecture, Auto-POJO Pipeline, Database Persistence & Engineering Manual", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=2, color=c_secondary, spaceBefore=0, spaceAfter=15))

    meta_table = Table([
        [Paragraph("<b>Project Version:</b> 1.0.0", body_style), Paragraph("<b>Tech Stack:</b> Java 21, Maven, PostgreSQL, H2, Jackson, Apache HC5", body_style)],
        [Paragraph("<b>Platform:</b> Windows / Cross-platform", body_style), Paragraph("<b>Target API:</b> SAP Subscription Billing Sandbox REST API", body_style)],
        [Paragraph("<b>Author / Team:</b> Final Year Engineering Project", body_style), Paragraph("<b>Document Type:</b> Comprehensive Architecture Reference", body_style)],
    ], colWidths=[260, 272])
    meta_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#F1F5F9")),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#CBD5E1")),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('TOPPADDING', (0,0), (-1,-1), 6),
        ('BOTTOMPADDING', (0,0), (-1,-1), 6),
        ('LEFTPADDING', (0,0), (-1,-1), 8),
        ('RIGHTPADDING', (0,0), (-1,-1), 8),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 15))

    # ─────────────────────────────────────────────────────────────
    # 1. EXECUTIVE SUMMARY & PROBLEM STATEMENT
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("1. Executive Summary & Problem Statement", h1_style))
    story.append(Paragraph(
        "Modern enterprise ERP systems like SAP expose dozens of interconnected REST/OData APIs (Bills, Subscriptions, "
        "Customers, Products, Rate Plans, Usage Records). However, standard integration workflows suffer from three critical bottlenecks:",
        body_style
    ))
    story.append(Paragraph("• <b>Rigid Schema Dependencies:</b> SAP APIs wrap responses in varying keys (<code>bills</code>, <code>subscriptions</code>, <code>value</code>, <code>items</code>, <code>d.results</code>). Traditional systems hardcode DTOs and fail when endpoints evolve.", bullet_style))
    story.append(Paragraph("• <b>Manual POJO Development:</b> Creating strongly-typed Java classes for nested enterprise payloads with dozens of fields takes days of boilerplate coding and introduces mapping bugs.", bullet_style))
    story.append(Paragraph("• <b>Manual Database Schema Maintenance:</b> Every new service requires writing custom SQL DDL migrations, mapping foreign keys, and maintaining upsert SQL statements.", bullet_style))
    story.append(Paragraph(
        "<b>Our Solution:</b> A 1-Click Automated Intelligence Pipeline. Given just a service name and endpoint URL, the engine auto-fetches live data, "
        "auto-infers OpenAPI 3.0 schema, generates strongly-typed Java POJOs via OpenAPI Generator, auto-creates database tables in PostgreSQL (with automatic embedded H2 fallback), "
        "and persists live records with zero manual mapping.",
        body_style
    ))
    story.append(Spacer(1, 10))

    # ─────────────────────────────────────────────────────────────
    # 2. HIGH-LEVEL SYSTEM ARCHITECTURE DIAGRAM
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("2. High-Level System Architecture Diagram", h1_style))
    story.append(Paragraph("The system is designed with a decoupled 6-tier modular architecture:", body_style))

    arch_ascii = """+-----------------------------------------------------------------------------------------------+
|                               1. SAP EXTERNAL CLOUD LAYER                                    |
|   SAP API Business Hub Sandbox  (Endpoint: https://sandbox.api.sap.com/subscriptionbilling/...)     |
|   Authentication: APIKey Header (wITmrAEUGLzlYWC2my5UOKunJinBNwe4)                           |
+-----------------------------------------------------------------------------------------------+
                                                |  HTTP GET (JSON Payload / Spec)
                                                v
+-----------------------------------------------------------------------------------------------+
|                       2. NETWORK & INGESTION ENGINE (SapApiClient.java)                       |
|   Apache HttpClient 5  *  Connection Pooling  *  Timeout Guards (10s connect / 15s response)  |
+-----------------------------------------------------------------------------------------------+
        |                                                               |
        | [Path A: Auto-Setup Pipeline]                                 | [Path B: Live Sync]
        v                                                               v
+-------------------------------------------------+     +---------------------------------------+
|  3. AUTOMATION ENGINE (SapAutoServiceManager)   |     |  4. UNIVERSAL DESERIALIZATION         |
|  * Auto-Infers OpenAPI 3.0 from Live JSON       |     |     (GenericSapParser.java)           |
|  * Generates specs/<service>-spec.json          |     |  * Jackson Databind + JavaTimeModule  |
|  * Invokes openapi-generator-maven-plugin       |     |  * Auto-Unwraps wrappers: bills,      |
|  * Produces typed POJOs in projectsap/sap/...   |     |    subscriptions, value, items, d     |
|  * Registers descriptor in SapServiceRegistry   |     |  * Converts JSON -> List<POJO>        |
+-------------------------------------------------+     +---------------------------------------+
        |                                                               |
        +-------------------------------+-------------------------------+
                                        |
                                        v
+-----------------------------------------------------------------------------------------------+
|                       5. DYNAMIC PERSISTENCE LAYER (DatabaseManager.java)                     |
|  * Auto-creates Tables (CREATE TABLE IF NOT EXISTS ...) with Typed Columns                    |
|  * ANSI-compatible Upsert (Check-then-Insert/Update)                                          |
|  * Full Raw JSON Preservation (raw_json TEXT) for 100% data fidelity                          |
|  * Dual Engine: PostgreSQL (sap_bills:5432)  -->  Automatic Fallback to H2 Embedded            |
+-----------------------------------------------------------------------------------------------+
                                        |
                                        v
+-----------------------------------------------------------------------------------------------+
|                      6. API SERVER & DASHBOARD (BillServer.java / frontend/)                  |
|  * REST Endpoints: /api/bills, /api/stats, /api/sync, /api/sync/{service}, /api/data/{service} |
|  * Modern Glassmorphism Dashboard: Chart.js Visualizations, Live Status, Search & Filters     |
+-----------------------------------------------------------------------------------------------+"""

    arch_table = Table([[Preformatted(arch_ascii, code_style)]], colWidths=[532])
    arch_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#0F172A")),
        ('TEXTCOLOR', (0,0), (-1,-1), colors.HexColor("#38BDF8")),
        ('BOX', (0,0), (-1,-1), 1.5, colors.HexColor("#1E3A8A")),
        ('TOPPADDING', (0,0), (-1,-1), 8),
        ('BOTTOMPADDING', (0,0), (-1,-1), 8),
        ('LEFTPADDING', (0,0), (-1,-1), 10),
        ('RIGHTPADDING', (0,0), (-1,-1), 8),
    ]))
    story.append(arch_table)
    story.append(Spacer(1, 15))

    # ─────────────────────────────────────────────────────────────
    # 3. END-TO-END PROCESS PIPELINE
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("3. End-to-End Pipeline Walkthrough", h1_style))
    story.append(Paragraph(
        "When an engineer or client adds a new service (e.g. <code>subscription</code>), the entire lifecycle occurs in 4 automated stages:",
        body_style
    ))

    pipeline_data = [
        ["Phase", "Component Responsible", "Detailed Operational Mechanism"],
        [
            "Phase 1: Ingestion & Schema Discovery",
            "SapAutoServiceManager\nSapApiClient",
            "Fetches live JSON data from the SAP endpoint using Apache HC5 with APIKey header. Evaluates payload: if already OpenAPI spec, stores directly; if live data, dynamically analyzes JSON data types (boolean, integer, float, string, nested objects) and infers a valid OpenAPI 3.0 specification."
        ],
        [
            "Phase 2: Code Generation & Compilation",
            "Maven OpenAPI Plugin\nSapServiceRegistry",
            "Executes 'openapi-generator-maven-plugin' in non-interactive mode. Produces strongly-typed Java POJOs in target/generated-sources and copies them to 'src/main/java/projectsap/sap/<service>/model/'. Registers descriptor dynamically into SapServiceRegistry without static compile-time lock."
        ],
        [
            "Phase 3: Database Synthesis & Ingestion",
            "DatabaseManager\n(PostgreSQL / H2)",
            "Checks PostgreSQL database 'sap_bills'. Generates dynamic DDL 'CREATE TABLE IF NOT EXISTS <service>s' with primary key 'id', typed relational columns (status, customer_id, market_id, currency, dates), plus 'raw_json TEXT' and timestamps. Upserts live records into the table."
        ],
        [
            "Phase 4: Serving & Visualization",
            "BillServer\nFrontend (HTML/JS)",
            "Exposes service via REST endpoints: '/api/sync/<service>' (fetch & persist) and '/api/data/<service>' (query stored records). The frontend dashboard displays active services, record counts, and metrics."
        ]
    ]

    pipe_table = Table([
        [Paragraph(f"<b>{cell}</b>", body_style) if i == 0 else Paragraph(cell.replace("\n", "<br/>"), body_style) for cell in row]
        for i, row in enumerate(pipeline_data)
    ], colWidths=[100, 130, 302])
    pipe_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor("#E2E8F0")),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#CBD5E1")),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 5),
        ('LEFTPADDING', (0,0), (-1,-1), 6),
        ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(pipe_table)
    story.append(Spacer(1, 15))

    # ─────────────────────────────────────────────────────────────
    # 4. COMPONENT-BY-COMPONENT DEEP DIVE
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("4. Core Component Breakdown", h1_style))

    components = [
        ("1. SapApiClient.java (Network Tier)", 
         "Located at: src/main/java/projectsap/sap/bill/client/SapApiClient.java",
         "Implements enterprise Apache HttpClient 5 with RequestConfig for connect timeout (10s) and response timeout (15s). Contains universal fetchFromUrl(String url) adding standard SAP headers (APIKey, Accept: application/json) and validating HTTP 2xx status codes."),

        ("2. GenericSapParser.java (Parsing Tier)",
         "Located at: src/main/java/projectsap/sap/common/GenericSapParser.java",
         "Configured with Jackson ObjectMapper, JavaTimeModule, and JsonNullableModule. Tolerates unknown properties and deserializes polymorphic structures. Uses extractArrayNode() to unwrap nested SAP keys (bills, subscriptions, value, items, data, results, d.results). Converts raw JSON to typed List<T> with zero manual field mapping."),

        ("3. SapServiceRegistry.java (Registry Tier)",
         "Located at: src/main/java/projectsap/sap/common/SapServiceRegistry.java",
         "Central directory of all recognized SAP microservices. Uses ServiceDescriptor<T> and reflection-safe registerDynamic(serviceId, name, endpoint, pojoClassName, desc) using Class.forName() to avoid hard compile-time circular dependencies."),

        ("4. SapAutoServiceManager.java (Automation Tier)",
         "Located at: src/main/java/projectsap/sap/common/SapAutoServiceManager.java",
         "1-Click CLI orchestrator. Reads/fetches endpoints, converts sample JSON tree into OpenAPI 3.0 schema document, executes OpenAPI Generator maven plugin, migrates generated classes, and triggers immediate DB creation & record persistence."),

        ("5. DatabaseManager.java (Data Tier)",
         "Located at: src/main/java/projectsap/sap/bill/db/DatabaseManager.java",
         "Hybrid PostgreSQL (primary on localhost:5432/sap_bills) with embedded H2 fallback. Features ensureServiceTable() for dynamic DDL synthesis, saveServiceData() for ANSI check-then-upserting, and getServiceData() / getServiceCount() for querying."),

        ("6. BillServer.java (Presentation & API Tier)",
         "Located at: src/main/java/projectsap/sap/bill/server/BillServer.java",
         "Lightweight, multi-threaded Java HTTP server (port 8080). Handles CORS headers, serves static frontend dashboard, and routes REST endpoints: /api/bills, /api/stats, /api/sync, /api/sync/{service}, /api/data/{service}, /api/services, /api/fetch-log."),

        ("7. DatabaseViewer.java & view_db_data.bat (Verification Tier)",
         "Located at: src/main/java/projectsap/sap/bill/db/DatabaseViewer.java",
         "Dedicated CLI inspection utility. Inspects JDBC metadata, counts all records in all user tables, and displays formatted previews directly in the console.")
    ]

    for title, loc, desc in components:
        story.append(Paragraph(f"<b>{title}</b>", h2_style))
        story.append(Paragraph(f"<i>{loc}</i>", ParagraphStyle('Ital', parent=body_style, textColor=c_accent, fontSize=8.5)))
        story.append(Paragraph(desc, body_style))
        story.append(Spacer(1, 4))

    story.append(Spacer(1, 10))

    # ─────────────────────────────────────────────────────────────
    # 5. CRITICAL CODE SNIPPETS
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("5. Critical Code Snippets Everyone Must Know", h1_style))
    story.append(Paragraph("The following snippets represent the core intellectual property of this architecture:", body_style))

    # Snippet 1
    story.append(Paragraph("Snippet 1: Universal SAP Wrapper Extraction (GenericSapParser.java)", h2_style))
    snip1 = """public static JsonNode extractArrayNode(JsonNode root) {
    if (root == null) return null;
    if (root.isArray()) return root;

    // Standard SAP REST wrappers
    String[] wrapperKeys = {"bills", "subscriptions", "value", "items", "data", "results", "content"};
    for (String key : wrapperKeys) {
        if (root.has(key) && root.get(key).isArray()) {
            return root.get(key);
        }
    }
    // SAP OData wrappers { "d": { "results": [ ... ] } }
    if (root.has("d")) {
        JsonNode dNode = root.get("d");
        if (dNode.has("results") && dNode.get("results").isArray()) return dNode.get("results");
        if (dNode.isArray()) return dNode;
    }
    return null;
}"""
    s1_table = Table([[Preformatted(snip1, code_style)]], colWidths=[532])
    s1_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), c_code_bg),
        ('BOX', (0,0), (-1,-1), 1, c_code_border),
        ('TOPPADDING', (0,0), (-1,-1), 5), ('BOTTOMPADDING', (0,0), (-1,-1), 5),
        ('LEFTPADDING', (0,0), (-1,-1), 6), ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(s1_table)
    story.append(Spacer(1, 8))

    # Snippet 2
    story.append(Paragraph("Snippet 2: Dynamic Table DDL Synthesis (DatabaseManager.java)", h2_style))
    snip2 = """public static void ensureServiceTable(Connection conn, String tableName, JsonNode sampleItem) throws SQLException {
    if (tableExists(conn, tableName)) return;

    StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\\n");
    ddl.append("    id VARCHAR(255) PRIMARY KEY,\\n");

    if (sampleItem != null && sampleItem.isObject()) {
        Iterator<Map.Entry<String, JsonNode>> fields = sampleItem.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String col = toSnakeCase(entry.getKey());
            JsonNode val = entry.getValue();

            if (val.isObject() && val.has("id")) ddl.append("    ").append(col).append("_id VARCHAR(255),\\n");
            else if (val.isBoolean())            ddl.append("    ").append(col).append(" BOOLEAN,\\n");
            else if (val.isInt() || val.isLong())ddl.append("    ").append(col).append(" BIGINT,\\n");
            else if (val.isFloatingPointNumber())ddl.append("    ").append(col).append(" DECIMAL(18,2),\\n");
            else                                 ddl.append("    ").append(col).append(" VARCHAR(255),\\n");
        }
    }
    ddl.append("    raw_json TEXT,\\n    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\\n    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\\n)");
    conn.createStatement().execute(ddl.toString());
}"""
    s2_table = Table([[Preformatted(snip2, code_style)]], colWidths=[532])
    s2_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), c_code_bg),
        ('BOX', (0,0), (-1,-1), 1, c_code_border),
        ('TOPPADDING', (0,0), (-1,-1), 5), ('BOTTOMPADDING', (0,0), (-1,-1), 5),
        ('LEFTPADDING', (0,0), (-1,-1), 6), ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(s2_table)
    story.append(Spacer(1, 8))

    # Snippet 3
    story.append(Paragraph("Snippet 3: Dynamic Multi-Service Sync Route (BillServer.java)", h2_style))
    snip3 = """server.createContext("/api/sync", exchange -> {
    String path = exchange.getRequestURI().getPath();
    String serviceName = path.length() > "/api/sync/".length() ? 
                         path.substring("/api/sync/".length()).trim() : "bills";

    if ("bills".equalsIgnoreCase(serviceName)) {
        String json = SapApiClient.fetchBillsFromSAP();
        int count = DatabaseManager.saveBills(json);
        sendJson(exchange, 200, "{\\"success\\":true,\\"count\\":" + count + "}");
    } else {
        var desc = SapServiceRegistry.getDescriptor(serviceName);
        String json = SapApiClient.fetchFromUrl(desc.getDefaultEndpoint());
        int count = DatabaseManager.saveServiceData(serviceName, json);
        sendJson(exchange, 200, "{\\"success\\":true,\\"service\\":\\"" + serviceName + "\\",\\"count\\":" + count + "}");
    }
});"""
    s3_table = Table([[Preformatted(snip3, code_style)]], colWidths=[532])
    s3_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), c_code_bg),
        ('BOX', (0,0), (-1,-1), 1, c_code_border),
        ('TOPPADDING', (0,0), (-1,-1), 5), ('BOTTOMPADDING', (0,0), (-1,-1), 5),
        ('LEFTPADDING', (0,0), (-1,-1), 6), ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(s3_table)
    story.append(Spacer(1, 15))

    # ─────────────────────────────────────────────────────────────
    # 6. WALKTHROUGH EXAMPLE: ADDING A NEW SERVICE
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("6. Practical Walkthrough: Adding a Service from Scratch", h1_style))
    story.append(Paragraph("Suppose SAP introduces a new microservice: <b>Customer Management</b>. Here is the exact end-to-end user workflow:", body_style))

    story.append(Paragraph("<b>Step 1: Execute 1-Click Setup Command</b>", h2_style))
    story.append(Paragraph("The developer runs the setup script with the service name and SAP Sandbox endpoint URL:", body_style))
    step1_cmd = """.\\auto_service_setup.bat customer "https://sandbox.api.sap.com/subscriptionbilling/api/customer/v1/customers\""""
    story.append(Table([[Preformatted(step1_cmd, code_style)]], colWidths=[532], style=[('BACKGROUND',(0,0),(-1,-1),c_code_bg),('BOX',(0,0),(-1,-1),1,c_code_border)]))
    story.append(Spacer(1, 6))

    story.append(Paragraph("<b>Step 2: What Happens Automatically Under the Hood</b>", h2_style))
    story.append(Paragraph("1. <b>Live Ingestion:</b> Connects to SAP, downloads customers payload (JSON).", bullet_style))
    story.append(Paragraph("2. <b>Schema Generation:</b> Synthesizes <code>specs/customer-spec.json</code> conforming to OpenAPI 3.0.", bullet_style))
    story.append(Paragraph("3. <b>POJO Generation:</b> Produces <code>Customer.java</code>, <code>CustomerAddress.java</code> in <code>projectsap.sap.customer.model</code>.", bullet_style))
    story.append(Paragraph("4. <b>Registry Update:</b> <code>customer</code> descriptor registered in <code>SapServiceRegistry</code>.", bullet_style))
    story.append(Paragraph("5. <b>Database Table Synthesis:</b> Executes <code>CREATE TABLE IF NOT EXISTS customers (...)</code> in PostgreSQL.", bullet_style))
    story.append(Paragraph("6. <b>Persistence:</b> Inserts all customer records into PostgreSQL <code>customers</code> table.", bullet_style))
    story.append(Spacer(1, 6))

    story.append(Paragraph("<b>Step 3: Immediate Verification</b>", h2_style))
    story.append(Paragraph("• <b>In Terminal:</b> Run <code>.\\view_db_data.bat</code> -> See <code>customers</code> table with row count and columns preview.<br/>"
                           "• <b>In Browser:</b> Open <code>http://localhost:8080/api/data/customers</code> -> Returns stored customer records.<br/>"
                           "• <b>Live Sync:</b> Open <code>http://localhost:8080/api/sync/customer</code> -> Re-crawls SAP and updates PostgreSQL.", body_style))
    story.append(Spacer(1, 15))

    # ─────────────────────────────────────────────────────────────
    # 7. DATABASE SCHEMA SPECIFICATIONS
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("7. Database Schema Reference (PostgreSQL / H2)", h1_style))
    
    db_schema_data = [
        ["Table Name", "Primary Columns", "Key Relational Columns", "Special Columns"],
        [
            "bills",
            "id VARCHAR(255) PK",
            "document_number BIGINT, billing_type, bill_status, customer_id, market_id, net_amount, gross_amount, due_date, billing_date",
            "raw_json TEXT\nfetched_at TIMESTAMP\nupdated_at TIMESTAMP"
        ],
        [
            "subscriptions",
            "id VARCHAR(255) PK",
            "subscription_id, status, customer_id, market_id, currency, valid_from, valid_until, billing_profile_id",
            "raw_json TEXT\nfetched_at TIMESTAMP\nupdated_at TIMESTAMP"
        ],
        [
            "fetch_log",
            "id SERIAL / IDENTITY PK",
            "bill_count INT, status VARCHAR(50), message TEXT",
            "fetched_at TIMESTAMP"
        ],
        [
            "<dynamic_service>s",
            "id VARCHAR(255) PK",
            "Dynamically inferred scalar fields (_id, status, type, amounts, booleans)",
            "raw_json TEXT\nfetched_at TIMESTAMP\nupdated_at TIMESTAMP"
        ]
    ]

    db_table = Table([
        [Paragraph(f"<b>{cell}</b>", body_style) if i == 0 else Paragraph(cell.replace("\n", "<br/>"), body_style) for cell in row]
        for i, row in enumerate(db_schema_data)
    ], colWidths=[90, 110, 212, 120])
    db_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor("#E2E8F0")),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#CBD5E1")),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
    ]))
    story.append(db_table)
    story.append(Spacer(1, 15))

    # ─────────────────────────────────────────────────────────────
    # 8. REST API SPECIFICATION
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("8. REST API Endpoints Reference", h1_style))

    api_data = [
        ["Method", "Endpoint", "Parameters", "Description"],
        ["GET", "/api/bills", "status, type, currency", "Queries bills from database with optional filters."],
        ["GET", "/api/bills/{id}", "id (Path)", "Retrieves single bill record by UUID."],
        ["GET", "/api/stats", "None", "Computes revenue, open bill counts, currency metrics, and time-series."],
        ["GET / POST", "/api/sync", "None / service=bills", "Crawls SAP Subscription Billing for latest bills and persists to DB."],
        ["GET / POST", "/api/sync/{service}", "service (Path)", "Crawls any registered SAP service and persists to its dedicated DB table."],
        ["GET", "/api/data/{service}", "service (Path / Query)", "Queries stored records for any service directly from PostgreSQL/H2."],
        ["GET", "/api/services", "None", "Returns all registered services, model class, table name, and DB row count."],
        ["GET", "/api/fetch-log", "None", "Returns recent crawling activity history and status logs."]
    ]

    api_table = Table([
        [Paragraph(f"<b>{cell}</b>", body_style) if i == 0 else Paragraph(cell, body_style) for cell in row]
        for i, row in enumerate(api_data)
    ], colWidths=[65, 125, 110, 232])
    api_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor("#E2E8F0")),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#CBD5E1")),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
    ]))
    story.append(api_table)
    story.append(Spacer(1, 15))

    # ─────────────────────────────────────────────────────────────
    # 9. OPERATIONAL CHEATSHEET
    # ─────────────────────────────────────────────────────────────
    story.append(Paragraph("9. Operational Cheatsheet (Scripts & Commands)", h1_style))

    ops_data = [
        ["Script / Command", "Purpose", "When to Use"],
        ["run.bat", "Compiles code, connects to PostgreSQL, and starts web server on port 8080.", "Daily operation, demoing the dashboard, monitoring."],
        ["auto_service_setup.bat <name> <url>", "1-Click automated POJO generation, registry update, and database table creation.", "When integrating any new SAP microservice."],
        ["view_db_data.bat", "CLI database viewer. Queries PostgreSQL/H2 and prints formatted tables and records.", "Quick terminal validation of database records."],
        ["test_subscription_pojo.bat", "Tests Subscription POJO deserialization and database persistence end-to-end.", "Regression testing subscription pipeline."],
        ["test_auto_pojo.bat", "Tests Bills Auto-POJO conversion and zero-mapping parsing.", "Regression testing bills parser."]
    ]

    ops_table = Table([
        [Paragraph(f"<b>{cell}</b>", body_style) if i == 0 else Paragraph(cell, body_style) for cell in row]
        for i, row in enumerate(ops_data)
    ], colWidths=[130, 210, 192])
    ops_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor("#E2E8F0")),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#CBD5E1")),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
    ]))
    story.append(ops_table)
    story.append(Spacer(1, 20))

    # Conclusion note
    story.append(Paragraph("<b>Engineering Summary:</b> This project transforms complex enterprise SAP API integration into a fully automated, dynamic, self-healing pipeline. By combining dynamic schema inference, automated code generation, and schema-adaptive SQL persistence, any SAP microservice can be integrated, stored, and visualized in seconds.", ParagraphStyle('Summary', parent=body_style, fontName='Helvetica-Oblique', textColor=c_primary)))

    # Build PDF with page numbering
    doc.build(story, canvasmaker=NumberedCanvas)
    print(f"[SUCCESS] PDF Generated successfully at: {os.path.abspath(filename)}")

if __name__ == "__main__":
    out_pdf = "SAP_Subscription_Billing_System_Architecture.pdf"
    if len(sys.argv) > 1:
        out_pdf = sys.argv[1]
    build_pdf(out_pdf)
