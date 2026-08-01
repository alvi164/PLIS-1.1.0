package edu.university.plis.student.client;

import edu.university.plis.shared.model.NetworkConnectionType;

import java.net.*;
import java.util.Locale;

final class NetworkConnectionDetector {
    private final URI serverUri;

    NetworkConnectionDetector(String serverUrl) {
        serverUri = URI.create(serverUrl);
    }

    NetworkConnectionType detect() {
        try (DatagramSocket routeProbe = new DatagramSocket()) {
            int port = serverUri.getPort() > 0 ? serverUri.getPort()
                    : ("https".equalsIgnoreCase(serverUri.getScheme()) ? 443 : 80);
            routeProbe.connect(InetAddress.getByName(serverUri.getHost()), port);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(routeProbe.getLocalAddress());
            if (networkInterface == null || networkInterface.isLoopback()) {
                return NetworkConnectionType.UNKNOWN;
            }
            String description = (networkInterface.getName() + " " + networkInterface.getDisplayName())
                    .toLowerCase(Locale.ROOT);
            if (description.contains("wi-fi") || description.contains("wifi")
                    || description.contains("wireless") || description.contains("wlan")) {
                return NetworkConnectionType.WIFI;
            }
            return NetworkConnectionType.WIRED_LAN;
        } catch (Exception ignored) {
            return NetworkConnectionType.UNKNOWN;
        }
    }
}
