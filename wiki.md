# TalkChatBot 项目 Wiki

> 本文档基于仓库源码与 Gradle 配置整理，与 `README.md` 中的产品说明互为补充；功能完成度以 `README.md` 为准。

## 1. 项目概览

| 项 | 说明 |
|----|------|
| 根工程名 | ChatBot（`settings.gradle` 中 `rootProject.name`） |
| 应用包名 | `com.example.chatbot` |
| 平台 | Android（最低 API 24，目标/编译 SDK 34） |
| 语言 | Kotlin（插件 1.9.22） |
| 定位 | 本地角色管理 + OpenAI 兼容 Chat Completions 的文字对话客户端 |

应用入口为 `MainActivity`，`Application` 子类 `App` 负责 Room 数据库初始化与未捕获异常处理。

## 2. 仓库结构（源码与构建）

```
TalkChatBot/
├── app/                          # 唯一 Android 模块
│   ├── build.gradle              # 模块依赖、SDK、View Binding 等
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/chatbot/
│       │   ├── App.kt            # Application、Room、全局异常
│       │   ├── ui/               # Activity、Fragment、Adapter、Dialog
│       │   ├── viewmodel/      # MVVM 中的 ViewModel
│       │   ├── data/
│       │   │   ├── model/       # Room 实体与网络 DTO
│       │   │   ├── network/     # Retrofit、ApiService
│       │   │   └── repository/ # 数据仓库
│       │   └── database/        # Room Database、DAO
│       └── res/                  # 布局、导航、主题、字符串等
├── build.gradle                  # 顶层 AGP / Kotlin 插件版本
├── settings.gradle
├── gradle/wrapper/               # Gradle 8.5
└── README.md                     # 功能清单、路线图、已知问题
```

构建产物与 `app/build/` 下的中间文件通常不纳入版本说明；日常开发关注 `app/src/main` 即可。

## 3. 技术栈（与 `app/build.gradle` 一致）

| 类别 | 依赖 | 版本（摘录） |
|------|------|----------------|
| Android Gradle Plugin | `com.android.tools.build:gradle` | 8.2.2 |
| Kotlin | `org.jetbrains.kotlin.android` | 1.9.22 |
| UI | Material、`appcompat`、`constraintlayout` | material 1.11.0 等 |
| 架构组件 | Lifecycle、Navigation | 2.7.0 / 2.7.7 |
| 本地存储 | Room（kapt 编译器） | 2.6.1 |
| 网络 | Retrofit + Gson、OkHttp Logging | 2.9.0 / 4.12.0 |
| 异步 | Kotlin Coroutines | 1.7.3 |

- **Java 字节码目标**：17（`compileOptions` / `kotlinOptions.jvmTarget`）。
- **构建特性**：View Binding、`buildConfig` 已开启；Release 开启混淆与资源压缩。

## 4. 应用架构

### 4.1 分层（MVVM + Repository）

- **UI**：`MainActivity` + Jetpack Navigation 托管的 Fragment（角色、聊天、API 配置、设置）。
- **ViewModel**：`CharacterViewModel`、`ChatViewModel`、`ApiConfigViewModel` 等，配合 LiveData/协程（以具体类为准）。
- **Repository**：`CharacterRepository`、`MessageRepository`、`ApiConfigRepository` 封装 Room 与调用链。
- **Data**：`database`（Room）、`network`（Retrofit 动态 `@Url` 请求）。

### 4.2 导航图

`res/navigation/nav_graph.xml` 定义四个目的地，起始页为 **角色**（`characterFragment`）；聊天页通过 `characterId`（`long`）参数进入。

## 5. 数据模型与持久化

### 5.1 Room

- **数据库类**：`AppDatabase`（`chatbot_db`），版本 1，`exportSchema = false`，迁移策略为 `fallbackToDestructiveMigration()`（结构变更会清库，生产环境需谨慎）。
- **实体**：
  - `Character`：`characters` 表（名称、头像、描述、系统提示 `prompt`、标签 `tags`、创建时间等）。
  - `Message`：`messages` 表（关联 `characterId`、内容、是否用户、时间戳）。
  - `ApiConfig`：`api_config` 表单行配置（`id = 1` 约定）：`baseUrl`、`apiKey`、`model`、`temperature`、`maxTokens`。

### 5.2 网络 API 形态

- **接口**：`ApiService.sendMessage(@Url url, @Body ChatRequest)`，与 OpenAI 风格的 **Chat Completions** 请求体对齐（`model`、`messages`、`temperature`、`max_tokens`）。
- **客户端**：`RetrofitClient.create(baseUrl, apiKey)` 组装 OkHttp（Bearer Token、超时、重试、`HttpLoggingInterceptor`），并对 `baseUrl` 做补全协议与尾部斜杠处理。

## 6. 权限与系统配置（Manifest 摘要）

- **权限**：`INTERNET`、`ACCESS_NETWORK_STATE`。
- **Application**：自定义 `App`，硬件加速、`largeHeap`、网络安全配置 `network_security_config`（便于调试 HTTP 等场景，详见 `res/xml`）。
- **主 Activity**：`MainActivity`，`singleTop`，处理部分 `configChanges`，软键盘 `adjustResize`。

## 7. 源码索引（Kotlin 主包）

| 路径 | 职责 |
|------|------|
| `App.kt` | 全局 `AppDatabase` 单例构建、初始化状态、未捕获异常退出 |
| `ui/MainActivity.kt` | `NavHostFragment` + `BottomNavigationView` 绑定 |
| `ui/character/*` | 角色列表、适配器、添加角色对话框、Safe Args 方向类 |
| `ui/chat/*` | 会话 UI、消息列表适配器 |
| `ui/config/ConfigFragment.kt` | API 与模型相关界面 |
| `ui/setting/SettingFragment.kt` | 设置聚合页 |
| `viewmodel/*ViewModel.kt` | 各屏业务状态与用例触发 |
| `data/repository/*` | 对 DAO / 网络的封装 |
| `data/network/*` | Retrofit 服务与 DTO |
| `database/*Dao.kt` | Room 访问接口 |

## 8. 本地构建与运行

1. 使用 **Android Studio**（建议 Hedgehog 及以上）打开仓库根目录。
2. JDK **17**，安装 **Android SDK 34**；Gradle Wrapper 当前为 **8.5**（见 `gradle-wrapper.properties`）。
3. 同步 Gradle 后运行 `app` 模块到真机或模拟器（API 24+）。

更细的产品功能列表、界面说明、已知问题与待办，见根目录 **[README.md](./README.md)**。

## 9. 与 README 的关系

- **wiki.md**：偏工程视角——包名、模块划分、依赖版本、Room/网络契约、导航与权限。
- **README.md**：偏产品与进度——各模块完成度、UI 规划、路线图、已知缺陷。

若两处描述冲突，以 **当前仓库源码与 `app/build.gradle`** 为准更新 wiki。

## 10. 文档元信息

| 项 | 值 |
|----|-----|
| Wiki 生成依据 | 源码树、`build.gradle`、`app/build.gradle`、`AndroidManifest.xml`、`nav_graph.xml`、核心 Kotlin 文件 |
| 建议维护方式 | 升级 AGP/SDK/重大架构时同步修订第 3、5、7 节 |

---

*许可证与联系方式以 `README.md` 为准。*
