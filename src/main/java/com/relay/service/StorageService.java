package com.relay.service;

import com.relay.config.MinioConfig;
import com.relay.domain.Attachment;
import com.relay.exception.RelayException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final long MAX_ATTACHMENT_SIZE = 25 * 1024 * 1024; // 25 MB

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Value("${relay.minio.public-url-prefix:http://localhost:9000}")
    private String publicUrlPrefix;

    public StorageService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) MinioClient minioClient,
            MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    public Mono<Attachment> uploadFilePart(String conversationId, FilePart filePart) {
        String attachmentId = UUID.randomUUID().toString();
        String originalFilename = filePart.filename();
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    if (bytes.length > MAX_ATTACHMENT_SIZE) {
                        return Mono.error(new RelayException("File size exceeds 25 MB attachment limit"));
                    }

                    return uploadBytes(conversationId, attachmentId, originalFilename, contentType, bytes);
                });
    }

    public Mono<Attachment> uploadBytes(String conversationId, String attachmentId,
                                        String filename, String contentType, byte[] bytes) {
        return Mono.fromCallable(() -> {
            if (minioClient == null) {
                // Mock storage fallback when MinIO is not running
                String mockUrl = "/attachments/mock/" + conversationId + "/" + attachmentId + "/" + filename;
                log.info("MinIO client unavailable, using fallback mock attachment URL: {}", mockUrl);
                return new Attachment(attachmentId, filename, contentType, mockUrl, bytes.length);
            }

            String bucket = minioConfig.getBucket();
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }

                String objectKey = conversationId + "/" + attachmentId + "_" + filename;
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucket)
                                    .object(objectKey)
                                    .stream(bais, bytes.length, -1)
                                    .contentType(contentType)
                                    .build()
                    );
                }

                String fileUrl = publicUrlPrefix + "/" + bucket + "/" + objectKey;
                log.info("Uploaded attachment {} to MinIO: {}", attachmentId, fileUrl);
                return new Attachment(attachmentId, filename, contentType, fileUrl, bytes.length);
            } catch (Exception e) {
                log.error("Failed to upload to MinIO: {}", e.getMessage());
                throw new RelayException("Failed to upload attachment: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
