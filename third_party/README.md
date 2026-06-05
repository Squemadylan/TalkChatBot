# Third-party binaries

本目录用来放不便通过 Maven 远程解析的本地 AAR / 模型 / .so 资源。

> **当前策略（2026-06 之后）：embedding 全部走远程 API（复用 Chat 的 baseUrl/apiKey），不再打包任何本地 ONNX 模型。**
>
> 远程 → Maven 远程依赖，`app/build.gradle` 已经引用以下坐标：

| 用途 | Maven 坐标 | 版本 | 仓库 |
| --- | --- | --- | --- |
| SQLite Vector 扩展 | `ai.sqlite:vector` | `0.9.94` | Maven Central |
| Android 重打包 SQLite（带 `SQLiteCustomExtension`） | `com.github.requery:sqlite-android` | `3.45.0` | JitPack |

`ai.sqlite:vector` 提供 `libvector.so`（arm64-v8a / armeabi-v7a / x86_64），要求 SQLite 库支持
`SQLiteCustomExtension` —— Android 标准 SQLite 不支持，所以引入 `requery/sqlite-android`
替换 SQLite（仅向量索引使用，Room 不受影响）。

## 体积影响

- 接 ONNX 模型时：debug APK ≈ 56 MB（其中 ORT .so ≈ 12 MB / ABI，bge int8 ≈ 47 MB）
- **当前（仅远程）**：debug APK ≈ 10 MB（只剩 sqlite-vec + libsqlite3x ≈ 5 MB / ABI）

## 离线兜底（可选）

如果未来要重新支持本地 ONNX embedding：

1. 加 `implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.26.0'`
2. 重新引入 `OnnxEmbedder`（`git log` 找回已被删除的版本即可）
3. 把 `BAAI/bge-large-zh-v1.5-int8.onnx` + `tokenizer.json` 放到 `app/src/main/assets/models/`
4. 在 `EmbedderFactory.buildOnce` 里把 onnx 路径加回
