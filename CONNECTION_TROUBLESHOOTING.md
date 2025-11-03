# Connection Troubleshooting Guide

## Common Issues and Solutions

### Issue 1: "Connection Refused" Error

**Symptoms:** Student sees "Connection Refused" when trying to connect.

**Solutions:**
1. ✅ **Make sure Teacher started the server FIRST**
   - Teacher must tap "Start Server" and see "Server Running" message
   - Wait until teacher sees "Waiting for student to connect..."

2. ✅ **Check the IP Address**
   - **If Teacher is on Emulator:** Student should use `10.0.2.2`
   - **If Teacher is on Real Device:** Use the IP shown on Teacher's screen
   - Make sure no spaces or extra characters in IP field

3. ✅ **Check Port Number**
   - Default is `8888`
   - Make sure both devices use the same port number
   - Try a different port if 8888 doesn't work (e.g., 8080, 9999)

4. ✅ **Same WiFi Network**
   - Both devices MUST be on the same WiFi network
   - Check WiFi settings on both devices
   - Some routers have "Isolate clients" feature - disable it if available

### Issue 2: "Connection Timeout" Error

**Symptoms:** Student sees "Connection Timeout" after waiting.

**Solutions:**
1. ✅ **Check Firewall Settings**
   - On computer running emulator: Check Windows/Mac firewall
   - Disable firewall temporarily to test
   - Add exception for port 8888

2. ✅ **Check IP Address Again**
   - For emulator: Must be `10.0.2.2` exactly
   - For real device: Get IP from Settings → WiFi → Network Details
   - Make sure it's the IPv4 address, not IPv6

3. ✅ **Server Not Running**
   - Teacher must see "Server Running" message
   - If teacher sees an error, restart the app

### Issue 3: Teacher Shows "IP not found"

**Solutions:**
1. ✅ **Check WiFi is Connected**
   - Teacher device must be connected to WiFi
   - Turn WiFi off and on again

2. ✅ **Manual IP Entry**
   - If IP is not detected, check manually:
     - Settings → WiFi → Tap connected network → View IP address
   - Share this IP with student

### Issue 4: Devices Connect But Drawing Doesn't Sync

**Solutions:**
1. ✅ **Check Connection Status**
   - Teacher should see "✓ Connected!" message
   - Student should see "Connected!" message
   - If not connected, connection was lost

2. ✅ **Restart Connection**
   - Disconnect and reconnect
   - Restart both apps

## Step-by-Step Connection Process

### For Teacher (Server):

1. ✅ Launch app
2. ✅ Tap "Show Development Mode"
3. ✅ Tap "Dev: Teacher"
4. ✅ Enter Port: `8888` (or another port)
5. ✅ Tap "Start Server"
6. ✅ Wait for "Server Running" message
7. ✅ **Share the IP address shown** with Student
8. ✅ Wait for "Student connected!" message

### For Student (Client):

1. ✅ Make sure Teacher has started server
2. ✅ Launch app
3. ✅ Tap "Show Development Mode"
4. ✅ Tap "Dev: Student"
5. ✅ Enter Teacher's IP Address:
   - Emulator teacher: `10.0.2.2`
   - Real device teacher: Use IP from teacher's screen
6. ✅ Enter Port: `8888` (same as teacher)
7. ✅ Tap "Connect"
8. ✅ Wait for "Connected!" message

## Testing Connection

### Quick Test:
1. Teacher: Start server, note the IP
2. Student: Use ping tool (if available) or try connecting
3. Check error messages - they provide specific guidance

### Alternative: Use Terminal/ADB
On computer with emulator, test if port is listening:
```bash
# Check if port is open
netstat -an | grep 8888
# or
lsof -i :8888
```

## Still Not Working?

1. ✅ **Restart Both Apps** - Close and reopen both apps
2. ✅ **Restart Devices** - Restart both devices
3. ✅ **Try Different Port** - Use 8080 or 9999 instead of 8888
4. ✅ **Check Router Settings** - Some routers block device-to-device communication
5. ✅ **Use Mobile Hotspot** - Create hotspot on one device, connect other to it
6. ✅ **Check Logs** - Use Android Studio Logcat to see detailed error messages

## Network Requirements

- ✅ Both devices on same WiFi network
- ✅ No VPN active on either device
- ✅ Firewall allows connections on port 8888
- ✅ Router allows device-to-device communication (AP isolation disabled)

## Common Error Messages

| Error Message | Meaning | Solution |
|--------------|---------|----------|
| "Connection Refused" | Server not running or wrong IP | Start teacher server, check IP |
| "Connection Timeout" | Can't reach server | Check IP, firewall, network |
| "Stream setup failed" | Connection established but streams failed | Restart connection |
| "IP not found" | Can't detect device IP | Connect to WiFi, check manually |

