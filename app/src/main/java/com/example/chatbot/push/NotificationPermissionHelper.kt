package com.example.chatbot.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

object NotificationPermissionHelper {

    private const val TAG = "NotificationPermission"

    /**
     * 检查通知权限是否已授予
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要检查 POST_NOTIFICATIONS 权限
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // 旧版本检查通知是否启用
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * 检查是否需要请求权限
     * 如果是第一次请求或权限被拒绝但未勾选"不再询问"，返回true
     */
    fun shouldRequestPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * 获取请求权限时的权限字符串
     */
    fun getRequiredPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    }

    /**
     * 创建通知渠道（Android 8.0+必需）
     * 这个已经在PushManager中实现了，这里留着作为参考
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // AI回复通知渠道
            val aiReplyChannel = NotificationChannel(
                PushManager.CHANNEL_AI_REPLY,
                "AI回复通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI助手回复消息时推送通知"
                enableVibration(true)
                enableLights(true)
            }
            
            // 记忆保存通知渠道
            val memoryChannel = NotificationChannel(
                PushManager.CHANNEL_MEMORY,
                "记忆保存通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "永久记忆保存状态通知"
                enableVibration(true)
            }
            
            // 其他通知渠道
            val otherChannel = NotificationChannel(
                PushManager.CHANNEL_OTHER,
                "其他通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "其他应用通知"
            }
            
            notificationManager.createNotificationChannels(
                listOf(aiReplyChannel, memoryChannel, otherChannel)
            )
        }
    }

    /**
     * 打开应用通知设置页面
     * 用户拒绝权限后可以引导到这里开启
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果上面的方法失败，尝试打开应用详细信息
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // 如果都失败了，至少显示Toast说明
                android.util.Log.e(TAG, "无法打开通知设置", e2)
            }
        }
    }

    /**
     * 检查特定通知渠道是否启用
     */
    fun isNotificationChannelEnabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(channelId)
            return channel?.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }
}
