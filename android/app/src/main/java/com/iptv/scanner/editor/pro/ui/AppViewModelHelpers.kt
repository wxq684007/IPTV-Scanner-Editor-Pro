package com.iptv.scanner.editor.pro.ui

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.scanner.editor.pro.data.IptvRepository
import com.iptv.scanner.editor.pro.data.MappingEntry
import com.iptv.scanner.editor.pro.data.ScanResult
import com.iptv.scanner.editor.pro.data.ScanStatus
import com.iptv.scanner.editor.pro.data.UserPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 扫描辅助类：从 AppViewModel 中提取的 URL 范围扫描逻辑。
 *
 * 职责：
 * - 启动/停止扫描
 * - 轮询扫描状态
 * - 管理扫描结果
 */
class ScanHelper(
    private val repository: IptvRepository,
    private val viewModelScope: androidx.lifecycle.CoroutineScope,
    private val userPrefs: UserPrefs
) {
    companion object {
        private const val TAG = "ScanHelper"
    }

    private val _scanStatus = MutableStateFlow<ScanStatus?>(null)
    val scanStatus: StateFlow<ScanStatus?> = _scanStatus.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _scanLoading = MutableStateFlow(false)
    val scanLoading: StateFlow<Boolean> = _scanLoading.asStateFlow()

    private val _scanError = MutableStateFlow("")
    val scanError: StateFlow<String> = _scanError.asStateFlow()

    private var scanPollJob: Job? = null

    fun startScan(
        baseUrl: String,
        timeout: Int = 10,
        threads: Int = 4,
        engine: String = "requests",
        retry: Boolean = false,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            _scanLoading.value = true
            _scanError.value = ""
            val result = repository.startScan(baseUrl, timeout, threads, engine, retry, append)
            result.fold(
                onSuccess = { started ->
                    if (started) {
                        startScanPolling()
                    } else {
                        _scanError.value = "扫描已在运行"
                        _scanLoading.value = false
                    }
                },
                onFailure = { e ->
                    _scanError.value = e.message ?: "启动扫描失败"
                    _scanLoading.value = false
                }
            )
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            repository.stopScan()
            scanPollJob?.cancel()
            _scanLoading.value = false
        }
    }

    fun refreshScanStatus() {
        viewModelScope.launch {
            repository.getScanStatus().fold(
                onSuccess = { status -> _scanStatus.value = status },
                onFailure = { /* 静默 */ }
            )
            repository.getScanResults().fold(
                onSuccess = { results -> _scanResults.value = results },
                onFailure = { /* 静默 */ }
            )
        }
    }

    private fun startScanPolling() {
        scanPollJob?.cancel()
        scanPollJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val statusResult = repository.getScanStatus()
                statusResult.fold(
                    onSuccess = { status ->
                        _scanStatus.value = status
                        if (!status.running) {
                            _scanLoading.value = false
                            repository.getScanResults().fold(
                                onSuccess = { results -> _scanResults.value = results },
                                onFailure = { /* 静默 */ }
                            )
                            return@launch
                        }
                    },
                    onFailure = { /* 静默 */ }
                )
                repository.getScanResults().fold(
                    onSuccess = { results -> _scanResults.value = results },
                    onFailure = { /* 静默 */ }
                )
            }
        }
    }
}

/**
 * 映射辅助类：从 AppViewModel 中提取的频道映射逻辑。
 */
class MappingHelper(
    private val repository: IptvRepository,
    private val viewModelScope: androidx.lifecycle.CoroutineScope
) {
    companion object {
        private const val TAG = "MappingHelper"
    }

    private val _mappingList = MutableStateFlow<List<MappingEntry>>(emptyList())
    val mappingList: StateFlow<List<MappingEntry>> = _mappingList.asStateFlow()

    private val _mappingLoading = MutableStateFlow(false)
    val mappingLoading: StateFlow<Boolean> = _mappingLoading.asStateFlow()

    private val _mappingStatusText = MutableStateFlow("")
    val mappingStatusText: StateFlow<String> = _mappingStatusText.asStateFlow()

    fun loadMappings() {
        viewModelScope.launch {
            _mappingLoading.value = true
            repository.getMappings().fold(
                onSuccess = { list -> _mappingList.value = list },
                onFailure = { e -> _mappingStatusText.value = "加载失败: ${e.message}" }
            )
            _mappingLoading.value = false
        }
    }

    fun addMapping(rawName: String, standardName: String, logoUrl: String = "", groupName: String = "") {
        viewModelScope.launch {
            repository.addMapping(rawName, standardName, logoUrl, groupName).fold(
                onSuccess = {
                    _mappingStatusText.value = "添加成功"
                    loadMappings()
                },
                onFailure = { e -> _mappingStatusText.value = "添加失败: ${e.message}" }
            )
        }
    }

    fun deleteMapping(standardName: String, rawName: String = "") {
        viewModelScope.launch {
            repository.deleteMapping(standardName, rawName).fold(
                onSuccess = {
                    _mappingStatusText.value = "删除成功"
                    loadMappings()
                },
                onFailure = { e -> _mappingStatusText.value = "删除失败: ${e.message}" }
            )
        }
    }

    fun refreshMappings() {
        viewModelScope.launch {
            _mappingLoading.value = true
            _mappingStatusText.value = "正在刷新..."
            repository.refreshMappings().fold(
                onSuccess = {
                    _mappingStatusText.value = "刷新成功"
                    loadMappings()
                },
                onFailure = { e -> _mappingStatusText.value = "刷新失败: ${e.message}" }
            )
            _mappingLoading.value = false
        }
    }
}

