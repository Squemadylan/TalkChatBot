package com.example.chatbot.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.example.chatbot.R
import com.example.chatbot.ui.MainActivity

/**
 * 推送通知构建器
 * 
 * 统一管理所有通知的构建逻辑，确保风格一致
 */
object PushNotificationBuilder {

    private const val MAX_CONTENT_LENGTH = 100  // 最大内容长度

    /**
     * 构建AI回复通知
     */
    fun buildAiReplyNotification(
        context: Context,
        characterName: String,
        replyContent: String
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 截断过长的内容
        val truncatedContent = if (replyContent.length > MAX_CONTENT_LENGTH) {
            replyContent.take(MAX_CONTENT_LENGTH) + "..."
        } else {
            replyContent
        }
        
        return NotificationCompat.Builder(context, PushManager.CHANNEL_AI_REPLY)
            .setSmallIcon(R.drawable.ic_notification) // TODO: 创建通知图标
            .setContentTitle(characterName)
            .setContentText(truncatedContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(truncatedContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
    }

    /**
     * 构建记忆保存通知
     */
    fun buildMemorySavedNotification(
        context: Context,
        success: Boolean,
        details: String = ""
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "memory")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = if (success) "永久记忆已保存" else "记忆保存失败"
        val content = if (success) {
            if (details.isNotEmpty()) "四层永久记忆已更新：$details"
            else "您的四层永久记忆已成功保存"
        } else {
            if (details.isNotEmpty()) "保存失败：$details"
            else "记忆保存失败，请稍后重试"
        }
        
        val icon = if (success) R.drawable.ic_notification else R.drawable.ic_notification
        
        return NotificationCompat.Builder(context, PushManager.CHANNEL_MEMORY)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    /**
     * 构建通用通知
     */
    fun buildGeneralNotification(
        context: Context,
        title: String,
        content: String,
        channelId: String = PushManager.CHANNEL_OTHER
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val truncatedContent = if (content.length > MAX_CONTENT_LENGTH) {
            content.take(MAX_CONTENT_LENGTH) + "..."
        } else {
            content
        }
        
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(truncatedContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(truncatedContent))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
    }

    /**
     * 构建下载完成通知
     */
    fun buildDownloadCompleteNotification(
        context: Context,
        fileName: String,
        filePath: String
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "downloads")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val content = "文件已保存至：$filePath"
        
        return NotificationCompat.Builder(context, PushManager.CHANNEL_OTHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("下载完成")
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    /**
     * 构建对话摘要通知
     */
    fun buildConversationSummaryNotification(
        context: Context,
        summary: String,
        messageCount: Int
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val content = "本次对话共 $messageCount 条消息，$summary"
        
        return NotificationCompat.Builder(context, PushManager.CHANNEL_OTHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("对话摘要")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }
}
