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

## 独立边界阶段十五（CI 40228679080）

源码 49c9eec3b07ffd5279dbc61fb513544b0f5c523b，30 项共 29 通过/1 登录条件跳过，74.221 秒；R8 not_requested。设备 artifact 12612457911 ZIP SHA256 e383c70303d45dc4ea6f1c2bf3f14353ddcd962083778a49233269d0d9fed5d0。纯能力模型/返回取消导入的现有软件流程通过；不覆盖失败的主线 a25a90b，也不能证明偶发 EQ 已解决。

## 独立缓存与实际弹窗（CI 40229369907）

源码 a48027065f016b863f358877c738a2122a7a4e47，32 项共 31 通过/1 登录条件跳过，72.218 秒。PlaybackCacheTest 实际由应用完整 DataSourceFactory 首读 loopback 合成 WAV、确认缓存长度，再关闭服务端，新建 DataSource 读回同一字节；只删除测试 UUID cache key。SoundSettingsScreenTest 实际点击取消、仅应用频段与错误提示，确认预览/取消不写偏好、有效参数落盘、OFF/前置增益不应用、无效文件不覆盖已有配置。没有操作系统文件选择器。

设备 artifact 12611714141 ZIP SHA256 cbe573b3714efa3c810eb89b895915a88271bcdab0edef2cdb2f8f9f4849a9e4，已下载并实际查看 layout-check/autoeq-preview-dialog.png；标题、替换数量、前置增益说明、取消/确认按钮均可读。预览 artifact 12612099296 SHA256 490dbf51f3f969787ba78ad246ea44051c8c2c39635cd0baa5617a6d7360635b。R8 not_requested；该分支尚未合入，主线 EQ 失败与 0.3.1 发布暂停状态保持。
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

## 0.3.1 发布完成

[Release](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.1) ID424674767，CI40387722240 成功。五个附件均 uploaded；APK SHA256 与 331d312 的原始 CI universal 一致（92b6…），未重签。native-libs.tar.gz SHA256 945e0975c4aa3c7fea09d080095d4bea89982244cbe3daf018e378647ab1587d，validation.zip 295b0ab83eb94b90db8ad15727825ef620ea4500e5febff1edb53f7a25b2bbc4，notes f957610765f59fabdd280d2a683dbb20d66d99a0aa5fecc12b5ee1777261f226，SHA256SUMS de093abf8e73041811f66e93684b3ac8208614792f6d4edc480d3814ddbd382b。交付工作流证据 artifact12681359098（ZIP b4c39568f965769c1149ef0ed547a269155137ac5ebd817dc7cc6a485391072f）已下载核对，精确安装/冷启动 2471ms 通过。Release validation 包含相同最终交付报告/首页图及历史失败/成功证据，index.json 可按 artifact ID 查找截图和无声动作录像；该包不含后续 0.3.2 功能。

## 0.3.2 组合回归失败（优先调查）

源码 5a956b5614552f25463bbaafaf96895ba4fca774 /CI40387147893，33 项为 31 通过/1 失败/1 登录跳过，92.071 秒；R8 not_requested。实际缓存断网重读、导入预览取消/应用/非法文件、公开 release、播放与插画检查通过，LiveEqualizerTest 失败：稳定 baseline0.13126217、position≥1s 后，应用 EQ 时进入 IDLE/position4560/duration12000/error=null，boostedNaN。运行日志已见每次公开释放调用 stopEngine，不能重新归为缺 COMMAND_RELEASE；此前 0.3.1 的成功与精确 R8 证据保持其原范围，后续快速切换可靠性仍需调查。

设备 artifact12681038285 ZIP SHA256 f28f985aaab6e765bc4b81876f08196291c7156de17f6e1a5a3e18afdecdd5fe 已下载核对；fastRelease APK fc80328e24a4a6efd8bc157f5c66b095844cb39feda391fbdfb0972b1b88f57f，测试 APK a50e76449ce41917c0efbd82bfbf4fc65b62528a2844561f26282a1f0bb0f55d。暂停 PR #4/#7 合入，不发布 0.3.2。新增停止原因、原生状态事件及 DSP 更新诊断（不记录账号/URL），失败信息读取真实 native state；先确认发出 Stop 的路径或底层状态，再选择修复，不延长等待或放宽幅度阈值。

