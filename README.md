# 🚨 EmergencyMesh

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Technology-Bluetooth_LE-0082FC?style=for-the-badge&logo=bluetooth&logoColor=white"/>
  <img src="https://img.shields.io/badge/Type-Emergency_Communication-FF1744?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge"/>
</p>

<p align="center">
  <strong>Infrastructure-free emergency communication using Bluetooth Low Energy mesh networking.</strong><br/>
  When the internet goes down, EmergencyMesh keeps people connected.
</p>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [Architecture](#-architecture)
- [Module Breakdown](#-module-breakdown)
- [UI & UX Design](#-ui--ux-design)
- [Permissions](#-permissions)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Build & Run](#build--run)
- [How It Works](#-how-it-works)
- [Emergency Alert Types](#-emergency-alert-types)
- [Project Structure](#-project-structure)
- [Configuration](#-configuration)
- [Contributing](#-contributing)
- [Authors](#-authors)
- [License](#-license)

---

## 🌐 Overview

**EmergencyMesh** is an Android application designed for **crisis communication without internet or cellular infrastructure**. Built entirely on **Bluetooth Low Energy (BLE)**, it creates a self-healing, peer-to-peer mesh network between nearby Android devices. Users can broadcast typed emergency alerts — fire, medical emergencies, evacuation orders, and SOS distress signals — which propagate hop-by-hop through any chain of devices in range.

This makes EmergencyMesh invaluable in:

- Natural disasters (earthquakes, floods, wildfires) where cell towers are down
- Remote locations with no connectivity
- Large-scale events requiring decentralized coordination
- Search and rescue operations in dead zones
- Military-style tactical communication

> **No server. No internet. No SIM card required.** Just Bluetooth.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 📡 **BLE Mesh Networking** | Devices advertise and scan simultaneously, forming a dynamic mesh |
| 🔁 **Multi-Hop Routing** | Messages relay through intermediate nodes to extend range far beyond a single BLE hop |
| 📍 **GPS-Tagged Alerts** | Each emergency message is stamped with the sender's real-time GPS coordinates |
| 🔥 **One-Tap Alert Types** | Fire, Medical, Evacuation, and SOS alerts broadcast instantly |
| 📟 **Background Foreground Service** | Mesh stays active even when the app is minimized, with a persistent notification |
| 📊 **Live Radar Visualization** | Custom `MeshRadarView` shows nearby discovered devices in real time |
| 🔔 **Vibration & Notification Alerts** | Incoming emergency messages trigger vibration and a system notification |
| 🌑 **Dark Glassmorphism UI** | Futuristic dark-mode interface with translucent glass cards and neon accents |
| ⚡ **Wake Lock Support** | Prevents the CPU from sleeping so the mesh stays active in critical moments |

---

## 🏗 Architecture

EmergencyMesh follows a **layered service-oriented architecture**:

```
┌─────────────────────────────────────────────────┐
│                  UI Layer                        │
│   MainActivity  ·  MeshRadarView                │
└────────────────────┬────────────────────────────┘
                     │ binds / starts
┌────────────────────▼────────────────────────────┐
│              Service Layer                       │
│              MeshService (Foreground)            │
└───────┬─────────────────────────┬───────────────┘
        │                         │
┌───────▼──────────┐   ┌──────────▼──────────────┐
│   BLE Layer      │   │   Routing & Data Layer   │
│  BleAdvertiser   │   │   MeshRoutingEngine      │
│  BleScanner      │   │   EmergencyMessage       │
│  MeshConfig      │   │   LocationProvider       │
└──────────────────┘   └─────────────────────────┘
```

The `MeshService` is a **foreground Android service** that coordinates the BLE subsystem and routing engine. It persists independently of the activity lifecycle, ensuring the mesh remains operational even when the user switches apps or the screen is off.

---

## 🧩 Module Breakdown

### `MainActivity.kt`
The single-activity entry point. Responsible for:
- Requesting runtime permissions (Bluetooth, Location, Notifications)
- Starting and stopping `MeshService`
- Displaying the live device count, service status, and last received emergency
- Wiring up the four emergency broadcast buttons and the SOS FAB
- Toggling the `MeshRadarView` visibility when the mesh is active

### `MeshRadarView.kt`
A fully **custom `View`** that renders a real-time animated radar sweep. Discovered BLE devices appear as blips on the radar canvas, giving users immediate visual feedback about nearby nodes in the mesh. Built with Android's Canvas API.

### `ble/BleAdvertiser.kt`
Manages **BLE peripheral (advertiser) mode**. Encodes the device's current `EmergencyMessage` payload into the BLE advertising data packet and starts/stops advertising using `BluetoothLeAdvertiser`. Handles advertisement failure callbacks and automatically retries on transient errors.

### `ble/BleScanner.kt`
Manages **BLE central (scanner) mode**. Continuously scans for nearby advertising EmergencyMesh devices using `BluetoothLeScanner`. On discovery, it decodes the advertising payload, reconstructs the `EmergencyMessage`, and passes it to `MeshRoutingEngine` for deduplication and relay decisions.

### `ble/MeshConfig.kt`
A centralized configuration object defining:
- The mesh **Service UUID** used to identify EmergencyMesh advertising packets
- BLE advertising and scan settings (power levels, intervals, scan mode)
- TTL (Time-To-Live) limits for message propagation
- Any other mesh-wide constants

### `location/LocationProvider.kt`
Wraps Android's `FusedLocationProviderClient` (or `LocationManager`) to provide the device's current `latitude` and `longitude`. Called when composing a new outbound `EmergencyMessage` so the alert is geographically tagged at the moment of transmission.

### `models/EmergencyMessage.kt`
The core **data model** representing a single emergency event. Fields include:
- `messageId` — unique UUID for deduplication across hops
- `type` — alert category (`FIRE`, `MEDICAL`, `EVACUATION`, `SOS`)
- `senderId` — originating device identifier
- `latitude` / `longitude` — GPS coordinates of the origin
- `timestamp` — epoch milliseconds of creation
- `ttl` — remaining hop count before the message expires
- `payload` — optional free-text content

### `routing/MeshRoutingEngine.kt`
The brain of the network layer. Implements:
- **Duplicate suppression** via a seen-message cache keyed on `messageId`
- **TTL management** — decrements TTL on each relay and discards messages that reach zero
- **Relay decision logic** — decides whether to re-advertise a received message
- **Incoming message dispatch** — notifies `MeshService` when a valid new emergency is received

### `service/MeshService.kt`
An Android **Foreground Service** that:
- Acquires a `WakeLock` to keep the CPU alive
- Starts `BleAdvertiser` and `BleScanner` in parallel
- Owns the `MeshRoutingEngine` and routes messages between the BLE and routing layers
- Posts a persistent notification (with `ic_mesh_notification` icon) showing mesh status
- Sends a `LocalBroadcast` to the UI when a new emergency message arrives
- Cleans up all resources on `onDestroy`

---

## 🎨 UI & UX Design

EmergencyMesh uses a **dark futuristic glassmorphism** design language:

- **Background**: Deep navy-to-black linear gradient (`bg_main_gradient`)
- **Cards**: Semi-transparent frosted glass effect (`bg_glass_card`) with rounded corners
- **Accent colors**: Neon blue (`#1976D2`), neon red for SOS, amber for evacuation, green for medical
- **Typography**: Bold uppercase labels for a tactical/HUD aesthetic
- **Status indicator**: Animated dot (active/inactive) in the header
- **Radar**: Animated circular sweep — visible only when mesh is running
- **Emergency Grid**: 2-column card grid with gradient fills per alert type:
  - 🔥 Fire — orange/red gradient
  - 🚑 Medical — green/teal gradient
  - ⚠️ Evacuation — amber/yellow gradient
- **SOS FAB**: Oversized (80dp) red floating action button anchored at the bottom center

---

## 🔐 Permissions

EmergencyMesh requests the following Android permissions:

| Permission | Purpose |
|---|---|
| `BLUETOOTH` | Legacy BLE access (Android < 12) |
| `BLUETOOTH_ADMIN` | Control BLE adapter state |
| `BLUETOOTH_SCAN` | Discover nearby BLE devices (Android 12+) |
| `BLUETOOTH_ADVERTISE` | Broadcast BLE advertising packets (Android 12+) |
| `BLUETOOTH_CONNECT` | Connect to discovered devices (Android 12+) |
| `ACCESS_FINE_LOCATION` | GPS coordinates for message tagging + BLE scan requirement |
| `ACCESS_COARSE_LOCATION` | Fallback location for BLE scan |
| `FOREGROUND_SERVICE` | Run `MeshService` in the foreground |
| `FOREGROUND_SERVICE_LOCATION` | Foreground service with location type (Android 14+) |
| `WAKE_LOCK` | Keep CPU awake during active mesh operation |
| `VIBRATE` | Alert user on receiving emergency messages |
| `POST_NOTIFICATIONS` | Show persistent mesh status notification (Android 13+) |

> **Note:** All dangerous permissions are requested at runtime with user-facing rationale dialogs. The app will gracefully inform the user if a critical permission is denied.

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Flamingo (2022.2.1) or newer
- **JDK 17** or higher
- An Android device or emulator running **Android 8.0 (API 26)** or higher
- A physical device is **strongly recommended** — BLE advertising/scanning does not function on most emulators
- For full testing: **two or more physical Android devices**

### Installation

1. **Clone the repository**

```bash
git clone https://github.com/raviraj82891/Sos-app.git
cd Sos-app
```

2. **Open in Android Studio**

   - File → Open → select the cloned folder
   - Wait for Gradle sync to complete

3. **Connect your device**

   - Enable **Developer Options** and **USB Debugging** on your Android device
   - Connect via USB

### Build & Run

**Via Android Studio:**

Click the ▶️ **Run** button or use `Shift + F10`.

**Via command line:**

```bash
# Debug build
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug

# Run all unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ How It Works

### 1. Mesh Activation
The user taps **ACTIVATE MESH**. `MainActivity` starts `MeshService` as a foreground service. The service acquires a `WakeLock` and simultaneously starts both `BleAdvertiser` and `BleScanner`.

### 2. Device Discovery
`BleScanner` continuously scans for BLE packets matching the EmergencyMesh **Service UUID**. Every discovered advertising packet is decoded into an `EmergencyMessage` and submitted to `MeshRoutingEngine`.

### 3. Routing & Deduplication
`MeshRoutingEngine` checks the message's `messageId` against a local seen-cache. If it has already been relayed, it is silently discarded. If it is new and TTL > 0, the TTL is decremented and the message is scheduled for re-advertisement.

### 4. Re-Advertisement (Relay)
`BleAdvertiser` encodes the relayed `EmergencyMessage` back into BLE advertising data and broadcasts it. This allows a message originating from Device A to reach Device C through Device B — even if A and C are out of direct range.

### 5. UI Notification
When a new incoming message passes deduplication, `MeshService` broadcasts a `LocalBroadcast` intent. `MainActivity` receives it, updates the "last emergency" text view, triggers the device vibrator, and posts a system notification.

```
Device A ──(BLE)──► Device B ──(BLE)──► Device C ──(BLE)──► Device D
  SOS                relay                relay                receives
  origin              TTL=4→3              TTL=3→2              TTL=2
```

---

## 🚨 Emergency Alert Types

| Alert | Icon | Use Case |
|---|---|---|
| **FIRE** | 🔥 | Active fire, smoke hazard, building evacuation |
| **MEDICAL** | 🚑 | Medical emergency, injury, need for first aid or ambulance |
| **EVACUATION** | ⚠️ | Ordered evacuation of an area, dangerous zone |
| **SOS** | 🆘 | General distress signal — life-threatening, need immediate help |

Each alert is tagged with the sender's live GPS coordinates at the time of broadcast, allowing recipients to know the geographic origin of the emergency.

---

## 📁 Project Structure

```
EmergencyMesh/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/raviraj/emergencymesh/
│           │   ├── MainActivity.kt              ← Entry point & UI controller
│           │   ├── MeshRadarView.kt             ← Custom radar canvas view
│           │   ├── ble/
│           │   │   ├── BleAdvertiser.kt         ← BLE peripheral/advertiser
│           │   │   ├── BleScanner.kt            ← BLE central/scanner
│           │   │   └── MeshConfig.kt            ← Mesh constants & UUIDs
│           │   ├── location/
│           │   │   └── LocationProvider.kt      ← GPS coordinate provider
│           │   ├── models/
│           │   │   └── EmergencyMessage.kt      ← Core data model
│           │   ├── routing/
│           │   │   └── MeshRoutingEngine.kt     ← Deduplication & relay logic
│           │   └── service/
│           │       └── MeshService.kt           ← Foreground mesh service
│           └── res/
│               ├── drawable/
│               │   ├── bg_main_gradient.xml
│               │   ├── bg_glass_card.xml
│               │   ├── fire_gradient.xml
│               │   ├── medical_gradient.xml
│               │   ├── evacuation_gradient.xml
│               │   ├── status_dot_active.xml
│               │   ├── status_dot_inactive.xml
│               │   └── ic_mesh_notification.xml
│               ├── layout/
│               │   └── activity_main.xml
│               └── values/
│                   ├── colors.xml
│                   ├── strings.xml
│                   └── themes.xml
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🔧 Configuration

All mesh-level tuning parameters are centralized in **`MeshConfig.kt`**:

| Parameter | Description |
|---|---|
| `MESH_SERVICE_UUID` | The unique BLE service UUID that identifies EmergencyMesh packets |
| `ADVERTISE_POWER` | BLE transmission power level (balance between range and battery) |
| `SCAN_MODE` | BLE scan aggressiveness (`LOW_LATENCY`, `BALANCED`, `LOW_POWER`) |
| `DEFAULT_TTL` | Number of hops an outbound message will travel before expiring |
| `SEEN_CACHE_SIZE` | Max number of message IDs held in the dedup cache |
| `ADVERTISE_DURATION_MS` | How long each relay advertisement is broadcast |

Adjust these values in `MeshConfig.kt` before building to tune the network for your specific environment and battery requirements.

---

## 🤝 Contributing

Contributions are warmly welcome! Here is how to get involved:

1. **Fork** the repository on GitHub
2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Commit your changes** with clear messages
   ```bash
   git commit -m "feat: add message priority levels to EmergencyMessage"
   ```
4. **Push** to your fork
   ```bash
   git push origin feature/your-feature-name
   ```
5. **Open a Pull Request** against `main`

### Ideas for Contribution

- 🔐 Message signing / authentication to prevent spoofing
- 🗺 Map view showing GPS-tagged alerts on an offline map
- 💬 Two-way text messaging between mesh nodes
- 🔋 Battery-aware scan interval throttling
- 📱 iOS companion app using CoreBluetooth
- 🌐 Optional LoRa / WiFi-Direct transport backends
- 🧪 Automated integration tests with BLE mocking

---

## 👤 Authors

| Name | Role | GitHub |
|---|---|---|
| **Ravi Raj** | Lead Developer | [@raviraj82891](https://github.com/raviraj82891) |
| **Pragya Kumari** | Contributor | [@pragya-k-24](https://github.com/pragya-k-24) |
| **Pooja Mohta** | Contributor | [@cybercobra28](https://github.com/cybercobra28) |
| **Aditi Gupta** | Contributor | [@adanoia](https://github.com/adanoia) |

---

## 📄 License

This project is licensed under the **MIT License**.

```
MIT License

Copyright (c) 2025 Ravi Raj

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

<p align="center">
  Built with ❤️ for when it matters most — when infrastructure fails and lives depend on communication.
</p>
