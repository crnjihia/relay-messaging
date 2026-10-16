import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const wsConnects = new Counter('ws_connects_total');
const wsErrors = new Counter('ws_errors_total');
const messagesSent = new Counter('messages_sent_total');
const messagesReceived = new Counter('messages_received_total');
const messageLatency = new Trend('message_roundtrip_latency_ms');
const successRate = new Rate('successful_operations');

export const options = {
    scenarios: {
        // Ramp up to 1,000 concurrent STOMP connections
        concurrent_connections: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 200 },   // Warm-up
                { duration: '1m',  target: 500 },   // Scale to 500 connections
                { duration: '2m',  target: 1000 },  // Peak load: 1,000 concurrent connections
                { duration: '2m',  target: 1000 },  // Sustain 100 msg/sec throughput
                { duration: '30s', target: 0 },     // Ramp-down
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        'ws_connects_total': ['count>900'],
        'message_roundtrip_latency_ms': ['p(95)<150', 'p(99)<300'],
        'successful_operations': ['rate>0.98'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';

export function setup() {
    // Create a shared benchmark conversation for load testing
    const userPayload = JSON.stringify({
        username: `load_admin_${Date.now()}`,
        displayName: 'Load Admin',
        email: `admin_${Date.now()}@relay.ke`,
        password: 'Password123!'
    });

    const regRes = http.post(`${BASE_URL}/auth/register`, userPayload, {
        headers: { 'Content-Type': 'application/json' },
    });
    const token = regRes.json('token');
    const adminId = regRes.json('user.id');

    // Create benchmark conversation
    const convRes = http.post(`${BASE_URL}/conversations`, JSON.stringify({
        type: 'GROUP',
        name: 'Benchmark SME Team',
        memberIds: [adminId]
    }), {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        }
    });

    return {
        conversationId: convRes.json('id') || 'bench-conv-001',
        token: token
    };
}

export default function (data) {
    const vuId = __VU;
    const iter = __ITER;

    // Fast-path auth for each VU (or use pre-generated tokens)
    const token = data.token;
    const conversationId = data.conversationId;

    const url = `${WS_URL}?token=${token}`;

    const res = ws.connect(url, {}, function (socket) {
        socket.on('open', function () {
            wsConnects.add(1);

            // 1. Send STOMP CONNECT
            const connectFrame = `CONNECT\naccept-version:1.2\nAuthorization:Bearer ${token}\nheart-beat:30000,30000\n\n\0`;
            socket.send(connectFrame);

            // 2. Subscribe to conversation
            const subFrame = `SUBSCRIBE\nid:sub-vu-${vuId}\ndestination:/topic/conversations/${conversationId}\nack:auto\n\n\0`;
            socket.send(subFrame);

            // 3. Periodic message sender (averaging 100 msg/sec aggregate across 1000 VUs = ~1 msg every 10s per VU)
            socket.setInterval(function () {
                const sendTimestamp = Date.now();
                const payload = JSON.stringify({
                    conversationId: conversationId,
                    content: `Load test message from VU ${vuId} at ${sendTimestamp}`,
                    contentType: 'TEXT'
                });

                const sendFrame = `SEND\ndestination:/app/chat.send\ncontent-type:application/json\nreceipt:r-${sendTimestamp}\n\n${payload}\0`;
                socket.send(sendFrame);
                messagesSent.add(1);
            }, 10000);

            // Heartbeat every 30s
            socket.setInterval(function () {
                socket.send(`SEND\ndestination:/app/presence.heartbeat\n\n{}\0`);
            }, 30000);
        });

        socket.on('message', function (msg) {
            messagesReceived.add(1);
            successRate.add(true);

            // Parse timestamp if it's our message
            if (msg.includes('Load test message')) {
                const match = msg.match(/at (\d+)/);
                if (match && match[1]) {
                    const sentAt = parseInt(match[1]);
                    const latency = Date.now() - sentAt;
                    if (latency >= 0 && latency < 60000) {
                        messageLatency.add(latency);
                    }
                }
            }
        });

        socket.on('error', function (e) {
            wsErrors.add(1);
            successRate.add(false);
        });

        socket.on('close', function () {
            // connection teardown
        });

        // Keep socket connected for scenario duration
        socket.setTimeout(function () {
            socket.close();
        }, 60000);
    });

    check(res, { 'WebSocket handshake status is 101': (r) => r && r.status === 101 });
}
