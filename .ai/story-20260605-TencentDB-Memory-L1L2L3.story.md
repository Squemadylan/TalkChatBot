# Story: TencentDB Agent Memory 4 层 + 短时压缩

> Linked PRD: `.ai/prd/20260605-1800-v2.0-TencentDB-Memory.md`
> Created: 2026-06-05
> Status: Backlog
> Owner: Cursor Agent

## 目标

把「独白匣」现有一份「长久记忆 Markdown」替换为 TencentDB Agent Memory 的 4 层渐进式记忆 + Mermaid 短时画布。

## 验收故事（AC）

### AC1. L0 原始对话滚动缓存
- Given 用户在角色 A 聊天
- When 任意消息完成（用户 / 助手）
- Then `filesDir/memory/characters/<A>/l0_cache/<yyyy-MM-dd>.md` 追加一行
  `HH:mm:ss [user|assistant] <content>`，单日文件超 256KB 时滚动到次日
- And 当前对话流不被阻塞，I/O 在 Dispatchers.IO

### AC2. L1 原子事实抽取
- Given 角色 A 累计 ≥ 5 条未被抽取的对话
- When 某轮对话成功完成
- Then 后台异步调用 LLM 一次，输出严格 JSONL（每行一个 atom）
- And 写入 `atoms/atoms.jsonl`（追加）
- And 对每条 atom 计算本地 embedding，写入 `atoms/atoms_index.sqlite` (sqlite-vec)
- And 写入 `atoms/atoms_meta.json`：本次抽取范围的最后 messageId

### AC3. L2 场景聚类
- Given 角色 A 的 atoms 总数 ≥ 50（自上次聚类）
- When 触发 L2 聚类
- Then 调用 LLM 一次，输入：上次聚类后的新 atoms 摘要
- And 输出：新增 / 更新 / 合并 / 废弃的场景决策
- And 落盘为 `scenarios/<scenarioId>.md`（每个场景一个文件）
- And 在 atom 上回写 `scenarioId`

### AC4. L3 Persona 增量更新
- Given 任意角色 atoms 累计变化 ≥ 50（自上次 L3）
- When 触发 L3 更新
- Then 调用 LLM 一次，输入：每个角色 L2 摘要的最新变化
- And 输出两段：① 跨角色 global persona 增量 ② 各角色专属 persona 增量
- And 落盘到 `persona_global.md` 与 `characters/<id>/persona.md`
- And 保留 `drillDown:` 字段引用回 L2 id

### AC5. Short-term Mermaid Canvas
- Given 角色 A 每次对话成功完成
- When 更新画布
- Then `short_term_canvas.md` 追加一个 graph 节点（node_id 形如 `n<msgId>`），含「时间」+「用户首句前 24 字」
- And 若 `ref` 决策触发（已用上下文 token 比例 ≥ mildOffloadRatio）→ 把整段对话外置到 `refs/<yyyy-MM-dd>.md`，节点只保留 `node_id` + 「已外置」
- And 当比例 ≥ aggressiveCompressRatio → 进一步把节点标题化（仅保留时间 + node_id）

### AC6. 召回 & Prompt 注入
- Given 即将调用大模型回复
- When 构造 system 消息
- Then `PromptBuilder` 按顺序拼：① L3 global + character persona ② L2 Top-2 命中场景摘要 ③ L1 Top-K（默认 8）原子（按 BM25+vec RRF） ④ Mermaid canvas 文本
- And 单条 L1 文本超 `maxCharsPerMemory` 截断；总和超 `maxTotalRecallChars` 取 RRF 分高者
- And 注入失败 / 异常一律静默降级到「不注入」，绝不阻塞发消息

### AC7. 设置页 / 角色编辑页 UI
- Given 用户进入设置页「记忆设置」卡片
- Then 可看到「4 层记忆」开关、「画布外置阈值」滑条、「embedding 状态」只读文本、「查看/导出记忆」按钮
- Given 用户进入角色编辑页并展开「长期记忆」
- Then 可看到「记忆层级状态」副标题（最近 L1 / L2 / L3 时间）+「重置该角色记忆」按钮（带二次确认）

### AC8. 旧数据迁移
- Given `filesDir/long_term_memory/memory_<id>.md` 存在
- When 应用启动 1 次
- Then 自动迁移到 `memory/characters/<id>/persona.md`（旧内容作为 L3 摘要初始值）
- And 删除旧文件 + 旧 SharedPreferences 中相关 key
- And 迁移失败回退到「保留旧文件、跳过新管线、不阻塞主流程」

### AC9. 备份恢复兼容
- Given 备份 zip v3（旧）
- When 用户恢复
- Then 按旧逻辑恢复角色 + `memory_<id>.md` → 立即触发一次 AC8 迁移
- Given 备份 zip v4（新）
- When 用户恢复
- Then 完整还原 `memory/` 目录树，并保留各层元数据

### AC10. 性能
- 发送消息前 `PromptBuilder.build` 在本地 50ms 以内完成（200 个 atoms + 20 个 scenarios 量级）
- L1 抽取、画布更新、备份均不阻塞聊天 UI
- Debug APK 启动到设置页 < 5s

## 任务拆分

1. **架构与目录**：`memory/MemoryPaths`、`memory/MemoryConfig`、`memory/MemoryPipeline` 骨架。
2. **存储与迁移**：L0 滚动文件 + 旧数据迁移。
3. **embedder + vec index**：ONNX Runtime 接入 + sqlite-vec AAR。
4. **L1 抽取**：prompt + JSONL 解析 + 写 atoms + 写 vec。
5. **L2 聚类**：prompt + 场景决策 + scenarios/*.md 维护。
6. **L3 Persona**：跨角色 + 角色级；drill-down 引用。
7. **Mermaid 画布**：节点生成、外置决策、refs 写入。
8. **PromptBuilder + MemoryRetriever**：BM25 + vec RRF 融合。
9. **ChatViewModel 接入**：替换 LongTermMemoryManager 调用。
10. **UI**：设置页 / 角色编辑页 / 记忆查看页（只读）。
11. **备份恢复**：版本升 4；目录打包。
12. **构建 + 冒烟**：打 Debug APK，跑基础流程。

## 不在范围

- 自动 Skill 抽取
- 远端 embedding API
- Mermaid 画布的可视化渲染
- 跨设备实时同步

## Definition of Done

- 全部 AC 勾选；PR 通过 `assembleDebug`；本地真机 / 模拟器跑过 6 轮对话后能看到 L1 atoms、50 轮后能看到 L2 scenarios、画布随轮数增加而变长。
- `.ai/prd/20260605-1800-v2.0-TencentDB-Memory.md` 引用至 `xnotes/`
- README 同步更新 v2.0 changelog。
