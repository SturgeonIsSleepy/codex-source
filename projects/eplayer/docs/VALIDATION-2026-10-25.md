# 2026-10-25 北京时间：云端验证索引

本页记录 Android 16 硬件加速模拟器检查，不能替代实机、音质、耳机或真实账号验收。源码工作分支为 `agent/eplayer-20261025-playback-layout`，汇总见 [PR #1](https://github.com/SturgeonIsSleepy/codex-source/pull/1)。

## 已完成的第三阶段

源码 `5a6304f71f5f5d17480a142b7fae86dd082cb4fe`，[CI 40122737899](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40122737899)。构建成功；Instrumentation 用时 42.126 秒，25 项中 **24 项通过、1 项条件跳过**。跳过项为普通版的真实模块登录校验，没有联网登录成功证据。

通过范围：合成 WAV 导入、无效文件拒绝、替换音频缓存失效、MediaSession 播放/暂停/拖动/切歌、后台进度、原有返回播放器动作、PCM/原生库/源协议、四种布局与缓冲暂停、共用修复插画与眼睑像素/动作检查、系统 DownloadManager 的失败重试/取消及私有音频副本保留。测试音仅存在于 androidTest。

压缩 release 另行冷启动 `Status: ok`，COLD，TotalTime 1906 ms，3 秒后进程仍在；只证明冷启动 smoke，不是完整 R8 播放验收。测试运行于 `sdk_gphone64_x86_64`、API 36。

| 产物 | SHA256 |
| --- | --- |
| fastRelease x86_64 APK | `43ba076cbb8055d9c72add2067548b849fede5d07572e590da392173e27ef92a` |
| R8 release x86_64 APK | `4829e16fe6f316b732216abf5f21a7d7fa87f1a185bb834159fbbe4bc0c3cf41` |
| 测试 APK | `b4459a2911503919647d3fcd693527c0443d5eea157a7aac1c237c7081e446e0` |

## 证据入口与人工查看

- [设备证据 Artifact](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40122737899/artifacts/12571508933)：ZIP SHA256 `0e64a7d06eeb30523f3b18a6736703ef29ed67abab086d764a3760ad72a2cc00`。保留 build.json、instrumentation.txt、release-launch.txt、runtime.txt、release-home.png，以及 visual-check、layout-check、art-check。
- [中间构建 Artifact](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40122737899/artifacts/12571896935)：普通/自用构建与对应测试 APK；不是最终交付包，不包含私钥。CI Artifact 有保留期，不能当永久 Release。
- 已实际查看第二阶段的 `visual-check/player-playing.png`、`layout-check/landscape-1.0.png`、`layout-check/portrait-2.0.png`，第三阶段的 `release-home.png`；确认文字与插画分区、操作区可达，首页显示默认双曲导入。第二阶段证据见 CI 40121564864。
- 旧的 Material 进度轨道较粗，与插画风格不协调，第四阶段改为墨线与圆点，尚待新截图复核。没有为此重绘角色或改变头发/手/皮肤图层。

## 第四阶段已完成

源码 `37ad87f9336d9710802a52762d311f1c61b8f949`，[CI 40123797367](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40123797367)：26 项中 25 通过、真实登录 1 项条件跳过，51.685 秒。后台定时到期与取消、Session PendingIntent 返回、设置交互、无障碍 seek 通过，R8 冷启动 smoke 通过（1835 ms）。已查看 `visual-check/player-playing.png`、`layout-check/landscape-2.0.png`、`layout-check/sleep-timer.png`，墨线/圆点和大字体操作正常。

[设备证据](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40123797367/artifacts/12572094416)，ZIP SHA256 `474b2fa7180a10afa5a99d4c582b6aa6931eaae84200cb4458a37fe7eb7cccc3`。fastRelease APK `39973e9e1f71207bb70d49c79a2514c6e8e617f4167d06cf1ebf9e823ae10c56`，R8 APK `15a933c8368d4231e55d22ab5a1f84876a7975f3a8f443297a4c73f2ecf2c65d`；测试 APK `936e2e1444ae04e91feb247060ee89759b9f98f9c25ebe252afe6e6de06ca57c`。仅证明上述模拟器范围，没有通知面板点击或实机证据。

## 第五/六阶段失败与通过范围

源码 `74ac16fc663705239a617f027f0d815c1714af33`，[CI 40124903082](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40124903082)：26 项中 **24 通过、1 失败、1 条件跳过**，117.399 秒。纯画布的像素/动作、横竖屏布局等用例通过，已查看本轮播放截图，人物资源与墨线进度条保持一致。

唯一失败为完整集成流程第 200 行等待 Rust `isPlaying`、有效 duration 和当前曲目；90 秒内条件未满足。不是自然进程崩溃；没有获得真实 Rust 播放/拖动/切回的通过证据。现有 runtime.txt 仅采集 AndroidRuntime/EPlayerTest，缺失原生细节，不能从该日志推断具体根因。因为测试组失败，R8 冷启动 smoke 未执行。

[失败证据](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40124903082/artifacts/12572746847)，ZIP SHA256 `0fa2099257b76ce38021c40616268df0a4cdb3cda23edb1d9d890178ce07fe02`；fastRelease APK SHA256 `2af260784d9e87badbebc76b01d431988f45d5e608d03ac67fca886aedf202d8`。本轮所有 APK/日志仍是 CI 中间证据，不宣称正式交付。

第七阶段源码 `ed716144c091b022b1d3d6bff7bde0f8f363a8f3` 已推送，[CI 40125811298](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40125811298) 运行中。第八阶段补充原生/会话状态、仅限 Rust 初始化和传输的日志摘要、失败截图，以及 RustAudioEngine/FlickPlayer 等日志标签；不导出整个账号/模块日志缓冲。非缓冲原生错误与 JNI 拒绝通过 Media3 playerError 回传，重试/换曲清除旧错误。未取得新设备结果，不能把这些改动记作 Rust 阻塞已修复。

## 第七阶段：通知点击通过，Rust 仍失败

[CI 40125811298](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40125811298)，源码 `ed716144c091b022b1d3d6bff7bde0f8f363a8f3`：24 通过、1 失败、1 条件跳过，125.699 秒。系统通知面板实际点击歌曲卡片，随后展开播放器检查通过；切引擎后暂停状态保持 12 秒的位置检查也通过。后续第 218 行等待 Rust 有效播放仍失败，未执行原生真实断点读数、Rust 拖动和切回，未执行 R8 smoke。

[设备证据](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40125811298/artifacts/12572803104)，ZIP SHA256 `27774e4ca48cca27b3a640cadffff70e79c51282427ee35305fe7adbfa4a5ead`。已实际查看 `visual-check/notification-controls.png`，确认 Android 系统媒体卡片显示《灰色のサーガ》与播放器控制，非应用内伪造通知。

第八阶段源码 `ffad7b3b6c00259635c5c798ba4e16cef18ab6f2` 的原生错误/诊断在 CI 40126620638 运行中，尚无结果。

## 第八阶段：时长缺失已定位，修复待验收

[CI 40126620638](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40126620638)，源码 `ffad7b3b6c00259635c5c798ba4e16cef18ab6f2`：24 通过、1 失败、1 条件跳过。`visual-check/playback-diagnostics.txt` 记录 controller 已到 witch_1、position=44640、duration=TIME_UNSET；日志显示原生解码并完成两首测试音频。原生端没有 Progress 事件产生点，适配层时长只在事件处理器更新，原轮询只更新位置，导致有效时长检查始终不能通过。后续断点/错误恢复/通知步骤及 R8 启动没有通过证据。

[设备证据](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40126620638/artifacts/12572734586)，ZIP SHA256 `ca1a890e818cac7b712deed5f156e0d4fd8689ff0b79576ae104d6521124c744`。这是实际读取的诊断，不代表手机音质验收。

第九阶段统一事件和轮询的进度处理，更新时长/幅度/状态流，保留循环、随机、音量并修复曲终/重播；13 项 JVM、本地构建和 15 项根脚本检查通过，三 ABI 幅度符号存在，新增真实回归待 CI。开发分支 R8 标为未请求，main/release/*/手动触发仍要求构建与冷启动检查；未通过检查不会被改成跳过或通过。

## 第九阶段：Rust 主流程通过，恢复检查需 prepare

[CI 40219333532](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40219333532)，源码 `0e87c8d26af6e13d8afe9b311e32f9fcfde0b2d9`：26 项中 24 通过、1 失败、1 条件跳过，84.686 秒。同一集成流程中，真实 Rust 播放与有效时长、原生 12 秒断点、非零幅度、暂停/拖动、配置状态保持、暂停跨曲/移除、曲终稳定和重播的断言已通过。已实际查看 `player-rust-playing.png` 与 `player-rust-paused.png`，分别显示播放闭眼/暂停睁眼，时长 0:45、进度正常。

唯一失败落在第 291 行：故意触发无效原生输入后，仅换曲就等待 controller.playerError 清除，未执行实际界面重试使用的 prepare。错误传播和取消播放请求检查已通过；切回系统引擎与末尾通知检查因此未执行。第十阶段按 `EPlayerViewModel.play` 的真实调用顺序补 prepare，继续验证恢复。整体仍失败，不能称全部通过。R8 在开发分支为 not_requested，没有被当作通过。

[设备证据](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40219333532/artifacts/12608478206)，ZIP SHA256 `19b976b6d5ba1e9dec167e46bbdcf8487c17f264fe14a8c18c73ec58d83456de`。fastRelease x86_64 APK SHA256 `75700d040a13625a94e071c3d6590769009f816b6f128a267fe70fdf298e7495`；测试 APK `fb72462d08381ff145f45c81b3aebb3d270e361409bdc6cd4edbab28344fd988`。

第十阶段还处理懒加载原生输出：构造阶段的音量设置可能因引擎尚未创建被拒绝，启动后必须重新应用。新增“带零音量切入 Rust，连续读取真实渲染 RMS 为零，再恢复到 .65 并取得非零 RMS”的检查；验证真正输出而非仅检查界面值。编译与 JVM 检查通过，设备待新 CI。

## 第十阶段：完整软件流程通过

[CI 40220536019](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40220536019)，源码 `9e091f4aa559fc3ab831d7d87fb77038b564b034`：**25 通过、1 项真实模块登录条件跳过**，52.265 秒。真实 Rust 播放/有效时长/12 秒断点、渲染 PCM 静音与恢复、暂停/拖动、循环/随机/音量状态、暂停跨曲/移除、曲终稳定/重播、无效输入错误与 prepare 恢复、切回 Exo、后台及真实通知卡片返回均通过。已查看 `notification-return.png`；Native 播放/暂停图沿用同一人物与眼睑区域。

[设备证据](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40220536019/artifacts/12608754845)，ZIP SHA256 `a3f5fa0d8b989fb350c618efd76a785ffffa92726d7dd47047f88f8a762f5e59`。fastRelease x86_64 APK SHA256 `bcc0e73aee0b193d93e1a5c66a3d869207f1b87a2869c4ad73848de3a1b69453`；测试 APK `8dffca0066cedfe8d6d1b87cdb66b508a4672bc908d40dacf31302129580b773`。

本轮 R8 明确 not_requested。后续主线完整构建和独立精确交付 APK 安装/启动检查另记；实机、音质、耳机、电话/焦点打断、真实账号和网络远端断点没有通过证据。

## 尚未完成

第七/八阶段的 Rust 断点、错误回传和真实通知卡片点击待对应设备检查；本地 JVM 13 项及主程序/测试包构建通过。实机焦点、耳机路由、真实源账号、长时间播放、完整 R8 音乐功能和全页视觉均未验收。DSP 编辑和淡入淡出入口仍需迁移。

早先 CI 40120164035 因 R8 目标剔除测试共享 Kotlin 类，未执行用例；CI 40121564864 为 23 通过、1 初始化失败，不能把这些失败覆盖为全通过。实际核对旧 0.1.1 Release 已于 2026-10-24 发布，APK 摘要一致，本轮未重传。

## 声音阶段：真实 EQ（CI 40226093507）

源码 54ba908a218238812ee8d31598a2cae91accd7c5；[运行](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40226093507)。Android 16 普通 fastRelease 共 27 项：26 通过、1 真实登录条件跳过，54.383 秒。release_start 为 not_requested；不能把本阶段写成完整 R8 或实机验收。

新增 LiveEqualizerTest：真实原生合成 440Hz 播放，保存 EQ 设置后实时提升，关闭恢复，不暂停。原生幅度经 MusicPulse 可视归一化的读数为 baseline 0.13161638、boosted 0.18562597、disabled 0.13057631；这些是归一化幅度，并非声卡测得的声压或原始 RMS 数值。

设备证据 artifact 12610979058，ZIP SHA256 b7e39fd0f28eb009e8cb729a130de0719bfac410835f905eb15e65ee3b56d83d；instrumentation.txt、instrumentation-runtime.txt、build.json 与截图在同包。其后声音页 ed74b9b 正在 CI 40226536372，不以此轮 EQ 结果代替新页面验收。

## 0.3.0 精确交付

[Release](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.0)，源码 a62e0fb；原始 universal R8 APK SHA256 9106fbbee4154a4c2a4c4b877a4976d364d9e65ba514ff110b6272d69bb49d40，证书 8f89883030d7d64aecb6358b43eddeb8e9cd5ac323f8ab9916af4070186f08dc。CI 40226257524 精确安装/冷启动通过，TotalTime 1562ms。证据 artifact 12610789084 ZIP SHA256 06c1af550082f2d502613bab7a0553dead37030834b89894cb93bd9296b897a7；device-evidence/release-home.png 已实际查看，纸白/蓝天/双曲导入首页可见。对应交付截图也在 Release validation ZIP。

前一发布工作流 40225961951 的交付 smoke 通过，但发布阶段按未创建 tag 查询空草稿返回 404，整轮失败，未上传附件；修复为按草稿 ID 核对，第二轮成功。旧 CLI 401 未通过读取或上传凭据解决；改用仓库授权的 Actions GITHUB_TOKEN 正常发布 CI 产物，无 Git 二进制运输、无私钥上传。签名与旧 0.1.1 不兼容，后续稳定升级签名仍是已知缺口。

## 声音页（CI 40226536372）

源码 ed74b9bc8f4926fa481aa4f81121e68b329d37f6；[运行](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40226536372)。29 项共 28 通过/1 登录条件跳过，65.879 秒。新增大字体滑块可达、无障碍提交与复位、未提供系统音效禁用通过；真实 EQ 幅度回归仍通过。R8 not_requested。

artifact 12611378871，ZIP SHA256 c81d6c734989a4a6fb65e02f105e47b126c0ae4fe6f75c66a3b26fa252f0fa0b。已实际查看 layout-check/sound-large-text.png 与 sound-system-unavailable.png：前者是滚动到混响后的组件截图，标题可在视口外；并非完整固定顶栏设置页截图。整体设置导航与 AutoEQ 文件路径新增检查仍待下一轮，不混为已验收。

## 0.3.1 整页与 URI 应用链路（CI 40227528360）

源码 b34e679d5bfb33552b2d59fe3c77a03f967bfd7f，[运行](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40227528360)。30 项共 29 通过/1 登录条件跳过，73.918 秒。真实私有文本 URI 经导入预览（不写偏好，OFF 不加入）及应用层确认后改变原生实际渲染幅度，关闭后恢复：归一化幅度 0.13074373→0.1840731→0.1309964。此链路没有操作系统文件选择器或点击导入确认弹窗，这两处交互仍需单独验收。

实际首页→整页设置→声音页→设置→首页通过；已查看 layout-check/settings-full-page.png、sound-full-page.png，固定标题/返回操作、纸白字重及会话未就绪时禁用能力可见。既有播放、暂停/拖动、后台、真实系统通知返回、角色像素和布局回归通过。

设备 artifact 12612675467，ZIP SHA256 f8ed23c537602a5d1682e8cf956c2f50ff75b652a2518d7d89eeb1fa627a3358。预览 artifact 12611932023，ZIP SHA256 d68556075992fd1a8e9bdd8253c4139c1fbc319eb2d5db89c067fab08bcf2c04。R8 not_requested，实机/账号未执行。中间 8c69e39 的 CI 40227292073 被后续等待提交替换，cancelled，未执行设备回归；本轮已包含其测试。

补记 AutoEQ 首版 361dd59：CI 40226942307 为 28 通过/1 登录条件跳过，69.294 秒；artifact 12612065536 SHA256 6f664f894e4f7f9517d9445c6e302132ee7489e1e23f3c585828a88032297b27，仍无完整 URI 确认链路，已由本轮补齐应用层部分。

## 主线 0.3.1 失败：CI 40228396656

源码 a25a90b7a58977c974bc8d3652a0ffc5de542973；普通/自用 R8 构建完成，普通 fastRelease 设备检查 28 通过/1 失败/1 登录条件跳过，73.853 秒。LiveEqualizerTest 第 105 行等待 EQ 幅度提升失败；初始播放、URI 预览与应用调用已走到，不能因此说实际 EQ 已生效。失败后 R8 cold-start 未执行。

设备 artifact 12612042754 ZIP SHA256 5671b0778c9676d526841de70023c51d4b158900ef291310d805951f45f5b8da。已下载查看 instrumentation.txt/runtime.txt；旧测试缺少失败时幅度和播放状态数值，不能武断判为采样抖动或已证实的停止竞态。原生写命令顺序存在独立已确认风险，正在 agent/eplayer-031-native-order 修复并补诊断。

R8 候选 artifact 12612652207 ZIP SHA256 c3a462eeec8b5941318fba30662db8762f664d05ae24287191fd497d80be237b。原始 universal APK 0.3.1/code46，SHA256 5f60192c2ba51a4c46727ef3eda094a488a897fbd4a8b0d4055ebc6462ab1c0d，证书 9ad8248ac5d246d9da74b38858a40ba510b35b53c5370d66f055043687a8bdf4；签名/版本/三 ABI 已实际校验，但不发布该失败源码候选。发布清单必须继续固定已发布的 0.3.0。

## 原生顺序修复验证

第一轮源码 185870c2ee115a70ccb7d6def7a54aa1736e40cc，CI 40230148361，30 项共 29 通过/1 登录跳过，72.04 秒。第二轮含取消后事件过滤源码 2f6db3953057e0841503099f9660f66cecfae442，CI 40230802502，30 项共 29 通过/1 登录跳过，74.86 秒。三次快速系统/Rust 重建后，12 秒原生合成音频持续推进，稳定基线、原阈值提升与禁用恢复均通过；R8 not_requested，尚需主线 R8 构建/启动，不能覆盖 a25a90b 历史失败。

第一轮设备 artifact 12613520218 SHA256 2feb746687ea89002e90d72dd141230ed72c603f63dbbe4cd2205948995a391d；第二轮设备 artifact 12612982122 SHA256 13d8592bb3819e91d93762e19d07b3260c9a24994e91a5883267aa86b259bbc9。第二轮已下载核对 runtime/instrumentation 与 build.json。初始化、Play/Stop/释放共用队列，旧任务被取消；JNI 返回后取消检查阻止旧 poller 发送事件。原失败没有足够数值证据来唯一归因，修复同时解决已确认生命周期风险和未稳定采样的问题；不宣称已反向证实唯一根因。

## fb48458 主线仍失败（CI 40374694929）

普通/自用 R8 构建通过；设备 30 项为 28 通过/1 失败/1 登录条件跳过，90.308 秒；R8 启动未执行。LiveEqualizerTest 在真实 EQ 操作前的基线阶段失败，诊断为 playing=false/state1/position0/duration12000/error=null/levelNaN。设备 artifact 12674574276，ZIP SHA256 e6f22fe08bcb2fb9940475c99ba2ced0e45cd0ab2ea914c728fa43a5eff2cd4a。已下载检查 runtime/instrumentation；三次快速切换未出现期望的 stopEngine 清理记录。

Media3 1.10.0 实际 AAR 的 SimpleBasePlayer.release 字节码：shouldHandleCommand(32) 不满足则 return；Player.COMMAND_RELEASE 常量为 32。现有 FlickPlayer.getState 能力列表缺失该项。对应清理逻辑未执行是已证实的契约缺陷，正在 agent/eplayer-031-release-contract 修复，新增公开 release 停止实际输出回归；尚不记为设备通过。

## COMMAND_RELEASE 契约修复（CI 40378425200）

源码 99655c2249e62e16bfd8e9a6eab5c4171e717054；31 项共 30 通过/1 登录条件跳过，54.033 秒。公开 Player.release 在播放 30 秒合成 WAV 后两秒内使原生 Stopped；快速系统/Rust 重建与 EQ 原阈值验证通过。归一化幅度 0.13143149→0.18598484→0.13141343。已实际检查日志：每次旧适配器释放出现同一 JNI 线程的 stopEngine，而之前失败主线只有测试末尾的全局停止，证明清理现在真正执行。

设备 artifact 12678440853 ZIP SHA256 f57044199ecc9921ca127fa242ff866288648ca060383698ac9f1ca2485b1a0f；预览 artifact 12678530309 SHA256 d5220bf91a8a580d02fd6c458e2e0e664c5fb1a861e33d9dc18f7fe55334fc21。R8 not_requested；需要合入后完成新主线 R8/启动及对应确切包的软件检查，不发布之前失败候选。

## 独立 R8 工具初始验收（旧 0.3.0 包）

CI 40376509058 在 SDK setup 请求下架 tools 包失败，未运行设备；改为 platform-tools 后，CI 40377644125 两项跨应用检查通过，33.35 秒。候选固定 a62e0fb /CI 40222561908，原始 universal R8 SHA256 9106fbbee4154a4c2a4c4b877a4976d364d9e65ba514ff110b6272d69bb49d40，未重签目标 APK。系统文件选择器导入运行时生成 30 秒 WAV，系统/Rust 在 UI 选择，框架 MediaSession 有效时长/音频输出活跃、暂停位置保持、12 秒 seek、后台推进、实际通知卡片点击返回均通过。框架位置按更新时间外推，不是原始 PCM 或物理音质测量；该报告不批准 0.3.1。

证据 artifact 12676401042 SHA256 2b9b1395be74860dc6494686d484df88c3b9dd8011da5486a1ac7a94de9ec87e。已查看 native-playing.png（取图时仍有插画加载圈，不能作为完整人物视觉通过）、native-returned-paused.png、system-notification.png。后续工具等待插画加载/眼睑过渡、实际核对单选状态，改用 480x800/160dpi 并保存暂停图；这些增强尚待新候选设备运行。

## 2026-10-27：0.3.1 确切交付候选

主线 331d312 /[CI 40379791181](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40379791181) 为 30 通过/1 登录跳过（76.436 秒），普通/自用 R8 构建通过，普通 R8 冷启动 1720ms。设备 artifact 12678359715 的 ZIP SHA256 为 164c2accbb8f518735a7e513bdc8efb4e60a38adaa01872901ca31b6778b35cd。

确切原始 universal 0.3.1/code52：APK SHA256 92b6b3d1f28940db408b033646b9a77233a0d1cd749c7e6c8b1ab6d0623973b3；证书 c92db3ca59c4ec286cea2f2b54f085b38d676b8e956a34b17c103fc61e04680a，与 0.3.0 不同，备份后可能需要卸载，不能保证无缝升级。API28 起/target36/三 ABI，未重签、未附录音。

独立 R8 两次验收均通过系统/原生两项：CI 40383667514（35.902 秒，artifact 12680016792，ZIP c6df3277cb34cdf12e3caa6f24a3b9dd34acaa0d453f95d37bfa4c172384b68e），[CI 40385418880](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40385418880)（artifact 12680541471，ZIP 9dcc2e40288d911a956c43b7d11c1cb7000fb98620e46e46ee8a7299d1950634）。报告源码/构建/APK 均匹配上述候选。实际系统选择器导入合成 WAV、暂停/12 秒定位/后台推进/真实系统通知点击通过。日志使用框架位置，不是 PCM 音质测量。

截图证据索引：12680541471 根目录 native/system 的 imported、playing、paused、notification、returned-paused PNG；播放闭眼/暂停睁眼已查看。native-motion.mp4（2.899 秒）、system-motion.mp4（2.769 秒）为有效无声录像，ffprobe 已核对；已抽查 native 6 帧，头帽/手/发线随共同姿态保持，未见白色接缝。不等于每帧/实机/长时动画验收。主线证据的 release-home.png 为默认双曲导入首页。

PR #6 已合入发布防错配门禁；27 项根脚本通过，原始候选 prepare 实际验签/版本/ABI 通过。发布清单已更新，但 Release 发布/精确交付 smoke 尚待自动工作流结果。a25a90b 与 fb48458 两次主线失败保留在新证据归档，不将后续通过改写为旧提交通过。下一步完成交付，再合并主线到 PR #4 的能力/缓存边界分支，保留实机验收由本地 agent 执行。
