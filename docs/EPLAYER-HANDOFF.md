# ePlayer 转移交接

更新：2026-10-24。主线为 [projects/eplayer](../projects/eplayer/)，原独立参考版为 [projects/witch-player](../projects/witch-player/)。原 ePlayer 工作树最后提交 `d196c0d`；迁移后的后续工作以本仓库提交为准。

## 每日云端分支：播放页适配（2026-10-25 北京时间）

本轮读取并同步到基线 `888f1a4ad397213115ebabf39a33bf1705854367`，分支 `agent/eplayer-20261025-playback-layout`。按协作约定继续代码、构建和模拟器自动检查；没有连接或操作本地 agent 的实机。

- 播放呈现从首页拆到 `app/PlayerScreen.kt`。竖屏保持操作区可达，横屏宽度足够时使用插画/操作双栏；长元数据和大字体可滚动，曲名与人物使用独立区域。队列、歌词、收藏、切歌等回调仍接到原 ViewModel。
- 新增四种 Compose 布局检查：竖/横屏、正常/2 倍字体，核对元数据与人物不重叠、操作可达及回调；导出的是原生组件渲染，不是实机或音质验收。
- `eplayer-preview` 增加 Android 16 加速模拟器检查阶段，使用普通 release 和对应测试包，自动记录源码提交、APK 校验值、结果和组件图。不打包自用模块、不登录真实账号，不把 CI Artifacts 当作获准发布的正式 Release。
- 本地 StandardDebug 主程序和测试包编译通过；目录检查、9 项目录规则、17 项 Android Cloud 控制器测试通过。新增设备检查尚待 CI 实际执行，未标通过。
- 核心能力和迁移缺口见 `projects/eplayer/docs/CORE-BOUNDARIES.md`。DSP 编辑、定时停止、淡入淡出等并未全部恢复入口，后续逐项接通。既有 0.1.1 公开发布被拒绝的记录保持原状态，本轮不重新公开上传该交付。

第二阶段已接入统一的 `core/playback/PlaybackCommands.kt`：按播放请求而非仅按 `isPlaying` 切换，支持缓冲/焦点抑制期间暂停以及曲终重播，首页、播放页和通知入口共用。五项 JVM 单元检查通过，本地 StandardDebug 与测试包再次编译通过。动画关闭后也复用修复后的同一原生底图；播放请求控制眼睑，实际音频状态控制摇摆，避免缓冲时反复睁眼。新增静态/动态底图像素与缓冲姿态检查，尚待设备执行。

第一轮 [CI 40120164035](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40120164035) 构建成功，但 R8 release 目标剔除的 Kotlin 类导致测试运行器启动失败，用例没有实际执行。流程调整为非调试 fastRelease 运行完整 Instrumentation，压缩 release 另做冷启动 smoke；不为测试框架扩大生产 keep 规则，也不把两者合称 R8 全功能验收通过。

第二轮 [CI 40121564864](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40121564864) 在 Android 16 x86_64 模拟器实际执行 24 项，其中 23 项通过；唯一失败为 `SourceLoginLiveTest` 的 JUnit 方法返回非 void，导致初始化失败，联网检查并未执行。已将该方法显式声明为 Unit，普通版默认仍按条件跳过真实模块登录。该轮因测试组未全通过，没有执行后续 R8 冷启动 smoke。

已取得并人工查看该轮普通字号播放截图、横屏组件图、2 倍字体组件图，确认曲名与插画分区、操作区可达；大字体竖屏的插画可通过滚动完整查看。快照与 build.json 见该运行的 `eplayer-device-checks-59293143cd76368237c381ee6e3155a9c609c2b2` Artifact。它们是模拟器/组件证据，不是实机音质或全页面验收。

第三阶段增加系统 DownloadManager 回环测试：404 失败记录、重试、可播放文件导入、移除系统任务后私有副本完整性、取消不重复入库。测试音复用仅存在于 androidTest 的合成 WAV 生成器。完整播放测试改为检查新队列存储，已有队列或用户默认歌单录音时跳过，避免覆盖用户数据。以上新增检查已编译，待新一轮 CI 执行。

第三阶段 [CI 40122737899](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40122737899) 已完成：25 项中 24 通过、1 项真实模块登录按条件跳过，系统下载流程通过；R8 release 冷启动 smoke 通过。结果、APK SHA256、截图路径与证据入口见 [本轮验证索引](../projects/eplayer/docs/VALIDATION-2026-10-25.md)，不要把条件跳过或模拟器结果写成实机通过。

