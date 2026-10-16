package com.relay.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StompFrameCodecTest {

    @Test
    @DisplayName("Should successfully encode and decode a CONNECT frame")
    void testConnectFrame() {
        StompFrame frame = StompFrame.builder(StompCommand.CONNECT)
                .header("accept-version", "1.2")
                .header("host", "localhost")
                .header("Authorization", "Bearer sample-jwt-token")
                .build();

        String encoded = StompFrameEncoder.encode(frame);
        assertThat(encoded).startsWith("CONNECT\n");
        assertThat(encoded).endsWith("\0");

        List<StompFrame> decodedList = StompFrameDecoder.decode(encoded);
        assertThat(decodedList).hasSize(1);

        StompFrame decoded = decodedList.get(0);
        assertThat(decoded.command()).isEqualTo(StompCommand.CONNECT);
        assertThat(decoded.getHeader("accept-version")).isEqualTo("1.2");
        assertThat(decoded.getHeader("Authorization")).isEqualTo("Bearer sample-jwt-token");
        assertThat(decoded.body()).isEmpty();
    }

    @Test
    @DisplayName("Should successfully encode and decode a SEND frame with JSON body")
    void testSendFrameWithBody() {
        String jsonBody = "{\"conversationId\":\"c123\",\"content\":\"Habari ya asubuhi\"}";
        StompFrame frame = StompFrame.builder(StompCommand.SEND)
                .header("destination", "/app/chat.send")
                .header("content-type", "application/json")
                .body(jsonBody)
                .build();

        String encoded = StompFrameEncoder.encode(frame);
        List<StompFrame> decodedList = StompFrameDecoder.decode(encoded);

        assertThat(decodedList).hasSize(1);
        StompFrame decoded = decodedList.get(0);
        assertThat(decoded.command()).isEqualTo(StompCommand.SEND);
        assertThat(decoded.getHeader("destination")).isEqualTo("/app/chat.send");
        assertThat(decoded.body()).isEqualTo(jsonBody);
    }

    @Test
    @DisplayName("Should handle batched frames and heartbeat whitespace gracefully")
    void testBatchedFramesWithHeartbeats() {
        String raw = "\n\r\n" +
                "CONNECT\naccept-version:1.2\n\n\0" +
                "\n\n" +
                "SUBSCRIBE\nid:sub-0\ndestination:/topic/conversations/123\n\n\0";

        List<StompFrame> decodedList = StompFrameDecoder.decode(raw);
        assertThat(decodedList).hasSize(2);
        assertThat(decodedList.get(0).command()).isEqualTo(StompCommand.CONNECT);
        assertThat(decodedList.get(1).command()).isEqualTo(StompCommand.SUBSCRIBE);
        assertThat(decodedList.get(1).getHeader("destination")).isEqualTo("/topic/conversations/123");
    }

    @Test
    @DisplayName("Should case-insensitively lookup headers in StompFrame")
    void testCaseInsensitiveHeaderLookup() {
        StompFrame frame = StompFrame.builder(StompCommand.CONNECTED)
                .headers(Map.of("Content-Type", "application/json", "Authorization", "Bearer xyz"))
                .build();

        assertThat(frame.getHeader("content-type")).isEqualTo("application/json");
        assertThat(frame.getHeader("AUTHORIZATION")).isEqualTo("Bearer xyz");
    }
}
