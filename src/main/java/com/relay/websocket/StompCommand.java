package com.relay.websocket;

public enum StompCommand {
    // Client commands
    CONNECT,
    STOMP,
    SEND,
    SUBSCRIBE,
    UNSUBSCRIBE,
    ACK,
    NACK,
    BEGIN,
    COMMIT,
    ABORT,
    DISCONNECT,

    // Server commands
    CONNECTED,
    MESSAGE,
    RECEIPT,
    ERROR
}
