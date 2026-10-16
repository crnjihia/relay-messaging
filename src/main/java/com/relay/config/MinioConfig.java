package com.relay.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${relay.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${relay.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${relay.minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${relay.minio.bucket:relay-attachments}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // Check or create bucket asynchronously in background or upon initial start
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Created MinIO bucket: {}", bucket);
                } else {
                    log.info("Connected to MinIO bucket: {}", bucket);
                }
            } catch (Exception e) {
                log.warn("MinIO bucket '{}' check/create deferred (service may not be up yet): {}", bucket, e.getMessage());
            }

            return client;
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client: {}", e.getMessage());
            return null;
        }
    }

    public String getBucket() {
        return bucket;
    }
}
