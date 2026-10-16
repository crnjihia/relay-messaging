package com.relay.controller;

import com.relay.domain.Attachment;
import com.relay.dto.*;
import com.relay.service.MessageService;
import com.relay.service.ReadReceiptService;
import com.relay.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/conversations/{conversationId}")
public class MessageController {

    private final MessageService messageService;
    private final ReadReceiptService readReceiptService;
    private final StorageService storageService;

    public MessageController(MessageService messageService,
                             ReadReceiptService readReceiptService,
                             StorageService storageService) {
        this.messageService = messageService;
        this.readReceiptService = readReceiptService;
        this.storageService = storageService;
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MessageResponse> sendMessage(Authentication authentication,
                                             @PathVariable String conversationId,
                                             @Valid @RequestBody SendMessageRequest request) {
        String currentUserId = authentication.getName();
        return messageService.sendMessage(currentUserId, conversationId, request);
    }

    @GetMapping("/messages")
    public Flux<MessageResponse> getMessages(Authentication authentication,
                                             @PathVariable String conversationId,
                                             @RequestParam(required = false) String before,
                                             @RequestParam(defaultValue = "50") int limit) {
        String currentUserId = authentication.getName();
        return messageService.getMessages(conversationId, currentUserId, before, limit);
    }

    @PostMapping("/read")
    public Mono<ReadReceiptResponse> markRead(Authentication authentication,
                                             @PathVariable String conversationId,
                                             @Valid @RequestBody MarkReadRequest request) {
        String currentUserId = authentication.getName();
        return readReceiptService.markRead(currentUserId, conversationId, request.messageId());
    }

    @GetMapping("/receipts")
    public Flux<ReadReceiptResponse> getReceipts(Authentication authentication,
                                                @PathVariable String conversationId) {
        String currentUserId = authentication.getName();
        return readReceiptService.getConversationReceipts(conversationId, currentUserId);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AttachmentResponse> uploadAttachment(Authentication authentication,
                                                     @PathVariable String conversationId,
                                                     @RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono
                .flatMap(filePart -> storageService.uploadFilePart(conversationId, filePart))
                .map(AttachmentResponse::fromDomain);
    }
}
