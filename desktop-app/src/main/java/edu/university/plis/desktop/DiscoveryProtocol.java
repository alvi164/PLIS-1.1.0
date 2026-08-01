package edu.university.plis.desktop;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class DiscoveryProtocol {
    static final String GROUP = "239.255.77.77";
    static final int PORT = 45454;
    private static final String PREFIX = "PLIS1";

    private DiscoveryProtocol() { }

    static byte[] encode(String installationId, String teacherName, int serverPort) {
        String payload = PREFIX + "|" + value(installationId) + "|" + value(teacherName) + "|" + serverPort;
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    static Advertisement decode(byte[] bytes, int length) {
        String[] fields = new String(bytes, 0, length, StandardCharsets.UTF_8).split("\\|", -1);
        if (fields.length != 4 || !PREFIX.equals(fields[0])) {
            throw new IllegalArgumentException("Not a PLIS discovery message");
        }
        int port = Integer.parseInt(fields[3]);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid PLIS server port");
        }
        return new Advertisement(text(fields[1]), text(fields[2]), port);
    }

    private static String value(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    record Advertisement(String installationId, String teacherName, int port) { }
}
