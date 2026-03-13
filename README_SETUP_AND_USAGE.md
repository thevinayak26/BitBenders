# RescueMesh / RescueChat Setup and Usage Guide

This guide explains everything needed to build, run, install, and test the app end-to-end (Android app + optional server/dashboard).

## 1. What this project contains

- Android app: `RescueChat` (Kotlin/Gradle)
- Optional command backend: `rescuemesh-server` (Node.js/Express)
- Optional web dashboard: `rescuemesh-dashboard` (Next.js)

The Android app supports:
- Mesh messaging over Bluetooth
- SOS trigger and relay over mesh
- Gateway mode (internet-assisted relay)
- Human-readable SOS messages with location links
- In-message location open action

## 2. Prerequisites

### Required (Android app)

- Windows/macOS/Linux machine
- Android Studio (latest stable)
- Android SDK installed
- Java 17 (Temurin/OpenJDK recommended)
- At least one Android phone with:
  - Android 8+ (recommended newer)
  - Bluetooth
  - Location services

### Required for USB install/debug

- USB cable
- Developer options enabled on phone
- USB debugging enabled
- `adb` available (from Android SDK platform-tools)

### Optional (server/dashboard)

- Node.js 18+ (recommended Node 20 LTS)
- npm

## 3. One-time machine setup

### 3.1 Set Android SDK path

Create or update `RescueChat/local.properties`:

```properties
sdk.dir=C:\\Users\\<your-user>\\AppData\\Local\\Android\\Sdk
```

### 3.2 Ensure Java 17 is used by Gradle

In PowerShell before Gradle commands:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;C:\Users\<your-user>\AppData\Local\Android\Sdk\platform-tools;$env:Path"
java -version
```

`java -version` should print Java 17.

### 3.3 Verify adb

```powershell
adb devices
```

Authorize your device on phone prompt if needed.

## 4. Build the Android app

From `RescueChat`:

```powershell
.\gradlew.bat app:assembleDebug --no-daemon
```

Output APKs are under:
- `RescueChat/app/build/outputs/apk/debug/`

Common installable APK:
- `app-universal-debug.apk`

## 5. Install on phone(s)

### Install on currently connected device

```powershell
adb install -r RescueChat\app\build\outputs\apk\debug\app-universal-debug.apk
```

### If install fails

- `INSTALL_FAILED_USER_RESTRICTED`: approve install on phone, enable install via USB (if OEM setting exists)
- `INSTALL_FAILED_INSUFFICIENT_STORAGE`: free space on phone and retry

## 6. Optional: run local backend and dashboard

### 6.1 Run server

From `rescuemesh-server`:

```powershell
npm install
node server.js
```

Server health:
- `http://localhost:3001/`

### 6.2 Run dashboard

From `rescuemesh-dashboard`:

```powershell
npm install
npm run dev
```

Dashboard:
- `http://localhost:3000`

## 7. Usage modes

## 7.1 Mesh-only mode (no backend required)

Use this to verify pure device-to-device relay.

Phone A (sender):
- Gateway mode OFF
- Internet OFF
- Bluetooth ON
- Location ON

Phone B (relay peer):
- Gateway mode OFF
- Internet OFF
- Bluetooth ON
- Location ON

Test:
1. Keep phones close (1-3m initially)
2. Confirm normal chat message A -> B works
3. Trigger SOS on A
4. Verify SOS readable message appears and relays on peer(s)

## 7.2 Mesh + gateway mode (with backend/dashboard)

Phone A (sender):
- Gateway mode OFF
- Internet OFF

Phone B (gateway):
- Gateway mode ON
- Internet ON
- (If using laptop localhost backend) keep USB connected and run:

```powershell
adb reverse tcp:3001 tcp:3001
```

Test:
1. Start server and dashboard
2. Trigger SOS on A
3. B relays SOS to backend
4. Dashboard shows active SOS
5. ACK from dashboard clears active event

## 8. In-app behavior to know

- SOS messages are sent in readable human format, not raw JSON in chat view
- SOS messages include maps URL
- Chat message bubble can show an "Open SOS Location" action for location-enabled SOS messages
- Fall/crash detection uses sensors and updates risk indicators

## 9. Required permissions on phone

Grant when prompted:
- Bluetooth permissions
- Location permissions
- Notifications (recommended)

Also recommended:
- Disable battery optimization for the app (prevents background throttling)

## 10. Troubleshooting

### Device not visible in adb

- Reconnect USB
- Unlock phone
- Re-enable USB debugging
- Accept RSA prompt
- Run:

```powershell
adb kill-server
adb start-server
adb devices -l
```

### Gradle uses wrong Java

If Gradle reports Java 8/11 mismatch, set `JAVA_HOME` to JDK 17 and prepend its `bin` in PATH for the shell session.

### Dashboard/server not opening

Check ports:
- 3001 for server
- 3000 for dashboard

Kill conflicting process or restart services.

### Mesh not relaying

- Ensure both devices are on same app build
- Keep both apps foreground during test
- Keep phones close first
- Confirm normal chat relay first before SOS relay

## 11. Suggested verification checklist

1. Build succeeds
2. Install succeeds on both phones
3. Mesh-only chat A -> B works
4. Mesh-only SOS relay works
5. SOS shows readable text + map action in message
6. Optional gateway relay shows on dashboard and ACK clears event

---

If you are sharing this with teammates, ask them to follow sections 2, 3, 4, 5, then test with sections 7 and 11.
