package projectsap.sap.common;

import projectsap.sap.bill.model.Bill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central registry mapping SAP service identifiers to their target POJO classes and metadata.
 * Enables the system to support any arbitrary SAP service dynamically without hardcoded branching.
 */
public class SapServiceRegistry {

    public static class ServiceDescriptor<T> {
        private final String serviceId;
        private final String displayName;
        private final String defaultEndpoint;
        private final Class<T> pojoClass;
        private final String description;

        public ServiceDescriptor(String serviceId, String displayName, String defaultEndpoint, Class<T> pojoClass, String description) {
            this.serviceId = serviceId;
            this.displayName = displayName;
            this.defaultEndpoint = defaultEndpoint;
            this.pojoClass = pojoClass;
            this.description = description;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDefaultEndpoint() {
            return defaultEndpoint;
        }

        public Class<T> getPojoClass() {
            return pojoClass;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final Map<String, ServiceDescriptor<?>> REGISTRY = new LinkedHashMap<>();

    static {
        // Register SAP Subscription Billing - Bills Service
        register(new ServiceDescriptor<>(
                "bills",
                "SAP Subscription Billing - Bills",
                "https://sandbox.api.sap.com/subscriptionbilling/api/bill/v2/bills?pageNumber=1&pageSize=40&$count=true",
                Bill.class,
                "Retrieves bills and billing line items from SAP Subscription Billing API"
        ));
    }

    /**
     * Registers a new SAP service descriptor with its corresponding auto-generated POJO class.
     */
    public static <T> void register(ServiceDescriptor<T> descriptor) {
        REGISTRY.put(descriptor.getServiceId().toLowerCase(), descriptor);
    }

    /**
     * Retrieves the descriptor for a registered service ID.
     */
    @SuppressWarnings("unchecked")
    public static <T> ServiceDescriptor<T> getDescriptor(String serviceId) {
        if (serviceId == null) return null;
        return (ServiceDescriptor<T>) REGISTRY.get(serviceId.toLowerCase());
    }

    /**
     * Returns all registered service identifiers.
     */
    public static Set<String> getAvailableServices() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * Returns all registered service descriptors.
     */
    public static Map<String, ServiceDescriptor<?>> getAllDescriptors() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
