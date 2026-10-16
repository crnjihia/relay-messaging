package com.relay.websocket;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record StompFrame(
        StompCommand command,
        Map<String, String> headers,
        String body
) implements Serializable {

    public StompFrame {
        headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Collections.emptyMap();
        body = body != null ? body : "";
    }

    public String getHeader(String name) {
        if (headers == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static Builder builder(StompCommand command) {
        return new Builder(command);
    }

    public static class Builder {
        private final StompCommand command;
        private final Map<String, String> headers = new HashMap<>();
        private String body = "";

        public Builder(StompCommand command) {
            this.command = command;
        }

        public Builder header(String name, String value) {
            if (name != null && value != null) {
                headers.put(name, value);
            }
            return this;
        }

        public Builder headers(Map<String, String> hdrs) {
            if (hdrs != null) {
                headers.putAll(hdrs);
            }
            return this;
        }

        public Builder body(String body) {
            this.body = body != null ? body : "";
            return this;
        }

        public StompFrame build() {
            return new StompFrame(command, headers, body);
        }
    }
}
