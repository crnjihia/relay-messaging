# Relay Messaging — STOMP over WebSocket Protocol Specification

## 1. Overview
Relay Messaging uses the **STOMP 1.2** (Simple Text Oriented Messaging Protocol) specification over full-duplex reactive WebSockets (`/ws`). This document describes the protocol frames, headers, destinations, authentication lifecycle, and error handling.

---

## 2. Handshake & Authentication

### 2.1 Connection Endpoint
- **URL**: `ws://<host>:<port>/ws` (or `wss://` in production via TLS)
- **SockJS Fallback**: Compatible with SockJS transport `/ws`

### 2.2 CONNECT Frame
Clients **MUST** transmit a valid JWT token in the `Authorization` header during the initial STOMP `CONNECT` frame (or via query parameter `?token=<jwt>`):

```stomp
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
heart-beat:30000,30000

^@
```
*(Note: `^@` denotes the ASCII NULL byte `\0`)*

### 2.3 CONNECTED Frame (Server Response)
Upon successful JWT validation, the server assigns a session and responds:

```stomp
CONNECTED
version:1.2
user-name:kamau
session:session-7a91bf
heart-beat:10000,10000

^@
```
*At this point, the user's presence is automatically transitioned to `ONLINE` in Redis and broadcasted to other members.*

---

## 3. Subscriptions (`SUBSCRIBE`)

### 3.1 Conversation Channel
Receives real-time messages sent by any member in this conversation:
- **Destination**: `/topic/conversations/{conversationId}`
- **Access Rule**: The server validates that the authenticated user is an active member of `conversationId`. Non-members receive an `ERROR` frame.

```stomp
SUBSCRIBE
id:sub-conv-1
destination:/topic/conversations/66b93f12a348
ack:auto

^@
```

### 3.2 Direct User Messages
Receives personal notifications and targeted messages:
- **Destination**: `/user/queue/messages`

```stomp
SUBSCRIBE
id:sub-user-msgs
destination:/user/queue/messages
ack:auto

^@
```

### 3.3 Read Receipts
Receives real-time read receipt updates for conversations:
- **Destination**: `/user/queue/receipts`

```stomp
SUBSCRIBE
id:sub-user-receipts
destination:/user/queue/receipts
ack:auto

^@
```

### 3.4 Presence Updates
Receives status transitions (`ONLINE`, `AWAY`, `OFFLINE`) for users:
- **Destination**: `/user/queue/presence`

```stomp
SUBSCRIBE
id:sub-presence
destination:/user/queue/presence
ack:auto

^@
```

---

## 4. Sending Messages (`SEND`)

### 4.1 Send Chat Message
Sends a new message into a conversation:
- **Destination**: `/app/chat.send`
- **Rate Limit**: Enforces 20 messages/minute per user.
- **Payload Limit**: Max 10 KB text content.

```stomp
SEND
destination:/app/chat.send
content-type:application/json
receipt:msg-ack-101

{
  "conversationId": "66b93f12a348",
  "content": "Habari za asubuhi! Je, delivery ya vifaa imefika?",
  "contentType": "TEXT",
  "replyToId": null,
  "attachments": []
}
^@
```

### 4.2 Mark Messages as Read
Updates read position up to a specific message:
- **Destination**: `/app/chat.read`

```stomp
SEND
destination:/app/chat.read
content-type:application/json
receipt:read-ack-202

{
  "conversationId": "66b93f12a348",
  "messageId": "msg-8f920ab"
}
^@
```

### 4.3 Presence Heartbeat
Clients **MUST** transmit a heartbeat every 30 seconds to maintain `ONLINE` status. Server sets Redis TTL to 60s:
- **Destination**: `/app/presence.heartbeat`

```stomp
SEND
destination:/app/presence.heartbeat

{}
^@
```

---

## 5. Server Frame Payloads (`MESSAGE`)

### 5.1 Chat Message Frame (Broadcast)
Delivered to subscribers of `/topic/conversations/{conversationId}`:

```stomp
MESSAGE
subscription:sub-conv-1
message-id:urn:uuid:8b31f7ac-1294-4d8b-90f7-11e4f9b8192a
destination:/topic/conversations/66b93f12a348
content-type:application/json

{
  "id": "67104b901a88b4",
  "conversationId": "66b93f12a348",
  "senderId": "67104a112f45",
  "senderName": "Wanjiku Enterprises",
  "content": "Habari za asubuhi! Je, delivery ya vifaa imefika?",
  "contentType": "TEXT",
  "attachments": [],
  "replyToId": null,
  "createdAt": "2026-10-16T17:20:00.123Z",
  "editedAt": null,
  "deletedAt": null
}
^@
```

### 5.2 Read Receipt Frame
Delivered to `/topic/conversations/{conversationId}` and `/user/queue/receipts`:

```stomp
MESSAGE
subscription:sub-user-receipts
message-id:urn:uuid:67104a33-91ab-4e32
destination:/topic/conversations/66b93f12a348
content-type:application/json

{
  "id": "rcpt-9921",
  "conversationId": "66b93f12a348",
  "userId": "67104a112f45",
  "lastReadMessageId": "67104b901a88b4",
  "readAt": "2026-10-16T17:21:15.890Z"
}
^@
```

### 5.3 Presence Status Frame
Delivered to `/user/queue/presence`:

```stomp
MESSAGE
subscription:sub-presence
destination:/user/queue/presence
content-type:application/json

{
  "userId": "67104a112f45",
  "status": "ONLINE",
  "timestamp": "2026-10-16T17:22:00.000Z"
}
^@
```

---

## 6. Disconnect & Presence Lifecycle

1. **Client Disconnect**:
   When the client cleanly disconnects:
   ```stomp
   DISCONNECT
   receipt:disc-01

   ^@
   ```
   The server sets status to `AWAY` with a 60-second grace period in Redis.
2. **Explicit Reconnection**:
   If the client reconnects within 60s, it sends `CONNECT` and presence is immediately restored to `ONLINE`.
3. **TTL Expiry / Abrupt Termination**:
   If heartbeat stops or client disconnects without reconnecting within 60s, Redis keyspace expiration triggers `__keyevent@*__:expired` for `presence:{userId}`.
   The server automatically marks the user `OFFLINE` in MongoDB and broadcasts an `OFFLINE` presence frame.

---

## 7. Backpressure & Slow Consumer Policy

- **Bounded Queue**: Every active WebSocket connection is allocated an internal bounded buffer of 256 frames.
- **Drop Policy for Non-Critical Events**: If a consumer is slow, presence frames (`ONLINE`, `AWAY`, `OFFLINE`) are dropped to protect connection health.
- **Integrity for Critical Messages**: Chat messages and read receipts are never dropped. If the consumer buffer overflows, the session is terminated with `CloseStatus.POLICY_VIOLATION` to prevent server memory bloat.
