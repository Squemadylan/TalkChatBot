# 独白匣

面向 Android 的大模型角色聊天应用：自建角色、流式对话、四层渐进式本地记忆、语音播报与备份。支持 OpenAI 兼容的 Chat Completions API（含 SSE 流式）。

> 仓库名仍为 [TalkChatBot](https://github.com/Squemadylan/TalkChatBot)；应用显示名与图标已更新为 **独白匣**。

---

## AI 开发工作流（Cursor 驱动）

本项目使用 Cursor Agent 全流程驱动开发，规则与模板位于 `.cursor/rules/` 和 `xnotes/`。

### 开发闭环

```
需求输入 → Cursor 生成 PRD → 架构方案 → 功能开发 → TDD 测试 → PR/CI → 发布
```

### 常用 Cursor 指令

| 阶段 | 触发指令 |
|------|---------|
| **需求** | "帮我写PRD，基于：[粘贴需求]" |
| **架构** | "基于PRD生成项目骨架" |
| **功能** | "实现 [功能名称]" |
| **测试** | "跑一下测试：[粘贴日志]" |
| **发布** | "帮我打包" |
| **迭代** | "开始迭代"、"开始更新" |

### 规则文件

```
.cursor/rules/
  011-prd-generation.mdc       # PRD 生成规则
  012-architecture-generation.mdc  # 架构生成规则
  031-test-fix-loop.mdc        # 测试修复循环规则
  041-ci-cd-rules.mdc          # CI/PR/发布规则
  050-auto-iteration.mdc        # 自动化迭代规则
  060-android-dev-standard.mdc  # Android 开发规范（Blankj 完结版）
  061-android-doubao-tts.mdc    # 豆包 TTS 适配（VolcTtsHelper / VoiceHelper）
  （基础规则 000 / 400 / android-packaging 已在安装时配置）
```

### 工作流模板

```
xnotes/
  template-prd.md              # PRD 填写模板
  template-arch.md             # 架构文档模板
  template-story.md            # Story 故事模板
  workflow-agile.md             # Agile 工作流规范
```

### 开发文档

```
.ai/
  prd/                         # PRD 需求文档目录
  story-*.story.md             # 故事卡（每个功能一个文件）
.cursor/skills/
  android-dev-standard/        # Android 开发规范技能（Blankj 完结版）
  android-doubao-tts/          # 豆包 TTS 技能（仓库副本；全局主副本 ~/.cursor/skills/android-doubao-tts/）
```

### 自动化脚本

实现位于 `scripts/automation/`；根目录 `Auto-*.ps1` 为快捷入口（兼容旧用法）。

```
scripts/automation/            # 构建、发布、迭代、Git 推送
scripts/state/                 # user-prompts.txt、backlog-todos.md 等
scripts/dev/                   # Python 工具脚本
_local/                        # 本机日志、APK 副本（不入 Git）
```

目录与脚本说明见 `scripts/automation/` 及各 `Auto-*.ps1` 文件头注释。

### CI 状态

- **Lint** → `./gradlew lintDebug`
- **Unit Test** → `./gradlew testDebugUnitTest`
- **Build** → `./gradlew assembleDebug`

CI 配置：`.github/workflows/ci.yml`（push 到 main/dev 自动触发）

### 月度迭代节奏

每月底整理下月迭代计划，按 Epic/Story 粒度排入冲刺清单。

| 月份 | 主题 | 目标 Epic |
|------|------|-----------|
| 2026-05 | 基础体验完善 | Epic-1（角色管理增强） |
| 2026-06 | 对话能力扩展 | Epic-2（搜索/语音已完成，图片待开发） |
| 2026-Q3 | 进阶特性 | Epic-3（个性化与生态） |

---

- **当前版本**：1.5.0（versionCode 8）
- **最低系统**：Android 8.0（API 26）
- **目标 SDK**：34
- **仓库**：[github.com/Squemadylan/TalkChatBot](https://github.com/Squemadylan/TalkChatBot)
- **预编译包**：[Releases](https://github.com/Squemadylan/TalkChatBot/releases)（提供 debug APK，可自行签名后长期使用）

> **安全说明**：应用与源码**均不包含**任何第三方 API Key。首次使用请在底部 **「配置」** 页填写 Base URL、API Key、模型名等。

---

## 近期更新

### v2.0.1 / 推送（2026-06）

- **四层记忆修复**：修复 L2 场景记忆不生效问题，优化 L2/L3 管线稳定性
- **推送通知**：AI 回复完成后，仅在应用处于后台时发送系统通知；前台对话不打扰
- **通知权限**：Android 13+ 启动时请求通知权限，拒绝后引导用户手动开启
- **体验微调**：移除记忆生成时的 Toast；增加调试日志便于排查 API 参数问题
- **厂商推送骨架**：预留小米/华为/OPPO/VIVO/荣耀 SDK 接入位（当前使用系统通知）

### v2.0（2026-06）

- **四层渐进式记忆**：参考 TencentDB Agent Memory，替换旧版单文件 Markdown 记忆
  - **L3 Persona**：跨角色 `persona_global.md` + 角色专属 `persona.md`
  - **L2 Scenario**：按主题聚类为 `scenarios/*.md`
  - **L1 Atom**：原子事实 JSONL + 向量索引（远程 embedding / BM25 兜底）
  - **L0 + 短时画布**：原始对话滚动缓存 + Mermaid 画布，超长历史外置到 `refs/`
- **记忆召回**：发消息前按 Persona → Scenario → Atom → Canvas 顺序注入 system prompt
- **记忆 UI**：设置页记忆开关与阈值、记忆查看/导出页、角色编辑页重置记忆
- **旧数据迁移**：启动时自动将 `long_term_memory/memory_<id>.md` 迁移到新目录树
- **备份升级**：ZIP 格式升至 v4，完整打包 `memory/` 目录树

### v1.5（2026-05）

- **豆包 TTS**：集成 VolcTtsHelper（HTTP），VoiceHelper 多引擎回退（系统 TTS → 豆包）
- **语音输入**：对话页麦克风按钮，调用系统语音识别
- **自动朗读**：助手回复完成后可自动播报（可在设置中调节语速）
- **聊天搜索**：对话内全文搜索，高亮匹配消息
- **消息收藏**：长按消息可收藏 / 取消收藏
- **对话导出**：支持导出当前角色聊天记录（多种文本格式）

### v1.4（2026-05）

- **气泡样式**：支持默认/紧凑/圆角/半透明四种样式，切换实时生效
- **回复策略**：支持标准/短回复/细腻三种预设，自动调整 API 参数
- **状态栏沉浸**：可开关状态栏沉浸模式
- **设置项优化**：占位功能标注为「即将支持」

### v1.3（2026-05）

- **API 检测**：配置页支持一键检测连接，显示成功/Key 错误/余额不足等状态
- **模型预设**：根据模型名自动推荐默认 max_tokens
- **配置摘要**：设置页展示当前 API 配置摘要

### v1.2.1（2026-05）

- **备份与恢复**：ZIP 现包含配置页的 API 地址、密钥、模型名、温度、最大 Token；恢复后无需重新填写（旧版备份无 `apiConfig` 字段时自动跳过）
- **应用内更新**：基于 `app/update.json`；支持强制最低版本、可选更新、SHA-256 校验；设置页提供 **网盘手动更新**（夸克网盘，国内 GitHub 不可达时使用）

### 2026-05-18

- **品牌**：应用更名为 **独白匣**，更换全新启动图标
- **长期记忆**（初版）：角色可单独开启；由模型将对话摘要为 Markdown 写入本地（v2.0 起升级为四层记忆架构）
- **对话交互**：长按消息支持复制（含占位符替换）与单条删除
- **开源许可**：新增 [MIT LICENSE](LICENSE)（含中文译文）

### v1.1.1（2026-05-18）

- 应用名 **独白匣**、新图标；版本号 1.1.1

### v1.1.0（2026-05）

- **配置页**：输入后自动保存；API Key 支持显示/隐藏；一键跳转硅基流动注册页
- **稳定性**：网络与 HTTP 错误提示优化

---

## 推荐模型（硅基流动）

以下模型均在 [硅基流动](https://siliconflow.cn) 平台提供，需在 **「配置」** 页填写：

| 项 | 填写内容 |
|----|----------|
| **Base URL** | `https://api.siliconflow.cn/v1` |
| **API Key** | 在硅基流动控制台创建（配置页可跳转注册） |

**推荐模型名**（直接填入「模型」输入框）：

| 模型 | 模型 ID | 说明 |
|------|---------|------|
| **DeepSeek V3.2**（推荐） | `deepseek-ai/DeepSeek-V3.2` | 应用默认推荐；对话与长期记忆摘要表现均衡 |
| **DeepSeek V3** | `deepseek-ai/DeepSeek-V3` | 上一代主力模型，仍适合日常角色扮演 |

应用亦兼容其它 OpenAI 格式 API；若换用别家服务，请按其文档自行填写 Base URL 与模型名。

---

## 主要界面

| Tab | 说明 |
|-----|------|
| **角色** | 角色卡片列表、搜索与按标签筛选、新建/编辑角色 |
| **回忆** | 按角色汇总最近一条消息；进入某角色后为对话页 |
| **配置** | 大模型 API 地址、密钥、模型、温度、最大 Token |
| **设置** | 个人资料、聊天显示、四层记忆、语音、备份恢复、检查更新 / 网盘手动更新、深色模式等 |

进入**某一角色的对话页**时底部导航会隐藏；在 **「回忆」列表**（未进入具体对话）时底部导航保持显示。

---

## 已实现功能

### 角色

- 网格展示角色卡片（头像、名称、标签等）
- 新建 / 编辑角色：名称、头像（相册或随机色块）、标签、角色描述（长文）、主开场白、**长期记忆**开关
- 删除角色（含该角色全部聊天记录）
- **搜索**：按名称、标签、描述过滤
- **筛选**：按一个或多个标签多选筛选

### 回忆与对话

- **回忆页**：各角色最近一条消息预览，支持搜索；长按菜单可清空该角色回忆或删除角色
- **对话页**：与当前角色流式聊天；用户气泡 / 助手气泡，助手回复支持 Markdown（流式结束后渲染）
- **长按消息**：复制（含占位符替换）、删除单条、收藏 / 取消收藏
- **对话内搜索**：按关键词检索当前角色聊天记录
- **对话导出**：导出当前角色聊天记录为文本文件
- **语音输入**：麦克风按钮调用系统语音识别
- **自动朗读**：助手回复完成后可 TTS 播报（豆包 TTS + 系统 TTS 回退）
- **主开场白**：无历史消息时自动插入角色配置的开场白，**不会**再自动请求模型生成第二条
- **占位符**：发送与展示时替换 `{{user}}`、`{{persona}}`、`{{char}}` 及常见别名，以及 `{{date}}`、`{{time}}` 等时间占位（见 `UserPromptPlaceholders`）
- 清空当前角色对话 / 清空全部回忆
- 左上角返回退出对话

### API 配置

- Base URL（自动补全 `https://` 与末尾 `/`）
- API Key、模型名、温度、最大 Token；**修改后自动保存**
- API Key 支持显示 / 隐藏；可跳转硅基流动注册页获取 Key
- 根据模型名推荐默认 `max_tokens`（可再手动改）
- **一键检测连接**：显示成功 / Key 错误 / 余额不足等状态
- **流式请求**（SSE）；失败时常见 HTTP 状态有中文提示
- 网络不可用、超时、域名解析失败等错误提示

### 四层记忆（v2.0）

- 按角色可选开启；对话完成后后台异步跑 L1 抽取 → L2 聚类 → L3 Persona 更新
- 发消息前由 `MemoryPipeline` 召回并注入 Persona、场景、原子事实、短时画布
- 向量召回复用 Chat API 配置的远程 embedding；未配置时降级为 BM25
- 设置页可查看 embedding 状态、调节外置阈值、手动触发 L1/L2/L3 刷新
- **记忆查看页**：只读浏览各层文件，支持导出记忆 ZIP
- 角色编辑页可查看记忆层级状态、重置该角色记忆
- 旧版 `memory_<id>.md` 启动时自动迁移；清空或删除角色时同步清除记忆目录
- 全量备份 ZIP（v4）完整打包 `memory/` 目录树；恢复 v3 备份时自动触发迁移

### 设置与个人资料

- 用户头像、显示名、人设（参与占位符替换）
- 深色 / 浅色模式
- 聊天气泡样式（默认 / 紧凑 / 圆角 / 半透明）
- 回复策略（标准 / 短回复 / 细腻），自动调整 API 参数
- 状态栏沉浸模式开关
- 聊天是否显示双方头像
- 带入上下文的历史消息条数（0–10 条）
- 聊天背景图（选择 / 清除）
- 语音语速、语言偏好
- **备份与恢复**（ZIP 格式版本 4）：全部角色、头像、四层记忆目录树、用户名/人设/用户头像，以及 **API 配置**；Android 10+ 保存到 `Download/ChatBot/`，较低版本写入存储根目录 `ChatBot/Backups`
- **检查更新**：拉取 `app/update.json`，支持可选更新与强制最低版本；下载安装需「安装未知应用」权限
- **网盘手动更新**：跳转夸克网盘分享页下载 APK（链接可在 `update.json` 的 `manualUpdateUrl` 配置，亦内置默认地址）

### 推送通知

- AI 回复完成后，**仅当应用处于后台**时发送系统通知（前台直接看对话，不打扰）
- Android 13+ 通知权限请求与引导开启流程
- 通知渠道：AI 回复、记忆保存、其他（厂商推送 SDK 接入位已预留）

### 其它

- 本地 Room 数据库持久化角色与消息（含收藏字段）
- 启动时自动检查更新（强制更新立即提示；可选更新 24 小时内最多提示一次）
- Release 构建开启 R8 压缩与资源收缩；Debug 下可选 HTTP 日志
- 未捕获异常写入应用私有目录 `last_crash.txt`

---

## 构建与安装

### 环境

- Android Studio（推荐自带 JBR 17）
- JDK 17
- Android SDK 34

### 命令行

```bash
# Windows（在项目根目录）
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
gradlew.bat installDebug
```

Debug APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

### 真机调试

1. 手机开启开发者选项与 USB 调试  
2. 连接电脑后执行 `adb devices` 确认设备  
3. `gradlew installDebug` 或 Android Studio 运行 **app**

---

## 更新策略说明

应用内更新由仓库根目录 **`app/update.json`** 驱动（`main` 分支）。客户端启动或设置页「检查更新」时拉取该文件，与**本机安装的 `versionCode`**（见 `app/build.gradle`）比较。

### 判定规则

| 条件 | 类型 | 用户体验 |
|------|------|----------|
| 本地 `versionCode` **<** `minVersionCode` | **强制更新** | 启动即弹窗；不可返回/点外部关闭；无「稍后再说」 |
| 本地 **≥** `minVersionCode` 且 **<** `versionCode` | **可选更新** | 可「稍后再说」；启动检查 24 小时内最多提示一次 |
| 本地 **≥** `versionCode` | 已最新 | 手动检查提示「当前已是最新版本」 |

须满足 **`minVersionCode` ≤ `versionCode`**。

### `update.json` 字段

| 字段 | 说明 |
|------|------|
| **`versionCode`** | 线上最新版整数；发新 APK 时递增 |
| **`minVersionCode`** | 最低可运行版本；低于此值强制更新 |
| `versionName` | 展示用版本名（弹窗不显示，可给发版备注） |
| `apkUrl` | 安装包 HTTPS 直链 |
| `changelog` | 更新说明（远程配置用；弹窗正文以应用内文案为主） |
| `sha256` | 可选；APK 的 SHA-256（小写十六进制），用于下载后校验 |
| `manualUpdateUrl` | 可选；网盘等手动下载页（设置页「网盘手动更新」及强更弹窗） |

拉取顺序：GitHub Raw → jsDelivr 镜像 → 内置 `app/src/main/assets/update_manifest_fallback.json`（兜底，发版时请与线上一致）。

### 发版对照清单

> 详细约定与踩坑说明见 **`.cursor/rules/android-app-update-release.mdc`**（GitHub Release 文件名与 `apkUrl` 必须一致，否则直更 404）。

1. 在 `app/build.gradle` 中设置新版本 `versionCode` / `versionName`，打包并上传 APK（Release 或网盘）。
2. 用 `gh release view <tag> --json assets` 确认 **`assets[].name`**（不是 label），`apkUrl` 末尾文件名必须与之相同。
3. 编辑 **`app/update.json`** 并 push 到 **`main`**：
   - `versionCode` = 新包 versionCode
   - `minVersionCode` = 仍允许运行的最低版（**要强制旧版升级时设为新区 versionCode**）
   - `apkUrl` = 新包下载地址（发版后用 `Invoke-WebRequest -Method Head` 验证非 404）
   - `sha256` = 新 APK 的 SHA-256（小写）
4. 同步修改 **`update_manifest_fallback.json`**（与 `update.json` 保持一致）。
5. 验证：用**低于 `versionCode` 的旧包**安装，冷启动或设置里检查更新。

### 示例（当前线上配置）

- **`versionCode` 必须与已发布的最新 APK 一致**。当前仓库配置为 **1.5.0（versionCode 8）**：`versionCode: 8`、`minVersionCode: 8`（低于 v8 的用户强制更新）。
- 已安装 v8 的用户手动检查会提示「当前已是最新版本」。

> **注意**：jsDelivr 的 `@main` 有缓存延迟。客户端会同时请求 Raw / 镜像 / `main` 多个地址，并采用 **`versionCode` 最大** 的一份，避免旧安装包内嵌的历史 commit 镜像盖住新配置。发版后仍可将 `UPDATE_MANIFEST_URL_MIRROR` 改为当次 commit SHA 以加快生效。

国内访问 GitHub 不稳定时，可在设置中使用「网盘手动更新」（`manualUpdateUrl`）。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Material Components、View Binding、ConstraintLayout |
| 架构 | MVVM + Repository |
| 本地库 | Room |
| 记忆 | MemoryPipeline（L0–L3 + 短时画布）、sqlite-vec 向量索引 |
| 网络 | Retrofit、OkHttp（流式 SSE） |
| 语音 | VolcTtsHelper（豆包 HTTP TTS）、系统 SpeechRecognizer |
| 导航 | Jetpack Navigation |
| 异步 | Kotlin Coroutines |
| Markdown | Markwon |

---

## 待办（规划）

以下功能**尚未实现**，后续按月度迭代推进。

### 2026-05 月（Epic-1：角色管理增强）

| Story | 功能 | 状态 |
|-------|------|------|
| story-1 | 角色分组 / 文件夹管理 | 待开发 |
| story-2 | 角色导入导出（JSON 格式） | 待开发 |

### 2026-06 月（Epic-2：多媒体与搜索）

| Story | 功能 | 状态 |
|-------|------|------|
| story-3 | 对话内发送图片 | 待开发 |
| story-4 | 聊天记录全文搜索 | 已完成 |
| story-5 | 语音输入 / 语音播报 | 已完成 |

### 2026-Q3（Epic-3：个性化与生态）

| Story | 功能 | 状态 |
|-------|------|------|
| story-6 | 多套 API 配置切换 | 待开发 |
| story-7 | API 连接一键检测 | 已完成 |
| story-8 | 背景音乐播放 | 待开发 |
| story-9 | 官网与社群链接 | 待开发 |

### v2.1+（个性化升级，PRD 已写）

- [ ] 气泡颜色自定义、字体大小/行间距
- [ ] 打字机效果、外语回复即时翻译
- [ ] Mermaid 画布可视化渲染
- [ ] 厂商推送 SDK 正式接入（小米/华为/OPPO/VIVO/荣耀）

### 已完成

- [x] 应用内检查更新（`app/update.json`，含强制/可选与网盘手动更新）
- [x] 角色长期记忆 → v2.0 四层渐进式记忆（2026-06）
- [x] 豆包 TTS、语音输入、自动朗读（v1.5）
- [x] 聊天搜索、消息收藏、对话导出（v1.5）
- [x] API 连接检测、气泡样式、回复策略、状态栏沉浸（v1.4）
- [x] 后台 AI 回复推送通知（2026-06）

### 占位待优化

- [ ] 消息转发
- [ ] 设置项「配图」等尚未落地的入口

---

## 许可证

本项目采用 [MIT License](LICENSE) 发布。
