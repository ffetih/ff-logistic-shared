package com.siose.ff.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the process that is allowed to call Keycloak ({@code identity.*}).
 */
@ConfigurationProperties(prefix = "identity")
public class IdentityProperties {

    /** Shared secret for POST /service-tokens. This is not the Keycloak client secret. */
    private String brokerKey;

    private final Keycloak keycloak = new Keycloak();

    private final Superset superset = new Superset();

    public String getBrokerKey() {
        return brokerKey;
    }

    public void setBrokerKey(String brokerKey) {
        this.brokerKey = brokerKey;
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public Superset getSuperset() {
        return superset;
    }

    public static class Keycloak {

        private String baseUrl;
        private String issuer;
        private String realm;
        private String apiClientId;
        private String audience;
        private String serviceClientId;
        private String serviceClientSecret;
        private String adminUsername;
        private String adminPassword;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getApiClientId() {
            return apiClientId;
        }

        public void setApiClientId(String apiClientId) {
            this.apiClientId = apiClientId;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getServiceClientId() {
            return serviceClientId;
        }

        public void setServiceClientId(String serviceClientId) {
            this.serviceClientId = serviceClientId;
        }

        public String getServiceClientSecret() {
            return serviceClientSecret;
        }

        public void setServiceClientSecret(String serviceClientSecret) {
            this.serviceClientSecret = serviceClientSecret;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }

    /**
     * The guest token Superset accepts. The secret and the audience are the same values
     * Superset is started with. An empty secret makes the endpoint answer 503.
     */
    public static class Superset {

        private String guestTokenSecret = "";
        private String guestTokenAudience = "ff-logistic";
        private Duration guestTokenTtl = Duration.ofMinutes(5);

        public String getGuestTokenSecret() {
            return guestTokenSecret;
        }

        public void setGuestTokenSecret(String guestTokenSecret) {
            this.guestTokenSecret = guestTokenSecret;
        }

        public String getGuestTokenAudience() {
            return guestTokenAudience;
        }

        public void setGuestTokenAudience(String guestTokenAudience) {
            this.guestTokenAudience = guestTokenAudience;
        }

        public Duration getGuestTokenTtl() {
            return guestTokenTtl;
        }

        public void setGuestTokenTtl(Duration guestTokenTtl) {
            this.guestTokenTtl = guestTokenTtl;
        }
    }
}
