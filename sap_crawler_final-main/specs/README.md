# SAP Service Specifications & Auto POJO Generation

This directory stores OpenAPI (Swagger) specifications (`.json` or `.yaml`) for SAP services.

## How Auto POJO Generation Works

To add and support **ANY new SAP Service**:

1. **Download the OpenAPI specification** for the desired SAP service from the [SAP Business Accelerator Hub](https://api.sap.com) (e.g. `sap-subscription-billing.json`, `sap-s4hana-salesorder.json`, etc.).
2. Save the spec file in this `specs/` folder (e.g. `specs/my-sap-service.json`).
3. Run the OpenAPI generator using Maven or the provided batch script:

### Method A: Using Maven Command
```cmd
mvn openapi-generator:generate -Dopenapi.generator.maven.plugin.inputSpec=specs/my-sap-service.json -Dopenapi.generator.maven.plugin.modelPackage=projectsap.sap.<service_name>.model
```

### Method B: Using `generate_service_pojos.bat`
Run:
```cmd
generate_service_pojos.bat specs/my-sap-service.json <service_name>
```

4. **Register the new service in `SapServiceRegistry.java`**:
```java
SapServiceRegistry.register(new ServiceDescriptor<>(
    "salesorders",
    "SAP S/4HANA Sales Orders",
    "https://sandbox.api.sap.com/s4hanacloud/sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder",
    SalesOrder.class,
    "Sales order processing"
));
```

5. **Consume data dynamically without hardcoding**:
```java
List<SalesOrder> orders = GenericSapParser.parseList(jsonResponse, SalesOrder.class);
```
Zero manual field-by-field mapping required!
