# Teacher-Student Drawing App (WiFi Direct POC)

A real-time collaborative drawing application where teachers can draw and guide students on their screens in real-time using **WiFi Direct** (peer-to-peer, no backend required).

## Features

- 🎨 **Drawing Canvas**: Smooth, responsive drawing with touch gestures
- 👨‍🏫 **Teacher Mode**: Draw on canvas, drawings sync to students in real-time
- 👨‍🎓 **Student Mode**: View-only mode that displays teacher's drawings in real-time
- 📶 **WiFi Direct**: Uses Android WiFi Direct for peer-to-peer communication (no internet/backend needed)
- 🔄 **Real-time Sync**: Socket-based communication for instant synchronization
- 🗑️ **Clear Canvas**: Teacher can clear the canvas at any time

## Architecture

- **Frontend**: Jetpack Compose with Material Design 3
- **Communication**: WiFi Direct + TCP Sockets
- **Navigation**: Jetpack Navigation Compose
- **State Management**: Kotlin Coroutines Flow

## Requirements

- Android 7.0 (API 24) or higher
- WiFi Direct support on both devices
- Location permissions (required for WiFi Direct discovery)

## Setup

1. **No Backend Required**: This app uses WiFi Direct for direct device-to-device communication

2. **Permissions**:
   - Location permission (required for WiFi Direct)
   - WiFi permissions (automatically handled)

3. **Build and Run**:
   ```bash
   ./gradlew build
   ```

## How to Use

### WiFi Direct Mode (Production - Two Real Devices)

1. **On Teacher Device (Device 1)**:
   - Launch the app
   - Tap "Teacher (Draw & Guide)"
   - Wait for student to connect via WiFi Direct
   - Once connected, start drawing on the canvas
   - Your drawings will appear on student device in real-time
   - Use "Clear Canvas" button to erase all drawings

2. **On Student Device (Device 2)**:
   - Launch the app
   - Tap "Student (View Only)"
   - Tap "Search for Devices"
   - Select the teacher's device from the list
   - Wait for connection to establish
   - Watch teacher's drawings appear in real-time

### Development Mode (Testing with Emulator + Real Device)

**Note:** WiFi Direct doesn't work with emulators. Use Development Mode for testing.

1. **On Emulator (Teacher)**:
   - Launch the app
   - Tap "Show Development Mode (Emulator Testing)"
   - Tap "Dev: Teacher"
   - Tap "Start Server"
   - Once connected, start drawing

2. **On Real Device (Student)**:
   - Make sure both devices are on the same WiFi network
   - Launch the app
   - Tap "Show Development Mode (Emulator Testing)"
   - Tap "Dev: Student"
   - Enter IP address:
     - For emulator as teacher: `10.0.2.2` (this connects to host machine)
     - For real device as teacher: Enter the real device's WiFi IP (find in WiFi settings)
   - Tap "Connect"
   - Watch teacher's drawings appear in real-time

**IP Address Help:**
- Emulator → Host machine: Use `10.0.2.2`
- Real device → Real device: Use the device's WiFi IP address (Settings → WiFi → Network details)
- Make sure both devices are on the same local network

## Project Structure

```
app/src/main/java/com/example/myapplication123/
├── MainActivity.kt              # Main activity with navigation & permissions
├── ui/
│   ├── DrawingCanvas.kt        # Drawing canvas component
│   ├── RoleSelectionScreen.kt  # Initial role selection
│   ├── ConnectionScreen.kt      # WiFi Direct connection UI
│   ├── TeacherScreen.kt        # Teacher drawing interface
│   └── StudentScreen.kt        # Student viewing interface
├── service/
│   ├── WiFiDirectService.kt    # WiFi Direct device discovery & connection
│   └── DrawingSyncService.kt   # Socket-based drawing sync service
└── util/
    └── PermissionsHelper.kt    # Permission management utilities
```

## Technologies Used

- Kotlin
- Jetpack Compose
- WiFi Direct (Android P2P)
- TCP Sockets
- Kotlin Coroutines & Flow
- Material Design 3

## Notes

- **WiFi Direct**: Both devices must support WiFi Direct (most modern Android devices do)
- **Permissions**: Location permission is required for WiFi Direct device discovery (Android requirement)
- **No Internet**: This app works completely offline - no internet connection or backend needed
- **IP Address**: The app automatically detects the Group Owner IP address from WiFi Direct connection

## Troubleshooting

- **No devices found**: Make sure both devices have WiFi Direct enabled and location permission granted
- **Connection fails**: Ensure both devices are running the app and close to each other
- **Drawing not syncing**: Check that WiFi Direct connection is established before drawing

## Future Enhancements

- Multiple students support
- Color selection
- Stroke width adjustment
- Undo/Redo functionality
- Connection status indicators
- Drawing history/save
- Better error handling

# RemoteDrawingSample-POC