诊断提交 8e410f0 /CI40389177223 为 32 通过/1 登录跳过，84.873 秒；设备 artifact12682520815 ZIP SHA256 80e4d70300526147acc16f1eb66c61d8cff5dfd0cf8f284124017d4186329fed 已下载，原生 EQ baseline0.13074183→0.1840731→0.13031313。此轮停止来源日志只在测试清理时出现，未复现原失败；不能声称已找到或修复间歇根因。下一检查在同一用例内建立 3 个完整独立生命周期，每轮均保留 3 次快速引擎切换、原幅度阈值与完整 finally 清理；任何一轮失败立即失败，不是断言重试。19 JVM/本地构建通过，强化检查设备待执行。
## 参数均衡编辑设备结果

源码 8db92a93695f0e048c8ace5798e8f9b80c93b4ba /CI40388022692：33 项共 32 通过/1 真实登录条件跳过，87.735 秒，R8 not_requested。编辑链路实际拒绝 NaN、取消保持已导入参数、保存 660Hz/−2.5dB/Q1.2 后偏好全部匹配；均衡器保持启用。layout-check/eq-band-editor.png 已实际查看，三参数和保存/取消可见，使用统一文楷。真实 EQ 此轮为 baseline0.13074374→boost0.18503658→关闭0.13031313；不将本轮通过掩盖 5a956b5 的间歇停止。暂不合入，继续等待诊断来源。

设备 artifact12681888678 ZIP SHA256 b3a3e49f229270e3bd8d08b9175de112efb8f211bb48492bebd761906b2cefc3 已下载核对；fastRelease APK 910a8aa7640965176b7da71d71c869d8225ec3265a959db351b8a5e9a8dc22e4，测试 APK 6731e0bb740e8ef3edc86b0839cbaa76702ed7bbc87688fc30bfa3c00720e15a。频率/Q 的独立声学响应、实机/账号未测，不能声称均已验收。未改人物素材或 0.3.1 已发布包。

USB 桥接剥离源码 6821dab1c635e3231b8d57a20861bd622c9956a4 /CI40391321733：33 项共32通过/1登录跳过（91.445秒），含无独占会话、公开释放、三个完整 EQ 生命周期、缓存/导入/布局/插画。R8 not_requested，未声称普通 Android 物理 USB/耳机已验收。设备 artifact12684602201，ZIP SHA256 53d5bdb7603d628bd349162efe496fc4c70a88f20fd5804d5bc7f2210b08b390 已实际下载；fastRelease APK 26d57bec854645621637f2a90a8205126cae21248860bb68e5e5b14db768e63a，测试APK667c82e04127ab8488926330ddba76addc733218ff7d47a468f9835efe2c6da4。独立保存，不以本轮通过抹去其他源码的间歇停止记录。

延迟缓存源码56d9156c0c58b67deec86ed13ddeffcda660f69a /CI40392011912：34项共33通过/1登录跳过（77.564秒），新增实际file/content字节读取未调用缓存提供器、远端factory/createDataSource未初始化缓存、两次open请求同一缓存及断网重读均通过。含三个完整EQ生命周期，R8 not_requested；没有启动耗时/物理设备性能结论。设备artifact12685806249 ZIP SHA256 b8794cc9b30ac894c1653b4d17d063f5716896d108a2a6274332b0d60e126205已实际下载核对；fastRelease APK a915fda743c68312e80075062d9fbea62b661006d5a88654c3cb0ddcbb318079，测试APK4415eaebf0b0de3f21bfc7bd3d543c531d28c99de4da4b47c403c8faa4227529。

## 跨采样率丢失 EQ 已取得修复前证据

9de19abb6dbf0ef0da909c9f3d7d2d3b069b3cc8 /CI40392306433：33项为31通过/1失败/1登录跳过（67.195秒）。明确失败在“Saved EQ lost after 44.1→48 kHz”：native/Media3 都在播放、设置 EQ=true/440Hz+6dB/Q1，但幅度0.13068521回到平直（原baseline0.13072309），不满足原提升阈值。日志确认输出由44100改成48000，后续Play未重新下发EQ，包含单曲循环重播也未恢复；与旧IDLE间歇停止是不同表现。

设备artifact12685521332 ZIP SHA256 b1bfe632d7e88d9d8737cab3f18039d09b4d3f00b748751fb2b75e74ae98865a已实际下载。候选修复ed1034b在每次成功Play后复位DSP标记并重新下发保存EQ/FX，设备尚待结果；不降低阈值、不删失败检查。

## 跨采样率 DSP 红绿回归完成

修复ed1034bf59ab0a1ef89813a411882ab4e82f1dd5 /CI40392967562：33项共32通过/1登录跳过（73.496秒）。三个完整生命周期全部通过44.1→48kHz真实换曲后EQ保留：baseline0.13058645→0.18459812，0.13047104→0.18418264，0.13153297→0.18589857。原幅度阈值不变，日志可见每次Play重新下发EQ/FX。修复前9de19ab的失败继续保留，不是断言重试。