第四阶段：根据实际组件截图，将进度条改成薄墨线和圆点，保留原生 Slider 的触控与无障碍语义。设置拆到独立文件并补定时停止：15–120 分钟、调整、取消，由播放服务持有；退出页面不影响倒计时，服务销毁会清除，不假称跨进程恢复。新增 4 项计时单元检查通过（本地 JVM 共 9 项），StandardDebug 与 AndroidTest 编译通过；新增后台到期暂停、无障碍 seek 和设置选择/取消仍待设备运行。返回播放器检查改为发送实际 MediaSession 的 sessionActivity PendingIntent，而不是直接拼 Activity Intent；尚未验证通知面板点击或实机。

第五阶段已实现固定 Player 的 Rust 焦点所有权，切换与销毁释放旧请求并屏蔽旧回调；定时和 noisy 暂停不被后续焦点 gain 自动恢复。重复临时失焦保留原请求；系统引擎仍由 ExoPlayer 管焦点。本地 JVM 共 13 项通过，APK/测试包构建通过。新增真实 Rust 播放/暂停/拖动、切回系统引擎及定时保留检查，设备结果待对应 CI；物理焦点路由仍未执行。

第六阶段将 WitchPlayerView 精简为纯画布：移除六个隐藏按钮/滑块、音量管理器、旧曲名/控制绘制与整套回调，Compose 保持唯一交互入口。不再解码旧参考图和字体，保留人物/皮肤/眼睑/动作算法与许可；本地编译通过，像素/布局设备回归待新 CI。检查另发现 Rust 的 setMediaItems 忽略 startPositionMs，断点保持尚未修复，列为下一项，不能宣称完整队列恢复已通过。

下一步：运行上述第四至第六阶段设备检查并审查墨线进度条截图。之后恢复 DSP 编辑入口、梳理焦点所有权和淡入淡出边界。实机音频焦点、耳机路由、长时间稳定性仍由本地 agent 验证。合入前再次拉取远端，保留对方新增提交。


第四阶段已复核：[CI 40123797367](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40123797367)，源码 `37ad87f9336d9710802a52762d311f1c61b8f949`。26 项中 25 通过、1 项真实模块登录按条件跳过，51.685 秒；后台定时到期/取消、Session PendingIntent 返回、设置交互和无障碍进度均通过。R8 冷启动 smoke 通过，TotalTime 1835 ms。已实际查看播放中的新墨线进度条、横屏 2 倍字体与定时控件图。截图与 build.json 见 [设备证据](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40123797367/artifacts/12572094416)，ZIP SHA256 `474b2fa7180a10afa5a99d4c582b6aa6931eaae84200cb4458a37fe7eb7cccc3`。fastRelease APK SHA256 `39973e9e1f71207bb70d49c79a2514c6e8e617f4167d06cf1ebf9e823ae10c56`，R8 APK `15a933c8368d4231e55d22ab5a1f84876a7975f3a8f443297a4c73f2ecf2c65d`。这是模拟器、组件与 PendingIntent 验证，通知面板真实点击和实机路由仍未执行。

第五阶段 `129a5ee2385660d55c05f75c938779af8c527b08` 与第六阶段 `74ac16fc663705239a617f027f0d815c1714af33` 均已推送；[联合 CI 40124903082](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40124903082) 运行中，焦点/真实 Rust 与纯画布回归不能提前标为通过。后续修复 Rust 起始进度并补真实通知面板点击，单独记录结果。

## 0.1.1 初步可用预览（2026-10-24）

源码提交 `cd595a91ba11810c00f5a035af2f28cc1cd8e344`，versionCode 12。无音乐源时首页直接提供默认歌单导入与本地曲库，曲库默认打开手机音乐，不再自动跳转所有文件访问设置。按用户最新要求删除冗余副标题和说明，使用布局及导入、更换等动作表达流程。

[预览构建](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40091056999) 产出通用 APK 与测试包。交付通用包经固定本地调试密钥重签，安装到带硬件加速的 Android 16 / API 36 x86_64 模拟器，全部 9 项 Instrumentation 检查通过：真实 WAV 导入与无效文件拒绝、MediaSession 播放/暂停/拖动、上一首/下一首、后台进度推进和返回播放器，以及 PCM、原生库、音乐源协议与插画检查。另通过系统文件选择器实际导入 WAV；仅授予普通音频权限后扫描到系统 Music 中的 PreviewTone，并从曲库点击播放，MediaSession 显示 PLAYING 且无错误，30 秒曲目可正常结束及重新播放。检查精简后的首页与播放截图。测试音频由本轮生成，未包含歌曲录音。

