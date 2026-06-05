package com.example.chatbot.ui.memory

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.Spanned
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.chatbot.R
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.App
import com.example.chatbot.databinding.ActivityMemoryViewerBinding
import com.example.chatbot.memory.AtomJsonl
import com.example.chatbot.memory.MemoryPaths
import com.example.chatbot.memory.ScenarioMarkdown
import com.example.chatbot.memory.layers.L2ScenarioClusterer
import com.example.chatbot.memory.layers.L3PersonaUpdater
import com.example.chatbot.memory.layers.ShortTermCanvas
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 只读 4 层记忆查看页。
 *
 *  - 顶部：全局 L3 persona
 *  - 角色列表：每行可展开，展示该角色 L3 persona / L2 场景 / L1 原子计数 / Mermaid 画布
 *  - 底部：可一键导出 memory/ 目录为 zip
 */
class MemoryViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryViewerBinding
    private val markwon by lazy { Markwon.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        loadContent()
        binding.btnExport.setOnClickListener {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { exportMemoryZip() }
                Toast.makeText(this@MemoryViewerActivity,
                    if (ok) "已导出到下载目录" else "导出失败",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadContent() {
        val app = applicationContext as App
        if (!app.isDatabaseInitialized()) {
            Toast.makeText(this, "数据库初始化中，请稍后", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val root = withContext(Dispatchers.IO) {
                val personaGlobal = MemoryPaths.globalPersona(this@MemoryViewerActivity)
                    .takeIf { it.exists() }?.readText().orEmpty()
                val charRepo = CharacterRepository(app.database.characterDao())
                val chars = runCatching { charRepo.allCharactersOnce() }.getOrNull()
                Pair(personaGlobal, chars)
            }
            render(root.first, root.second)
        }
    }

    private fun render(global: String, chars: List<com.example.chatbot.data.model.Character>?) {
        binding.container.removeAllViews()
        binding.container.addView(header("全局画像"))
        if (global.isBlank()) {
            binding.container.addView(muted("(尚未生成全局画像 — 对话多了会自动生成)"))
        } else {
            val tv = TextView(this).apply { setTextColor(0xFFE5E5E5.toInt()); textSize = 14f }
            markwon.setMarkdown(tv, global)
            binding.container.addView(tv)
        }
        binding.container.addView(divider())
        binding.container.addView(header("角色记忆"))
        val list = chars ?: emptyList()
        if (list.isEmpty()) {
            binding.container.addView(muted("(暂无角色)"))
            return
        }
        list forEach { c ->
            binding.container.addView(divider())
            binding.container.addView(header("「${c.name}」(${c.id})"))
            if (!c.enableLongTermMemory) {
                binding.container.addView(muted("(该角色未开启永久记忆)"))
                return@forEach
            }
            
            val persona = L3PersonaUpdater.loadForCharacter(this, c.id).content
            binding.container.addView(header("角色画像", level = 2))
            binding.container.addView(plainOrEmpty(persona))

            val scens = L2ScenarioClusterer.loadAllScenarios(this, c.id)
            binding.container.addView(header("场景记忆 (${scens.size})", level = 2))
            if (scens.isEmpty()) binding.container.addView(muted("(暂无场景)"))
            scens.forEach { s ->
                val tv = TextView(this).apply { setTextColor(0xFFE5E5E5.toInt()); textSize = 13f }
                markwon.setMarkdown(tv, "# ${s.title}\n\n${s.summary}")
                binding.container.addView(tv)
                binding.container.addView(smallMuted("文件：${MemoryPaths.scenarioFile(this, c.id, s.id).absolutePath}"))
            }

            val atoms = AtomJsonl.readAll(MemoryPaths.atomsJsonl(this, c.id))
            binding.container.addView(header("关键信息 (${atoms.size})", level = 2))
            atoms.take(50).forEach { a ->
                val tv = TextView(this).apply { setTextColor(0xFFCCCCCC.toInt()); textSize = 12f }
                tv.text = "• [${a.type.keyword}/${a.subject.keyword}] ${a.text}"
                binding.container.addView(tv)
            }
            if (atoms.size > 50) binding.container.addView(muted("… (只展示前 50 条)"))

            val canvas = ShortTermCanvas.loadCanvasText(this, c.id)
            binding.container.addView(header("对话摘要", level = 2))
            val ctv = TextView(this).apply { setTextColor(0xFFB5E853.toInt()); textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE }
            if (canvas.isBlank()) {
                binding.container.addView(muted("(暂无摘要)"))
            } else {
                ctv.text = canvas.take(4000)
                binding.container.addView(ctv)
            }
        }
    }

    private fun exportMemoryZip(): Boolean {
        return try {
            val src = MemoryPaths.root(this)
            if (!src.exists()) return false
            val zipFile = File(cacheDir, "memory_export_${System.currentTimeMillis()}.zip")
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                src.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.absolutePath.removePrefix(src.absolutePath).removePrefix("/")
                    zos.putNextEntry(java.util.zip.ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            val authority = "$packageName.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@MemoryViewerActivity, authority, zipFile)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
            }
            startActivity(android.content.Intent.createChooser(intent, "导出记忆 zip"))
            true
        } catch (e: Exception) {
            android.util.Log.e("MemoryViewer", "export failed", e)
            false
        }
    }

    private fun header(text: String, level: Int = 1): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFFFFD180.toInt())
        tv.textSize = if (level == 1) 18f else 15f
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setPadding(0, 24, 0, 12)
        return tv
    }
    private fun muted(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(0xFF888888.toInt()); textSize = 13f
    }
    private fun smallMuted(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(0xFF777777.toInt()); textSize = 11f
    }
    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = 24; bottomMargin = 16
        }
        setBackgroundColor(0xFF333333.toInt())
    }
    private fun plainOrEmpty(s: String): View = if (s.isBlank()) muted("(尚未生成)") else {
        val tv = TextView(this).apply { setTextColor(0xFFE5E5E5.toInt()); textSize = 14f }
        markwon.setMarkdown(tv, s)
        tv
    }
}