设备artifact12685077511 ZIP SHA25666bb2f0278e312362fe8dbbbd0e9a248ab82fce2fbeebe3e8432d84a029b23d5已实际下载；R8 not_requested，实机/音质未测。此证实采样率重建丢失DSP的修复，不代替早期IDLE间歇问题根因结论。准备整合已验证组件，另待联网预缓冲暂停红绿回归。

## 联网预缓冲暂停红绿回归完成

修复前949c9e3312fbb3018ca00bbff4f0a78340cfa6e5 /CI40394134796：34项为30通过/2失败/1登录跳过（91.72秒）。新检查明确失败“Prebuffer completion must not start native audio after pause”；同时旧LiveEqualizerTest在稳定基线前再次失败，native=stopped/IDLE/position3240/eq=false，日志20:22:19.704明确Native Stop reason=player-stop，不是EQ接口错误或适配器release。后者根因待控制器来源诊断，不能忽略第二项失败。设备artifact12685827899 ZIP SHA25685f8c73719b4853ade5a89ffa93b32a97d7d0579e37c5716cb3a4524c2398afd已下载核对。

修复82075bae4d66745c3689a8fe3462119f7b29059f /CI40394832829：34项共33通过/1登录跳过（83.024秒）。下载完成仍保持原生不播放/会话暂停，关闭上游后主动恢复具有真实原生进度、12秒有效时长和非零幅度；三个EQ生命周期和其余检查同时通过。设备artifact12686274869 ZIP SHA256475b0bd6e95fb0a7c4c0f6fcc1b7b8fff6b315dc0a0f190c364760bebc851e59已实际下载；fastRelease APK043595068eae5b932c7e7d3c642b9d9b6389a8ed766e1e9f8c04546d7dcf1f2d，测试APK359a03d31e8eb268e0bea96b3afd0250a41ba95edca3e6c6402669f0353d7e3d。R8 not_requested；不把此通过称为已定位旧Stop命令来源。

## 2026-10-28 北京时间：0.3.2 组合候选

已重新实际读取远端 README、AGENTS、catalog、交接和计划；main69325ed无他人新增主线提交。正常合并 PR7/8/9/10/12 的已保存分支到 PR4 开发线，保留分项成功/失败及提交历史，未强推、未改独立角色参考或Android Cloud入口。20项JVM、普通debug主程序/设备包构建和28项根脚本检查通过；35项组合设备检查尚未执行，普通/自用R8尚未执行，不发布0.3.2。整合后先取得组合结果，再仅向main推进一次完整R8构建，避免逐个阶段重复大构建。

明确通过的分项：编辑窗口/取消/非法/三参数保存；本地file/content不初始化缓存及远端断网重读；无USB独占会话；三轮跨44.1→48kHz后真实EQ幅度保留；联网预缓冲暂停后不自动出声、关闭上游后恢复有真实原生进度/时长/幅度。保留原生停止命令来源诊断，稀发IDLE不能仅因新检查通过视为根因已修复。新增控制器夹具标签和Stop调用栈/批次来源采集，只记录内置白名单标签及包名，不输出任意连接提示、账号或URL。

当前已读取独立PR11/codex/ncm-basic-completion的收尾交接：0.4.0账号/歌词/队列工作和内置模块隔离继续独立保存，未擅自合入本候选，不改其固定本地签名/已有登录会话；实机仍由本地agent处理。下一步：组合35项→main R8→确切原始包双引擎跨应用/截图→Release证据归档；若Stop来源复现，优先按标签/调用栈定位，保持真实记录。随后继续流媒体取消/所有权和未迁移的声音/存储控制。

## 0.3.2 组合失败：已定位到旧媒体会话 Stop 请求

4f35cbbcd588f7dbd8d487853e0459095e3e8ad4 /CI40558035337：35项为33通过/1失败/1登录跳过（109.775秒），R8 not_requested。唯一失败为第二个EQ生命周期开始后native=stopped/IDLE/position0，尚未应用EQ；其余缓存、联网暂停、编辑、插画检查通过。设备artifact12758985697 ZIP SHA256b03980d96b0c28496f0a8944c25d93818448e38f2ff22f704ab7be28f9118dab已实际下载核对。

18:20:42.811的调用栈明确为FlickPlayer.handleStop→SimpleBasePlayer.stop→PlayerWrapper.stop→MediaSessionLegacyStub.onStop；批次来源owner=external、package=app.eplayer.music，不是带integration/equalizer/stream标签的测试控制器。前一轮清理的equalizer停止请求也分别记录，不能把外部legacy请求混称清理或DSP故障。

