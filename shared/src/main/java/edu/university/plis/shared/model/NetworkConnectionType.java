package edu.university.plis.shared.model;

public enum NetworkConnectionType {
    ANY("Ethernet or Wi-Fi"),
    WIRED_LAN("Wired LAN / Ethernet"),
    WIFI("Wi-Fi"),
    UNKNOWN("Unknown connection");

    private final String displayName;

    NetworkConnectionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
