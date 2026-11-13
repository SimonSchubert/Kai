# Kai Next

<img src="https://img.shields.io/badge/Platform-Android-34a853.svg?logo=android" alt="Android" />
<img src="https://img.shields.io/badge/Package-com.kainext.duo-blue.svg" alt="Package" />

面向 Android 的开源 AI 助手，具备持久记忆与深度设备控制能力。  
源自 [Kai](https://github.com/SimonSchubert/Kai) 的 Fork，已重构为仅面向 Android 的应用。

包名：`com.kainext.duo`

---

## 本 Fork 与上游的不同

| 维度 | 变更说明 |
|------|----------|
| **设备控制** | 提供 root、Shizuku、无障碍服务三种特权后端，支持 `/sdcard` 访问与 UI 自动化。点击操作基于实时节点树解析，而非猜测坐标。 |
| **多智能体协同 (Swarm)** | Planner 将请求拆解为步骤；UI / 系统 / 研究等专项 Worker 并发执行，并通过黑板共享结果。 |
| **动态 UI 树切片** | 在本地对无障碍树进行剪枝，只保留关键节点，将单屏的 token 消耗降低约一个数量级。 |
| **自动化脚本录制 + 技能沉淀** | 手动操作一次，系统将点击抽象为语义化步骤，保存为可复用的技能，并注入到 Prompt 中供 AI 调用。 |
| **自我反省 / 自动修正** | 命令执行失败时，将错误、退出码与之前尝试返回给模型，模型据此修正并重试，最多 3 次。 |
| **流式输出** | 响应逐 token 流式返回，渲染为 Markdown 卡片；Shell 命令获得独立的内嵌终端卡片。 |
| **用量遥测** | 作曲栏下方的统计条显示轮次、步数、总 Token、Tok/s、缓存命中率及上下文窗口使用比例。 |
| **思考强度** | 可在聊天栏的模型选择器中调节每模型的思考强度。 |
| **目标与计划** | 带步骤计划、进度追踪与截止日期的目标，已注入系统 Prompt。 |
| **国际化 (i18n)** | 设备控制字符串随应用语言切换，包含中文。 |
| **浮窗控制窗口** | 助手控制手机时，屏幕上显示一个可拖拽的浮窗，展示当前正在做什么，支持向模型发送消息，并提供停止按钮。在 **设置 → 设备控制 → 浮窗控制窗口** 开启（需先授予“显示在其他应用上层”权限）。 |

---

## 从源码构建

```bash
git clone <此仓库>
cd Kai-Next
./gradlew :androidApp:assembleDebug
```

需要 **JDK 21** 与 **Android SDK 37**。APK 输出至  
`androidApp/build/outputs/apk/debug/`。

详细构建说明与故障排查请参阅 [`编译提示.md`](编译提示.md)（由于维护者机器内存不足，未在此处执行 Gradle 编译；所有更改均为源码层面并已做语法检查）。

---

## 设备控制设置

设备控制默认关闭。在 **设置 → 设备控制** 中开启，再选择特权后端：

| 后端 | 提供的能力 | 如何设置 |
|------|-----------|----------|
| **无障碍服务** | 读取实时屏幕并分发点击、滑动、文本输入。无需 root，点击落在预期控件上。 | 设置 → 设备控制 → 无障碍 → 打开系统设置并启用 Kai Next |
| **Shizuku** | 作为 `shell` 用户的完整 shell（adb 权限）—— 文件访问、包管理、`input`。 | 安装 [Shizuku](https://shizuku.rikka.app/)，启动后授予 Kai Next 权限 |
| **Root** | 不受限制的 shell。 | 向 Kai Next 授予 `su` 访问权限 |

**自动** 模式优先尝试 root，然后是 Shizuku，最后是无障碍服务。工具结果中会报告实际使用的后端，你始终能知道到底是什么在运行。

---

## 浮窗控制窗口

当助手正在控制设备时，你可以打开一个小浮窗，它始终置于其他应用之上：

- **显示内容**：当前动作（例如 `tap_node` → “设置”）、带成功/失败标记的工具调用滚动日志。
- **消息输入框**：助手工作时可在浮窗中输入消息；消息会排队，并在当前任务结束后作为下一轮发送。
- **停止按钮**：立即中断正在运行的助手。停止后输入的消息会作为下一轮发出。

使用步骤：**设置 → 设备控制 → 浮窗控制窗口 → 授予权限 → 开启**。  
浮窗可拖拽（抓住标题栏），隐藏或停止服务后会消失。

---

## 仅限 Android

本 Fork 刻意移除了上游项目中所有非 Android 部分（桌面、iOS、Web、仅 JVM 模块）。模块结构如下：

- `composeApp` — 共享 Kotlin Multiplatform 源码（`commonMain`、`androidMain`、`commonTest`）
- `androidApp` — Android 应用模块，包名 `com.kainext.duo`

不存在 `iosMain`、`desktopMain` 或 `wasmJsMain` 源集。

---

## 项目结构

```
composeApp/src/commonMain/kotlin/com/kainext/duo/
├── overlay/               # 浮窗控制窗口状态模型 + 控制器
├── agents/                # 多智能体协同：规划器、工作者、黑板、编排器
├── skills/recording/      # 动作录制、技能抽象、技能 Prompt 注入
├── tools/reflection/      # 自我反省 / 自动修正：重试 + 错误提示词
├── tools/uitree/          # UI 树切片与无障碍盲操
├── ui/chat/               # 聊天界面、统计条、思考强度选择器
├── ui/settings/           # 设置（含设备控制与浮窗）
└── data/                  # DataRepository、目标、计划、Token 用量

composeApp/src/androidMain/kotlin/com/kainext/duo/
├── overlay/KaiOverlayService.kt   # 承载浮窗的前台服务
├── tools/uitree/                  # UI 剪枝、动作执行器、shell 回退
└── ...                            # 平台特定实现
```

---

## 许可证

见 [LICENSE.txt](LICENSE.txt)。上游版权归 Kai 作者所有；本 Fork 的修改在相同条款下发布。

---

## 贡献

这是个人 Fork。欢迎通过 Issue 提交问题与功能建议。  
由于项目仅面向 Android，请在真机或模拟器上测试更改后再提交——因内存限制，维护者无法在本机执行 Gradle 构建。
