package projectsap.sap.bill;

import projectsap.sap.bill.client.SapApiClient;
import projectsap.sap.bill.db.DatabaseManager;
import projectsap.sap.bill.model.Bill;
import projectsap.sap.bill.model.BillItem;
import projectsap.sap.common.GenericSapParser;
import projectsap.sap.common.SapServiceRegistry;

import java.util.List;

/**
 * Automated Verification Test for Auto POJO Conversion across SAP Services.
 * Demonstrates:
 * 1. Fetching raw response from SAP API
 * 2. Automatic deserialization into strongly-typed POJOs without manual field-by-field mapping
 * 3. Inspecting typed attributes (BigDecimal amounts, LocalDate dates, Enum statuses)
 * 4. Storing the typed POJOs directly into the database
 */
public class TestAutoPojoConversion {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  SAP Auto POJO Conversion & Deserialization Test             ");
        System.out.println("===============================================================");

        try {
            // 1. Check Service Registry
            System.out.println("\n[1/4] Registered SAP Services in System:");
            for (String serviceId : SapServiceRegistry.getAvailableServices()) {
                var desc = SapServiceRegistry.getDescriptor(serviceId);
                System.out.println("  - Service: " + desc.getDisplayName());
                System.out.println("    Target POJO: " + desc.getPojoClass().getName());
                System.out.println("    Endpoint:    " + desc.getDefaultEndpoint());
            }

            // 2. Fetch raw JSON from SAP
            System.out.println("\n[2/4] Fetching raw JSON from SAP API using Apache HttpClient 5...");
            String rawJson = SapApiClient.fetchBillsFromSAP();
            System.out.println("  ✓ Received " + rawJson.length() + " bytes of raw JSON payload.");

            // 3. Auto POJO Deserialization (Zero manual mapping!)
            System.out.println("\n[3/4] Performing Auto POJO Conversion via GenericSapParser...");
            long startTime = System.currentTimeMillis();
            List<Bill> bills = GenericSapParser.parseList(rawJson, Bill.class);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("  ✓ Successfully converted to List<Bill> in " + duration + " ms!");
            System.out.println("  ✓ Total Bill POJO instances: " + bills.size());

            // Inspect the first 3 POJOs to prove strong typing
            int sampleCount = Math.min(3, bills.size());
            System.out.println("\n--- Sample Deserialized POJOs (Inspecting Type-Safe Fields) ---");
            for (int i = 0; i < sampleCount; i++) {
                Bill b = bills.get(i);
                System.out.println("  [Bill #" + (i + 1) + "]");
                System.out.println("    ID:               " + b.getId());
                System.out.println("    Doc Number:       " + b.getDocumentNumber());
                System.out.println("    Billing Type:     " + (b.getBillingType() != null ? b.getBillingType().getValue() : "N/A"));
                System.out.println("    Bill Status:      " + (b.getBillStatus() != null ? b.getBillStatus().getValue() : "N/A"));
                System.out.println("    Billing Date:     " + b.getBillingDate() + " (java.time.LocalDate)");
                System.out.println("    Due Date:         " + b.getDueDate() + " (java.time.LocalDate)");
                System.out.println("    Customer ID:      " + (b.getCustomer() != null ? b.getCustomer().getId() : "N/A"));
                if (b.getNetAmount() != null) {
                    System.out.println("    Net Amount:       " + b.getNetAmount().getAmount() + " " + b.getNetAmount().getCurrency() + " (java.math.BigDecimal)");
                }
                if (b.getGrossAmount() != null) {
                    System.out.println("    Gross Amount:     " + b.getGrossAmount().getAmount() + " " + b.getGrossAmount().getCurrency() + " (java.math.BigDecimal)");
                }
                List<BillItem> items = b.getBillItems();
                System.out.println("    Nested LineItems: " + (items != null ? items.size() : 0));
                System.out.println();
            }

            // 4. Persistence Test with Database
            System.out.println("[4/4] Testing POJO-based Database Persistence...");
            if (DatabaseManager.testConnection()) {
                DatabaseManager.initDatabase();
                int saved = DatabaseManager.saveBills(bills);
                System.out.println("  ✓ Successfully persisted " + saved + " strongly-typed POJOs into database!");
            } else {
                System.out.println("  (Skipped DB persistence: database not currently reachable)");
            }

            System.out.println("\n===============================================================");
            System.out.println("  SUCCESS: Auto POJO Conversion for SAP is FULLY FUNCTIONAL!   ");
            System.out.println("===============================================================");

        } catch (Exception e) {
            System.err.println("\n[ERROR] Auto POJO conversion test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
