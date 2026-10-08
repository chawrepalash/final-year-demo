package projectsap.sap.bill.client;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;

/**
 * Apache HttpClient 5 client for fetching bill data from SAP Subscription Billing API.
 */
public class SapApiClient {

    private static final String API_KEY = "wITmrAEUGLzlYWC2my5UOKunJinBNwe4";
    private static final String API_URL = "https://sandbox.api.sap.com/subscriptionbilling/api/bill/v2/bills?pageNumber=1&pageSize=40&%24count=true";

    /**
     * Fetches bills from SAP Sandbox API using Apache HttpClient 5.
     *
     * @return Raw JSON response string from SAP API.
     * @throws IOException    If network or I/O error occurs.
     * @throws ParseException If response body parsing fails.
     */
    public static String fetchBillsFromSAP() throws IOException, ParseException {
        System.out.println("[SapApiClient] Initializing Apache HttpClient 5 request to SAP...");

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(15))
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet request = new HttpGet(API_URL);
            request.addHeader("APIKey", API_KEY);
            request.addHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                HttpEntity entity = response.getEntity();
                String responseBody = entity != null ? EntityUtils.toString(entity) : "";

                if (statusCode == 200) {
                    System.out.println("[SapApiClient] Successfully received HTTP 200 response from SAP API.");
                    return responseBody;
                } else {
                    System.err.println("[SapApiClient] API returned HTTP error " + statusCode + ": " + responseBody);
                    throw new IOException("SAP API request failed with status code " + statusCode + ": " + responseBody);
                }
            }
        }
    }
}