继续阻止主线合入及0.3.2发布。新增固定媒体STOP按键入口的dismissal布尔标志日志，核对是否源于旧通知删除PendingIntent；不输出任意extras、不拒绝Stop命令。若证实旧通知事件迟到，下一步按播放代次绑定删除动作，并同时验证“旧动作不停止新播放”和“当前动作仍能停止”；不能通过屏蔽所有legacy Stop或放宽断言消除失败。20 JVM/本地组合构建与28根脚本检查已通过，新入口诊断设备待执行。

## Stop 入口诊断仍未确认通知删除来源

7f578192da3c1ad41c541825e805be9dad0516cd /CI40560772114：35项为33通过/1失败/1登录跳过（93.815秒）。第二个EQ生命周期在legacy Stop后进入IDLE；没有出现新增的Service媒体STOP按键入口日志，因此不能把问题认定为通知删除PendingIntent，也未实施通知代次过滤或屏蔽任何控制。设备artifact12760188441 ZIP SHA25650882014f6f6a0f42c3555492e7b516b8764802630a035bfc8ed75db3edd60e9已下载核对。

继续采集legacy来源的同UID/版本/包名校验布尔值，以及媒体按钮回调中的固定Stop/dismissal标志，避免仅凭包名推断发送者。同时新增真实框架控制正向检查：原生12秒合成音频有实际进度/时长/幅度后，通过Android框架MediaController.stop停止，2秒内须原生Stopped且会话IDLE；保持正常Stop能力。只临时采用测试所需MEDIA_CONTENT_CONTROL权限，finally释放并恢复原引擎/音量、清理唯一夹具。20 JVM/本地构建通过，新增设备总计36项，尚未执行，不将编译或后续其他通过替代这项结果。

## 引擎交接框架状态观测（修复前）

新增真实框架 MediaController 状态订阅：原生音频已有实际进度后，两次往返系统/原生引擎，要求不向媒体会话发布 STATE_NONE/STATE_STOPPED，再验证合法 framework Stop 仍停止原生输出。当前仅修改测试，产品交接顺序尚未改变；本地设备包构建通过，修复前 CI 待执行。代码审计确认当前先 session.setPlayer 再恢复队列，不能在设备观测前称其为旧 Stop 的根因。

## 框架 Stop 正向夹具首轮失败及权限范围修正

4dbb6b1b83044b45c59c2551f3a688c3329bba9e /CI40563989815：36项34通过/1失败/1登录跳过（72.927秒），三轮LiveEqualizerTest通过。唯一失败是新增framework Stop检查在发送Stop之前原生音频未启动，不能称为legacy Stop再次复现，也没有本轮Stop发送者证据。artifact12762133513 ZIP SHA256a7d796318d3251be5c7656a675f8bf0cb15787789c7aa8d1a1633e40b857c33c已下载；普通fastRelease APK09f7fc873311c52b181da50de40d4223d7e3afdd719713c19efdc330fd2ec053，测试APK10a645380442ae13f91906a6b514b3700685659b907044b33e382f12c3c9ef6f。

修正测试准备：启动Activity后等待主线程空闲；临时MEDIA_CONTENT_CONTROL仅包围读取active sessions，不再跨越音频启动和引擎切换。产品代码未变，本地测试包再次构建通过；设备待执行。修复前交接状态观测bce115366c3f064a7972652e244489c041c621cc已通过GitHub连接工具正常快进保存，CI40565618951进行中。Git HTTPS凭据失败后采用现有连接工具，未强推、未读取或写入凭据。

## 引擎交接顺序候选修复

MusicService.SET_ENGINE 先恢复替代播放器的队列、位置、循环/随机、音量、prepare 和播放请求，再 session.setPlayer；仍保持唯一会话，随后释放旧播放器，不过滤任何媒体控制命令。20项 JVM、debug主程序和设备测试包构建通过，28项根脚本通过。修复前36项设备检查仍在运行，候选设备尚未执行；不得把本地通过写成旧Stop根因已修复。

已实际查看4dbb6b1设备artifact中的 visual-check/player-rust-paused.png 和 layout-check/eq-band-editor.png，480×800暂停页和编辑窗口在该尺寸下未见明显头帽/手部错位或字体混用；未修改人物资源，静态截图不代表连续动画或实机验收。CORE-BOUNDARIES更新为当前能力，区分已接手动参数/AutoEQ与未接缓存清理、Crossfade，并记录后续流媒体取消/资源所有权的前置回归。

