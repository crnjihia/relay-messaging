/**
 * Relay Messaging — Automated STOMP over WebSocket Verification Script
 *
 * Requirements: Node.js 18+ (built-in fetch and WebSocket)
 * Usage: node scripts/test-stomp-client.js [BASE_URL] [WS_URL]
 */

const BASE_URL = process.argv[2] || 'http://localhost:8080';
const WS_URL = process.argv[3] || 'ws://localhost:8080/ws';

async function run() {
    console.log(`\n========================================`);
    console.log(`🚀 RELAY MESSAGING STOMP VERIFIER`);
    console.log(`========================================`);
    console.log(`HTTP Endpoint: ${BASE_URL}`);
    console.log(`WS Endpoint:   ${WS_URL}\n`);

    // 1. Register / Login User A
    const userA = {
        username: `user_a_${Date.now()}`,
        displayName: 'Kamau Retail',
        email: `kamau_${Date.now()}@relay.ke`,
        password: 'Password123!'
    };

    console.log(`[1/4] Registering User A (${userA.username})...`);
    const regResA = await fetch(`${BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userA)
    });
    const authA = await regResA.json();
    if (!regResA.ok) throw new Error(`User A registration failed: ${JSON.stringify(authA)}`);
    console.log(`✅ User A Registered! Token acquired. User ID: ${authA.user.id}`);

    // 2. Register User B
    const userB = {
        username: `user_b_${Date.now()}`,
        displayName: 'Wanjiku Supplies',
        email: `wanjiku_${Date.now()}@relay.ke`,
        password: 'Password123!'
    };

    console.log(`[2/4] Registering User B (${userB.username})...`);
    const regResB = await fetch(`${BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userB)
    });
    const authB = await regResB.json();
    if (!regResB.ok) throw new Error(`User B registration failed: ${JSON.stringify(authB)}`);
    console.log(`✅ User B Registered! User ID: ${authB.user.id}`);

    // 3. Create Direct Conversation
    console.log(`[3/4] Creating DM conversation between User A and User B...`);
    const convRes = await fetch(`${BASE_URL}/conversations`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${authA.token}`
        },
        body: JSON.stringify({
            type: 'DM',
            memberIds: [authB.user.id]
        })
    });
    const conv = await convRes.json();
    if (!convRes.ok) throw new Error(`Conversation creation failed: ${JSON.stringify(conv)}`);
    console.log(`✅ Conversation created! ID: ${conv.id}`);

    // 4. Connect via WebSocket STOMP
    console.log(`[4/4] Connecting User A to WebSocket STOMP (${WS_URL})...`);
    const WebSocketClient = globalThis.WebSocket || (await import('ws')).default;
    const ws = new WebSocketClient(WS_URL);

    await new Promise((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error('WebSocket connection timed out')), 10000);

        ws.onopen = () => {
            console.log(`⚡ WebSocket Connected. Sending STOMP CONNECT frame...`);
            const connectFrame = `CONNECT\naccept-version:1.2\nAuthorization:Bearer ${authA.token}\nheart-beat:10000,10000\n\n\0`;
            ws.send(connectFrame);
        };

        ws.onmessage = (event) => {
            const raw = event.data.toString();
            console.log(`📩 Received Frame:\n${raw.replace(/\0/g, '^@')}`);

            if (raw.startsWith('CONNECTED')) {
                console.log(`🎉 STOMP CONNECTED successfully! Presence marked ONLINE.`);

                // Subscribe to conversation topic
                const subFrame = `SUBSCRIBE\nid:sub-1\ndestination:/topic/conversations/${conv.id}\nack:auto\n\n\0`;
                ws.send(subFrame);
                console.log(`📡 Subscribed to /topic/conversations/${conv.id}`);

                // Send test chat message
                const msgPayload = JSON.stringify({
                    conversationId: conv.id,
                    content: 'Jambo kutoka kwa automated test script!',
                    contentType: 'TEXT'
                });
                const sendFrame = `SEND\ndestination:/app/chat.send\ncontent-type:application/json\nreceipt:receipt-101\n\n${msgPayload}\0`;
                ws.send(sendFrame);
                console.log(`📤 Sent message to /app/chat.send`);
            } else if (raw.startsWith('MESSAGE')) {
                console.log(`\n========================================`);
                console.log(`✅ SUCCESS: Received Broadcasted STOMP Message!`);
                console.log(`========================================\n`);
                clearTimeout(timeout);
                ws.close();
                resolve();
            } else if (raw.startsWith('ERROR')) {
                clearTimeout(timeout);
                reject(new Error(`STOMP Error frame: ${raw}`));
            }
        };

        ws.onerror = (err) => {
            clearTimeout(timeout);
            reject(err);
        };
    });

    console.log(`✨ All verification steps completed successfully!`);
}

run().catch(err => {
    console.error(`❌ Verification failed:`, err);
    process.exit(1);
});
