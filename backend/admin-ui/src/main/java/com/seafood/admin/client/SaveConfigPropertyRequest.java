package com.seafood.admin.client;

/**
 * Request DTO for saving configuration property.
 */
public class SaveConfigPropertyRequest {
    private String serviceName;
    private String profile;
    private String label;
    private String key;
    private String value;
    private boolean encrypted;

    public SaveConfigPropertyRequest() {
    }

    public SaveConfigPropertyRequest(String serviceName, String profile, String label, String key, String value, boolean encrypted) {
        this.serviceName = serviceName;
        this.profile = profile;
        this.label = label;
        this.key = key;
        this.value = value;
        this.encrypted = encrypted;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }
}