通用包 `ePlayer-v0.1.1-preview.apk` 为 97,796,111 字节，SHA256 为 `b8b13e24885dce0f7f905151b8eeb96dc120f851332fef5fa751da764d667cc1`，含三个 ABI 的 JNI，不含测试 WAV/MP3，签名验证通过。签名证书 SHA256 为 `ec9641dae05841b8fff31a61b8e5be689adbae66a5b547434ab740398e237a53`。本地签名私钥未上传；新签名不能覆盖旧 0.1.0 签名安装，应先保存需要的数据再处理旧安装。

APK、说明、SHA256SUMS 与仅含测试结果/截图的验证归档已在本地准备好。公开 Release 上传被自动审批拒绝，正在等待用户对具体文件公开发布的明确授权；尚未创建或发布 0.1.1 Release，不要把 CI 原始签名 APK 当作上述重签交付包。

本轮没有连接实机，物理耳机路由、音频焦点打断、真实在线源/账号、手机音质、横屏与长时间稳定性仍待验证。此前“最终验收未完成”的记录是迁移时的历史状态；本轮只补齐上述模拟器检查，不代表所有最终验收通过。普通较长曲名仍可能被插画遮挡，下一轮同时处理曲名区域。继续实机基线、CPPlayer 核心边界与其余页面重构。

## 先读与最新授权

1. 阅读根目录 AGENTS.md、catalog.json、本页和 [开发计划](../projects/eplayer/docs/DEVELOPMENT-PLAN.md)。
2. 用户最新要求：继续开发和适配，尽可能只保留 CPPlayer 核心代码；外观和使用逻辑全面重构，自行规划，所有阶段成果保存到本 GitHub 仓库。
3. 保留完整音乐能力、去除 USB 独占；保留默认双曲歌单和音频导入，不附录音。角色、字体和动作要求见计划。不要修改无关工具。
4. 希望每次在没有使用限额且平台仍允许执行时持续推进，达到运行时限或硬阻塞则先保存断点；不能保证平台提供无限单次运行。

## 本地实机接手与每日 agent 协作（2026-10-24）

本轮已接收并核对迁移基线 `ee53637fca4d7e8b49236d642e9b51a04b479db9`、开发计划、集成说明及现有 Android 测试入口。用户再次明确：参考 CPPlayer 重构，只保留核心代码，主题全面采用《魔女之旅》，先完成交接。本轮只完成接手与协作约定，没有修改应用功能，也没有新增播放器验收通过项。

- 本地 agent 是唯一负责实机安装、交互、音频路由、后台行为和视觉验收的 agent。每日北京时间 02:00 的 agent 负责可独立推进的代码、构建和自动检查，将待测提交与操作步骤留在本页。本约定来自用户本轮授权；尚未收到每日 agent 的回复。
- 每轮开始先同步远端、检查工作区与本页。并行工作使用不同分支，提交只包含本轮负责的文件；合入前核对对方新增提交，不覆盖未提交更改，不强制推送。
- 实机回报必须关联完整源码提交、APK 的 SHA256、versionCode、签名是否兼容、设备型号与 Android 版本、操作步骤和实际结果。截图、日志和 APK 放 Release，源码交接只保存结果与链接。未运行、受阻、失败、通过分别记录。
- 云端可以运行构建、静态检查及具备条件的模拟器检查，但这些结果不能替代实机验收。共享待测项优先级为：真实文件导入与完整播放；暂停、拖动、切歌；后台进度、通知返回与音频焦点；耳机路由；横竖屏及全页主题。
- 本轮仅执行 ADB 连接清单检查，未发现连接设备。因此没有安装、卸载或运行应用，没有验证音质、播放稳定性及实际 UI。设备连接后由本地 agent 接续基线验收。
- 重构前先梳理播放、MediaSession、音乐源、曲库、下载、队列和歌词依赖，逐块替换上游 UI 与使用流程；避免在基线未确认前批量删除。保留许可、JNI 兼容、默认歌单和导入能力，不把旧独立版的验证当作 ePlayer 验证。

每日任务的创建与启用状态沿用前任记录，本轮未读取调度器实时状态，也未重复创建或修改任务。每日 agent 下次读取本页后，应在此回写所用分支、提交、检查结果、待实机步骤及下一断点。

本轮交接文档检查：目录检查与 9 项目录规则测试通过，暂存差异格式检查通过。Android Cloud 控制器测试在 Windows 导入阶段因缺少 POSIX `fcntl` 未能运行，不记作通过；本轮未改该工具。

## 已迁移成果

已接入 CPPlayer，独立包名 `app.eplayer.music`、名称 ePlayer；`cp.player` 保留为源码/JNI 命名空间。曲库、搜索、音乐源、歌词、评论、下载、队列、双引擎仍保留；USB 独占管理器、设置、过滤器及默认 uac2 编译已移除，普通 Android 音频路由保留。

