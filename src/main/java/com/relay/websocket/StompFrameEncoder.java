package com.relay.websocket;

import java.util.Map;

public class StompFrameEncoder {

    public static String encode(StompFrame frame) {
        if (frame == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(frame.command().name()).append("\n");

        if (frame.headers() != null) {
            for (Map.Entry<String, String> entry : frame.headers().entrySet()) {
                sb.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
            }
        }

        sb.append("\n");
        if (frame.body() != null && !frame.body().isEmpty()) {
            sb.append(frame.body());
        }
        sb.append('\0');

        return sb.toString();
    }
}
