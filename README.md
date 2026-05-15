# TalkChatBot

面向 Android 的大模型角色聊天应用：自建角色、流式对话、本地回忆与备份。支持 OpenAI 兼容的 Chat Completions API（含 SSE 流式）。

- **当前版本**：1.1.0（versionCode 3）
- **最低系统**：Android 7.0（API 24）
- **目标 SDK**：34
- **仓库**：[github.com/Squemadylan/TalkChatBot](https://github.com/Squemadylan/TalkChatBot)
- **预编译包**：[Releases](https://github.com/Squemadylan/TalkChatBot/releases)（提供 debug APK，可自行签名后长期使用）

> **安全说明**：应用与源码**均不包含**任何第三方 API Key。首次使用请在底部 **「配置」** 页填写 Base URL、API Key、模型名等。

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
- 新建 / 编辑角色：名称、头像（相册或随机色块）、标签、角色描述（长文）、主开场白
- 删除角色（含该角色全部聊天记录）
- **搜索**：按名称、标签、描述过滤
- **筛选**：按一个或多个标签多选筛选

### 回忆与对话

- **回忆页**：各角色最近一条消息预览，支持搜索；长按菜单可清空该角色回忆或删除角色
- **对话页**：与当前角色流式聊天；用户气泡 / 助手气泡，助手回复支持 Markdown（流式结束后渲染）
- **主开场白**：无历史消息时自动插入角色配置的开场白，**不会**再自动请求模型生成第二条
- **占位符**：发送与展示时替换 `{{user}}`、`{{persona}}`、`{{char}}` 及常见别名，以及 `{{date}}`、`{{time}}` 等时间占位（见 `UserPromptPlaceholders`）
- 清空当前角色对话 / 清空全部回忆
- 左上角返回退出对话

### API 配置

- Base URL（自动补全 `https://` 与末尾 `/`）
- API Key、模型名、温度、最大 Token
- 根据模型名推荐默认 `max_tokens`（可再手动改）
- **流式请求**（SSE）；失败时常见 HTTP 状态有中文提示
- 网络不可用、超时、域名解析失败等错误提示

### 设置与个人资料

- 用户头像、显示名、人设（参与占位符替换）
- 深色 / 浅色模式
- 聊天是否显示双方头像
- 带入上下文的历史消息条数（0–10 条）
- 聊天背景图（选择 / 清除）
- **备份与恢复**：ZIP 包含全部角色、头像文件、用户名/人设/用户头像；Android 10+ 保存到 `Download/ChatBot/`，较低版本写入存储根目录 `ChatBot/Backups`

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
- [ ] 消息长按复制、转发
- [ ] 对话内发送图片
- [ ] 语音输入 / 语音播报
- [ ] 聊天记录全文搜索
- [ ] 多套 API 配置切换
- [ ] API 连接一键检测
- [ ] 设置项：气泡样式、语音、回复策略、配图、状态栏等（当前为占位）
- [ ] 应用内检查更新、官网与社群链接
- [ ] 背景音乐播放

---

## 许可证

本项目仅供学习与交流使用。
