val dpm = getSystemService(DevicePolicyManager::class.java)

val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

val executor = ContextCompat.getMainExecutor(this)

val listener =
    DevicePolicyManager.OnClearApplicationUserDataListener { pkg, success ->
        Log.i("MDM", "Wipe result for $pkg = $success")
    }

val started = dpm.clearApplicationUserData(
    admin,
    "com.other.app",
    executor,
    listener
)

Log.d("MDM", "Request accepted = $started")