## 交接修复前追加结果与诊断限制

bce115366c3f064a7972652e244489c041c621cc /CI40565618951：36项33通过/2失败/1登录跳过（110.676秒）。LiveEqualizerTest再次被legacy Stop中断，新framework检查仍在音频启动前失败，尚未到达交接状态断言或主动Stop。artifact12762813323 ZIP SHA2567b46e5a4165ba197d8009fef6ca942f1703b38f4aaebc533a003c24469cdb281已下载核对，APK78681cbef2f56f857145ae8048564f904706921c45fc384b60533b6c8cd9a539。准备修正后的2de55f3设备轮次与产品交接候选6120be5分别排队，未用一个改动混称两项问题的根因。

重要诊断限制：本轮Stop批次显示sameUid=true、version=1010000300、package=app.eplayer.music，但实际Media3 1.10.0字节码中 MediaSessionImpl.resolveControllerInfoForCallback 会将系统UI控制器映射为应用通知控制器。因此这些回调字段不能证明原始发送者就是应用自身；legacy调用栈仍是有效路径证据，未出现Service/回调媒体Stop按键日志也不能再凭包名认定具体来源。未添加过滤、反射私有字段或延时忽略Stop。

## 引擎交接状态：真实设备修复前失败已确认

准备修正后的2de55f35a307ac2a3cbb3189889ef2431fa0a062 /CI40565941188：36项34通过/1失败/1登录跳过（82.267秒）。真实原生音频启动和两次引擎往返均已走通；唯一失败正是新增framework状态断言，观测序列[2,2,0,0,0,0,2,6,2,0,0,6,6,6,0,0,6,6,6,6,6,0,0,0,0,6,6,3,3]多次包含STATE_NONE=0。不是音频启动失败，也未到达后续合法Stop断言。LiveEqualizerTest三轮通过，不把本轮未复现旧Stop称为已解决。

artifact12763159386 ZIP SHA256e86aa9c186f745e3c0f08fd3d214d4ee5371aa3838b1703bc949e22dbf417376已下载核对；APK b0c810ee5e54687ee0191252fb10bceea56e12e8f0a3f70bbacab1aced85e6c9，测试包14b137b0735627d3a07b8902979ea7fcbbd028229ecb4bef4297430b13c8a9b5。产品交接修复6120be577274c32088a997e7a328314918ca6349 /CI40567120983正在执行，同一测试不放宽断言，尚未取得绿色结果。

## 独立下一阶段：流媒体取消所有权修复前回归

以PR4交接候选为基础建立agent/eplayer-stream-cancellation，避免替换正在执行的交接检查。本阶段先仅增加StreamProxyCancellationTest：真实本地数据源读到第一块后取消所属协程，后续不得通知ready，且自己的未完成输出应删除。使用独立ContextWrapper.cacheDir和唯一临时目录，全部读写/清理只限本测试；不访问账号、用户曲库或真实网络。编译通过，产品StreamProxy尚未修改，37项设备检查待执行，不提前声称红绿。该检查仅覆盖下载函数取消契约，切曲后旧回调不影响新原生输出仍需后续延迟loopback检查。Core交接的36项结果保持独立，继续优先处理。

## 下载取消契约候选修复（设备待执行）

StreamProxy 读取前后及通知前检查所属协程取消，CancellationException向上传播；FileOutputStream改为use确保异常/取消均关闭，失败或取消时只删除本次未完成输出；临时文件使用唯一名称，成功缓存保留。20 JVM、debug主程序及设备测试包构建通过。修复前测试05891511239a48e01699f357170b673f74502c64 /CI40568511349已在运行，修复后37项设备待执行；不提前声称红绿成功，不将检查取消等同于主动中断阻塞网络read。

此改动尚未修改FlickPlayer通用catch或跨曲下载所有权，迟到回调与新曲目隔离仍需下一阶段真实loopback切曲回归；不通过增加睡眠、放宽幅度断言或屏蔽系统Stop规避失败。Core交接36项仍独立运行。

## 引擎交接红绿回归完成；0.3.2 进入交付准备

6120be577274c32088a997e7a328314918ca6349 /CI40567120983：36项35通过/1登录跳过（95.368秒）。同一框架状态检查通过，两次引擎往返不再出现STATE_NONE/STATE_STOPPED；随后合法framework Stop在2秒内让真实原生输出停止且会话IDLE。日志中主动Stop来源version=0；三轮EQ换采样率仍保持幅度（0.13085853→0.1858295、0.13058582→0.18418264、0.13077573→0.18495134）。这是空状态交接的红绿证据，不足以宣称所有历史外部Stop根因均消失。