/**
 * 版本更新辅助类：从 AppViewModel 中提取的版本检查和 APK 下载逻辑。
 */
class UpdateHelper(
    private val application: Application,
    private val repository: IptvRepository,
    private val viewModelScope: androidx.lifecycle.CoroutineScope
) {
    companion object {
        private const val TAG = "UpdateHelper"
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class UpdateAvailable(val latestVersion: String, val downloadUrl: String, val releaseUrl: String) : UpdateState()
        object UpToDate : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    sealed class ApkDownloadState {
        object Idle : ApkDownloadState()
        data class Downloading(val progress: Int) : ApkDownloadState()
        object Completed : ApkDownloadState()
        data class Error(val message: String) : ApkDownloadState()
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _apkDownloadState = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    val apkDownloadState: StateFlow<ApkDownloadState> = _apkDownloadState.asStateFlow()

    @Volatile
    private var apkDownloadId: Long = -1L

    @Volatile
    private var apkDownloadReceiver: BroadcastReceiver? = null

    @Volatile
    private var apkProgressJob: Job? = null

    fun checkForUpdates(currentVersion: String, auto: Boolean = false) {
        viewModelScope.launch {
            if (_updateState.value is UpdateState.Checking) return@launch
            _updateState.value = UpdateState.Checking
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.checkForUpdates(currentVersion)
                }
                result.fold(
                    onSuccess = { info ->
                        if (info.hasUpdate) {
                            _updateState.value = UpdateState.UpdateAvailable(
                                info.latestVersion, info.downloadUrl, info.releaseUrl
                            )
                            if (!auto) Log.i(TAG, "Update available: ${info.latestVersion}")
                        } else {
                            _updateState.value = UpdateState.UpToDate
                        }
                    },
                    onFailure = { e ->
                        _updateState.value = UpdateState.Error(e.message ?: "检查失败")
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "检查失败")
            }
        }
    }

    fun downloadApk(downloadUrl: String) {
        if (_apkDownloadState.value is ApkDownloadState.Downloading) return
        _apkDownloadState.value = ApkDownloadState.Downloading(0)

        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("ISEP 更新")
                setDescription("下载最新版本")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(application, Environment.DIRECTORY_DOWNLOADS, "ISEP-update.apk")
            }
            val dm = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            apkDownloadId = dm.enqueue(request)

            apkDownloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == apkDownloadId) {
                            _apkDownloadState.value = ApkDownloadState.Completed
                            apkProgressJob?.cancel()
                            installApk()
                        }
                    }
                }
            }
            application.registerReceiver(apkDownloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

            apkProgressJob = viewModelScope.launch {
                while (isActive) {
                    delay(500)
                    val query = DownloadManager.Query().setFilterById(apkDownloadId)
                    val cursor = dm.query(query)
                    if (cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                            _apkDownloadState.value = ApkDownloadState.Downloading(progress)
                        }
                    }
                    cursor.close()
                }
            }
        } catch (e: Exception) {
            _apkDownloadState.value = ApkDownloadState.Error(e.message ?: "下载失败")
        }
    }

    private fun installApk() {
        try {
            val apkFile = File(application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ISEP-update.apk")
            if (!apkFile.exists()) {
                _apkDownloadState.value = ApkDownloadState.Error("APK 文件不存在")
                return
            }
            val uri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            application.startActivity(intent)
        } catch (e: Exception) {
            _apkDownloadState.value = ApkDownloadState.Error(e.message ?: "安装失败")
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun cleanup() {
        apkProgressJob?.cancel()
        try {
            apkDownloadReceiver?.let { application.unregisterReceiver(it) }
        } catch (_: Exception) {}
        apkDownloadReceiver = null
    }
}