界面采用纸白、蓝天、墨线与文楷 Medium。伊蕾娜共用同一人物底图，只在眼睛区域改变眼睑；连续动作使用阻尼，避免换脸时改变头发、帽子、手和肤色。已修复 Media3 空缓冲区自复制导致的播放异常，限制实时 PCM 采样开销；本地 file/content URI 绕过网络缓存；替换录音会使播放与节奏缓存失效。

分阶段曾通过：三项插画检查、三项 PCM 检查、原生引擎初始化且无 USB 独占、音乐源 ZIP/中文搜索/地址映射。**最终完整播放、暂停/拖动、后台续播、通知返回和整页视觉验收尚未取得完整通过结果。** 旧轮次存在超时与缓冲，最终非调试测试在用户要求收尾时手动停止；日志中的 Process crashed 不能据此当作自然崩溃。不要宣称九项最终验收全部通过。

## 安装包、原生库、历史与证据

- [ePlayer 0.1.0 测试交付与证据](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.1.0)：约 90 MiB 的非调试、未 R8 缩小通用 APK，原工作树源码 ZIP、三 ABI JNI、开发补丁历史、验证归档、阶段说明和 SHA256SUMS。
- [Witch Player 1.8 及历史归档](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/witch-player-v1.8.0)：独立版 APK、此前各版产物、动作预览、原始参考图和非密钥诊断素材。

历史源码压缩包中的本地签名材料和编译目录在上传时剔除，因此可能与原本地 ZIP 校验值不同；以各 Release 的 SHA256SUMS 为准。未上传 SDK、AVD、机器身份、环境凭据或签名私钥。上传包不包含用户歌曲录音。

## 恢复开发

需要 JDK 21、Android SDK Platform 37.0、Build Tools 36.0.0；targetSdk 为 36，minSdk 为 28。Rust 完整构建需要 NDK、三个 Android targets、cargo-ndk 与 CMake。

```sh
git clone https://github.com/SturgeonIsSleepy/codex-source.git
cd codex-source/projects/eplayer
./tools/prepare-local-signing.sh
# 用 ANDROID_HOME、ANDROID_SDK_ROOT 或未提交的 local.properties 指定 SDK
./gradlew assembleFastRelease
```

若先复用已验证能加载的 JNI，可下载 `eplayer-v0.1.0-native-libs.tar.gz` 并在项目根解压（内含 app/src/main/jniLibs），然后加 `-PwithRust=false`。独立源码 ZIP 不应替代本仓库最新主线。

本地密钥重新生成后不能覆盖旧签名安装包，需自行使用同一原签名或在测试设备卸载旧包；不要上传密钥。迁移后 versionCode 下限为已发布预览的 6，避免 monorepo 较少提交造成降级。

Android 16 软件 TCG 模拟器非常慢；测试与大构建不要并行，不要反复强制完整 speed 预编译。区分模拟器超时、真实异常和人工终止。Android Cloud setup 按原约定后台启动；需要实际 UI 验收时才显式进入测试阶段。历史环境中的固定路径、端口和 timeout 属性不能直接当作新环境配置。

## 自动开发任务

已创建并启用「持续开发 ePlayer」自动任务，每天北京时间 02:00（Asia/Shanghai），首次为 2026-10-25 02:00。用户已清理旧任务名额，先前 5/5 限额阻塞已解除。

任务要求自主规划、实际开发适配、尽量保留 CPPlayer 核心、全面重构界面和使用流程、分阶段验证并持续推送本仓库。没有使用限额且平台仍允许执行时持续推进；单次执行仍受平台时长、资源和权限限制，受限时保存可恢复断点。不要重复创建同名任务。

## 本次迁移验证

已执行目录检查、9 项目录规则测试、17 项 Android Cloud 控制器测试、构建脚本语法检查和独立版 MotionCheck。迁移未重新进行 Android UI 验收，测试 APK仍对应原工作树 d196c0d；源码迁移仅涉及签名准备、构建版本下限、目录和交接说明等调整。

## 转移完成确认

2026-10-24 已完成转移：ePlayer Release 共 8 个附件，Witch Player 历史 Release 共 41 个附件，49 个附件逐一核对远端大小、uploaded 状态和 SHA256 摘要一致。源码、素材、开发补丁、JNI、安装包、历史打包、参考输入和经过凭据清理的诊断归档均已保存。

[发布归档工作流](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40062807892) 与同提交的 Repository checks 均成功。`.github/handoff-assets.json` 记录校验清单；临时传输对象没有进入源码提交树。两个 Release 已发布，无需再次执行转存。

每日自动开发已启用；本次仅完成迁移与任务设置，没有把未完成的播放器功能验收改写为通过。下一轮直接从开发计划和真实验收断点继续。
