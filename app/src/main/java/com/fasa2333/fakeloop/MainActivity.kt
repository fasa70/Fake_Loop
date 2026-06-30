package com.fasa2333.fakeloop

import android.Manifest
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.fasa2333.fakeloop.ui.theme.FakeLoopTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var blePeripheralManager: BlePeripheralManager

    // SharedPreferences keys
    companion object {
        private const val KEY_TARGET_JUMPS = "targetJumps"
        private const val KEY_TARGET_TIME = "targetTime"
        private const val KEY_RANDOM_ENABLED = "randomEnabled"
        private const val KEY_LAST_USED_TARGET_JUMPS = "lastUsedTargetJumps"
    }

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.Default)

    // Track whether we already started peripheral automatically at app launch
    private var autoPeripheralStarted: Boolean = false
    // If user chosen to start but permissions missing, remember to start after permission is granted
    private var pendingStartAfterPermission: Boolean = false

    private val TARGET_BT_NAME = "LR029429BD"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            if (pendingStartAfterPermission) {
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
        setBluetoothName(TARGET_BT_NAME)
        if (hasAllNeededPermissions()) {
            if (!blePeripheralManager.isAdvertising()) {
                blePeripheralManager.startPeripheral()
                autoPeripheralStarted = true
            }
        } else {
            pendingStartAfterPermission = true
            requestNeededPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 显示免责声明对话框
        AlertDialog.Builder(this)
            .setTitle("免责声明")
            .setMessage("本软件仅供蓝牙通信技术的交流与学习使用。严禁利用本软件进行任何形式的体育打卡作弊或虚假记录。因违规使用产生的一切后果由用户自行承担，开发者不承担任何法律责任。")
            .setPositiveButton("我知道了") { _, _ ->
                // 用户确认后继续初始化
                blePeripheralManager = BlePeripheralManager(this)
                prefs = getPreferences(Context.MODE_PRIVATE)
            }
            .show()

        blePeripheralManager = BlePeripheralManager(this)

        prefs = getPreferences(Context.MODE_PRIVATE)

        val savedTargetJumps = prefs.getInt(KEY_TARGET_JUMPS, 800)
        val savedTargetTime = prefs.getInt(KEY_TARGET_TIME, 400)
        val savedRandomEnabled = prefs.getBoolean(KEY_RANDOM_ENABLED, true)

        if (hasAllNeededPermissions()) {
            setBluetoothName(TARGET_BT_NAME)
            if (!blePeripheralManager.isAdvertising()) {
                blePeripheralManager.startPeripheral()
                autoPeripheralStarted = true
            }
        } else {
            pendingStartAfterPermission = true
            requestNeededPermissions()
        }

        setContent {
            FakeLoopTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        savedTargetJumps = savedTargetJumps,
                        savedTargetTime = savedTargetTime,
                        savedRandomEnabled = savedRandomEnabled
                    )
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
        val toRequest = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent(
        modifier: Modifier = Modifier,
        savedTargetJumps: Int = 800,
        savedTargetTime: Int = 400,
        savedRandomEnabled: Boolean = true
    ) {
        var targetJumpsText by remember { mutableStateOf(savedTargetJumps.toString()) }
        var targetTimeText by remember { mutableStateOf(savedTargetTime.toString()) }
        var targetJumpsValid by remember { mutableStateOf(savedTargetJumps) }
        var targetTimeValid by remember { mutableStateOf(savedTargetTime) }
        var randomEnabled by remember { mutableStateOf(savedRandomEnabled) }
        var isRunning by remember { mutableStateOf(false) }
        var remainingSeconds by remember { mutableStateOf(0) }
        var errorMessage by remember { mutableStateOf("") }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

            Spacer(modifier = Modifier.weight(0.5f))

            // 1. Switch row: "为跳数增加随机值"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("为跳数增加随机值")
                Switch(checked = randomEnabled, onCheckedChange = { newVal ->
                    randomEnabled = newVal
                    prefs.edit().putBoolean(KEY_RANDOM_ENABLED, newVal).apply()
                })
            }

            // 2. 目标跳数输入框
            OutlinedTextField(
                value = targetJumpsText,
                onValueChange = { s ->
                    targetJumpsText = s
                    val v = s.toIntOrNull()
                    if (v != null && v in 1..6553) {
                        targetJumpsValid = v
                        errorMessage = ""
                        prefs.edit().putInt(KEY_TARGET_JUMPS, v).apply()
                    } else {
                        errorMessage = when {
                            s.isEmpty() -> "输入不能为空"
                            v == null -> "请输入有效的整数"
                            else -> "请输入 1~6553 之间的数值"
                        }
                    }
                },
                isError = errorMessage.isNotEmpty(),
                label = { Text("请输入目标跳数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // 3. 目标时间输入框
            OutlinedTextField(
                value = targetTimeText,
                onValueChange = { s ->
                    targetTimeText = s
                    val v = s.toIntOrNull()
                    if (v != null && v in 1..6553) {
                        targetTimeValid = v
                        errorMessage = ""
                        prefs.edit().putInt(KEY_TARGET_TIME, v).apply()
                    } else {
                        errorMessage = when {
                            s.isEmpty() -> "输入不能为空"
                            v == null -> "请输入有效的整数"
                            else -> "请输入 1~6553 之间的数值"
                        }
                    }
                },
                isError = errorMessage.isNotEmpty(),
                label = { Text("请输入目标时间(秒)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = Color.Red, fontSize = 14.sp)
            }

            // 开始跳绳按钮
            Button(onClick = {
                val jumps = targetJumpsValid
                val time = targetTimeValid

                isRunning = true
                errorMessage = ""

                // 发送开始包
                blePeripheralManager.notifySubscribers(hexStringToByteArray("6F0201000072"))

                // 初始化状态
                val finalTarget = if (randomEnabled) jumps + (0..50).random() else jumps
                var currentJumps = 0
                var remaining = time
                remainingSeconds = remaining

                scope.launch {
                    // 初始随机延迟 375~425ms，错开整秒边界
                    delay((375L..425L).random())
                    val anchorTime = System.nanoTime()

                    var iteration = 0
                    while (remaining > 0) {
                        val cycleStartNanos = anchorTime + iteration * 1_000_000_000L

                        // 在当前秒内的 375~425ms 窗口随机发包
                        val sendOffsetNanos = (375L..425L).random() * 1_000_000L
                        val sendPointNanos = cycleStartNanos + sendOffsetNanos

                        var now = System.nanoTime()
                        var waitNanos = sendPointNanos - now
                        if (waitNanos > 0) delay(waitNanos / 1_000_000)

                        // 计算增量并发送数据包
                        val remainingJumps = finalTarget - currentJumps
                        val baseInc = if (remaining > 0) remainingJumps / remaining else 0
                        val randInc = (0..2).random()
                        var actualInc = baseInc + randInc
                        if (actualInc > remainingJumps) actualInc = remainingJumps

                        currentJumps += actualInc
                        val hexJumps = (currentJumps * 10).toString(16).padStart(4, '0').uppercase()
                        val payload = "6F040B000000${hexJumps}6930005703D802A8"
                        blePeripheralManager.notifySubscribers(hexStringToByteArray(payload))

                        remaining--
                        remainingSeconds = remaining

                        // 等待到当前周期结束，补偿到恰好 1000ms
                        val cycleEndNanos = cycleStartNanos + 1_000_000_000L
                        now = System.nanoTime()
                        waitNanos = cycleEndNanos - now
                        if (waitNanos > 0) delay(waitNanos / 1_000_000)

                        iteration++
                    }

                    // 结束
                    isRunning = false
                    prefs.edit().putInt(KEY_LAST_USED_TARGET_JUMPS, jumps).apply()
                }
            }, enabled = !isRunning && errorMessage.isEmpty()) {
                Text(if (isRunning) "剩余 ${remainingSeconds} 秒" else "开始跳绳")
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun setBluetoothName(name: String): Boolean {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    return false
                }
            }
            try {
                val method = BluetoothAdapter::class.java.getMethod("setName", String::class.java)
                method.invoke(adapter, name)
            } catch (ex: Exception) {
                adapter.name = name
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val cleaned = s.replace("\\s".toRegex(), "")
        val len = cleaned.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] =
                ((Character.digit(cleaned[i], 16) shl 4) + Character.digit(cleaned[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun hasAllNeededPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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
