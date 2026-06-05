package com.example.chatbot.memory.vec

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 真·sqlite-vec（asg017 / sqliteai 系）向量索引。
 *
 * 走 [io.requery.android.database.sqlite.SQLiteDatabase] 加载 [ai.sqlite:vector] AAR 里的
 * `libvector.so`，然后用 `vector_init` + `vector_quantize` + `vector_quantize_scan` 做 KNN。
 *
 * Android 标准 SQLite（android.database.sqlite）不支持 `load_extension`，所以这里必须换
 * SQLite 库；只对本索引生效，Room 走的是 androidx SQLite，两者不冲突。
 *
 * 失败策略：扩展装不上 / 编译版本不兼容 → `isReady()=false`，由 [VecIndexFactory] 退回
 * [LinearScanIndex]。
 */
class SqliteVecIndex(
    private val context: Context,
    private val dbFile: File,
    override val dim: Int
) : VecIndex {

    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var ready: Boolean = false
    private val table = "atom_vectors"

    init {
        runCatching { open() }.onFailure {
            Log.w(TAG, "sqlite-vec init failed, fallback expected", it)
            close()
        }
    }

    private fun open() {
        if (dbFile.parentFile?.exists() != true) dbFile.parentFile?.mkdirs()
        // 1. 找 libvector.so
        val soPath = resolveLibVectorPath() ?: run {
            Log.w(TAG, "libvector.so not found in nativeLibraryDir, fallback")
            return
        }

        // 2. 配 requery SQLite，加载扩展
        val cfg = SQLiteDatabaseConfiguration(
            dbFile.absolutePath,
            SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.OPEN_READWRITE,
            null, null,
            mutableListOf(SQLiteCustomExtension(soPath, null))
        )
        val handle = try {
            SQLiteDatabase.openDatabase(cfg, null, null)
        } catch (e: SQLiteException) {
            Log.w(TAG, "openDatabase with vector extension failed: ${e.message}")
            return
        } catch (t: Throwable) {
            Log.w(TAG, "openDatabase with vector extension failed: ${t.message}")
            return
        }
        // 3. 建表：必须含 INTEGER PRIMARY KEY（rowid），vector 列是 BLOB。
        try {
            handle.execSQL(
                "CREATE TABLE IF NOT EXISTS $table (" +
                    "id TEXT PRIMARY KEY, " +
                    "vec BLOB NOT NULL, " +
                    "ts INTEGER NOT NULL DEFAULT 0)"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "create table failed: ${t.message}")
            try { handle.close() } catch (_: Throwable) {}
            return
        }
        // 4. 调 vector_init 把 vec 列注册为向量列，dim/type/distance 都钉死。
        //    execSQL 不支持 ? 占位 + SELECT 调函数，必须 rawQuery。
        try {
            handle.rawQuery(
                "SELECT vector_init(?, ?, ?)",
                arrayOf<Any>(table, "vec", "dimension=$dim,type=FLOAT32,distance=cosine")
            ).use { c -> c.moveToFirst() }
        } catch (t: Throwable) {
            Log.w(TAG, "vector_init failed: ${t.message}")
            try { handle.close() } catch (_: Throwable) {}
            return
        }
        db = handle
        ready = true
        Log.d(TAG, "SqliteVecIndex ready dim=$dim db=$dbFile")
    }

    /**
     * 兼容不同 ABI 下 .so 的命名。`ai.sqlite:vector` 包里 jniLibs 下是 libvector.so，
     * 运行时从 applicationInfo.nativeLibraryDir 拿到（AGP 8 默认不解压到 app_lib/）。
     */
    private fun resolveLibVectorPath(): String? {
        val dir = context.applicationInfo.nativeLibraryDir ?: return null
        val direct = File(dir, "libvector.so")
        if (direct.exists()) return direct.absolutePath
        // 退化：有的 ABI 编译时不带 lib 前缀（极少见）
        val bare = File(dir, "vector.so")
        if (bare.exists()) return bare.absolutePath
        return null
    }

    override fun isReady(): Boolean = ready

    override fun upsert(id: String, vector: FloatArray) {
        val handle = db ?: return
        require(vector.size == dim) { "dim mismatch: ${vector.size} vs $dim" }
        val bytes = floatArrayToBytes(vector)
        try {
            handle.beginTransaction()
            // 先 REPLACE 进表（保证 rowid 稳定；用 id 字符串做 PK 也行）
            handle.execSQL(
                "INSERT OR REPLACE INTO $table(id, vec, ts) VALUES(?, ?, ?)",
                arrayOf<Any>(id, bytes, System.currentTimeMillis())
            )
            // 然后调 vector_quantize 触发该行的量化/索引刷新（SELECT 函数走 rawQuery）
            handle.rawQuery("SELECT vector_quantize(?, ?)", arrayOf<Any>(table, "vec")).use { it.moveToFirst() }
            handle.setTransactionSuccessful()
        } catch (t: Throwable) {
            Log.w(TAG, "upsert($id) failed: ${t.message}")
        } finally {
            try { handle.endTransaction() } catch (_: Throwable) {}
        }
    }

    override fun search(query: FloatArray, topK: Int): List<VecIndex.Match> {
        val handle = db ?: return emptyList()
        if (topK <= 0 || query.size != dim) return emptyList()
        val bytes = floatArrayToBytes(query)
        val out = ArrayList<VecIndex.Match>(topK.coerceAtMost(32))
        val cursor = try {
            handle.rawQuery(
                "SELECT id, distance FROM vector_quantize_scan(?, ?, ?, ?)",
                arrayOf<Any>(table, "vec", bytes, topK)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "vector_quantize_scan failed: ${t.message}")
            return emptyList()
        }
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow("id")
            val distCol = c.getColumnIndexOrThrow("distance")
            while (c.moveToNext()) {
                val id = c.getString(idCol) ?: continue
                val d = c.getDouble(distCol)
                // 距离越小越相似；VecIndex.Match.score 越大越相似
                out.add(VecIndex.Match(id, 1.0f - d.toFloat()))
            }
        }
        return out
    }

    override fun close() {
        runCatching { db?.close() }
        db = null
        ready = false
    }

    private fun floatArrayToBytes(v: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in v) buf.putFloat(f)
        return buf.array()
    }

    companion object {
        private const val TAG = "SqliteVecIndex"
    }
}