artifact12764217339 ZIP SHA256c2292ff6a0cac691acda242cd4dc702673649ded5820e73a1072975690c1f08d已实际下载核对；APK4a4c607140a643bec8818d0fda6daec237afd023fb20a405a3a66e7fc15e1e3f，测试包124f66ffe410d24b800e865adf4ad68f11471efa449c5793876cd6a1cb0342f0。R8 not_requested；仍需最终组合及主线普通/自用R8、确切交付、Release归档。

0.3.2本轮冻结产品范围为既有Core组合、已验证交接顺序，以及PR13待验证的下载取消资源契约；此后先交付一次完整R8/确切包，不把依赖精简、跨曲下载所有权等下一阶段工作继续塞进该交付。PR4与main文档存在合并冲突，待PR13证据后正常合并main并保留双方内容，不强推。

下载取消修复前CI40568511349 attempt1为环境准备失败：SDK Manager下载Android Emulator时“Error on ZipFile unknown archive”，模拟器/37项测试均未执行，无device artifact；不能称测试红色。已读取job122417488564日志并保存SHA2560bea9b611391ec6bbb8a37fc54222220b5ea3db881021f975faf9abeff20ed6d。仅重跑失败设备job，复用成功构建，不重复全量构建；候选f3fb52a356ec924e8a2c42b8134f12f11949a639 /CI40568783358正在运行，修复前重试随后排队。若环境仍失败，明确保留not_run，不无意义重试。

## 37项候选通过，进入主线一次R8构建

f3fb52a356ec924e8a2c42b8134f12f11949a639 /CI40568783358：37项36通过/1登录跳过（76.455秒），包含真实数据源取消后不通知ready、不残留本次未完成输出，原36项交接/框架Stop/EQ/联网暂停/布局同时通过。artifact12763938192 ZIP SHA256db13411d737674567c994b3f4d3749d477eaebdf1db5cece3b545206c59283ea已下载核对；APK0f72ecddfc559c9c5e6721f9a80aea657dc2773a1f2e00bf1b55a99bcb5ada23，测试包5273297ff3a58e488e217416706ed2301b2e5404582e327299be6cf16d1c7582。R8 not_requested。修复前0589151重试仍执行中，不提前称取消契约具有红绿证据。

已正常合并main0e5af42进候选，两处文档冲突逐字确认main内容是当前文件完整前缀后保留完整当前版本；合并提交63d2a2a具有双方父提交，未强推。后续只有交接/规划说明变化，应用和测试与通过的f3fb52a相同。PR13现统一以main为目标，包含PR4全部源码；最终main构建/37项与确切原始包验收仍必须执行，不能复用fastRelease结果冒充R8通过。

下一步：main普通/自用R8及37项→取确切原始universal APK的真实versionCode/证书/摘要→双引擎系统文件选择器导入/暂停定位/后台/实际通知返回→Release0.3.2归档所有新成功、失败及未执行记录。主线发布0.3.1附件保持不变。随后独立推进UI依赖精简和跨曲下载所有权。

## 下载取消红绿确认与主线合入

修复前05891511239a48e01699f357170b673f74502c64 /CI40568511349 attempt2仅重跑失败设备任务，37项35通过/1失败/1登录跳过（65.993秒），唯一失败为“Cancelled downloads must not notify a new playback owner”。artifact12765421803 ZIP SHA256beb60ff2cc2129f5eeacd5c60e442ab35ae4a5a64c2c105b74163adc7da6b1da已下载核对，APK1746350c7b3872a47b95b2de7200355d62aa92411a4999d0190eea15743313f1，测试包bcdf2e2f0cec6e28b59f28bd8263e93ff69e26a11a626b28531f586483a43b0a。修复后f3fb52a的37项36通过/1跳过已记录；attempt1 SDK准备失败仍单独保留，不充当红色回归。

PR13已以正常合并保留全部父历史进入main113acb0d7c72f392aae754cb3c7cb65ea1be3df4，PR4随祖先提交自动标记merged；未强推、未修改PR11或AndroidCloud。主线CI40570928840正在构建普通/自用R8并将运行37项与R8冷启动；最终确切universal包跨应用和Release0.3.2仍待执行，不提前发布。

为将无device artifact的SDK准备失败也收入Release，交付脚本支持可选evidence_jobs：归档已完成任务的原始日志、任务/运行元数据、源SHA和内容摘要，保留原failure结论；不允许未完成或ID不匹配任务供证。新增归档真实bytes/失败结论、拒绝未完成/错误任务和ID输入校验回归，根脚本合计31项通过。未改应用源码、构建签名、当前0.3.1交付manifest或旧附件；新版本manifest稍后索引job122417488564。

