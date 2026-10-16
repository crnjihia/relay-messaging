package com.relay.controller;

import com.relay.config.SecurityConfig;
import com.relay.domain.ContentType;
import com.relay.domain.ConversationType;
import com.relay.dto.*;
import com.relay.exception.GlobalExceptionHandler;
import com.relay.security.JwtAuthenticationManager;
import com.relay.security.JwtServerAuthenticationConverter;
import com.relay.security.JwtService;
import com.relay.service.ConversationService;
import com.relay.service.MessageService;
import com.relay.service.ReadReceiptService;
import com.relay.service.StorageService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {ConversationController.class, MessageController.class})
@Import({SecurityConfig.class, JwtAuthenticationManager.class, JwtServerAuthenticationConverter.class, GlobalExceptionHandler.class})
class ConversationAndMessageControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private MessageService messageService;

    @MockBean
    private ReadReceiptService readReceiptService;

    @MockBean
    private StorageService storageService;

    @MockBean
    private JwtService jwtService;

    private final String validToken = "valid-test-token";
    private final String testUserId = "user-123";

    @BeforeEach
    void setupAuth() {
        Claims mockClaims = mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn(testUserId);
        when(mockClaims.get("username")).thenReturn("testuser");
        when(jwtService.parseAndValidate(validToken)).thenReturn(mockClaims);
    }

    @Test
    @DisplayName("POST /conversations should create conversation and return 201 Created")
    void testCreateConversation() {
        CreateConversationRequest request = new CreateConversationRequest(
                ConversationType.GROUP,
                "Nairobi Logistics Team",
                List.of("user-456", "user-789")
        );

        ConversationResponse response = new ConversationResponse(
                "c-999", ConversationType.GROUP, "Nairobi Logistics Team",
                List.of(testUserId, "user-456", "user-789"), null,
                testUserId, Instant.now(), Instant.now(), null, 0
        );

        when(conversationService.createConversation(eq(testUserId), any(CreateConversationRequest.class)))
                .thenReturn(Mono.just(response));

        webTestClient.post()
                .uri("/conversations")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("c-999")
                .jsonPath("$.name").isEqualTo("Nairobi Logistics Team")
                .jsonPath("$.type").isEqualTo("GROUP");
    }

    @Test
    @DisplayName("GET /conversations should return user's conversation list")
    void testListConversations() {
        ConversationResponse response = new ConversationResponse(
                "c-1", ConversationType.DM, null,
                List.of(testUserId, "user-2"), null,
                testUserId, Instant.now(), Instant.now(), null, 0
        );

        when(conversationService.listUserConversations(testUserId))
                .thenReturn(Flux.just(response));

        webTestClient.get()
                .uri("/conversations")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("c-1");
    }

    @Test
    @DisplayName("POST /conversations/{id}/messages should post message and return 201")
    void testSendMessage() {
        SendMessageRequest req = new SendMessageRequest(
                "c-999", "Tuonane kwa mkutano kesho saa tatu asubuhi",
                ContentType.TEXT, null, null
        );

        MessageResponse msgResp = new MessageResponse(
                "m-100", "c-999", testUserId, "Test User",
                "Tuonane kwa mkutano kesho saa tatu asubuhi",
                ContentType.TEXT, List.of(), null, Instant.now(), null, null
        );

        when(messageService.sendMessage(eq(testUserId), eq("c-999"), any(SendMessageRequest.class)))
                .thenReturn(Mono.just(msgResp));

        webTestClient.post()
                .uri("/conversations/c-999/messages")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("m-100")
                .jsonPath("$.content").isEqualTo("Tuonane kwa mkutano kesho saa tatu asubuhi");
    }

    @Test
    @DisplayName("GET /conversations/{id}/messages should support cursor pagination")
    void testGetMessagesCursorPagination() {
        MessageResponse m1 = new MessageResponse("m-1", "c-1", testUserId, "User", "Hello", ContentType.TEXT, null, null, Instant.now(), null, null);
        MessageResponse m2 = new MessageResponse("m-2", "c-1", testUserId, "User", "World", ContentType.TEXT, null, null, Instant.now(), null, null);

        when(messageService.getMessages(eq("c-1"), eq(testUserId), any(), anyInt()))
                .thenReturn(Flux.just(m1, m2));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/conversations/c-1/messages")
                        .queryParam("limit", 10)
                        .queryParam("before", "m-3")
                        .build())
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("m-1");
    }

    @Test
    @DisplayName("POST /conversations/{id}/read should mark read up to messageId")
    void testMarkRead() {
        MarkReadRequest req = new MarkReadRequest("m-100");
        ReadReceiptResponse resp = new ReadReceiptResponse("r-1", "c-1", testUserId, "m-100", Instant.now());

        when(readReceiptService.markRead(testUserId, "c-1", "m-100"))
                .thenReturn(Mono.just(resp));

        webTestClient.post()
                .uri("/conversations/c-1/read")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lastReadMessageId").isEqualTo("m-100");
    }
}
