package com.relay.websocket;

import java.util.*;

public class StompFrameDecoder {

    public static List<StompFrame> decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<StompFrame> frames = new ArrayList<>();
        // Split by null byte delimiter
        int startIndex = 0;
        while (startIndex < raw.length()) {
            // Skip any heartbeat newlines or whitespace between frames
            while (startIndex < raw.length() && (raw.charAt(startIndex) == '\n' || raw.charAt(startIndex) == '\r')) {
                startIndex++;
            }
            if (startIndex >= raw.length()) break;

            int nullIndex = raw.indexOf('\0', startIndex);
            String frameText;
            if (nullIndex != -1) {
                frameText = raw.substring(startIndex, nullIndex);
                startIndex = nullIndex + 1;
            } else {
                frameText = raw.substring(startIndex);
                startIndex = raw.length();
            }

            if (frameText.trim().isEmpty()) {
                continue;
            }

            StompFrame frame = parseSingleFrame(frameText);
            if (frame != null) {
                frames.add(frame);
            }
        }

        return frames;
    }

    private static StompFrame parseSingleFrame(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int emptyLineIndex = normalized.indexOf("\n\n");

        String headerSection;
        String body = "";

        if (emptyLineIndex != -1) {
            headerSection = normalized.substring(0, emptyLineIndex);
            body = normalized.substring(emptyLineIndex + 2);
        } else {
            headerSection = normalized;
        }

        String[] headerLines = headerSection.split("\n");
        if (headerLines.length == 0 || headerLines[0].trim().isEmpty()) {
            return null;
        }

        String commandStr = headerLines[0].trim();
        StompCommand command;
        try {
            command = StompCommand.valueOf(commandStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i];
            int colonIdx = line.indexOf(':');
            if (colonIdx != -1) {
                String key = line.substring(0, colonIdx).trim();
                String val = line.substring(colonIdx + 1).trim();
                headers.putIfAbsent(key, val);
            }
        }

        return new StompFrame(command, headers, body);
    }
}
