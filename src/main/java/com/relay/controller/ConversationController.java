package com.relay.controller;

import com.relay.dto.AddMembersRequest;
import com.relay.dto.ConversationResponse;
import com.relay.dto.CreateConversationRequest;
import com.relay.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ConversationResponse> createConversation(Authentication authentication,
                                                         @Valid @RequestBody CreateConversationRequest request) {
        String currentUserId = authentication.getName();
        return conversationService.createConversation(currentUserId, request);
    }

    @GetMapping
    public Flux<ConversationResponse> listConversations(Authentication authentication) {
        String currentUserId = authentication.getName();
        return conversationService.listUserConversations(currentUserId);
    }

    @GetMapping("/{id}")
    public Mono<ConversationResponse> getConversation(Authentication authentication,
                                                      @PathVariable String id) {
        String currentUserId = authentication.getName();
        return conversationService.getConversation(id, currentUserId);
    }

    @PostMapping("/{id}/members")
    public Mono<ConversationResponse> addMembers(Authentication authentication,
                                                 @PathVariable String id,
                                                 @Valid @RequestBody AddMembersRequest request) {
        String currentUserId = authentication.getName();
        return conversationService.addMembers(id, currentUserId, request.memberIds());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Mono<ConversationResponse> removeMember(Authentication authentication,
                                                   @PathVariable String id,
                                                   @PathVariable String userId) {
        String currentUserId = authentication.getName();
        return conversationService.removeMember(id, currentUserId, userId);
    }
}
