package com.napcat.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.napcat.monitor.data.readMonitorsFlow
import com.napcat.monitor.data.saveMonitors
import kotlinx.coroutines.launch

class DetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Surface(color = MaterialTheme.colorScheme.background) { DetailScreen() } }
    }
}

@Composable
private fun DetailScreen(vm: MainViewModel = viewModel(factory = MainViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))) {
    val context = LocalContext.current
    val id = (context as DetailActivity).intent.getStringExtra("id") ?: return
    val monitors by readMonitorsFlow(context).collectAsState(initial = emptyList())
    val item = monitors.firstOrNull { it.id == id } ?: return

    var api by remember { mutableStateOf(item.apiUrl) }
    var token by remember { mutableStateOf(item.token) }
    var interval by remember { mutableStateOf(item.intervalSec.toString()) }
    var uin by remember { mutableStateOf(item.uin) }
    var enabled by remember { mutableStateOf(item.enabled) }
    val scope = rememberCoroutineScope()
    var showDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "详情配置")
        OutlinedTextField(value = api, onValueChange = { api = it }, label = { Text("API 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = uin, onValueChange = { uin = it.filter { ch -> ch.isDigit() } }, label = { Text("QQ 号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = interval, onValueChange = { interval = it.filter { ch -> ch.isDigit() }.ifBlank { "0" } }, label = { Text("查询间隔(秒)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("是否开启")
            Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors())
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { (context as DetailActivity).finish() }) { Text("取消") }
            Button(onClick = {
                val sec = interval.toIntOrNull() ?: 0
                if (api.isBlank() || token.isBlank() || uin.isBlank() || sec <= 0) return@Button
                vm.addOrUpdateMonitor(id, api.trim(), token.trim(), uin.trim(), sec, enabled)
                (context as DetailActivity).finish()
            }) { Text("保存") }
            Button(onClick = { showDelete = true }) { Text("删除配置") }
        }
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("确认删除") },
                text = { Text("主人真的要删掉我吗？删除后不可恢复哦~") },
                confirmButton = {
                    Button(onClick = {
                        vm.deleteMonitor(id)
                        showDelete = false
                        (context as DetailActivity).finish()
                    }) { Text("删除") }
                },
                dismissButton = {
                    Button(onClick = { showDelete = false }) { Text("点错了") }
                }
            )
        }
    }
}


