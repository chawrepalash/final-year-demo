package projectsap.sap.bill;

import projectsap.sap.bill.client.SapApiClient;

public class TestSapApi {
    public static void main(String[] args) {
        System.out.println("Testing SAP API with Apache HttpClient 5...");
        try {
            String json = SapApiClient.fetchBillsFromSAP();
            System.out.println("Received Response (length: " + json.length() + " bytes)");
            System.out.println("Snippet: " + json.substring(0, Math.min(200, json.length())));
            System.out.println("Apache HttpClient 5 test passed successfully!");
        } catch (Exception e) {
            System.err.println("API Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
