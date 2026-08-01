package edu.university.plis.desktop;

import java.net.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class TeacherDiscoveryListener implements AutoCloseable {
    private final Map<String, DiscoveredTeacher> teachers = new ConcurrentHashMap<>();
    private final Consumer<List<DiscoveredTeacher>> listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "plis-student-discovery");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;
    private volatile MulticastSocket socket;

    TeacherDiscoveryListener(Consumer<List<DiscoveredTeacher>> listener) {
        this.listener = listener;
    }

    void start() {
        running = true;
        executor.submit(this::listen);
    }

    private void listen() {
        try {
            MulticastSocket discoverySocket = new MulticastSocket(null);
            socket = discoverySocket;
            discoverySocket.setReuseAddress(true);
            discoverySocket.bind(new InetSocketAddress(DiscoveryProtocol.PORT));
            discoverySocket.setSoTimeout(1_000);
            InetAddress group = InetAddress.getByName(DiscoveryProtocol.GROUP);
            joinActiveInterfaces(discoverySocket, group);
            byte[] buffer = new byte[1_024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    discoverySocket.receive(packet);
                    DiscoveryProtocol.Advertisement advertisement = DiscoveryProtocol.decode(
                            packet.getData(), packet.getLength());
                    String host = packet.getAddress().getHostAddress();
                    String url = "http://" + host + ":" + advertisement.port();
                    teachers.put(advertisement.installationId(), new DiscoveredTeacher(
                            advertisement.installationId(), advertisement.teacherName(), url, Instant.now()));
                } catch (SocketTimeoutException ignored) {
                    // Timeout provides a chance to remove stale hosts and observe shutdown.
                } catch (RuntimeException ignored) {
                    // Ignore malformed or unrelated multicast packets.
                }
                publish();
            }
        } catch (Exception ignored) {
            publish();
        }
    }

    private void joinActiveInterfaces(MulticastSocket socket, InetAddress group) throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            try {
                if (networkInterface.isUp() && networkInterface.supportsMulticast()) {
                    socket.joinGroup(new InetSocketAddress(group, DiscoveryProtocol.PORT), networkInterface);
                }
            } catch (Exception ignored) {
                // Some virtual adapters report multicast but cannot join.
            }
        }
    }

    private void publish() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(8));
        teachers.entrySet().removeIf(entry -> entry.getValue().lastSeen().isBefore(cutoff));
        List<DiscoveredTeacher> current = teachers.values().stream()
                .sorted(Comparator.comparing(DiscoveredTeacher::teacherName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        listener.accept(current);
    }

    @Override
    public void close() {
        running = false;
        MulticastSocket current = socket;
        if (current != null) {
            current.close();
        }
        executor.shutdownNow();
    }
}
