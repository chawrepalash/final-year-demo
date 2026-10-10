package projectsap.sap.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import projectsap.sap.bill.db.DatabaseManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

/**
 * Fully Automated SAP Service Setup & POJO Generation Manager.
 *
 * Given just a service name and a URL (or specification link):
 * 1. Automatically downloads the OpenAPI spec (or infers it directly from a live SAP endpoint response).
 * 2. Saves the normalized OpenAPI specification into specs/<serviceName>.json.
 * 3. Triggers the OpenAPI Generator to produce strongly-typed POJOs in src/main/java/projectsap/sap/<serviceName>/model/.
 * 4. Registers the new service in SapServiceRegistry.
 */
public class SapAutoServiceManager {

    private static final String API_KEY = "wITmrAEUGLzlYWC2my5UOKunJinBNwe4";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        String serviceName = null;
        String urlOrPath = null;

        if (args.length >= 2) {
            serviceName = args[0].trim().toLowerCase();
            urlOrPath = args[1].trim();
        }

        // Check environment variables or system properties if arguments are missing or truncated
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = System.getProperty("service.name", System.getenv("SERVICE_NAME"));
            if (serviceName != null) serviceName = serviceName.trim().toLowerCase();
        }
        if (urlOrPath == null || urlOrPath.isEmpty() || (urlOrPath.endsWith("?paging") && System.getenv("SERVICE_URL") != null)) {
            String envUrl = System.getProperty("service.url", System.getenv("SERVICE_URL"));
            if (envUrl != null && !envUrl.trim().isEmpty()) {
                urlOrPath = envUrl.trim();
            }
        }

        if (serviceName == null || urlOrPath == null || urlOrPath.isEmpty()) {
            System.out.println("==================================================================");
            System.out.println("  SAP Automated Service Setup & POJO Generator                   ");
            System.out.println("==================================================================");
            System.out.println("Usage: java SapAutoServiceManager <serviceName> <serviceUrlOrSpecPath>");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  1. With OpenAPI spec URL:");
            System.out.println("     java SapAutoServiceManager customer https://example.com/customer-openapi.json");
            System.out.println();
            System.out.println("  2. With SAP Sandbox API endpoint URL (Auto-infers schema & POJOs):");
            System.out.println("     java SapAutoServiceManager product https://sandbox.api.sap.com/subscriptionbilling/api/product/v1/products");
            System.out.println("==================================================================");
            return;
        }

        try {
            setupService(serviceName, urlOrPath);
        } catch (Exception e) {
            System.err.println("\n[ERROR] Service setup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes the complete end-to-end automation.
     */
    public static File setupService(String serviceName, String urlOrPath) throws Exception {
        System.out.println("==================================================================");
        System.out.println("  Setting Up SAP Service: " + serviceName.toUpperCase());
        System.out.println("==================================================================");

        // Step 1: Ensure specs directory exists
        Path specsDir = Paths.get("specs");
        if (!Files.exists(specsDir)) {
            Files.createDirectories(specsDir);
        }

        Path targetSpecPath = specsDir.resolve(serviceName + "-spec.json");

        // Step 2: Fetch or read content
        String content;
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            System.out.println("\n[Step 1/3] Fetching from URL: " + urlOrPath);
            content = fetchUrlContent(urlOrPath);
        } else {
            System.out.println("\n[Step 1/3] Reading from local file: " + urlOrPath);
            content = Files.readString(Paths.get(urlOrPath));
        }

        // Step 3: Check if content is already an OpenAPI/Swagger spec, or if we need to auto-infer it
        JsonNode root = mapper.readTree(content);
        boolean isOpenApi = root.has("openapi") || root.has("swagger");

        if (isOpenApi) {
            System.out.println("  ✓ Detected valid OpenAPI/Swagger specification.");
            Files.writeString(targetSpecPath, content);
        } else {
            System.out.println("  ℹ Received live API data payload. Automatically inferring OpenAPI 3.0 schema...");
            String inferredSpec = inferOpenApiSpec(serviceName, root);
            Files.writeString(targetSpecPath, inferredSpec);
            System.out.println("  ✓ Generated OpenAPI 3.0 specification from live payload.");
        }

        System.out.println("  ✓ Saved specification to: " + targetSpecPath.toAbsolutePath());

        // Step 4: Run POJO generation command
        System.out.println("\n[Step 2/4] Generating Java POJOs for " + serviceName + "...");
        runPojoGenerator(serviceName, targetSpecPath.toString());
        copyGeneratedFilesToSrc(serviceName);

        // Step 5: Auto-register in SapServiceRegistry
        System.out.println("\n[Step 3/4] Registering service in SapServiceRegistry...");
        String capitalizedModel = capitalize(serviceName);
        String fullPojoClassName = "projectsap.sap." + serviceName + ".model." + capitalizedModel;
        SapServiceRegistry.registerDynamic(
                serviceName,
                "SAP " + capitalizedModel + " Service",
                urlOrPath,
                fullPojoClassName,
                "Auto-generated service for SAP " + serviceName
        );

        // Step 6: Automatically create database table & store data
        System.out.println("\n[Step 4/4] Creating Database Table & Persisting Data in Database...");
        try {
            DatabaseManager.testConnection();
            DatabaseManager.initDatabase();
            String tableName = DatabaseManager.resolveTableName(serviceName);
            if (!isOpenApi) {
                int storedCount = DatabaseManager.saveServiceData(serviceName, content);
                System.out.println("  ✓ Database Table '" + tableName + "' created and active in " + DatabaseManager.getDbEngineName());
                System.out.println("  ✓ Stored " + storedCount + " records directly into database table '" + tableName + "'!");
            } else {
                System.out.println("  ✓ Database Table '" + tableName + "' ready for data persistence in " + DatabaseManager.getDbEngineName());
            }
        } catch (Exception e) {
            System.err.println("  ℹ Database initialization note: " + e.getMessage());
        }

        System.out.println("\n==================================================================");
        System.out.println("  Setup complete! Service POJOs and Database are ready.");
        System.out.println("  - Model Package:  projectsap.sap." + serviceName + ".model");
        System.out.println("  - Main POJO:      " + fullPojoClassName);
        System.out.println("  - Database Table: " + DatabaseManager.resolveTableName(serviceName));
        System.out.println("  - Specification:  " + targetSpecPath);
        System.out.println("==================================================================");

        return targetSpecPath.toFile();
    }

    private static void copyGeneratedFilesToSrc(String serviceName) {
        Path generatedDir = Paths.get("target/generated-sources/openapi/src/main/java/projectsap/sap/" + serviceName + "/model");
        Path targetDir = Paths.get("src/main/java/projectsap/sap/" + serviceName + "/model");

        if (Files.exists(generatedDir)) {
            try {
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                try (var stream = Files.list(generatedDir)) {
                    for (Path file : stream.toList()) {
                        if (Files.isRegularFile(file)) {
                            Files.copy(file, targetDir.resolve(file.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                System.out.println("  ✓ Copied " + serviceName + " POJOs to " + targetDir.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("  ⚠ Failed copying to src/main/java: " + e.getMessage());
            }
        }
    }

    private static String fetchUrlContent(String url) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.addHeader("APIKey", API_KEY);
            request.addHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int status = response.getCode();
                String body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
                if (status >= 200 && status < 300) {
                    return body;
                } else {
                    throw new IOException("HTTP " + status + " received from " + url + ": " + body);
                }
            }
        }
    }

    /**
     * Converts an arbitrary JSON response into a valid OpenAPI 3.0 specification document.
     */
    public static String inferOpenApiSpec(String serviceName, JsonNode data) throws Exception {
        ObjectNode openapi = mapper.createObjectNode();
        openapi.put("openapi", "3.0.0");

        ObjectNode info = openapi.putObject("info");
        info.put("title", "SAP " + capitalize(serviceName) + " Service API");
        info.put("version", "1.0.0");
        info.put("description", "Auto-generated OpenAPI 3.0 specification for SAP " + serviceName);

        openapi.putObject("paths");

        ObjectNode components = openapi.putObject("components");
        ObjectNode schemas = components.putObject("schemas");

        String modelName = capitalize(serviceName);

        JsonNode sampleItem = data;
        if (data.isArray() && data.size() > 0) {
            sampleItem = data.get(0);
        } else {
            String[] wrapperKeys = {"bills", "value", "items", "data", "results", "content", "subscriptions"};
            for (String key : wrapperKeys) {
                if (data.has(key) && data.get(key).isArray() && data.get(key).size() > 0) {
                    sampleItem = data.get(key).get(0);
                    break;
                }
            }
        }

        ObjectNode modelSchema = buildSchemaNode(sampleItem, modelName, schemas);
        schemas.set(modelName, modelSchema);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(openapi);
    }

    private static ObjectNode buildSchemaNode(JsonNode node, String name, ObjectNode schemas) {
        ObjectNode schema = mapper.createObjectNode();

        if (node == null || node.isNull()) {
            schema.put("type", "string");
            return schema;
        }

        if (node.isBoolean()) {
            schema.put("type", "boolean");
        } else if (node.isInt() || node.isLong()) {
            schema.put("type", "integer");
        } else if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            schema.put("type", "number");
        } else if (node.isTextual()) {
            schema.put("type", "string");
        } else if (node.isArray()) {
            schema.put("type", "array");
            if (node.size() > 0) {
                schema.set("items", buildSchemaNode(node.get(0), name + "Item", schemas));
            } else {
                ObjectNode itemNode = mapper.createObjectNode();
                itemNode.put("type", "object");
                schema.set("items", itemNode);
            }
        } else if (node.isObject()) {
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String propName = field.getKey();
                JsonNode propVal = field.getValue();

                if (propVal.isObject()) {
                    String nestedName = name + capitalize(propName);
                    ObjectNode nestedSchema = buildSchemaNode(propVal, nestedName, schemas);
                    schemas.set(nestedName, nestedSchema);

                    ObjectNode refNode = mapper.createObjectNode();
                    refNode.put("$ref", "#/components/schemas/" + nestedName);
                    properties.set(propName, refNode);
                } else if (propVal.isArray()) {
                    ObjectNode arrProp = mapper.createObjectNode();
                    arrProp.put("type", "array");
                    if (propVal.size() > 0 && propVal.get(0).isObject()) {
                        String itemModelName = name + capitalize(propName) + "Item";
                        ObjectNode itemSchema = buildSchemaNode(propVal.get(0), itemModelName, schemas);
                        schemas.set(itemModelName, itemSchema);

                        ObjectNode refNode = mapper.createObjectNode();
                        refNode.put("$ref", "#/components/schemas/" + itemModelName);
                        arrProp.set("items", refNode);
                    } else if (propVal.size() > 0) {
                        arrProp.set("items", buildSchemaNode(propVal.get(0), name + "Elem", schemas));
                    } else {
                        ObjectNode fallbackObj = mapper.createObjectNode();
                        fallbackObj.put("type", "object");
                        arrProp.set("items", fallbackObj);
                    }
                    properties.set(propName, arrProp);
                } else {
                    properties.set(propName, buildSchemaNode(propVal, propName, schemas));
                }
            }
        }

        return schema;
    }

    private static void runPojoGenerator(String serviceName, String specFilePath) {
        String mvnCmd = resolveMavenCmd();
        String modelPackage = "projectsap.sap." + serviceName + ".model";
        String outputDir = ".";

        java.util.List<String> command = new java.util.ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(mvnCmd);
        command.add("openapi-generator:generate");
        command.add("-Dopenapi.generator.maven.plugin.inputSpec=" + specFilePath);
        command.add("-Dopenapi.generator.maven.plugin.modelPackage=" + modelPackage);
        command.add("-Dopenapi.generator.maven.plugin.outputDir=" + outputDir);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("  ✓ Successfully generated POJOs in src/main/java/" + modelPackage.replace('.', '/'));
            } else {
                System.out.println("  ⚠ Maven command finished with code " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("  ⚠ Could not run Maven directly: " + e.getMessage());
            System.out.println("  Run 'generate_service_pojos.bat " + specFilePath + " " + serviceName + "' manually.");
        }
    }

    private static String resolveMavenCmd() {
        String ideaMvn = "C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2023.3.2\\plugins\\maven\\lib\\maven3\\bin\\mvn.cmd";
        if (new File(ideaMvn).exists()) {
            return ideaMvn;
        }
        return "mvn";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
