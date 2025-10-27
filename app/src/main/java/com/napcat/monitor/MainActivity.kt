package com.napcat.monitor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
// 键盘选项可选，避免额外依赖
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.napcat.monitor.data.readApiUrlFlow
import com.napcat.monitor.data.saveApiUrl
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults

class MainActivity : ComponentActivity() {
    private val scope = MainScope()
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(color = MaterialTheme.colorScheme.background) { MainScreen() }
        }
        // 运行时通知权限请求（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel(factory = MainViewModel.Factory(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application))) {
    val context = LocalContext.current
    val storedUrl by readApiUrlFlow(context).collectAsState(initial = "")
    var url by remember(storedUrl) { mutableStateOf(storedUrl) }
    val status by vm.status.collectAsState()
    val monitors by vm.monitorsFlow.collectAsState(initial = emptyList())
    var showSheet by remember { mutableStateOf(false) }
    var api by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("60") }
    var enabled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.updateStatusFromStore() }

    Scaffold(
        topBar = { TopAppBar(title = { Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.weight(1f)); Text("监控列表"); Spacer(modifier = Modifier.weight(1f)) } }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }) { Icon(Icons.Default.Add, contentDescription = "添加") }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(monitors, key = { it.id }) { item ->
                MonitorCard(item = item, onClick = {
                    val intent = android.content.Intent(context, DetailActivity::class.java)
                    intent.putExtra("id", item.id)
                    context.startActivity(intent)
                })
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "添加监控")
                OutlinedTextField(value = api, onValueChange = { api = it }, label = { Text("API 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = interval, onValueChange = { interval = it.filter { ch -> ch.isDigit() }.ifBlank { "0" } }, label = { Text("查询间隔(秒)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("是否开启")
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors())
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        showSheet = false
                    }, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) { Text("取消") }
                    Button(onClick = {
                        val apiTrim = api.trim()
                        val tokenTrim = token.trim()
                        val sec = interval.toIntOrNull() ?: 0
                        if (apiTrim.isEmpty() || tokenTrim.isEmpty() || sec <= 0) {
                            Toast.makeText(context, "请填写有效的地址/Token/间隔(>0)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        vm.addOrUpdateMonitor(null, apiTrim, tokenTrim, sec, enabled)
                        scope.launch { saveApiUrl(context, apiTrim) }
                        showSheet = false
                    }, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) { Text("保存") }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MonitorCard(item: com.napcat.monitor.data.MonitorItem, onClick: () -> Unit) {
    val uin = item.lastUin ?: "1946275451"
    val avatarUrl = "http://q.qlogo.cn/headimg_dl?dst_uin=$uin&spec=640&img_type=jpg"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "avatar",
                modifier = Modifier.size(56.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "QQ: $uin", style = MaterialTheme.typography.titleMedium)
                // 可选显示 API，便于识别
                Text(text = item.apiUrl, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            val statusText = if (item.lastStatus.equals("Online", true)) "online" else "offline"
            Text(text = statusText, style = MaterialTheme.typography.titleMedium)
        }
    }
}
