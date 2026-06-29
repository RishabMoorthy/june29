package com.stubserver.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String corsOrigin;
    private String prodUrl;
    private CoreServer coreServer = new CoreServer();
    private Jwt jwt = new Jwt();
    private String stubBase;
    private String serverLogDir;
    private String reqrespLogDir;
    private String datasetPaths;
    private Batch batch = new Batch();
    private String healthApi;

    @PostConstruct
    public void logResolvedPaths() {
        log.info("Working directory (user.dir): {}", System.getProperty("user.dir"));
        log.info("Resolved server-log path : {}", resolveAbsolute(serverLogDir));
        log.info("Resolved reqres path     : {}", resolveAbsolute(reqrespLogDir));
        log.info("Resolved dataset path    : {}", resolveAbsolute(datasetPaths));
    }

    private String resolveAbsolute(String configured) {
        if (configured == null || configured.isEmpty()) {
            return "<not configured>";
        }
        Path resolved = Paths.get(configured).toAbsolutePath().normalize();
        return resolved.toString();
    }

    @Getter @Setter
    public static class CoreServer {
        private int port = 9093;
        private String name = "StubServer";
    }

    @Getter @Setter
    public static class Jwt {
        private String accessSecret;
        private String refreshSecret;
        private String resetSecret;
        private long accessExpiresSeconds = 900;
        private long refreshExpiresSeconds = 1296000;
        private long resetExpiresSeconds = 900;
    }

    @Getter @Setter
    public static class Batch {
        private String javaStart;
        private String javaStop;
    }
}
