# 第三方 SDK 归档（本地参考，默认不入 Git）

本目录用于存放**未编入 Android 工程**的第三方 SDK 压缩包/解压目录，避免污染仓库根目录。

| 子目录 | 说明 |
|--------|------|
| `archive/iflytek-xtts-sdk/` | 讯飞 XTTS 离线 SDK（已弃用，仅作参考） |

应用当前 TTS：**豆包 HTTP API**（见 `app/.../VolcTtsHelper.kt` 与 `.ai/prd/20260528-豆包TTS-Android适配经验.md`）。

如需恢复本地 SDK，将厂商包解压到 `archive/` 下即可，勿放回项目根目录。
