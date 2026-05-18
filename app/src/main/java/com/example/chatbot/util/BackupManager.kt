package com.example.chatbot.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.example.chatbot.App
import com.example.chatbot.data.model.Character
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    /** 用户可见的主存储目录名（与「下载」「文档」并列时指手机存储根下 ChatBot） */
    private const val CHATBOT_FOLDER = "ChatBot"
    private const val BACKUPS_SUB = "Backups"
    private const val BACKUP_FILE_PREFIX = "characters_backup_"
    private const val BACKUP_FILE_EXTENSION = ".zip"
    private const val CHARACTERS_JSON = "characters.json"
    private const val AVATARS_DIR = "avatars"
    private const val MEMORIES_DIR = "memories"
    /** zip 内用户头像固定文件名，避免与角色 `user_avatar.jpg` 等重名冲突 */
    private const val USER_AVATAR_ZIP_NAME = "user_profile_avatar"

    /** 备份完成后的说明路径（用于 Toast） */
    data class BackupSaveInfo(
        val userVisiblePath: String,
        val legacyFile: File? = null,
        val mediaUri: Uri? = null
    )

    data class RestoreSummary(
        val characterCount: Int,
        val userProfileRestored: Boolean
    )

    data class BackupData(
        val version: Int = 2,
        val timestamp: Long = System.currentTimeMillis(),
        val characters: List<CharacterBackup>,
        /** 个人资料：显示名、人设、头像；旧备份无此字段则恢复时跳过 */
        val user: UserProfileBackup? = null
    )

    data class UserProfileBackup(
        val displayName: String? = null,
        val persona: String? = null,
        val avatarFileName: String? = null,
        val avatarBase64: String? = null
    )

    data class CharacterBackup(
        val id: Long,
        val name: String,
        val avatarFileName: String?,
        val avatarBase64: String?,
        val description: String,
        val prompt: String,
        val tags: String,
        val openingGreeting: String,
        val createdAt: Long
    )

    private fun Character.toBackup(): CharacterBackup {
        val avatarBase64 = if (avatar.isNotBlank()) {
            try {
                val file = File(avatar)
                if (file.exists()) {
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                } else null
            } catch (e: Exception) {
                null
            }
        } else null

        return CharacterBackup(
            id = id,
            name = name,
            avatarFileName = if (avatar.isNotBlank()) File(avatar).name else null,
            avatarBase64 = avatarBase64,
            description = description,
            prompt = prompt,
            tags = tags,
            openingGreeting = openingGreeting,
            createdAt = createdAt
        )
    }

    private fun CharacterBackup.toCharacter(): Character {
        return Character(
            id = 0,
            name = name,
            avatar = "",
            description = description,
            prompt = prompt,
            tags = tags,
            openingGreeting = openingGreeting,
            createdAt = createdAt
        )
    }

    /** Android 9 及以下：主存储根目录 `ChatBot/Backups`（需 WRITE 权限） */
    fun legacyPublicChatBotBackupsDir(): File {
        val root = Environment.getExternalStorageDirectory()
        val dir = File(File(root, CHATBOT_FOLDER), BACKUPS_SUB)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)

    private fun buildUserProfileBackup(context: Context): UserProfileBackup {
        val p = prefs(context)
        val displayName = p.getString(App.KEY_USER_DISPLAY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
        val persona = p.getString(App.KEY_USER_PERSONA, null) ?: ""
        val avatarPath = p.getString(App.KEY_USER_AVATAR_PATH, null)?.trim()
        var avatarInZip: String? = null
        var avatarB64: String? = null
        if (!avatarPath.isNullOrBlank()) {
            val f = File(avatarPath)
            if (f.exists() && f.length() > 0L) {
                avatarInZip = USER_AVATAR_ZIP_NAME
                try {
                    avatarB64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                } catch (_: Exception) {
                    avatarB64 = null
                }
            }
        }
        return UserProfileBackup(
            displayName = displayName,
            persona = persona,
            avatarFileName = avatarInZip,
            avatarBase64 = avatarB64
        )
    }

    private fun writeBackupZip(context: Context, characters: List<Character>, zipOut: ZipOutputStream) {
        val userBackup = buildUserProfileBackup(context)
        val backupData = BackupData(
            characters = characters.map { it.toBackup() },
            user = userBackup
        )
        val gson = Gson()
        val json = gson.toJson(backupData)
        zipOut.putNextEntry(ZipEntry(CHARACTERS_JSON))
        zipOut.write(json.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        characters.forEach { character ->
            if (character.avatar.isNotBlank()) {
                val avatarFile = File(character.avatar)
                if (avatarFile.exists()) {
                    zipOut.putNextEntry(ZipEntry("$AVATARS_DIR/${avatarFile.name}"))
                    FileInputStream(avatarFile).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
            
            if (character.enableLongTermMemory) {
                val memoryFile = File(
                    context.filesDir,
                    "long_term_memory/memory_${character.id}.md"
                )
                if (memoryFile.exists()) {
                    zipOut.putNextEntry(ZipEntry("$MEMORIES_DIR/memory_${character.id}.md"))
                    FileInputStream(memoryFile).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }

        if (userBackup.avatarFileName != null) {
            val path = prefs(context).getString(App.KEY_USER_AVATAR_PATH, null)?.trim()
            if (!path.isNullOrBlank()) {
                val avatarFile = File(path)
                if (avatarFile.exists() && avatarFile.length() > 0L) {
                    zipOut.putNextEntry(ZipEntry("$AVATARS_DIR/${userBackup.avatarFileName}"))
                    FileInputStream(avatarFile).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun applyUserProfileRestore(context: Context, user: UserProfileBackup, avatarDir: File) {
        val ed = prefs(context).edit()
        if (user.displayName.isNullOrBlank()) {
            ed.remove(App.KEY_USER_DISPLAY_NAME)
        } else {
            ed.putString(App.KEY_USER_DISPLAY_NAME, user.displayName)
        }
        ed.putString(App.KEY_USER_PERSONA, user.persona ?: "")

        var avatarPath = ""
        val fileName = user.avatarFileName?.trim()?.takeIf { it.isNotEmpty() }
        if (!fileName.isNullOrBlank()) {
            val fromZip = File(avatarDir, fileName)
            if (fromZip.exists() && fromZip.length() > 0L) {
                avatarPath = fromZip.absolutePath
            }
        }
        if (avatarPath.isBlank() && user.avatarBase64 != null) {
            try {
                val bytes = Base64.decode(user.avatarBase64, Base64.NO_WRAP)
                val outF = File(avatarDir, "user_restored_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outF).use { it.write(bytes) }
                avatarPath = outF.absolutePath
            } catch (_: Exception) {
                avatarPath = ""
            }
        }
        if (avatarPath.isNotBlank()) {
            ed.putString(App.KEY_USER_AVATAR_PATH, avatarPath)
        } else {
            ed.remove(App.KEY_USER_AVATAR_PATH)
        }
        ed.apply()
    }

    suspend fun createBackup(context: Context, characters: List<Character>): Result<BackupSaveInfo> =
        withContext(Dispatchers.IO) {
            try {
                val timestamp =
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "$BACKUP_FILE_PREFIX$timestamp$BACKUP_FILE_EXTENSION"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/$CHATBOT_FOLDER"
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val uri = resolver.insert(collection, values)
                        ?: return@withContext Result.failure(IllegalStateException("无法创建备份文件（MediaStore）"))
                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            ZipOutputStream(out.buffered()).use { zip ->
                                writeBackupZip(context, characters, zip)
                            }
                        } ?: return@withContext Result.failure(IllegalStateException("无法打开备份输出流"))

                        val done = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        resolver.update(uri, done, null, null)
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        throw e
                    }
                    val label =
                        "${Environment.DIRECTORY_DOWNLOADS}/$CHATBOT_FOLDER/$fileName（系统下载目录）"
                    Result.success(BackupSaveInfo(userVisiblePath = label, mediaUri = uri))
                } else {
                    val dir = legacyPublicChatBotBackupsDir()
                    val backupFile = File(dir, fileName)
                    ZipOutputStream(FileOutputStream(backupFile)).use { zip ->
                        writeBackupZip(context, characters, zip)
                    }
                    Result.success(
                        BackupSaveInfo(
                            userVisiblePath = backupFile.absolutePath,
                            legacyFile = backupFile
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun restoreBackup(
        context: Context,
        backupFile: File,
        characterDao: com.example.chatbot.database.CharacterDao
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            var characterCount = 0
            var userProfileRestored = false
            val avatarDir = File(context.filesDir, "restored_avatars").apply { mkdirs() }
            val memoryDir = File(context.filesDir, "long_term_memory").apply { mkdirs() }

            // 先解压 zip 内 avatars/ 和 memories/ 下全部文件
            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.startsWith("$AVATARS_DIR/")) {
                        val simpleName = File(entry.name).name
                        if (simpleName.isNotBlank()) {
                            FileOutputStream(File(avatarDir, simpleName)).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                    } else if (!entry.isDirectory && entry.name.startsWith("$MEMORIES_DIR/")) {
                        val simpleName = File(entry.name).name
                        if (simpleName.isNotBlank() && simpleName.endsWith(".md")) {
                            FileOutputStream(File(memoryDir, simpleName)).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == CHARACTERS_JSON) {
                        val json = zipIn.bufferedReader().readText()
                        val backupData = Gson().fromJson(json, BackupData::class.java)

                        for (charBackup in backupData.characters) {
                            val fileName = charBackup.avatarFileName?.trim()?.takeIf { it.isNotEmpty() }

                            var avatarPath = ""
                            if (!fileName.isNullOrBlank()) {
                                val fromZip = File(avatarDir, fileName)
                                if (fromZip.exists() && fromZip.length() > 0L) {
                                    avatarPath = fromZip.absolutePath
                                }
                            }
                            if (avatarPath.isBlank() && charBackup.avatarBase64 != null) {
                                try {
                                    val bytes = Base64.decode(charBackup.avatarBase64, Base64.NO_WRAP)
                                    val outName = fileName ?: "avatar_${System.currentTimeMillis()}.bin"
                                    val outF = File(
                                        avatarDir,
                                        "char_restored_${System.currentTimeMillis()}_$outName"
                                    )
                                    FileOutputStream(outF).use { it.write(bytes) }
                                    avatarPath = outF.absolutePath
                                } catch (_: Exception) {
                                    // 保持空路径
                                }
                            }

                            val newCharacter = charBackup.toCharacter().copy(avatar = avatarPath)
                            characterDao.insertCharacter(newCharacter)
                            characterCount++
                        }

                        backupData.user?.let { u ->
                            applyUserProfileRestore(context, u, avatarDir)
                            userProfileRestored = true
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            Result.success(RestoreSummary(characterCount, userProfileRestored))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
