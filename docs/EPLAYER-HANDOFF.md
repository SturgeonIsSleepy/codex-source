# ePlayer 转移交接

每日云端开发正在 [PR #1](https://github.com/SturgeonIsSleepy/codex-source/pull/1) 的 `agent/eplayer-20261025-playback-layout` 分支进行。继续前先读[该分支最新交接](https://github.com/SturgeonIsSleepy/codex-source/blob/agent/eplayer-20261025-playback-layout/docs/EPLAYER-HANDOFF.md)、[计划](https://github.com/SturgeonIsSleepy/codex-source/blob/agent/eplayer-20261025-playback-layout/projects/eplayer/docs/DEVELOPMENT-PLAN.md)和[证据索引](https://github.com/SturgeonIsSleepy/codex-source/blob/agent/eplayer-20261025-playback-layout/projects/eplayer/docs/VALIDATION-2026-10-25.md)，接续既有提交，不从旧迁移基线重做。本段仅登记入口，功能代码仍在草稿 PR，未合入主线。

截至 2026-10-25 北京时间，已推送自适应播放页、统一播放请求、共用静态/动态修复插画、系统下载回归、服务定时停止、固定 Player 焦点、纯插画画布。CI 40123797367 为 25 通过/1 条件跳过及 R8 启动 smoke 通过；后续 CI 40124903082 为 24 通过/1 失败/1 条件跳过，阻塞在真实 Rust 播放，不能当最终验收通过。最新源码 `ffad7b3b6c00259635c5c798ba4e16cef18ab6f2` 增加断点修复、真实通知点击、原生错误回传与诊断，设备结果待分支回写。实机、真实账号、网络远端断点、DSP/淡入淡出等缺口仍在；不覆盖本地 agent 的实机职责。

更新：2026-10-24。主线为 [projects/eplayer](../projects/eplayer/)，原独立参考版为 [projects/witch-player](../projects/witch-player/)。原 ePlayer 工作树最后提交 `d196c0d`；迁移后的后续工作以本仓库提交为准。

## 每日云端开发当前状态（2026-10-25 北京时间）

[草稿 PR #1](https://github.com/SturgeonIsSleepy/codex-source/pull/1)，分支 `agent/eplayer-20261025-playback-layout`；功能基线 `888f1a4`，最新功能提交 `ffad7b3b6c00259635c5c798ba4e16cef18ab6f2`。主线已登记入口并合回本分支，未把待验证功能合入 main，也未操作实机。下一轮先同步此分支和 main，保护本地 agent 的新增更改。

### 已实施

- 播放页与设置从首页拆出；横屏分栏，长曲名与 2 倍字体可滚动，插画和文字分区，操作保持可达。进度使用墨线/圆点，保留 Slider 拖动和无障碍操作。
- 统一页面/小组件的播放请求，缓冲期可暂停；眼睑按播放请求变化，摇摆按实际播放变化。关闭动画仍用同一修复底图，未重绘人物、皮肤、帽、发或手。
- WitchPlayerView 变为纯画布，移除六个隐藏控件、媒体音量管理器、旧曲名/控件绘制和回调；不再解码旧参考图或加载旧字体，Compose 保持唯一交互入口。
- 补回服务持有的定时停止，15–120 分钟、调整/取消；退出页面仍生效，服务销毁后清除。Rust 焦点绑定固定 Player，切换/销毁释放请求，定时/noisy 暂停不被旧 gain 回调恢复；ExoPlayer 使用自身焦点管理。
- Rust 记住待播放位置，启动 Play→Seek 与主要传输命令串行提交；暂停跨曲定位和删除当前曲不恢复旧解码，清空队列停止原生输出。非缓冲错误/JNI 拒绝回传 Media3；尚不能据此声称 Rust 真播放阻塞已修复。
- 新增系统 DownloadManager 失败/重试/私有副本/取消检查。破坏性播放检查要求空队列且默认歌单无用户录音；只使用 androidTest 合成 WAV，不上传用户录音或账号。
- 依赖/能力审计见 [CORE-BOUNDARIES.md](../projects/eplayer/docs/CORE-BOUNDARIES.md)。DSP 编辑、淡入淡出等入口仍有迁移缺口，未以“源码还在”代替功能保留验收；USB 独占移除状态和 Android Cloud 固定下载入口保持原样。

### 实际验证

| 验证 | 对应源码/运行 | 结果 |
| --- | --- | --- |
| 本地构建/JVM | 当前功能源码 ffad7b3 | StandardDebug 与测试包构建通过，13 项播放/定时/焦点 JVM 检查通过 |
| 目录/脚本 | 本地检查 | 目录规则检查与原 9 项通过；Android Cloud 控制器 17 项通过；新 6 项证据分类检查通过，脚本测试合计 15 项，不是 Android 播放验证 |
| 定时与进度 | 37ad87f / [CI 40123797367](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40123797367) | 25 通过、1 真实登录条件跳过；后台定时、设置交互、无障碍 seek、Session PendingIntent 返回通过；R8 冷启动 smoke 通过 |
| 纯画布/双引擎 | 74ac16f / [CI 40124903082](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40124903082) | 24 通过、1 失败、1 条件跳过；画布像素/动作与布局通过；等待 Rust 有效播放 90 秒失败，后续 R8 未执行 |
| 真通知/位置保持 | ed71614 / [CI 40125811298](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40125811298) | 24 通过、1 失败、1 条件跳过；系统通知卡片点击后返回展开播放器、切换后暂停位置保持 12 秒通过；Rust 有效播放仍失败，原生实际断点/拖动/切回未执行 |
| 原生诊断/错误回传 | ffad7b3 / [CI 40126620638](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40126620638) | 24 通过、1 失败、1 条件跳过；确认原生已解码至曲终，但 Media3 时长一直未知；R8 启动未执行 |
| Rust 时长/完整流程 | 0e87c8d / [CI 40219333532](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40219333532) | 24 通过、1 失败、1 跳过；Rust 播放、实际断点、幅度、暂停/拖动、曲终/重播等步骤通过；错误后未 prepare 的恢复检查等待失败，整体未通过；R8 未请求 |
| 双引擎完整软件流程 | 9e091f4 / [CI 40220536019](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40220536019) | 25 通过、1 真实登录条件跳过，52.265 秒；静音启动/恢复、错误恢复、切回系统引擎、真实通知返回均通过；R8 未请求 |

已实际查看首页、播放、新墨线进度条、横屏/2 倍字体、定时控件及系统通知面板图。APK SHA256、截图路径、失败证据、历史失败和各轮范围见 [验证索引](../projects/eplayer/docs/VALIDATION-2026-10-25.md)。Instrumentation 使用非调试 fastRelease；另行记录 R8 启动检查，不能当完整 R8 播放验收。云端模拟器不能替代实机、音质、耳机或真实账号验收。已实际核对 0.1.1 Release 已于 2026-10-24 发布，APK 摘要与历史记录一致；不重复上传。当前 CI 产物是中间证据。

### 具体断点与下一步

第十阶段 CI 40220536019 已完成，25 通过/1 条件跳过。Rust 的真实输出静音/恢复、有效时长、断点、暂停/拖动、曲终/重播、错误恢复、切回系统引擎、后台与真实通知返回均已走通；截图与 SHA256 见验证索引。功能源码为 `9e091f4aa559fc3ab831d7d87fb77038b564b034`。实机、音质、耳机/焦点和真实账号仍未验收，不能把软件模拟器结果扩大为这些通过。

准备由主线执行完整 R8 构建与启动检查，并以独立 delivery-check 验证最终上传 APK 的精确 SHA256、安装和冷启动；不在该检查中冒充完整音乐/实机验收。之后接续 DSP/声音入口迁移与全页流程。

开发分支构建普通/自用 fastRelease 并执行完整普通版功能检查；main、release/* 或手动触发另外构建 R8。build.json 区分通过、失败、超时、中断、未请求与未执行；六项分类回归通过。保留运行中的检查，后续源码排队，文档变更不触发完整 app 构建。先完成这轮，再继续 DSP/淡入淡出、网络未缓存断点、快速切换与全页流程；实机焦点/耳机/长时间稳定性仍由本地 agent 验证。

## 0.1.1 初步可用预览（2026-10-24）

源码提交 `cd595a91ba11810c00f5a035af2f28cc1cd8e344`，versionCode 12。无音乐源时首页直接提供默认歌单导入与本地曲库，曲库默认打开手机音乐，不再自动跳转所有文件访问设置。按用户最新要求删除冗余副标题和说明，使用布局及导入、更换等动作表达流程。

[预览构建](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40091056999) 产出通用 APK 与测试包。交付通用包经固定本地调试密钥重签，安装到带硬件加速的 Android 16 / API 36 x86_64 模拟器，全部 9 项 Instrumentation 检查通过：真实 WAV 导入与无效文件拒绝、MediaSession 播放/暂停/拖动、上一首/下一首、后台进度推进和返回播放器，以及 PCM、原生库、音乐源协议与插画检查。另通过系统文件选择器实际导入 WAV；仅授予普通音频权限后扫描到系统 Music 中的 PreviewTone，并从曲库点击播放，MediaSession 显示 PLAYING 且无错误，30 秒曲目可正常结束及重新播放。检查精简后的首页与播放截图。测试音频由本轮生成，未包含歌曲录音。

通用包 `ePlayer-v0.1.1-preview.apk` 为 97,796,111 字节，SHA256 为 `b8b13e24885dce0f7f905151b8eeb96dc120f851332fef5fa751da764d667cc1`，含三个 ABI 的 JNI，不含测试 WAV/MP3，签名验证通过。签名证书 SHA256 为 `ec9641dae05841b8fff31a61b8e5be689adbae66a5b547434ab740398e237a53`。本地签名私钥未上传；新签名不能覆盖旧 0.1.0 签名安装，应先保存需要的数据再处理旧安装。

历史交接曾记录公开上传被拒绝；本轮实际读取 GitHub 后确认 [0.1.1 Release](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.1.1) 已于 2026-10-24T12:51:39Z 发布，含 APK、说明、SHA256SUMS 和验证归档。远端 APK 摘要仍为 `b8b13e24885dce0f7f905151b8eeb96dc120f851332fef5fa751da764d667cc1`，无需重复上传；本轮没有发布或替换这些旧附件。

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
