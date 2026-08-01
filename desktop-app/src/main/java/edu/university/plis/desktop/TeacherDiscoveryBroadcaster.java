package edu.university.plis.desktop;

import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class TeacherDiscoveryBroadcaster implements AutoCloseable {
    private final String installationId;
    private final String teacherName;
    private final int serverPort;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "plis-teacher-discovery");
        thread.setDaemon(true);
        return thread;
    });

    TeacherDiscoveryBroadcaster(String installationId, String teacherName, int serverPort) {
        this.installationId = installationId;
        this.teacherName = teacherName;
        this.serverPort = serverPort;
    }

    void start() {
        executor.scheduleWithFixedDelay(this::broadcast, 0, 2, TimeUnit.SECONDS);
    }

    private void broadcast() {
        byte[] payload = DiscoveryProtocol.encode(installationId, teacherName, serverPort);
        try (MulticastSocket socket = new MulticastSocket()) {
            socket.setTimeToLive(1);
            InetAddress group = InetAddress.getByName(DiscoveryProtocol.GROUP);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, group, DiscoveryProtocol.PORT);
            boolean sent = false;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && networkInterface.supportsMulticast()) {
                    try {
                        socket.setNetworkInterface(networkInterface);
                        socket.send(packet);
                        sent = true;
                    } catch (Exception ignored) {
                        // Continue with the other active adapters.
                    }
                }
            }
            if (!sent) {
                socket.send(packet);
            }
        } catch (Exception ignored) {
            // Discovery is optional; manual address entry remains available.
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
