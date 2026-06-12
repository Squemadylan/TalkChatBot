# 豆包 TTS（火山引擎）Android 适配经验

> 记录时间：2026-05-28  
> 项目：Dubaixia / TalkChatBot  
> 实现文件：`app/src/main/java/com/example/chatbot/util/VolcTtsHelper.kt`  
> 官方文档：<https://www.volcengine.com/docs/6561/79820>（小模型 HTTP 非流式）  
> **Cursor 技能**：`.cursor/skills/android-doubao-tts/SKILL.md`（Agent 可调用的精简版 + `reference.md` 全文）

---

## 1. 方案选型

| 方案 | 结论 | 原因 |
|------|------|------|
| 系统 TTS | 作兜底 | 各品牌 ROM 差异大，红米/红魔等体验差 |
| 讯飞 XTTS 离线 SDK | 放弃 | 设备授权/架构限制（18708）、资源路径复杂 |
| Azure TTS | 放弃 | 需 Visa 信用卡 |
| **豆包 HTTP API** | **采用** | 免费额度、音质好、纯 HTTP 无 SDK 依赖 |

**推荐架构**：豆包 TTS（主）→ 系统 TTS（兜底），由 `VoiceHelper.kt` 统一调度。

---

## 2. 接入要点（速查）

### 2.1 认证

```http
Authorization: Bearer;{access_token}
X-Appid: {app_id}
Content-Type: application/json
```

- **Bearer 与 token 之间是分号 `;`，不是空格**
- 错误写法：`Bearer {token}` → 401，`code: 3001`，`invalid auth token`
- 正确写法：`Bearer;{token}`

控制台获取：`appid`、`access_token`（及 `secret_key`，HTTP Bearer 场景通常只用前两者）。

### 2.2 接口

| 项 | 值 |
|----|-----|
| URL | `https://openspeech.bytedance.com/api/v1/tts` |
| 方法 | POST |
| 非流式 operation | **`query`**（HTTP 只能用 query） |
| 流式 operation | `submit`（WebSocket） |

### 2.3 请求体结构（最小可用）

```json
{
  "app": {
    "appid": "你的APPID",
    "token": "你的access_token",
    "cluster": "volcano_tts"
  },
  "user": {
    "uid": "uid_唯一标识"
  },
  "audio": {
    "voice_type": "BV700_streaming",
    "encoding": "mp3",
    "rate": 24000,
    "speed_ratio": 1.0,
    "volume_ratio": 1.0,
    "pitch_ratio": 1.0,
    "emotion": "neutral",
    "language": "cn"
  },
  "request": {
    "reqid": "每次唯一，建议 UUID",
    "text": "要合成的文本",
    "text_type": "plain",
    "operation": "query"
  }
}
```

### 2.4 成功响应（务必按此解析）

HTTP **200** 时，body 为 **JSON**，不是原始 PCM：

```json
{
  "reqid": "...",
  "code": 3000,
  "operation": "query",
  "message": "Success",
  "sequence": -1,
  "data": "base64编码的音频二进制",
  "addition": { "duration": "1960" }
}
```

| 字段 | 说明 |
|------|------|
| `code` | **3000 = 成功**（不是 HTTP 状态码，也不是 1000） |
| `data` | **音频在 `data` 字段**，不是 `audio` |
| `data` 内容 | Base64 解码后即为 MP3/PCM 等（由 `encoding` 决定） |

---

## 3. 踩坑记录（按排查顺序）

### 坑 1：JSON 手工拼接 + Emoji → 500 / code 3031

**现象**：`invalid character 'ð' looking for beginning of value`  
**原因**：AI 回复含大量 Emoji、特殊 Unicode，手写 `escapeJson` 无法可靠转义。  
**解决**：用 **Gson**（或 kotlinx.serialization）序列化整个请求体：

```kotlin
private val gson = GsonBuilder().disableHtmlEscaping().create()
val json = gson.toJson(requestBody)
```

