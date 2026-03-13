# RescueMesh Runbook

This workspace now contains three parts:

- Android app: `RescueChat`
- Gateway server: `rescuemesh-server`
- Command dashboard: `rescuemesh-dashboard`

## 1) Android (RescueChat)

### Required local setup

- JDK 17+
- Android SDK installed
- `local.properties` in `RescueChat` with:

```
sdk.dir=C\\:\\Path\\To\\Android\\Sdk
```

Example:

```
sdk.dir=C\\:\\Users\\sidsh\\AppData\\Local\\Android\\Sdk
```

### Build

From `RescueChat`:

```
.\gradlew.bat assembleDebug testDebugUnitTest
```

### What was integrated

- SOS crash/fall detection foreground service
- Manual SOS trigger flow (SOS modal from header)
- Mesh relay bridge for SOS/ACK payloads using `SOS::` and `ACK::` message prefixes
- Gateway POST bridge to `/sos`

## 2) Gateway Server

From `rescuemesh-server`:

```
npm install
npm run dev
```

Server URL:

- `http://localhost:3001`

Useful endpoints:

- `GET /`
- `POST /sos`
- `POST /ack`
- `GET /events`

## 3) Dashboard

From `rescuemesh-dashboard`:

1. Create `.env.local`:

```
NEXT_PUBLIC_SERVER_URL=http://localhost:3001
```

2. Install and run:

```
npm install
npm run dev
```

3. Production build check:

```
npm run build
```

Dashboard URL:

- `http://localhost:3000`

## 4) End-to-end smoke test

1. Start gateway server.
2. Start dashboard.
3. Post a sample SOS:

```
curl -X POST http://localhost:3001/sos \
  -H "Content-Type: application/json" \
  -d '{"id":"demo-1","nodeId":"phone-A","latitude":28.6139,"longitude":77.2090,"batteryLevel":80,"sosType":"CRASH","ttl":7}'
```

4. Verify dashboard receives and renders alert.
5. Send ACK from dashboard or API and verify active alert clears.

## 5) Security and dependency status

- `rescuemesh-server`: `npm audit` clean.
- `rescuemesh-dashboard`: upgraded Next.js to `15.5.10`, `npm audit` clean.

## 6) Deployment

- Server: deploy `rescuemesh-server` to Render.
- Dashboard: deploy `rescuemesh-dashboard` to Vercel.
- After deployment, update Android gateway URL in:
  - `RescueChat/app/src/main/java/com/bitchat/android/sos/GatewayBridgeService.kt`
