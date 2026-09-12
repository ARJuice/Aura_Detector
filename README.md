# AUR/S

## Local Android Aura Scanner

AUR/S is a local-network Android application that turns real-time person detection into a fictional aura-monitoring experience. The Android phone provides the camera preview and interactive overlay. A local Python server receives camera frames, detects and tracks people, and returns temporary subject metadata. Aura values, scan results, visual effects, audio, and haptics are deliberately fictional and generated for the experience.

The system is designed for one Android client and one inference server on the same local network. It does not use cloud services, persistent identity, face recognition, or video recording.

## Current Status

Implemented:

- CameraX preview and frame analysis on Android.
- JPEG frame transport over an authenticated WebSocket connection.
- FastAPI health endpoint and WebSocket server.
- YOLO instance segmentation configured for the `person` class.
- BoT-SORT tracking with normalized bounding boxes and simplified contours.
- Maximum of six subjects returned per frame.
- Android overlay coordinate mapping that accounts for crop, rotation, and preview transforms.
- Single-subject selection and selected-subject highlighting.
- Stable per-track fictional aura profiles and local bounded live readings.
- Local double-tap scans with finite and infinity outcomes.
- Optional scan sound, haptic feedback, reconnect state, and connection diagnostics.
- Python unit and integration tests for protocol, server, aura, and pipeline behavior.

Still requires full physical-device acceptance:

- Long-running performance measurements on the target GPU.
- Portrait and landscape alignment checks across representative devices.
- Manual validation of scan audio, haptics, selection, and track reacquisition.

## Architecture

```text
Android phone
	CameraX -> JPEG encoder -> OkHttp WebSocket
			^                         |
			| frame_state              | hello + frame
			|                         v
Local PC
	FastAPI /health and /ws -> JPEG decode -> YOLO segmentation -> BoT-SORT
													 -> normalized subjects + aura profiles
```

### Android client

The Android app is a Kotlin application built with Jetpack Compose, CameraX, OkHttp, and DataStore. It starts on a settings screen where the operator enters the server address, port, and session token. After a successful health check, the scanner opens the camera and maintains a single in-flight frame request so network or inference latency cannot create an ever-growing queue.

The client keeps the camera preview local. It draws server subject metadata over that preview, owns subject selection, generates live aura readings, and runs the scan interaction locally.

### Python server

The server is a FastAPI application served by Uvicorn. It loads `yolo26n-seg.pt` once at startup, performs a warm-up inference, decodes incoming JPEGs with OpenCV, filters detections to people, tracks them with BoT-SORT, and returns at most six subjects. Relative model paths resolve from the repository root.

The server accepts one active WebSocket client. A random session token is generated at process startup and printed to the server log. Aura profiles are deterministic for a subject within the server session, but they do not represent measurements or real personal attributes.

## Requirements

### Inference server

- Windows, Linux, or macOS with Python 3.11 recommended.
- NVIDIA GPU and CUDA-enabled PyTorch for production-like inference performance. CPU fallback is supported for development.
- Python packages listed in [server/requirements.txt](server/requirements.txt).
- The repository checkpoint [yolo26n-seg.pt](yolo26n-seg.pt), or a compatible YOLO segmentation checkpoint supplied through `AURA_MODEL_PATH`.

### Android client

- Android Studio with SDK 35 installed.
- A physical Android device with Android 8.0/API 26 or newer.
- USB debugging enabled for installation and testing.
- Android device and inference server connected to the same reachable Wi-Fi network or private hotspot.

## Run the Server

From the repository root in PowerShell:

```powershell
python -m venv server\.venv
server\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r server\requirements.txt
python -m server.app --host 0.0.0.0 --port 8765
```

The server prints the session token during startup. Keep that token available for the Android settings screen. Verify the service locally with:

```powershell
Invoke-RestMethod http://127.0.0.1:8765/health
```

For a phone connection, replace `127.0.0.1` with the PC's LAN address. Allow TCP port `8765` through Windows Firewall on the Private network profile only, and avoid VPNs or Wi-Fi networks with client isolation.

Optional environment variables:

```powershell
$env:AURA_MODEL_PATH = "C:\path\to\model.pt"
$env:AURA_DEVICE = "0"       # use "cpu" for CPU inference
python -m server.app
```

## Build and Run Android

Open [android/AuraDetector](android/AuraDetector) in Android Studio, allow Gradle to sync, select a connected device, and run the `app` configuration. From PowerShell, a debug APK can also be built with:

```powershell
cd android\AuraDetector
.\gradlew.bat assembleDebug
```

Install the generated APK from `app\build\outputs\apk\debug\app-debug.apk`, or use Android Studio's Run action.

On first launch:

1. Enter the PC's LAN IP address.
2. Enter `8765`, unless the server uses another port.
3. Enter the session token printed by the server.
4. Select **TEST CONNECTION** and wait for `LINK: OK`.
5. Select **CONNECT** and grant camera permission.

The app stores the last server configuration locally with Android DataStore. Clear the app's data to remove the stored token and connection settings.

## Protocol Overview

The protocol is versioned and uses JSON messages over `/ws`.

1. The client sends a `hello` message containing `version`, `clientId`, and the session token.
2. The server returns `hello_ack` with a session ID and subject limit.
3. The client sends base64-encoded JPEG `frame` messages.
4. The server returns a matching `frame_state` with inference timing and normalized subjects.
5. Invalid frames return recoverable `error` messages when the connection can continue.

Frame limits are 1920x1080 and 2 MB per JPEG. Frame IDs must increase strictly. Subject boxes use `[x, y, width, height]` with values normalized to `0..1`; contours use normalized `[x, y]` points.

## Test

Run the Python test suite from the repository root after activating the virtual environment:

```powershell
python -m pytest server\tests -q
```

The tests cover message validation, authentication, health reporting, WebSocket round trips, invalid frames, subject filtering, normalized geometry, tracking fallbacks, and deterministic aura profile behavior.

## Repository Layout

```text
.
├── android/AuraDetector/       Android application
├── server/app.py               FastAPI lifecycle, health, and WebSocket endpoint
├── server/pipeline.py          YOLO decoding, person filtering, tracking, geometry
├── server/protocol.py          Versioned Pydantic message models and limits
├── server/aura.py              Fictional deterministic aura profile generation
├── server/tests/               Server and pipeline tests
├── docs/                       Product, technical, protocol, and acceptance docs
└── yolo26n-seg.pt              Default YOLO segmentation checkpoint
```

## Privacy and Scope

AUR/S is an entertainment and visualization system, not a medical, scientific, safety, or behavioral measurement tool. The server processes camera frames in memory for detection and does not provide face recognition. The application is intended for a trusted local network; the WebSocket token is session-scoped, clear-text local HTTP/WebSocket transport is enabled for development, and no cloud security boundary is provided.

Do not use the fictional aura output to make decisions about a person. Obtain consent before pointing a camera at people, and stop the server when it is no longer needed.

## Further Documentation

- [Build guide](AURA_DETECTOR_BUILD_GUIDE.md)
- [Product requirements](docs/PRD.md)
- [Technical requirements](docs/TRD.md)
- [WebSocket protocol](docs/WEBSOCKET_PROTOCOL.md)
- [Test and acceptance plan](docs/TEST_AND_ACCEPTANCE.md)
- [Security, privacy, and operations](docs/SECURITY_PRIVACY_OPERATIONS.md)
