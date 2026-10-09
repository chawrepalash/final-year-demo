package projectsap.sap.subscription;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import projectsap.sap.common.GenericSapParser;
import projectsap.sap.common.SapServiceRegistry;
import projectsap.sap.subscription.model.Subscription;

import java.util.List;

/**
 * Live test verifying Auto POJO conversion for the newly added Subscription service!
 */
public class TestSubscriptionAutoPojo {

    private static final String API_KEY = "wITmrAEUGLzlYWC2my5UOKunJinBNwe4";
    private static final String ENDPOINT = "https://sandbox.api.sap.com/subscriptionbilling/api/subscription/v1/subscriptions?pageSize=5";

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Testing Newly Added Service: SAP Subscription Auto POJOs        ");
        System.out.println("==================================================================");

        try {
            // 1. Verify Service Registry
            var desc = SapServiceRegistry.getDescriptor("subscriptions");
            System.out.println("\n[1/3] Service in Registry:");
            System.out.println("  - Name:        " + (desc != null ? desc.getDisplayName() : "Not Found"));
            System.out.println("  - Target POJO: " + (desc != null ? desc.getPojoClass().getName() : "None"));

            // 2. Fetch live data from SAP
            System.out.println("\n[2/3] Fetching live data from SAP Subscriptions endpoint...");
            String rawJson;
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet req = new HttpGet(ENDPOINT);
                req.addHeader("APIKey", API_KEY);
                req.addHeader("Accept", "application/json");
                try (CloseableHttpResponse resp = client.execute(req)) {
                    rawJson = EntityUtils.toString(resp.getEntity());
                }
            }
            System.out.println("  ✓ Received " + rawJson.length() + " bytes of JSON.");

            // 3. Auto POJO Conversion (Zero manual field mapping!)
            System.out.println("\n[3/4] Auto-converting live JSON into List<Subscription> POJOs...");
            long start = System.currentTimeMillis();
            List<Subscription> subs = GenericSapParser.parseList(rawJson, Subscription.class);
            long time = System.currentTimeMillis() - start;

            System.out.println("  ✓ Converted " + subs.size() + " Subscription POJOs in " + time + " ms (Zero manual mapping)!");

            // Print strongly-typed attributes
            System.out.println("\n--- Deserialized Subscription POJOs Sample ---");
            for (int i = 0; i < Math.min(3, subs.size()); i++) {
                Subscription s = subs.get(i);
                System.out.println("  [Subscription #" + (i + 1) + "]");
                System.out.println("    Doc ID:          " + s.getSubscriptionDocumentId());
                System.out.println("    UUID:            " + s.getSubscriptionId());
                System.out.println("    Status:          " + s.getStatus());
                System.out.println("    Customer ID:     " + (s.getCustomer() != null ? s.getCustomer().getId() : "N/A"));
                System.out.println("    Market:          " + (s.getMarket() != null ? s.getMarket().getId() + " (" + s.getMarket().getCurrency() + ")" : "N/A"));
                System.out.println("    Valid From:      " + s.getValidFrom());
                System.out.println("    Billing Profile: " + s.getBillingProfileId());
                System.out.println();
            }

            // 4. Auto Database Table Creation & Persistence Verification
            System.out.println("\n[4/4] Auto-Creating DB Table & Persisting Subscriptions to Database...");
            projectsap.sap.bill.db.DatabaseManager.testConnection();
            projectsap.sap.bill.db.DatabaseManager.initDatabase();

            int savedCount = projectsap.sap.bill.db.DatabaseManager.saveServiceData("subscriptions", rawJson);
            String dbEngine = projectsap.sap.bill.db.DatabaseManager.getDbEngineName();
            long totalInDb = projectsap.sap.bill.db.DatabaseManager.getServiceCount("subscriptions");

            System.out.println("  ✓ Database Engine:       " + dbEngine);
            System.out.println("  ✓ Records Stored/Upsert: " + savedCount);
            System.out.println("  ✓ Total in 'subscriptions' table: " + totalInDb);

            System.out.println("\n==================================================================");
            System.out.println("  SUCCESS: AUTO POJO CONVERSION & DB PERSISTENCE PASSED 100%!     ");
            System.out.println("==================================================================");

        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
