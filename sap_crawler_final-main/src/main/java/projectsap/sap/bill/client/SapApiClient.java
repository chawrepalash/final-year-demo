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
        return fetchFromUrl(API_URL);
    }

    /**
     * Universal fetcher: Fetches JSON data from any SAP endpoint using Apache HttpClient 5 with APIKey.
     *
     * @param url SAP API endpoint URL.
     * @return Raw JSON response body.
     * @throws IOException    If network or I/O error occurs.
     * @throws ParseException If response body parsing fails.
     */
    public static String fetchFromUrl(String url) throws IOException, ParseException {
        System.out.println("[SapApiClient] Initializing Apache HttpClient 5 request to: " + url);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(15))
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet request = new HttpGet(url);
            request.addHeader("APIKey", API_KEY);
            request.addHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                HttpEntity entity = response.getEntity();
                String responseBody = entity != null ? EntityUtils.toString(entity) : "";

                if (statusCode >= 200 && statusCode < 300) {
                    System.out.println("[SapApiClient] Received HTTP " + statusCode + " response (" + responseBody.length() + " bytes).");
                    return responseBody;
                } else {
                    System.err.println("[SapApiClient] API returned HTTP error " + statusCode + ": " + responseBody);
                    throw new IOException("SAP API request failed with status code " + statusCode + ": " + responseBody);
                }
            }
        }
    }
}
