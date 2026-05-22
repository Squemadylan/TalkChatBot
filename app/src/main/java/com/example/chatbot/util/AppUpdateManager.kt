package com.example.chatbot.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.chatbot.App
import com.example.chatbot.BuildConfig
import com.example.chatbot.R
import com.example.chatbot.data.model.UpdateManifest
import com.example.chatbot.ui.common.UpdateDialogFragment
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val DIALOG_TAG = UpdateDialogFragment.TAG
    private const val PREFS_KEY_LAST_OPTIONAL_CHECK_MS = "last_optional_update_check_ms"
    private const val OPTIONAL_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 拉取 version.json 专用，失败时尽快走内置兜底 */
    private val manifestHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val isDownloading = AtomicBoolean(false)
    private var resultListenerRegistered = false

    sealed class UpdateStatus {
        data object UpToDate : UpdateStatus()
        data class Optional(val manifest: UpdateManifest) : UpdateStatus()
        data class ForceRequired(val manifest: UpdateManifest) : UpdateStatus()
    }

    /** 内置夸克网盘链接；若 [manifest] 含 manualUpdateUrl 则优先使用远程配置 */
    fun resolveManualUpdateUrl(context: Context, manifest: UpdateManifest? = null): String {
        val remote = manifest?.manualUpdateUrl?.trim().orEmpty()
        if (remote.startsWith("https://")) return remote
        return context.getString(R.string.update_manual_url).trim()
    }

    fun openManualUpdatePage(
        context: Context,
        manifest: UpdateManifest? = null,
        urlOverride: String? = null
    ): Boolean {
        val url = urlOverride?.trim()?.takeIf { it.startsWith("https://") }
            ?: resolveManualUpdateUrl(context, manifest)
        if (url.isBlank()) {
            toast(context, context.getString(R.string.update_manual_open_failed, ""))
            return false
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open manual update url", e)
            copyManualUpdateUrlToClipboard(context, url)
            toast(context, context.getString(R.string.update_manual_open_failed, url))
            false
        }
    }

    private fun copyManualUpdateUrlToClipboard(context: Context, url: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("manual_update_url", url))
        }
    }

    fun localVersionCode(context: Context): Int {
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionCode
            }
        } catch (_: PackageManager.NameNotFoundException) {
            0
        }
    }

    fun evaluate(localVersionCode: Int, manifest: UpdateManifest): UpdateStatus {
        if (manifest.versionCode <= 0 || manifest.minVersionCode <= 0) {
            throw IllegalArgumentException("version.json 中 versionCode / minVersionCode 无效")
        }
        if (localVersionCode < manifest.minVersionCode) {
            return UpdateStatus.ForceRequired(manifest)
        }
        if (localVersionCode < manifest.versionCode) {
            return UpdateStatus.Optional(manifest)
        }
        return UpdateStatus.UpToDate
    }

    private fun manifestUrls(): List<String> = listOfNotNull(
        BuildConfig.UPDATE_MANIFEST_URL.trim().takeIf { it.isNotEmpty() },
        BuildConfig.UPDATE_MANIFEST_URL_MIRROR.trim().takeIf { it.isNotEmpty() }
    ).distinct()

    suspend fun fetchManifest(context: Context): Result<UpdateManifest> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in manifestUrls()) {
            val result = fetchManifestFromUrl(url)
            if (result.isSuccess) {
                Log.d(TAG, "Loaded update manifest from $url")
                return@withContext result
            }
            lastError = result.exceptionOrNull()
            Log.w(TAG, "Failed to load manifest from $url", lastError)
        }
        loadEmbeddedFallbackManifest(context)?.let { manifest ->
            Log.i(TAG, "Using embedded fallback update manifest (minVersionCode=${manifest.minVersionCode})")
            return@withContext Result.success(manifest)
        }
        Result.failure(lastError ?: IllegalStateException("无法获取更新配置"))
    }

    private fun fetchManifestFromUrl(url: String): Result<UpdateManifest> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        manifestHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            parseManifestJson(response.body?.string().orEmpty())
        }
    }

    private fun loadEmbeddedFallbackManifest(context: Context): UpdateManifest? = runCatching {
        context.assets.open("update_manifest_fallback.json").bufferedReader().use { reader ->
            parseManifestJson(reader.readText())
        }
    }.onFailure { e ->
        Log.w(TAG, "Embedded fallback manifest unavailable", e)
    }.getOrNull()

    private fun parseManifestJson(body: String): UpdateManifest {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) error("更新清单为空")
        val manifest = gson.fromJson(trimmed, UpdateManifest::class.java)
        validateManifest(manifest)
        return manifest
    }

    private fun validateManifest(manifest: UpdateManifest) {
        if (manifest.versionCode <= 0 || manifest.minVersionCode <= 0) {
            error("versionCode / minVersionCode 必须大于 0")
        }
        if (manifest.minVersionCode > manifest.versionCode) {
            error("minVersionCode 不能大于 versionCode")
        }
        if (manifest.apkUrl.isBlank()) {
            error("apkUrl 不能为空")
        }
        if (!manifest.apkUrl.startsWith("https://")) {
            error("apkUrl 必须使用 HTTPS")
        }
    }

    /** 启动时检查：强制更新立即提示；可选更新 24 小时内只查一次 */
    fun runStartupCheck(activity: AppCompatActivity) {
        if (activity.supportFragmentManager.findFragmentByTag(DIALOG_TAG) != null) return
        activity.lifecycleScope.launch {
            val localCode = localVersionCode(activity)
            val manifestResult = fetchManifest(activity)
            manifestResult.onFailure { e ->
                Log.w(TAG, "Startup update check failed", e)
                return@launch
            }
            val manifest = manifestResult.getOrThrow()
            val status = evaluate(localCode, manifest)
            when (status) {
                is UpdateStatus.ForceRequired -> {
                    Log.i(TAG, "Force update required: local=$localCode min=${status.manifest.minVersionCode}")
                    showUpdateDialog(activity, status.manifest, force = true)
                }
                is UpdateStatus.Optional -> {
                    if (shouldRunOptionalCheck(activity)) {
                        markOptionalCheckDone(activity)
                        showUpdateDialog(activity, status.manifest, force = false)
                    }
                }
                is UpdateStatus.UpToDate -> Unit
            }
        }
    }

    /** 设置页手动检查：始终请求网络并给出明确结果 */
    fun runManualCheck(activity: FragmentActivity) {
        if (activity.supportFragmentManager.findFragmentByTag(DIALOG_TAG) != null) {
            toast(activity, activity.getString(R.string.update_check_in_progress))
            return
        }
        activity.lifecycleScope.launch {
            toast(activity, activity.getString(R.string.update_checking))
            val localCode = localVersionCode(activity)
            val manifestResult = fetchManifest(activity)
            manifestResult.onFailure { e ->
                Log.w(TAG, "Manual update check failed", e)
                toast(activity, activity.getString(R.string.update_check_failed, e.message ?: "未知错误"))
                return@launch
            }
            val manifest = manifestResult.getOrThrow()
            when (evaluate(localCode, manifest)) {
                is UpdateStatus.UpToDate -> toast(activity, activity.getString(R.string.update_already_latest))
                is UpdateStatus.Optional -> showUpdateDialog(activity, manifest, force = false)
                is UpdateStatus.ForceRequired -> showUpdateDialog(activity, manifest, force = true)
            }
        }
    }

    private fun shouldRunOptionalCheck(context: Context): Boolean {
        val last = context.applicationContext
            .getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREFS_KEY_LAST_OPTIONAL_CHECK_MS, 0L)
        return System.currentTimeMillis() - last >= OPTIONAL_CHECK_INTERVAL_MS
    }

    private fun markOptionalCheckDone(context: Context) {
        context.applicationContext
            .getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREFS_KEY_LAST_OPTIONAL_CHECK_MS, System.currentTimeMillis())
            .apply()
    }

    private fun showUpdateDialog(
        activity: FragmentActivity,
        manifest: UpdateManifest,
        force: Boolean
    ) {
        if (activity.supportFragmentManager.findFragmentByTag(DIALOG_TAG) != null) return

        val title = if (force) {
            activity.getString(R.string.update_force_title)
        } else {
            activity.getString(R.string.update_optional_title, manifest.versionName.ifBlank { manifest.versionCode.toString() })
        }
        val changelog = manifest.changelog.trim().ifBlank { activity.getString(R.string.update_no_changelog) }
        val message = buildString {
            append(changelog)
            if (!force) {
                append("\n\n")
                append(activity.getString(R.string.update_optional_hint))
            } else {
                append("\n\n")
                append(activity.getString(R.string.update_force_hint))
                append("\n")
                append(activity.getString(R.string.update_force_manual_hint))
            }
        }

        ensureResultListener(activity)

        val negativeText = if (force) {
            activity.getString(R.string.update_action_manual_pan)
        } else {
            activity.getString(R.string.update_action_later)
        }

        UpdateDialogFragment.newInstance(
            title = title,
            message = message,
            forceUpdate = force,
            positiveText = activity.getString(R.string.update_action_download),
            negativeText = negativeText,
            manifestJson = gson.toJson(manifest),
            manualUpdateUrl = resolveManualUpdateUrl(activity, manifest)
        ).showNow(activity.supportFragmentManager, DIALOG_TAG)
    }

    private fun ensureResultListener(activity: FragmentActivity) {
        if (resultListenerRegistered) return
        resultListenerRegistered = true
        activity.supportFragmentManager.setFragmentResultListener(
            UpdateDialogFragment.REQUEST_KEY,
            activity
        ) { _, bundle ->
            if (bundle.getString(UpdateDialogFragment.RESULT_ACTION) != UpdateDialogFragment.ACTION_UPDATE) {
                return@setFragmentResultListener
            }
            val json = bundle.getString(UpdateDialogFragment.ARG_MANIFEST_JSON).orEmpty()
            if (json.isBlank()) return@setFragmentResultListener
            val manifest = runCatching {
                gson.fromJson(json, UpdateManifest::class.java)
            }.getOrNull() ?: return@setFragmentResultListener
            activity.lifecycleScope.launch {
                downloadAndInstall(activity, manifest)
            }
        }
    }

    private suspend fun downloadAndInstall(activity: FragmentActivity, manifest: UpdateManifest) {
        if (!isDownloading.compareAndSet(false, true)) {
            toast(activity, activity.getString(R.string.update_download_in_progress))
            return
        }
        try {
            if (!ensureInstallPermission(activity)) {
                return
            }
            toast(activity, activity.getString(R.string.update_downloading))
            val apkFile = withContext(Dispatchers.IO) {
                downloadApk(activity.applicationContext, manifest)
            }
            withContext(Dispatchers.Main) {
                launchPackageInstaller(activity, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download or install failed", e)
            toast(activity, activity.getString(R.string.update_download_failed, e.message ?: "未知错误"))
        } finally {
            isDownloading.set(false)
        }
    }

    private suspend fun downloadApk(context: Context, manifest: UpdateManifest): File {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val safeName = "独白匣_${manifest.versionName.ifBlank { "v${manifest.versionCode}" }}.apk"
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
        val outFile = File(dir, safeName)
        if (outFile.exists()) outFile.delete()

        val request = Request.Builder().url(manifest.apkUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载失败 HTTP ${response.code}")
            val body = response.body ?: error("下载内容为空")
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
        }

        val expectedHash = manifest.sha256?.trim()?.lowercase().orEmpty()
        if (expectedHash.isNotEmpty()) {
            val actual = sha256Hex(outFile)
            if (actual != expectedHash) {
                outFile.delete()
                error("安装包校验失败，请检查 version.json 中的 sha256")
            }
        }
        return outFile
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureInstallPermission(activity: FragmentActivity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (activity.packageManager.canRequestPackageInstalls()) return true
        toast(activity, activity.getString(R.string.update_install_permission_required))
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivity(intent)
        return false
    }

    private fun launchPackageInstaller(activity: FragmentActivity, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
