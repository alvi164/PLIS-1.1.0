package edu.university.plis.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

final class DesktopStorage {
    private static final String SECRET_KEY = "jwt-secret";

    private final Path root;
    private final Path configurationFile;

    DesktopStorage() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".plis") : Path.of(localAppData, "PLIS");
        root = base.toAbsolutePath().normalize();
        configurationFile = root.resolve("installation.properties");
    }

    synchronized String jwtSecret() {
        try {
            Files.createDirectories(root);
            Properties properties = new Properties();
            if (Files.exists(configurationFile)) {
                try (InputStream input = Files.newInputStream(configurationFile)) {
                    properties.load(input);
                }
            }
            String secret = properties.getProperty(SECRET_KEY);
            if (secret == null || secret.isBlank()) {
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                secret = Base64.getEncoder().encodeToString(bytes);
                properties.setProperty(SECRET_KEY, secret);
                try (OutputStream output = Files.newOutputStream(configurationFile)) {
                    properties.store(output, "PLIS local installation settings");
                }
            }
            return secret;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize PLIS application storage", exception);
        }
    }

    String databaseUrl() {
        try {
            Path database = root.resolve("data").resolve("plis");
            Files.createDirectories(database.getParent());
            return "jdbc:h2:file:" + database.toString().replace('\\', '/') + ";AUTO_SERVER=TRUE";
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize the PLIS database directory", exception);
        }
    }

    String logFile() {
        try {
            Path logs = root.resolve("logs");
            Files.createDirectories(logs);
            return logs.resolve("plis.log").toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize the PLIS log directory", exception);
        }
    }

    Path root() { return root; }
}