`disableHtmlEscaping()` 避免中文/符号被转成 `\uXXXX` 影响可读性（按需）。

---

### 坑 2：Authorization 格式错误 → 401 / code 3001

**现象**：`invalid auth token`  
**原因**：写成 `Bearer {token}`（空格）。  
**解决**：`Bearer;{token}`（分号，无空格）。

---

### 坑 3：文本超长 → 400 / code 3010

**现象**：`exceed max len limit!`  
**原因**：单次请求文本过长（聊天长回复 + 装饰符号）。  
**解决**：

- 客户端截断（本项目约 **480 字符**）
- 或分段多次合成 + 顺序播放（未实现，可扩展）

---

### 坑 4：误把响应当 PCM 直接喂 AudioTrack → 无声音

**现象**：HTTP 200、控制台扣费，但无播放。  
**原因**：

1. 响应是 JSON，内含 Base64，不是裸 PCM
2. 曾误读 `audio` 字段（实际为 **`data`**）
3. 曾把整段 JSON 当 PCM 写入 `AudioTrack`

**解决**：

```kotlin
val jsonObj = gson.fromJson(responseStr, Map::class.java)
if ((jsonObj["code"] as? Number)?.toInt() == 3000) {
    val b64 = jsonObj["data"] as? String ?: return null
    val bytes = Base64.decode(b64, Base64.NO_WRAP)
    // encoding=mp3 → 用 MediaPlayer 播临时文件
}
```

---

### 坑 5：operation 用错 → 无音频或行为异常

| operation | 适用 |
|-----------|------|
| `query` | **HTTP 非流式**，一次返回完整 JSON + data |
| `submit` | 流式 / WebSocket，HTTP 单独 submit 易得到中间态 |

文档明确：**HTTP 只能 query**。  
曾改为 `submit` 导致返回 `code:3000` 但无 `data` 或需二次查询的困惑。

---

### 坑 6：illegal input text → 400 / code 3011

**现象**：`illegal input text!`  
**原因**：文本含控制字符、异常符号，或 `language` 与内容不匹配。  
**解决**：

- `language` 用 **`cn`**（文档示例），勿随意用 `cmn`
- 过滤控制字符：`text.replace(Regex("[\\x00-\\x1F\\x7F]"), "")`
- `encoding` 与播放方式一致：**mp3 + MediaPlayer**，**pcm + AudioTrack**

---

### 坑 7：encoding 与播放器不匹配

| encoding | 播放方式 |
|----------|----------|
| `mp3` | `MediaPlayer` + 临时文件（推荐，简单稳定） |
| `pcm` | `AudioTrack`（需 24kHz、单声道、16bit 等参数一致） |

本项目最终：**请求 mp3 → Base64 解码 → 写 `cacheDir/tts_temp.mp3` → MediaPlayer**。

---

### 坑 8：讯飞 SDK 依赖残留

删除讯飞后需同步清理：

- `FlyTtsHelper.kt`
- `app/libs/AIKit.aar` 及 `build.gradle` 中 `files('libs/AIKit.aar')`
- `VoiceHelper` 中的初始化与 speak 分支

---

## 4. 推荐实现骨架（Kotlin）

```kotlin
// 1. 合成（子线程）
private fun synthesize(text: String): ByteArray? {
    val conn = URL(TTS_URL).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Authorization", "Bearer;$ACCESS_TOKEN")
    conn.setRequestProperty("Content-Type", "application/json")
    // ... 构建 requestBody，gson.toJson，写入 body
    if (conn.responseCode != 200) {
        Log.e(TAG, conn.errorStream?.readBytes()?.toString(Charsets.UTF_8))
        return null
    }
    val body = conn.inputStream.readBytes().toString(Charsets.UTF_8)
    val json = gson.fromJson(body, Map::class.java)
    if ((json["code"] as? Number)?.toInt() != 3000) return null
    val data = json["data"] as? String ?: return null
    return Base64.decode(data, Base64.NO_WRAP)
}

// 2. 播放（主线程 Handler + MediaPlayer）
fun speak(text: String, onDone: (() -> Unit)?) {
    Thread {
        val bytes = synthesize(text.take(480)) ?: run { onDone?.invoke(); return@Thread }
        val file = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
        file.writeBytes(bytes)
        mainHandler.post {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { file.delete(); onDone?.invoke() }
                prepare(); start()
            }
        }
    }.start()
}
```

