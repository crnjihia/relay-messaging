package com.relay.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${relay.firebase.credentials-path:}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                if (credentialsPath != null && !credentialsPath.isBlank() && Files.exists(Path.of(credentialsPath))) {
                    try (InputStream serviceAccount = new FileInputStream(credentialsPath)) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                                .build();
                        FirebaseApp app = FirebaseApp.initializeApp(options);
                        log.info("Initialized FirebaseApp with credentials from: {}", credentialsPath);
                        return app;
                    }
                } else {
                    log.info("No Firebase credentials provided or file not found at '{}'. Running FCM service in fallback/dev mode.", credentialsPath);
                    return null;
                }
            } catch (Exception e) {
                log.warn("Failed to initialize FirebaseApp (will run in fallback mode): {}", e.getMessage());
                return null;
            }
        }
        return FirebaseApp.getInstance();
    }
}
