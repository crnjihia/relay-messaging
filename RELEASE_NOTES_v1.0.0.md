# Relay Messaging — Release Notes v1.0.0

**Release Date:** October 16, 2026  
**Commit/Tag:** `v1.0.0`

---

## 🌟 Highlights & Key Features

### 1. Reactive Core Architecture
- Built on **Spring Boot 3.3.0**, **Spring WebFlux**, and **Project Reactor Netty** for non-blocking I/O throughout the entire HTTP and WebSocket stack.
- Zero servlet threads; all REST controllers and WebSocket handlers return reactive `Mono` and `Flux` publishers.

### 2. Full STOMP 1.2 Protocol over WebSocket
- Standard `/ws` endpoint with SockJS fallback capability.
- Authenticated `CONNECT` handshake requiring JWT bearer tokens in headers or query params.
- Subscriptions to conversation channels (`/topic/conversations/{id}`) and personal queues (`/user/queue/messages`, `/user/queue/receipts`, `/user/queue/presence`).
- Client frame dispatch destinations: `/app/chat.send`, `/app/chat.read`, and `/app/presence.heartbeat`.

### 3. Multi-Instance Redis 7 Pub/Sub Fan-Out
- Horizontal scaling across multiple independent application nodes behind an Nginx load balancer.
- Persistent-first message pipeline: Messages saved to MongoDB, then published to Redis channel `chat:conversation:{id}` for local cluster fan-out.
- Read receipts published to `chat:receipt:{id}`.

### 4. Robust Presence Engine with Keyspace Expirations
- Heartbeats sent every 30 seconds by connected clients.
- Server-side Redis presence records stored with 60-second TTL.
- Automatic detection of dropped connections and network cuts using Redis keyspace notifications (`__keyevent@*__:expired`), triggering instant `OFFLINE` status broadcasts.
- Graceful 60-second `AWAY` grace period on clean disconnects.

### 5. Smart Backpressure & Slow Consumer Protection
- Every session is backed by an internal bounded buffer of 256 frames via `Sinks.Many`.
- Drop policies for non-critical telemetry (presence events) to shield degraded connections.
- Strict delivery guarantees for chat messages and read receipts; stalls exceeding buffer limits trigger protective session closures (`CloseStatus.POLICY_VIOLATION`) to avoid OOM crashes.

### 6. Firebase Cloud Messaging (FCM) Offline Pushes
- Automatic push dispatch when conversation members are `OFFLINE`.
- 30-second per-conversation anti-spam throttling to prevent notification fatigue.
- Automated cleanup of invalid and expired FCM registration tokens.

### 7. SME Quotas, Rate Limiting & Storage
- Redis sliding-window limiters enforcing 20 messages/minute and 5 conversations/day.
- Message body validation capped at 10 KB.
- S3-compatible attachment uploads (up to 25 MB) backed by MinIO.

---

## 📦 Release Artifacts Included
- `relay-messaging-1.0.0-SNAPSHOT.jar` — Production executable Spring Boot JAR
- `Relay_Messaging.postman_collection.json` — Comprehensive Postman test collection
- `Relay_Messaging.postman_environment.json` — Postman local environment variables
- `stomp-client-ui.html` — Interactive live browser STOMP console
- `test-stomp-client.js` — Automated verification script
- `k6-load-test.js` & `LOAD_TEST_REPORT_TEMPLATE.md` — 1,000 connection / 100 msg/sec benchmark suite
