package edu.university.plis.desktop;

import edu.university.plis.server.PlisServerApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

final class EmbeddedTeacherServer implements AutoCloseable {
    private ConfigurableApplicationContext context;
    private String localUrl;

    synchronized String start(DesktopStorage storage) {
        if (context != null) {
            return localUrl;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.address", "0.0.0.0");
        properties.put("server.port", "0");
        properties.put("spring.datasource.url", storage.databaseUrl());
        properties.put("spring.datasource.username", "sa");
        properties.put("spring.datasource.password", "");
        properties.put("plis.security.jwt-secret", storage.jwtSecret());
        properties.put("plis.security.token-lifetime", "P7D");
        properties.put("spring.main.headless", "false");
        properties.put("logging.file.name", storage.logFile());
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        context = new SpringApplicationBuilder(PlisServerApplication.class)
                .headless(false)
                .registerShutdownHook(false)
                .properties(properties)
                .run();
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        localUrl = "http://127.0.0.1:" + port;
        return localUrl;
    }

    int port() {
        if (context == null) {
            throw new IllegalStateException("Teacher server is not running");
        }
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @Override
    public synchronized void close() {
        if (context != null) {
            context.close();
            context = null;
        }
    }
}
