# 大模型聊天对话应用 - Android Studio 使用指南

## 📱 项目概述

这是一个基于 Android Jetpack 架构的大模型聊天对话应用，支持自定义角色和灵活的 API 配置。

### 主要功能
- ✅ 角色管理（创建、编辑、删除）
- ✅ 与 AI 角色对话
- ✅ 灵活配置各大模型 API
- ✅ 聊天历史记录
- ✅ 深色主题 UI

## 🔧 在 Android Studio 中打开项目

### 步骤 1：打开 Android Studio
启动 Android Studio 应用程序

### 步骤 2：打开项目
1. 点击 "Open an existing project"（或使用快捷键 Ctrl+O / Cmd+O）
2. 浏览到 `/workspace` 目录
3. 点击 "OK"

### 步骤 3：等待 Gradle 同步
- 首次打开会下载依赖，请耐心等待
- 右下角会显示同步进度
- 状态栏显示 "Gradle sync finished" 表示同步完成

### 步骤 4：运行应用
1. **连接设备**：连接 Android 手机（需开启 USB 调试）或启动模拟器
2. **选择运行目标**：在工具栏的下拉菜单中选择您的设备
3. **运行**：点击绿色运行按钮 ▶️

## 📁 项目结构说明

```
/workspace/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/chatbot/
│   │   │   ├── App.kt                    # 应用程序入口
│   │   │   ├── data/
│   │   │   │   ├── model/                # 数据模型
│   │   │   │   ├── repository/            # 数据仓库
│   │   │   │   └── network/              # 网络请求
│   │   │   ├── database/                 # Room 数据库
│   │   │   ├── viewmodel/                # ViewModel 层
│   │   │   └── ui/                      # 界面层
│   │   │       ├── character/            # 角色管理
│   │   │       ├── chat/                 # 聊天功能
│   │   │       ├── config/               # API 配置
│   │   │       └── setting/              # 设置页面
│   │   └── res/                         # 资源文件
│   └── build.gradle                      # App 模块配置
├── build.gradle                          # 项目级配置
└── settings.gradle                       # Gradle 设置
```

## 🎯 测试步骤

### 首次使用流程

#### 1️⃣ 配置 API（必须！）
1. 进入「连接」页面
2. 填写以下信息：
   - **API 基础地址**：如 `https://api.openai.com/v1/`
   - **API 密钥**：您的 API 密钥
   - **模型名称**：如 `gpt-3.5-turbo`
   - **温度参数**：建议 0.7
   - **最大令牌数**：建议 1024
3. 点击「保存配置」

#### 2️⃣ 创建角色
1. 进入「角色」页面
2. 点击底部 "+" 按钮
3. 填写角色信息：
   - **角色名称**：如 "小助手"
   - **角色描述**：简短描述
   - **人设提示词**：定义角色性格（最重要！）
   - **标签**：如 "助手,友好"
4. 点击「保存」

#### 3️⃣ 开始对话
1. 点击角色卡片进入聊天界面
2. 输入消息并发送
3. 等待 AI 回复

## 🔑 API 配置示例

### OpenAI API
```
API 基础地址: https://api.openai.com/v1/
API 密钥: sk-xxxxxxxxxxxxxxxxxxxxxxxx
模型名称: gpt-3.5-turbo
```

### 其他兼容 API
应用支持所有兼容 OpenAI API 格式的接口，只需修改基础地址和密钥即可。

## 🛠️ 常见问题

### Q1: Gradle 同步失败
**解决方案**：
- 检查网络连接
- 点击 "File" → "Invalidate Caches" → "Invalidate and Restart"
- 确保 Gradle 版本兼容

### Q2: 无法运行到设备
**解决方案**：
- 手机端：进入「设置」→「关于手机」，连续点击「版本号」7次开启开发者模式
- 返回「设置」→「系统」→「开发者选项」，开启 USB 调试
- 电脑端安装手机驱动

### Q3: API 请求失败
**解决方案**：
- 检查 API 地址是否正确（需包含 `/v1/`）
- 确认 API 密钥有效
- 检查网络连接

## 📝 开发扩展

如需添加更多功能，可以修改以下文件：
- 添加新功能：`ui/` 目录
- 修改数据模型：`data/model/`
- 调整网络请求：`data/network/`
- 自定义界面样式：`res/values/`

## 📄 技术栈

- **语言**: Kotlin
- **架构**: MVVM
- **UI**: Material Design 3
- **网络**: Retrofit + OkHttp
- **数据库**: Room
- **导航**: Jetpack Navigation

## 📞 获取帮助

如有问题，请检查：
1. Android Studio 版本（建议最新稳定版）
2. Gradle 版本（与项目配置匹配）
3. JDK 版本（建议 JDK 17+）
