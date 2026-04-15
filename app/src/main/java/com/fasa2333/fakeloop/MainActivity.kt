package com.fasa2333.fakeloop

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fasa2333.fakeloop.ui.theme.FakeLoopTheme

class MainActivity : ComponentActivity() {
    private lateinit var blePeripheralManager: BlePeripheralManager

    // Track whether we already started peripheral automatically at app launch
    private var autoPeripheralStarted: Boolean = false
    // If user chosen to start but permissions missing, remember to start after permission is granted
    private var pendingStartAfterPermission: Boolean = false

    private val TARGET_BT_NAME = "LR029429BD"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // If all requested permissions were granted, start peripheral and show name prompt
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // If a start was pending because of missing permissions, start now
            if (pendingStartAfterPermission) {
                // try to set name and start peripheral
                setBluetoothName(TARGET_BT_NAME)
                if (!blePeripheralManager.isAdvertising()) {
                    blePeripheralManager.startPeripheral()
                    autoPeripheralStarted = true
                }
                pendingStartAfterPermission = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun ensureNameAndStartIfAllowed() {
        // Try to set name (requires BLUETOOTH_CONNECT on Android S+)
        setBluetoothName(TARGET_BT_NAME)
        if (hasAllNeededPermissions()) {
            if (!blePeripheralManager.isAdvertising()) {
                blePeripheralManager.startPeripheral()
                autoPeripheralStarted = true
            }
        } else {
            // request permissions and mark pending
            pendingStartAfterPermission = true
            requestNeededPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        blePeripheralManager = BlePeripheralManager(this)

        // Request permissions at startup; if granted immediately we'll set name and start advertising.
        // If not granted yet, permission callback will set name and start once user grants.
        if (hasAllNeededPermissions()) {
            // permissions already granted: set name and start
            setBluetoothName(TARGET_BT_NAME)
            if (!blePeripheralManager.isAdvertising()) {
                blePeripheralManager.startPeripheral()
                autoPeripheralStarted = true
            }
        } else {
            // request and mark pending start
            pendingStartAfterPermission = true
            requestNeededPermissions()
        }

        setContent {
            FakeLoopTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val toRequest = perms.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent(modifier: Modifier = Modifier) {
        // Simplified UI: big title, subtitle, jump-rope buttons, footer
        var showCustomDialog by remember { mutableStateOf(false) }
        var customHex by remember { mutableStateOf("") }
        var customError by remember { mutableStateOf("") }
        // Show disclaimer at startup
        var showDisclaimer by remember { mutableStateOf(true) }
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text(
                text = "Fake Loop",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )

            // Subtitle / hint
            Text(
                text = "若小程序扫描不到设备，请检查是否给予了Fake Loop所需的权限，或在系统设置里修改设备名称为\"LR029429BD\"",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // Jump-rope buttons (primary interactive elements)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    // 开始跳绳 -> send 6F0201000072
                    val hex = "6F0201000072"
                    val payload = hexStringToByteArray(hex)
                    blePeripheralManager.notifySubscribers(payload)
                }) {
                    Text(text = "开始跳绳")
                }

                Button(onClick = {
                    // 跳绳800下 -> send 6F040B0000001F406930005703D802A8
                    val hex = "6F040B0000001F406930005703D802A8"
                    val payload = hexStringToByteArray(hex)
                    blePeripheralManager.notifySubscribers(payload)
                }) {
                    Text(text = "跳绳800下")
                }

                Button(onClick = {
                    // 跳绳1600下 -> send 6F040B0000003E8069B1288F015E036C
                    val hex = "6F040B0000003E8069B1288F015E036C"
                    val payload = hexStringToByteArray(hex)
                    blePeripheralManager.notifySubscribers(payload)
                }) {
                    Text(text = "跳绳1600下")
                }

                // Send custom data button
                Button(onClick = { showCustomDialog = true }) {
                    Text(text = "发送自定义数据")
                }
            }

            if (showCustomDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomDialog = false; customHex = ""; customError = "" },
                    title = { Text(text = "发送自定义 Hex") },
                    text = {
                        Column {
                            Text(text = "请输入十六进制字符串（例如 6F0201000072）：", fontSize = 12.sp)
                            TextField(
                                value = customHex,
                                onValueChange = { customHex = it; customError = "" },
                                placeholder = { Text(text = "Hex 字符串") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (customError.isNotEmpty()) {
                                Text(text = customError, color = Color.Red, fontSize = 12.sp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val hex = customHex.replace("\\s".toRegex(), "")
                            val ok = hex.matches(Regex("^[0-9a-fA-F]+$")) && hex.length % 2 == 0
                            if (!ok) {
                                customError = "输入不是有效的偶数长度十六进制字符串"
                                return@TextButton
                            }
                            val payload = try {
                                hexStringToByteArray(hex)
                            } catch (e: Exception) {
                                customError = "Hex 转换失败"
                                return@TextButton
                            }
                            blePeripheralManager.notifySubscribers(payload)
                            showCustomDialog = false
                            customHex = ""
                        }) { Text("发送") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomDialog = false; customHex = ""; customError = "" }) { Text("取消") }
                    }
                )
            }

            // Startup disclaimer dialog
            if (showDisclaimer) {
                AlertDialog(
                    onDismissRequest = { /* require explicit dismiss */ },
                    title = { Text(text = "免责声明") },
                    text = { Text(text = "本软件仅供蓝牙通信技术的交流与学习使用。严禁利用本软件进行任何形式的体育打卡作弊或虚假记录。因违规使用产生的一切后果由用户自行承担，开发者不承担任何法律责任。") },
                    confirmButton = {
                        TextButton(onClick = { showDisclaimer = false }) {
                            Text(text = "我知道了")
                        }
                    },
                    dismissButton = null
                )
            }

            // Footer
            val uriHandler = LocalUriHandler.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "by 风洒青泥",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "觉得好用吗？别忘了戳这里前往 GitHub，给我点个 Star 支持下我哦⭐~",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/fasa70/Fake_Loop")
                    }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setBluetoothName(name: String): Boolean {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Request permission and return false for now
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    return false
                }
            }
            // Prefer setName when available
            try {
                val method = BluetoothAdapter::class.java.getMethod("setName", String::class.java)
                method.invoke(adapter, name)
            } catch (ex: Exception) {
                // Fallback to property if available
                adapter.name = name
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Helper: convert hex string to ByteArray
    private fun hexStringToByteArray(s: String): ByteArray {
        val cleaned = s.replace("\\s".toRegex(), "")
        val len = cleaned.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleaned[i], 16) shl 4) + Character.digit(cleaned[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun hasAllNeededPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FakeLoopTheme {
        Greeting("Android")
    }
}