# 独白匣

面向 Android 的大模型角色聊天应用：自建角色、流式对话、本地回忆与备份。支持 OpenAI 兼容的 Chat Completions API（含 SSE 流式）。

> 仓库名仍为 [TalkChatBot](https://github.com/Squemadylan/TalkChatBot)；应用显示名与图标已更新为 **独白匣**。

- **当前版本**：1.1.1（versionCode 4）
- **最低系统**：Android 8.0（API 26）
- **目标 SDK**：34
- **仓库**：[github.com/Squemadylan/TalkChatBot](https://github.com/Squemadylan/TalkChatBot)
- **预编译包**：[Releases](https://github.com/Squemadylan/TalkChatBot/releases)（提供 debug APK，可自行签名后长期使用）

> **安全说明**：应用与源码**均不包含**任何第三方 API Key。首次使用请在底部 **「配置」** 页填写 Base URL、API Key、模型名等。

---

## 近期更新

### 2026-05-18

- **品牌**：应用更名为 **独白匣**，更换全新启动图标
- **长期记忆**：角色可单独开启；由模型将对话摘要为 Markdown 写入本地，发消息时自动注入上下文；备份 ZIP 含记忆文件
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
| **设置** | 个人资料、聊天显示、记忆条数、备份恢复、深色模式等 |

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
- **对话页**：与当前角色流式聊天；用户气泡 / 助手气泡，助手回复支持 Markdown（流式结束后渲染）；**长按消息**可复制或删除单条
- **主开场白**：无历史消息时自动插入角色配置的开场白，**不会**再自动请求模型生成第二条
- **占位符**：发送与展示时替换 `{{user}}`、`{{persona}}`、`{{char}}` 及常见别名，以及 `{{date}}`、`{{time}}` 等时间占位（见 `UserPromptPlaceholders`）
- 清空当前角色对话 / 清空全部回忆
- 左上角返回退出对话

### API 配置

- Base URL（自动补全 `https://` 与末尾 `/`）
- API Key、模型名、温度、最大 Token；**修改后自动保存**
- API Key 支持显示 / 隐藏；可跳转硅基流动注册页获取 Key
- 根据模型名推荐默认 `max_tokens`（可再手动改）
- **流式请求**（SSE）；失败时常见 HTTP 状态有中文提示
- 网络不可用、超时、域名解析失败等错误提示

### 长期记忆

- 按角色可选开启；后台调用同一套 API 将历史对话摘要为结构化 Markdown
- 聊天请求时以 system 消息注入 **【长期记忆】**；清空或删除角色时同步清除本地记忆文件
- 全量备份 ZIP 时包含 `memories/` 目录，恢复后可继续使用

### 设置与个人资料

- 用户头像、显示名、人设（参与占位符替换）
- 深色 / 浅色模式
- 聊天是否显示双方头像
- 带入上下文的历史消息条数（0–10 条）
- 聊天背景图（选择 / 清除）
- **备份与恢复**：ZIP 包含全部角色、头像文件、已开启角色的长期记忆、用户名/人设/用户头像；Android 10+ 保存到 `Download/ChatBot/`，较低版本写入存储根目录 `ChatBot/Backups`

### 其它

- 本地 Room 数据库持久化角色与消息
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

1. 在 `app/build.gradle` 中设置新版本 `versionCode` / `versionName`，打包并上传 APK（Release 或网盘）。
2. 编辑 **`app/update.json`** 并 push 到 **`main`**：
   - `versionCode` = 新包 versionCode
   - `minVersionCode` = 仍允许运行的最低版（**仅在不兼容旧版时提高到新包 versionCode**）
   - `apkUrl` = 新包下载地址
3. 同步修改 **`update_manifest_fallback.json`**（与 `update.json` 保持一致）。
4. 验证：用**低于 `versionCode` 的旧包**安装，冷启动或设置里检查更新。

### 示例（当前线上配置思路）

- `versionCode: 5`、`minVersionCode: 4`：已装 **v4** 的用户为**可选更新**；**v3 及以下**为**强制更新**。
- 若发布 **v6** 且必须淘汰 v5：`versionCode: 6`、`minVersionCode: 6`。

国内访问 GitHub 不稳定时，可在设置中使用「网盘手动更新」（`manualUpdateUrl`）。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Material Components、View Binding、ConstraintLayout |
| 架构 | MVVM + Repository |
| 本地库 | Room |
| 网络 | Retrofit、OkHttp（流式 SSE） |
| 导航 | Jetpack Navigation |
| 异步 | Kotlin Coroutines |
| Markdown | Markwon |

---

## 待办（规划）

以下功能**尚未实现**或仅为设置页占位入口，后续可能迭代：

- [ ] 角色分组 / 文件夹管理
- [ ] 角色导入导出（除现有 ZIP 备份外的单独 JSON 等格式）
- [ ] 消息转发
- [ ] 对话内发送图片
- [ ] 语音输入 / 语音播报
- [ ] 聊天记录全文搜索
- [ ] 多套 API 配置切换
- [ ] API 连接一键检测
- [ ] 设置项：气泡样式、语音、回复策略、配图、状态栏等（当前为占位）
- [x] 应用内检查更新（`app/update.json`，含强制/可选与网盘手动更新）
- [ ] 官网与社群链接
- [ ] 背景音乐播放

---

## 许可证

本项目采用 [MIT License](LICENSE) 发布。