---

## 5. 与 VoiceHelper 集成建议

```text
init() 顺序：
  1. VolcTtsHelper.init(context) → 成功则 volcTtsReady = true
  2. 失败则 initSystemTts()

speak(text)：
  if (volcTtsReady) VolcTtsHelper.speak(text, onDone)
  else systemTts.speak(...)

stop() / shutdown()：
  同时停止 VolcTtsHelper 与 System TTS
```

**注意**：长文本只朗读前 N 字时，可在 `VoiceHelper` 或 `VolcTtsHelper` 统一截断，并可选提示用户「仅朗读摘要」。

---

## 6. 音色与配额

**本项目已授权音色（2026-06-08，22 个）**：详见 `.cursor/skills/android-doubao-tts/voices.md`。

| 要点 | 说明 |
|------|------|
| API 填 | `voice_type` = `BVxxx_streaming`（如 `BV700_streaming`），勿填实例 ID |
| 默认 | 灿灿 `BV700_streaming` |
| 免费 | `BV001_streaming`、`BV002_streaming` |

- 控制台：https://console.volcengine.com/speech/app
- 扣费成功但无声音 → **优先查响应 JSON 是否含 `data`**，而非怀疑网络

---

## 7. 安全与配置（生产必做）

当前项目为调试方便将 **APP ID / Token 写在代码中**，生产环境应：

1. 使用 `BuildConfig` + `local.properties`（不提交 Git）
2. 或后端代理 TTS（客户端不持有 Token，防反编译泄露）
3. Token 泄露可在火山控制台轮换

---

## 8. 调试命令

```powershell
# Windows 完整 adb 路径
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -c
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.example.chatbot
# 触发朗读后
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d -s "VolcTtsHelper:V" "VoiceHelper:V"
```

**Log 关键行**：

- `TTS response code: 200` → HTTP 成功
- `TTS response code: 3000`（body 内）→ 业务成功
- `Audio data length: xxx` → Base64 长度正常
- `MP3 playback completed` → 播放结束

---

## 9. 常见错误码速查

| code | 含义 | 处理 |
|------|------|------|
| 3000 | 合成成功 | 解析 `data` Base64 |
| 3001 | 鉴权失败 | 检查 Bearer;token、appid |
| 3010 | 文本超长 | 截断或分段 |
| 3011 | 非法文本 | 过滤控制字符、检查 language |
| 3031 | JSON 解析失败（服务端） | 检查请求 JSON 是否合法、Emoji 转义 |

---

## 10. 后续可优化项

- [ ] 长文分段合成 + 队列连续播放
- [ ] 音色/语速/音量与角色表 `Character` 字段联动（已有 `voiceSpeed` 等）
- [ ] 合成中 UI 状态（加载动画、停止按钮）
- [ ] 失败自动降级系统 TTS 并 Toast 提示
- [ ] Token 后端代理 + 缓存 mp3（相同文本复用）
- [ ] 考虑 v3 大模型音色接口（需另接文档，与 v1 字段不同）

---

## 11. 参考链接

- [小模型 HTTP 非流式接口](https://www.volcengine.com/docs/6561/79820)
- [参数基本说明](https://www.volcengine.com/docs/6561/79823)
- [鉴权方法](https://www.volcengine.com/docs/6561/1105162)
- [错误码说明](https://www.volcengine.com/docs/6561/79829)

---

*本文档根据 Dubaixia 项目 2026-05-28 实际联调记录整理，供后续 Android / 跨端项目复用。*