## 主线R8与确切原始通用包均通过，准备发布0.3.2

主线113acb0d7c72f392aae754cb3c7cb65ea1be3df4 /CI40570928840：普通/自用R8构建通过；37项36通过/1登录条件跳过（104.993秒），唯一跳过SourceLoginLiveTest.officialModuleCanCreateLocallyRenderedLoginCode，未跳过框架Stop或取消回归。普通x86_64 R8冷启动1378ms。artifact12765768463 ZIP SHA256e26259a226bf0bbc6d39ef8cc6b5d8ea5c58b056a2f227f39a20e08023613b90已下载并与GitHub digest核对；构建artifact12765359826 ZIP SHA256ce571178314066f820ba41af7decca1e79c99263725573104827ca8bff4d2576亦已下载核对。

原始standard universal R8 APK：0.3.2/versionCode106，45,272,762字节，SHA25613c1401d2121e2cde477c2b3d08064366c65b34357e210253c641aa775f9a27c，证书c9ccdf76dd260a4cc48a595e21b940958580b3fe318b75ce3d3e482bbc7a37c0；V2验签通过，minSdk28/target36，含arm64-v8a/armeabi-v7a/x86_64。没有重签名、重打包或回构建替代文件；该CI临时签名与0.3.1不同，不能承诺原位升级，本地固定签名和登录会话仍不触碰。

确切包跨应用CI40573577516通过2项（55.117秒），系统/原生均实际使用系统文件选择器导入30秒合成WAV，播放/暂停/12秒定位、Home后台进度、真实通知点击返回通过；报告source/source_run/APK摘要与113acb0/40570928840/13c1401完整匹配。artifact12766192574 ZIP SHA256e2f64c05e34848b21a3186ad4800277d0b7b5a4fdd37d060f21ef46d8306e126已下载并核对GitHub digest。归档r8-check/native-playing、native-paused、system-playing、system-paused、notification与returned-paused截图，以及native-motion.mp4（无声2.519秒55帧）/system-motion.mp4（无声3.475秒74帧）；已实际查看原生播放/暂停及六个原生动作抽帧。没有声学PCM/实机听感测量，不称逐帧全验收。

主线加载的portrait-2.0/landscape-2.0、sound-full-page、release-home、player-rust-playing截图已查看。常规字号人物头帽/手部无明显错位；2倍字体长标题时插画在滚动容器中，首屏下半身未完全显示，滚动可见，仍列首屏空间分配优化，不能称所有视觉已完成。下一阶段优先调整这项，再推进跨曲下载所有权、无调用UI依赖剥离；另审计确认Rust lib.rs默认LevelFilter::Trace会产生大量JNI日志，尚未修改或重编原生库，不声称已节电。

交付manifest现固定上述确切通用包、20个成功/失败artifact、5个已完成工作任务日志及R8跨应用证明12766192574。自动发布仍须精确通用包安装/冷启动和所有归档检查通过，Release0.3.2尚未发布；0.3.1既有五附件保持不变。原始SDK准备失败日志、回归失败、登录跳过及物理设备/真实源未执行范围均保留。

## 首次0.3.2发布停在日志归档，已修复原始日志捕获

CI40575569334 /job122440962811：prepare中的APK摘要/版本/签名校验通过，精确原始通用包安装和冷启动通过（1677ms），随后publish在归档job122417488564日志时失败。GitHub CLI拒绝返回含ANSI终端颜色控制符的text响应，提示--allow-escape-sequences；不是应用播放、签名或SDK验收失败，Release0.3.2未发布。

artifact12766293038 ZIP SHA256a8f01e60bffd7d1bd3c5e97ed11ecff6a67396f21ab3f7839464c9f7ef42e044已下载核对，delivery.json为passed且source/CI/SHA与113acb0/40570928840/13c1401匹配。原始失败job和该安装证据加入交付索引（共21个artifact、6个job）；不覆盖或丢弃失败。

捕获日志时使用CLI明确提供的原始响应选项，只由check_output获得bytes并写ZIP，不向终端渲染控制符；ANSI颜色字节加入既有归档回归，仍核对完整原始字节/摘要与failure结论。31项根脚本通过。未改应用、源CI、APK、证书或0.3.1附件，重跑只涉及交付流程，不重复Gradle/Rust/R8构建；最终发布结果仍待执行。

## 0.3.2 已发布且五附件逐项核对

