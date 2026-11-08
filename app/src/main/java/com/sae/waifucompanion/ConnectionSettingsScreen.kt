package com.sae.waifucompanion

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ConnectionSettingsScreen(
    repository: SettingsRepository,
    currentWebView: WebView?,
    onReloadRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentIp by repository.serverIpFlow.collectAsState(initial = "127.0.0.1")
    val currentPort by repository.serverPortFlow.collectAsState(initial = "8000")

    var ipInput by remember { mutableStateOf(currentIp) }
    var portInput by remember { mutableStateOf(currentPort) }

    LaunchedEffect(currentIp, currentPort) {
        ipInput = currentIp
        portInput = currentPort
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Connection Configurations",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Server IP Address / Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        scope.launch {
                            repository.saveServerSettings(ipInput, portInput)
                            onReloadRequested()
                            Toast.makeText(context, "Saved & Reloading: http://${ipInput}:${portInput}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Reload WebView")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        currentWebView?.let { webView ->
                            webView.clearCache(true)
                            webView.clearHistory()
                            webView.clearFormData()
                        }
                        WebStorage.getInstance().deleteAllData()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        Toast.makeText(context, "Cache, Cookies, and History cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Cache, Cookies, & Storage")
                }
            }
        }
    }
}