package com.relay.dto;

import com.relay.domain.Attachment;

public record AttachmentResponse(
        String id,
        String fileName,
        String contentType,
        String fileUrl,
        long fileSize
) {
    public static AttachmentResponse fromDomain(Attachment a) {
        if (a == null) {
            return null;
        }
        return new AttachmentResponse(
                a.id(),
                a.fileName(),
                a.contentType(),
                a.fileUrl(),
                a.fileSize()
        );
    }
}
