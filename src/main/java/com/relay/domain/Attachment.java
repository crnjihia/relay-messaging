package com.relay.domain;

import java.io.Serializable;

public record Attachment(
        String id,
        String fileName,
        String contentType,
        String fileUrl,
        long fileSize
) implements Serializable {
}