CI40576719254成功发布Release425943718 /eplayer-v0.3.2（非draft、preview），target_commitish=113acb0d7c72f392aae754cb3c7cb65ea1be3df4。最终原始通用包安装/冷启动1749ms通过。Artifact12768185941 ZIP SHA25682ef5444d47c8fd5bc3a61b7d492512a875f471851384eb5ac83cb87861f5c1b已下载；四条SHA256SUMS与GitHub资产digest逐项一致，校验文件自身摘要也一致，五项state均uploaded：

- ePlayer-v0.3.2-preview.apk：45,272,762字节，13c1401d2121e2cde477c2b3d08064366c65b34357e210253c641aa775f9a27c。
- eplayer-v0.3.2-native-libs.tar.gz：c79833616e421baaa72eeb575809fceda9b4bf93aa2c74e3e221ede5d1d93648。
- eplayer-v0.3.2-validation.zip：60,580,855字节，e19625d319bad557e9ae973af3c112d72c950631b93582a125c16122f6a00f2a。
- eplayer-v0.3.2-notes.md：0563499e7292ca63cb4d659544585b053873b3936580338940ba64f7c86aefbe。
- SHA256SUMS：1eb962bed58cdef58ec7332a762552161750421d43096b3964c6e493c76751bc。

21个成功/失败artifact及6个原始job日志已归档，包含SDK准备失败未执行、取消/交接修复前失败与首次发布归档失败；最终安装证据也在ZIP中。无用户录音、密钥、SDK或AVD上传。0.3.1附件未修改。交付manifest回填ReleaseID便于稳定恢复，已发布匹配版本的流程只检查不重复下载/安装/覆盖。

继续0.3.3：agent/eplayer-portrait-fit修复前回归161c63f17e0b9d0a74f0c02de7d52bfff019f655 /CI40577808745正在执行，产品布局未改；本地设备包编译通过，尚未设备红色或绿色。优先让长标题/2倍字体的竖屏首屏完整人物可见，随后跨曲下载所有权/依赖剥离，再考虑真实三ABI原生重编以降低Trace日志；不因0.3.2发布就停止，也不把尚未完成项写成已验收。

## 2026-10-28续接：大字号修复前失败已取得

重新实际读取远端main38692c9的README/AGENTS/catalog/交接/开发计划和上传约定，未发现他人主线新提交。0.3.2 Release425943718仍非draft、target113acb0且五附件digest/状态与此前收据一致；补齐此前已准备而尚未推送的发布记录。

0.3.3修复前161c63f17e0b9d0a74f0c02de7d52bfff019f655 /CI40577808745：37项35通过/1失败/1登录跳过（98.997秒）。唯一失败是2倍字号首屏插画完整可见要求：未裁剪bounds bottom666dp，而transport top568dp，相差98dp；其余音频/插画及布局检查通过，产品布局尚未修复。artifact12769250606 ZIP SHA25615c5b8db039795fe3541cebf2bd0c983afc76e57bfcd71c01d93363fc159ef95已下载核对。保留layout-check/portrait-2.0.png作为修复前图，后续用同断言/同尺寸检查修复，不把自动滚动到图像后的状态冒充首屏完整显示。

## 0.3.3 独立首项：大字号首屏完整人物回归

从已保存的main38692c9分支agent/eplayer-portrait-fit继续，不改正在发布的0.3.2源码113acb0或原始APK。新检查使用原有横竖屏/1倍与2倍字号真实Compose+AndroidView夹具，要求竖屏未裁剪插画bounds完全位于控制区上方且高度非零；失败前先保存截图。当前产品PlayerLayout尚未修改，新增只是回归与0.3.3版本登记，设备尚未执行。现有布局会在长标题/2倍字体时把插画放入可滚动区域，首屏人物脚部未显示；下一步改为按标题实测占位分配剩余插画空间，复用已有按宽高等比绘制，不增加素材补丁或测量状态循环。

0.3.2发布仍优先跟进，归档修复38692c9只改变原始日志捕获选项，未重编应用。后续0.3.3先完成这项视觉红绿，再处理跨曲下载所有权与UI依赖；真实账号、实机、固定签名仍留给本地agent，AndroidCloud入口/角色参考项目不改。

0.3.3布局回归本地编译：首次因DpRect.height访问不适用失败，改为bottom>top后设备测试包构建通过（2秒），设备尚未执行。0.3.2已由CI40576719254发布（Release425943718，tag eplayer-v0.3.2），五附件uploaded/摘要已读取核对；后续先保存本分支修复前检查，再同步主线正式发布记录，保持不可变113acb0/13c1401交付。
