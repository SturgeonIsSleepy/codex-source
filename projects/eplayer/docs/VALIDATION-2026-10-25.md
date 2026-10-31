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

## 0.3.3 首屏人物适配候选修复

PlayerLayout竖屏移除固定100dp元数据预留与内部滚动容器，元数据先按实际内容测量，AndroidView用剩余高度；WitchPlayerView原有min(width/865,height/1280)等比绘制无需变化。减少一层BoxWithConstraints/滚动状态，没有增加测量回调、补丁图、表情帧或新的播放状态。横屏独立插画/可滚动操作区不变。

本地20 JVM、debug主程序/设备包构建通过（53秒），31项根脚本通过。修复前161c63f /CI40577808745的35通过/1失败/1登录跳过和98dp越界证据保留，当前候选设备绿色尚未执行。同步main20cd8c5发布说明时保留双方文档与独立0.3.3登记，不覆盖0.3.2原始APK或无关项目。下一步同一37项真实检查，查看portrait-2.0首屏全身，再继续核心边界工作；不以编译通过替代视觉通过。


## 2026-10-28：首屏边界通过，组合暴露旧引擎焦点竞态

候选7c991a000afc8bafef11ab0a0d72af403ecb6fc2 /[CI40743065174](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40743065174)：37项35通过/1失败/1登录跳过（69.822秒）。横竖屏/1倍2倍字号及新增首屏人物边界通过，已实际查看layout-check/portrait-2.0.png，人物全身和控制区同屏。未改角色素材、皮肤、眼睛或骨架。唯一失败LiveEqualizerTest.savedEqualizerChangesApplyWhilePlayingAndDisableCleanly：READY、PWR=false、position=0、duration=12000、native=paused、level=0，无错误；不是STATE_IDLE或UI假进度。artifact12840673407 ZIP SHA25685dc83175484aa290dd1e3bb3509ecec99d600eb0e8ada032a4530330c841d26已下载核对，保留失败截图/原始日志，不冒充绿色。

日志18:21:00.175原生开始playing，.176当前RustAudioFocus收到永久LOSS(-1)，.226原生paused。审计实际Media3 1.10字节码：ExoPlayerImplInternal在播放Looper管理AudioFocusManager，release等待ConditionVariable，releaseInternal释放焦点后才open；原顺序在释放旧Exo之前恢复新Rust PWR，旧线程可能晚请求焦点。修复只把播放请求移到session.setPlayer和旧播放器release之后；已准备的非空队列先交接，保留上一轮避免NONE/STOPPED的修复。未全局屏蔽失焦、改变外部路由或自动播放策略。

现有真实框架Stop检查增为三轮快速双引擎往返，并用另一个Android AudioFocusRequest验证永久失焦确实暂停原生，明确恢复后再验证合法framework Stop。原LiveEqualizer三轮真实幅度检查保留，不删不跳过。本地20项JVM、debug主程序/设备包构建通过（32秒），根脚本31项与目录检查通过；设备新结果待执行，不因视觉单项通过称完整通过。PR11/PR14独立0.4/0.5已读最新规划，保留对方固定签名、真实源、导航/歌词和依赖清理成果；后续跨曲下载所有权仍是共同未完成核心边界。


## 跨曲下载所有权：先补真实失败夹具，产品尚未修复

新增switchingFromPrebufferToLocalTrackCannotBeOverwrittenByOldDownload：loopback先发送96KiB（低于240KiB阈值），服务切到30秒本地合成WAV，要求实际原生时长/进度/有限非零幅度与MediaController队列/PWR/READY一致；再释放旧12秒HTTP，并在服务端确认客户端响应消费/关闭后连续检查2秒，不能让旧下载覆盖新曲或置IDLE。尚未运行设备，未预判为失败或通过。

提取DelayedAudioServer复用原预缓冲暂停检查与新检查，连接/线程/等待均有上限，支持取消时服务端遇到预期SocketException（不吞SocketTimeout）。共有withNativePlayer只在确认空队列后接管，恢复音量/引擎并释放控制器，不删用户媒体/账号。每个URI使用UUID，缓存清理只涉及该URI与字节完全匹配的新夹具临时文件。初次本地设备包编译因Song.album缺参失败，补齐Test专辑后构建通过（4秒）。产品FlickPlayer尚未改动，焦点修复cfb1ec4的CI40747846822独立运行中，不重复大构建或本地慢模拟器。


## 焦点候选回归与原生回执边界

cfb1ec419d2587082c6b82af8c24fa2b64227911 /[CI40747846822](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40747846822)：37项35通过/1失败/1登录跳过（87.333秒）。LiveEqualizer三轮通过，原先永久失焦停0秒未复现；新增三轮框架交接检查失败，状态含STATE_NONE=0。后续竞争焦点/合法Stop步骤因前置断言失败未执行，不标通过。artifact12842787502 ZIP SHA256b797e86266a1f2093fe5992ae36c1ee642a8581d20e809803aa3f9cf79cd4ea2已下载核对；portrait-2.0首屏全身图再次查看通过。

runtime.txt在18:51:03.044至.097记录连续stopped/buffering/playing/paused/stop回执，显式测试清理Stop在.438才到。沿用独立PR11的24873a8485a9d1ecc2903a9d6ce5563d0cbfde95启动回执边界（该分支最终1d74691c36e17a519d4bacea087c79e3f9d38b0e已有设备回归）：等待Playing确认时保留BUFFERING，忽略前一个原生命令的stopped/idle/paused，handleStop和错误仍立即结束等待。只移植此核心小修，不覆盖PR11账号/歌词/本地签名等内容；本分支尚待组合验证，不能直接挪用其绿色结果。

同时实现下载所有权：park/换曲/空队列/Stop统一cancelStreaming，取消旧下载与seek/underrun恢复并集中重置下载状态；ready和progress子任务归属下载协程，所有路径/预缓存决策回主线程，JNI仍串行引擎线程。取消显式重抛，不改新曲IDLE；无新增曲目代号、整脸素材或状态补丁。新增缓冲期Stop的真实HTTP完成检查，Stop后保持IDLE而非被旧下载转READY。原暂停后离线续播检查继续保留。e39becf /CI40748464795为仅新跨曲检查、未改产品的独立修复前运行，当前尚未取得结果。最新组合本地20 JVM/主程序/设备包构建通过（6秒），设备待执行；Rust原生库不重编，未声明性能或物理设备通过。


修复前下载检查e39becf /CI40748464795 attempt1：build成功，设备job123032345846在安装system-images;android-36;google_apis;x86_64时报Error on ZipFile unknown archive，未启动应用测试，无device artifact。不是已复现产品失败。已仅重跑该设备job，保留第一次原始job日志；e86295b组合CI40749734603单独运行，不重复成功构建。后续依实际attempt2记录红/绿，不能因SDK失败声称下载缺陷有红色设备证据。


## 0.3.3核心组合已通过，继续独立0.5.1集成

源码e86295be40881de16e8e5c15794474a65847e281 /[CI40749734603](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40749734603)：39项38通过/1真实登录条件跳过（71.056秒）。原三轮EQ、三轮双引擎往返无NONE/STOPPED、另一个AudioFocusRequest造成正常永久失焦暂停、显式恢复及实际framework Stop均执行通过；跨曲旧下载不能替换30秒本地真实进度/时长/非零幅度，缓冲Stop保持IDLE，预缓冲暂停后关闭上游再恢复亦通过。不是物理外设/电话/听感验证。

artifact12843119440 ZIP SHA25631c82ec07ccd43aa1d3c4232e1a0b2813026b9a47a97dad067a01225de0e5822已下载核对；实际查看layout-check/portrait-2.0.png首屏全身及visual-check/player-rust-playing.png、player-rust-paused.png，闭眼/睁眼与头帽对齐保持。新代码未改任何人物/字体资产，没有逐脸换帧。开发分支本轮没有R8/确切发布APK验证；不挪用0.3.2发布结果。修复前e39becf attempt2仍在运行，SDK首次失败不算产品红色。

独立0.5.0基线916f4e1 /CI40699053291证据12822860756 ZIP SHA256c73796d7c5d33e1aa5db2eeb5b9e0e3260ccbe0b66001a33357c305d8c298b3c已下载核对，实际查看release-home、portrait-2.0、settings-full-page：新导航/首页已经存在，大字号裁切仍存在。准备agent/eplayer-050-core-integration，以e86295b与ab7a227两父普通合并，不重写PR11/14历史；0.5.1未发布，组合验收不能继承旧绿色。去掉页面硬编码MusicRed，采用主题主色统一天空蓝，并保留深色模式、共同人物/字体、完整音乐源与新歌词流程。用户自用签名/模块/账号不操作。


修复前e39becf /CI40748464795 attempt2已实际执行：38项36通过/1失败/1登录条件跳过（102.660秒），唯一失败switchingFromPrebufferToLocalTrackCannotBeOverwrittenByOldDownload，旧响应完成后本地30秒原生输出被改变。artifact12844910325 ZIP SHA25699b1c4474c563e88bd445a58446568344bfc680fabaafe114e84bda2fca6c079已下载核对，runtime保留本地nativePlay之后旧stream_cache再次nativePlay。与e86295b/CI40749734603相同检查通过形成红绿；首次SDK ZIP失败未执行仍保留，未撤销任何失败结果。

## 0.5.1正常两父集成准备

agent/eplayer-050-core-integration把037f742（e86295b的39项已验收核心及文档）与ab7a227（PR14、含PR11）正常合并，保留双方历史。冲突限于版本/入口说明与相同启动回执注释，实际保留源/账号/歌词、低频断点、导航、新增音乐源回归及本轮下载/焦点/布局修复；未改任何AndroidCloud入口、其他项目、图片或字体资产。统一MaterialTheme主色供首页/曲库/搜索/音质/底栏使用，移除MusicRed硬编码，纸白天空蓝与夜间蓝色容器并保留深色功能。原PR14的12个无调用旧文件/依赖清理保留，不能把从未接入的新会话淡入淡出功能称为已恢复。

本地全新独立工作区构建通过（4分1秒），28项JVM全部通过，debug主程序/测试包已构建；本地临时debug密钥仅用于编译，不复制本地agent固定签名，也不安装到用户设备。只复用已校验Release原生库、SDK缓存；尚未运行本集成45项设备检查/R8/确切包和全页视觉。不挪用旧绿色结果；下一项就是组合验收，不在未完成前发布APK。


## 新导航与歌词实际页面验收补充（未运行设备）

LightNavigationTest在真实MainActivity保存首页、设置、音质弹层截图；新增曲库→歌曲分区的魔女双曲/导入可达和网易云二级返回流程，保存library/discovery。截图使用系统UiAutomation一次取图，不影响生产GPU玻璃渲染，也不把单个组件图冒充整页。现有首页不自动加载推荐、设置音质只读检查保留。

LyricsFlowTest给UUID独占的离线歌词缓存与30秒合成WAV建立暂停会话，从真实迷你播放器→歌词页：检查日文/中文译文、开关译文、点12秒行使实际MediaController定位且保持暂停，再回播放器；保存lyrics-full-page.png。不发账号/音乐源请求、无录音，清理前以同引擎命令等待队列清空进入服务后才释放控制器，只删本次文件/历史。该测试是实际UI/会话定位，不声称有真实音源下载、PCM输出或实机听感；双引擎音频另由既有检查负责。本地设备包编译通过（21秒，收尾修正后的增量2秒）；预计整轮47项，设备尚未执行，ee69403的45项组合仍独立运行。


## 0.5.1两轮组合失败与整页/歌词分项通过

- ee694031db2224f7a119409af532b345e06c71b6 /CI40751820043：45项43通过/1失败/1登录条件跳过（111.564秒）；唯一失败LiveEqualizer，state=IDLE、native=stopped、position=0，19:19:03.894经过MediaSessionLegacyStub.onStop，随后回调身份被Media3映射到应用通知控制器。不因同UID推定原始调用者为应用，也不屏蔽它。artifact12845256790 ZIP SHA256c2a88f00e4b6ab205b4d3a17b94d351821f78b7357ef7c2ccf84308cb9e9e607已下载核对。
- 75ce13ffec66a9c5bdea6500b1adccef9a468b04 /CI40753222782：47项45通过/1失败/1登录条件跳过（102.674秒），唯一仍是同一EQ启动Stop。新曲库双曲/导入、二级页返回、译文开关、从真实迷你播放器打开歌词、点12秒行/保持暂停/返回播放器均通过。artifact12844168763 ZIP SHA256f6d295fb652340a91920ebd8d2832c4ba5d453984bd302493bef08ec8867440e已下载核对；实际查看navigation-home/library/settings和lyrics-full-page，纸白天空蓝与完整新导航/歌词呈现通过该分项。没有将整轮写为通过。

进一步审计：RustEngine命令已在单线程，但native事件原由IO轮询，取消旧轮询不清除共享原生事件队列；旧Playing可先清除awaitingNativeStart，之后旧Stopped仍能发布IDLE。jni_api/audio_api实际使用try_recv_event非阻塞读取，且状态事件没有曲目标识。候选把读取也放到同一JNI调度器，初始化/新Play前排空已积压回执；不重编原生库、不声称已得到原生代号或彻底根因证明。

FlickPlayer去掉awaitingNativeStart附加标志，以已有用户意图统一处理：仍请求播放的内部Stopped保持BUFFERING，已请求暂停保持READY，显式Player.stop仍同步IDLE且不可被旧Playing/Paused复活；本地输出真实持续停止超过15秒报告错误并停止，不永久假装Playing。暂停意图只由恢复/显式Stop解除，旧Paused不能改变它。合法框架Stop检查继续保留，新增1秒稳定性窗口；未按来源过滤任何Stop。会话状态日志记录是否真正发布到session，设备采集补系统媒体/通知标签，帮助检验是否出现“暂时IDLE→通知移除→系统Stop”的链条，不把该链条预判为已证实。

诊断字段首次把nullable session误作lateinit，导致本地编译失败，改为安全引用后28 JVM/主程序/设备包构建通过（6秒）；收尾源码28 JVM/主程序/设备包构建通过（5秒），设备结果待执行。31项根脚本通过，失败路径也保存系统媒体诊断的证据契约已补检查。下一项同47项整轮及实际截图，不盲重跑旧失败源码。


## 9a55215未修复系统Stop；交接中人为PAUSED的进一步审计

9a5521578ce7b43752fa4d83c8e2ad2c6614eb39 /CI40756489129：47项45通过/1失败/1登录跳过（100.501秒），仍是EQ启动state=IDLE/native=stopped，未解决。artifact12845774812 ZIP SHA256eed1c0f5611cfa82661a65c2d46819dc381209f3e3a8aa053277171bf58c5517已下载核对。系统MediaSessionService日志明确19:50:07.095 callingPackage=com.android.systemui，随后legacy Stop；此前当前会话BUFFERING，不可再声称“临时IDLE→通知撤销”已证实或已修复。新事件/意图边界保留其独立价值，但不当作这次Stop根因已解决。

进一步实际读Android16 AOSP LegacyMediaDataManagerImpl/MediaCarouselController：关闭媒体续播时，paused媒体数据可经removePlayer→dismissMediaData→transportControls.stop；异步回调可能晚到。当前MusicService在仍发布旧player时previous.pause，并将PWR=false的新player交接后才恢复true，真实框架日志19:50:05.038/.066/.068连续PAUSED后再BUFFERING。这个人为暂停不属于用户暂停，新增同一三轮真实框架检查，禁止播放请求中的交接发布PAUSED（既有NONE/STOPPED与真实失焦暂停/合法Stop检查保留）。当前只加断言，产品未改；本地/设备结果待执行。

下一候选用短期ForwardingPlayer交接快照，已恢复非空新队列对外保持BUFFERING/PWR=true，旧player在解除发布后再pause/release，最后启动并发布实际新player。不是过滤任何系统Stop，也不把新音频提前启动来抢焦点；需要同检查红绿和EQ真实幅度组合通过。另审计快速原生Playing可能在Main订阅排队期间丢失，待用UNDISPATCHED同步建立事件订阅，不增加整脸贴图/新用户状态标志。


## 交接快照候选已构建，等待强化框架红绿

3c853ab6d951722ac1e05b87c3ee8992db57f4a7 /CI40761055147只增加“请求播放的三轮往返不得出现PAUSED”断言，设备结果待取得；未改产品。本地设备包构建通过（17秒）。

产品候选增加core/playback/PlaybackHandoverPlayer（短期ForwardingPlayer，无队列/解码器/服务）：新队列prepare后先以BUFFERING和原播放请求交接session，再暂停/释放旧player、恢复实际新player PWR，最后解除快照。旧player的pause已不在发布期间发生；真实暂停/失焦/Stop不被过滤，未启动新音频抢旧焦点。Flick的SharedFlow订阅改UNDISPATCHED，在构造的Main调用内立即建立，避免新Playing在订阅排队期间丢失；后续事件仍调度Main。28 JVM/主程序/设备包本地构建通过（16秒），31根脚本通过；强化47项设备尚未运行，不能称系统Stop已解决。

新增只读CI诊断：记录qs_media_resumption原值，不修改；失败/超时/不完整时保存限定媒体相关的SystemUI与media_session dumpsys，帮助核对旧移除时序。只在本轮新建的隔离CI模拟器执行，不接用户设备。元数据不包含账户/音源凭据，根失败证据契约检查保留两份快照。


## 交接PAUSED断言已红，保留EQ的间歇性事实

3c853ab6d951722ac1e05b87c3ee8992db57f4a7 /CI40761055147：47项45通过/1失败/1登录条件跳过（110.196秒）。唯一失败currentFrameworkStopStillStopsRealNativeAudio的新交接断言，多次收到STATE_PAUSED=2；EQ三轮本次通过，不能将此前EQ失败改成通过或将该轮视为已确定所有Stop根因。真实竞争焦点/末尾Stop步骤在新断言后，因前置失败未执行。artifact12849106286 ZIP SHA2563aa644ae70f7912354669dc14a1f045f280fae64236b7ca3f385346803c8cb78已下载核对。与待测8dbc4d6使用同断言，保持原始失败和已有系统Stop正控制。

主线21bd59574b4ab854d59bb52196eb329bb4e70896只更新恢复入口，应用仍0.3.2、Release不变；已正常合入本开发分支，仅处理README/交接/计划文档冲突，不重复应用构建。主线读者可发现PR16实际候选与失败，不再误续早期0.3.3断点。8dbc4d6的47项设备验证独立，文档合并不改变该应用源码或取消运行。


## 2026-10-30恢复：0.5.1交接快照通过完整47项

固定源码8dbc4d658dbe0643ec988d116120eee0ec57afd1，[CI40761643034](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40761643034)，build job123078518334、device job123081681602均success。47项46通过/1 SourceLoginLiveTest真实登录条件跳过，94.027秒。修复前3c853ab /CI40761055147的人工PAUSED失败保留，此前EQ失败不改成通过。

三轮引擎交接不发布NONE/STOPPED/PAUSED；三轮EQ真实幅度0.13085723→0.18598484→0.13141343、0.13044979→0.18598488→0.1309964、0.1304184→0.18503658→0.1309964。44.1/48kHz保存参数、外部永久失焦暂停/显式恢复、合法framework Stop及1秒不复活通过。延迟旧HTTP不得抢占30秒本地PCM、缓冲Stop、下载完成仍暂停及新导航/译文/12秒行定位保持暂停/返回通过。

设备artifact12849017358 ZIP SHA256166c59466afc2ceabca47b02b2b68bc64d7f5e121cfa0f0f7737fae17308d1ab已下载核对。实际查看layout-check/navigation-home.png、lyrics-full-page.png、portrait-2.0.png和visual-check/player-rust-playing.png、player-rust-paused.png，完整人物/闭眼播放/睁眼暂停在静态图中无明显头帽/手错位；素材/字体未改。构建artifact12849616973 ZIP SHA25633f7deb7627e2b3c7e6b60a5451e5973103f2677e0e960f38490fb891c55a6a8未下载，不计为确切R8交付。

build.json明确instrumentation=passed、release_start=not_requested；媒体续播设置为字符串null，未改OS设置，不能解释为关闭。不宣称所有历史SystemUI Stop根因穷尽，真实Stop未过滤。1070706仅合主线交接文档，应用与8dbc4d6相同。28 JVM、本地构建16秒和31根检查通过；主线普通/自用R8及独立47项、确切通用包跨应用、Release待执行。无新增实机/真实账号写入/声学听感结果。


## 2026-10-30：0.5.2歌词核心拆分（设备待执行）

以已合入main493a9a0为父，在agent/eplayer-lyrics-core独立开发，不改变冻结的0.5.1交付。LyricParser/TimedLine/LyricsText从Compose文件移入core/lyrics；LyricsRepository接收目录与suspend加载器，只负责数据缓存，不依赖Android Context、SourceStore、ViewModel或Compose。app/LyricsLoader保留原lyric/new请求和lrc/tlyric映射，缓存SHA键/JSON兼容。

缓存以请求独占半文件和同目录ATOMIC_MOVE/REPLACE_EXISTING发布，finally只清自己的半文件；加载器吞掉取消也不能继续发布，损坏/缺字段缓存重新获取。4项新JVM测试覆盖离线跨实例、并发加载及完整JSON、取消、损坏数据/无歌词不缓存；原6项解析测试移包保留。Android新增UUID独占目录检查实际原子替换/离线读/坏缓存恢复，不碰用户歌词或源模块。

最终本地32 JVM全部通过，debug应用/设备包构建24秒通过，31仓库检查通过。预计完整设备48项，尚未执行；没有新截图、R8、实机、账号或声学验收结果。0.5.1主线CI41104525573独立继续，不能用新分支结果替换它。


歌词拆分后审计R8反射边界，原cp.player.app下三项keep同步移到core.lyrics，并精确保留Gson缓存DTO，避免只在debug/fastRelease可用。修正首次并发建目录时mkdirs返回false但另一请求已成功创建目录的竞态，使用Files.createDirectories统一处理。该增量编译/32 JVM已复核通过（20秒）；Files.createDirectories最终增量也构建通过；R8仍未执行，不因keep配置存在称混淆后通过。


## 独立队列恢复：修复前真实播放器检查待执行

agent/eplayer-queue-resume基于0.5.2歌词候选9b76ed3，产品QueueStore未修改。新增两项真实ExoPlayer检查（不prepare、不发网络、不打开音频输出）：过滤当前曲之前的无URI项后，应保留当前曲和12秒位置；当前曲不可用时，下一有效曲不得继承旧12秒位置。现实现直接对过滤后列表clamp旧index，代码审计有错曲/错位置风险，但设备失败尚未取得。

测试只将eplayer_queue重定向到UUID专用SharedPreferences，使用虚构file URI且不准备解码；finally释放播放器并删除该UUID偏好，不覆盖用户队列/历史或导入。首轮设备包编译失败（夹具Song漏填album），设备未执行；此前误记编译通过在此更正。补齐字段后本地设备包编译15秒通过，设备仍未执行。预计整轮50项，设备待执行，不能提前称红绿。0.5.1主线R8交付与0.5.2歌词验收独立继续。


## 队列恢复修复前：两个真实断言失败；候选保持原始索引

3b9641e6ba9c0b8978a50061e95556ad1ccb6959 /CI41106489854，build124238698775成功/device124240444820失败。50项46通过/3失败/1真实登录跳过（103.480秒）。新增两项分别expected saved-current却得到next、expected position0却12000；第三项是已知LiveEqualizer Stop，并未把整轮红色归为只有队列问题。artifact13001388977 ZIP SHA2568c00ad5e72160b3735543d790940cbfa937d95c30a7e7cbfbee5f7d60bed94a4已下载核对。更早9b75503 /CI41106319134为测试夹具album缺失导致构建失败、设备未执行，不计为功能红色。

产品QueueStore.restore改为过滤时保留原始索引；当前曲仍有效则恢复原曲/位置，当前无效则后继有效曲从零开始，末尾缺失回最后有效曲从零开始。使用已有唯一Player/队列，不改源标识/质量/序列化格式，不启动prepare或联网。原两个断言不改，新增尾部缺失分支检查，预计完整51项。仅UUID专用偏好/虚构URI，无用户队列或导入变更；候选32 JVM/应用及设备包本地构建27秒通过；失败时快照增量设备包另已编译通过，整轮51项设备待执行。


队列候选同时沿用149c5227e的只读SystemUI缓冲，并在LiveEqualizer失败截止点（固定原断言结果后）抓取MediaCarouselCtlrLog/MediaTimeout/MediaLog，避免整轮结束前100条缓冲覆盖故障时间。仅明确CI参数eplayer.mediaDiagnostics=1且ranchu/goldfish模拟器启用，输出到本轮UUID文件；不变更系统设置、不延长等待或重试失败条件，不影响通过/失败判定。script拉取system-media-check，正常绿色基线149c5227e /CI41107199704为47项46通过/1登录跳过（79.036秒），其完整缓冲已核对artifact13002716783 /SHA25645ac7deac7ac76876494409531d5cf74a4d32b4b3d59dd2477dee3423b16def4；该轮并未复现Stop，不能作修复证明。


## 0.5.1主线独立重复：EQ再次被SystemUI Stop，发布阻断

main493a9a0f0d73b859d64b28e4343d46e3e3ef048c /CI41104525573：普通/自用R8构建成功；build job124230387594，device job124235507885失败。47项45通过/1失败/1真实登录跳过，93.272秒；唯一LiveEqualizer启动失败，原生stopped、position80ms。三轮框架交接断言/正常失焦/合法Stop与其他分项通过，不能据此将整轮算成功。R8冷启动因前置失败未执行，确切包跨应用未执行，未发布0.5.1。

18:22:04.809发布真实PLAYING，05.171原始MediaSessionService调用包com.android.systemui、reason MediaSessionRecord:stop，05.180进入MediaSessionLegacyStub.onStop/FlickPlayer.handleStop。公开回调身份仍映射为应用通知控制器，不能以其sameUid证明应用自身发出；没有过滤合法Stop。最后SystemUI dump useMediaResumption=true，secure设置原值null不等于关闭。需要媒体日志缓冲的移除原因，不能继续仅猜PAUSED根因。

设备artifact13000628932 ZIP SHA2568e5e8b1903c478430dd1ef10a789bc014a950d7f6c2efe28eabfd6396ca9369d已下载核对，包含runtime/system-media/system-sessions及未通过结果。构建artifact13000408541 ZIP SHA256a5bc40adc53af6cd1af5a7051a52b8c17b47292a129c70458619c34682c78906已下载核对。原始standard universal APK44,773,259字节，version0.5.1/code146，SHA256544f3fa7ac72ec46aa767e7089de3b9cf284ad3db24ba463c7f8e3d0855ea63d，证书b9785b963d3af11067911f469884397c7c84fc6dca00dc942b8893e6ee8a7689，V2有效、min28/target36、三ABI。静态校验不是播放通过；不得将此失败主线包送入要求successful main的交付门禁。旧0.3.2附件保持。

独立歌词36d5c875b40a9571fc43daa600db46cf25ca42d3 /CI41105257305：48项47通过/1登录跳过，108.620秒，含真实Android原子缓存发布/损坏恢复及旧歌词UI/会话定位。artifact13001596264 ZIP SHA2561de245cd6659693af4e0257be6c7e88c46b057bf2463066aa102b99356931ffe已下载核对。后续9b76ed3同步R8保留规则/建目录后，CI41105793719待结果；本轮R8未执行，不能用歌词分支通过替换main失败。


## 会话连续性候选：统一公开代理，移除临时会话Player重绑

agent/eplayer-session-continuity接续诊断149c5227e，基线应用为失败的main493a9a0；不混入歌词/队列或本地PR17的界面。删除PlaybackHandoverPlayer，使用Media3提供的ForwardingSimpleBasePlayer作为服务整个生命周期唯一公开SessionPlayer；在其setPlayer中解除旧delegate监听，再暂停/排空旧输出/焦点并启动新delegate，最后投影新状态。交接保留原用户请求与BUFFERING；命令由Media3原有转发处理，合法Stop/焦点仍有效。服务销毁通过代理调用CPPlayerManager释放实际输出和Exo音效，不再重复释放实际player。

本地初次28 JVM/主程序/设备包28秒通过，错误态快照边界最终增量28 JVM/构建5秒复核通过；整轮47项设备尚未执行。系统媒体缓冲诊断CI41107199704独立运行，尚无其移除原因，不宣称候选已解决所有Stop。保留截图/素材/字体/默认双曲与导入、完整音乐协议，不改系统媒体设置。


## 通知连续性：旧应用代码上的强化检查待执行

诊断149c5227e /CI41107199704为47项46通过/1登录跳过（79.036秒）；不会把未复现Stop写成修复。其已校验的SystemUI缓冲可见非用户移除，但对应正常清理。新增实际NotificationManager.activeNotifications观测，先确认真实媒体通知存在，再仅在三轮播放中交接区间每5ms只读本应用媒体通知；结束/停止观察后才发送合法失焦与Stop。若采样看到消失则失败，不能把未采样到消失称为绝对不会移除。原框架状态断言保留。

仅修改设备测试/规划，应用保持原主线代码；不更改POST_NOTIFICATIONS、系统媒体设置、来源或签名。新增观察线程限定该区间、finally关闭并等待，正常清理不算通知回归。本地设备包编译12秒通过，实际设备结果待取得，用于与持续公开SessionPlayer候选对照，而非靠放宽断言或重跑同代码凑绿色。


## 持续SessionPlayer现有47项通过，正常合入强化通知检查

固定eadde4bc487ddccd82205e63e87276d9102c326b /CI41108013086：build124242832977/device124244551215成功，47项46通过/1真实登录跳过（92.607秒）。真实EQ三生命周期/跨采样率、三轮框架交接、正常永久失焦/显式恢复与合法Stop/1秒不复活通过。设备artifact13002956552 ZIP SHA25616bf3b12ab54a70475519ebfd05110f2f3eeb160691fbcd9f05a94b6f8ac3ee9已下载核对；R8 not_requested，没有凭此绿色发布或宣称全部根因穷尽。

正常两父合并e1eb7fe，把相同真实媒体通知观测用于本候选；两处文档冲突按共同前缀/后缀确认仅双方新增段后完整保留。旧应用e1eb7fe修复前47项独立运行，产品仍eadde4bc而强化检查设备待执行。本次28 JVM/应用和设备包构建21秒通过，强化47项设备待执行，主线失败/Release阻断仍有效。后续与PR18/PR21正常汇合形成最终组合，不覆盖本地PR17或固定签名/模块/登录。


同期PR21 b012298e /CI41108850985：51项49通过/1 EQ失败/1真实登录跳过（110.748秒），两个旧队列断言与新尾部缺失均通过，整轮仍未通过。artifact13001738799 ZIP SHA25630c1456c341ab3bdd062291ffb847fcf7ebb24c55396ed74bd5c8fea4aab94de已下载核对，故障瞬间system-media-check/equalizer-b9b45276-a831-4bc3-bb33-88760328321b.txt实际生成。18:46:17.265 MediaCarouselCtlrLog记录removing player/by user false，17.275原始SystemUI发送Stop，17.288进入FlickPlayer.handleStop，失败停止于130ms；已将移除与停止对齐，但具体触发移除的先前状态仍需通知观测/持续代理候选验证，不能称穷尽根因。没有延长原断言等待、变更系统设置或过滤Stop。


## 0.5.2歌词最终候选：48项通过，R8仍未执行

固定9b76ed3bfe70607883a3dd0d3dccd9ab3d4c3312 /CI41105793719，build124237178663和device124239009750成功，48项47通过/1真实登录条件跳过，107.921秒。设备artifact13001293160 ZIP SHA25636f067617f073b370298dfada461797adaf454f2838a514069be28305764e4b1已下载核对，instrumentation=passed/release_start=not_requested；构建artifact13002475944 ZIP SHA256aea0e8359b27fe7ad8f23ae0d38b6194428ccbc42e6ebc43484614807a19edac未下载，不计为R8验收。

包含Android真实Files原子替换/坏缓存重取/离线读取和既有译文/12秒行点选/保持暂停/返回。此前36d5c87的48项也通过（107.921秒为本最终轮，前轮108.620秒）；已查看前轮原生播放闭眼及全屏日中歌词截图，人物和字体未修改。同步保留规则并不等于实际混淆后通过，R8仍待后续主线一次验证。

main493a9a0 /CI41104525573为47项45通过/1 EQ系统Stop失败/1跳过（93.272秒）；其两版R8构建成功，但冷启动前置阻断/确切包未执行，未发布0.5.1。本分支通过不能覆盖主线失败。所有既有失败/未执行保留，PR19内部SystemUI媒体日志和下一会话连续性候选独立推进。


## 0.5.2最终核心组合待验收

正常汇合队列b012298e、持续会话/强化通知2c3f77d、歌词最终文档e364f5c与主线d9dc2dad四父历史，未强推。应用0.5.2，保持PR11/PR14音乐完整路径及角色/字体资产，未混入本地PR17界面或用户模块/签名。最终组合包含51项检查，不能把会话独立47绿色、歌词48绿色和队列三个分项通过计作组合绿色。0.5.1失败主线包不发布，后续以最终0.5.2源码执行一次主线R8/全轮/确切包交付。

文档冲突保存四份阶段计划原文并重写当前实施顺序；验证索引以共同基线加双方新增记录完整保留，歌词最终证据另追加。Android Cloud入口/其他项目未改，所有二进制仍在Git外。本地32 JVM全部通过，应用与设备包构建24秒通过，31根检查通过；51项设备结果待执行，不提前称组合通过。


## 强化真实媒体通知：会话候选47项再次通过，组合51项运行中

2c3f77d89502491574cce937a18fce7dcfab479e /CI41110061129，build124249857674与device124251636642成功：47项46通过/1登录跳过，115.051秒。真实通知存在后在三轮播放交接中采样未见消失，框架NONE/STOPPED/PAUSED保护、真实EQ、永久失焦与合法Stop/1秒不复活均通过。artifact13002389697 ZIP SHA256f81930ed9aed5d018da4285df5960513eca2cdd3b971109cc62c9254685a016d已下载核对。采样通过不证明更短间隙绝不发生，也不穷尽所有历史停止根因。

旧应用e1eb7fe /CI41109753203也47项46通过/1登录跳过（88.435秒），本轮未复现消失/Stop，不将其视为修复前功能红色。组合e4ea4cb1a4da92fb0c495c4a28986c4282189f4d /CI41110662240构建成功，设备51项正在执行，不能沿用两轮47绿色。已正常合并main4202d33恢复指针，双方独有结果与当前计划保留，后续仅文档变化，应用/测试仍e4ea4cb1。31根检查待本次提交复核，R8/确切包/新Release未执行。


## 0.5.2核心最终组合51项通过，准备一次主线R8

固定应用e4ea4cb1a4da92fb0c495c4a28986c4282189f4d /CI41110662240，build124251916316/device124253588193成功。51项50通过/1 SourceLoginLiveTest真实登录条件跳过，103.810秒。持续SessionPlayer的真实EQ/交接/媒体通知采样/合法失焦/Stop、原子歌词缓存与译文/暂停行定位/返回、三个队列恢复检查在同一轮通过。32 JVM及本地应用/设备包24秒、31仓库检查通过。

设备artifact13003099676 ZIP SHA256a3820db126bdccc90cbea1177214238a1866ef7b888937a66f0fe4de5e0c8693已下载核对，source_commit完整匹配e4ea4cb1，instrumentation=passed、release_start=not_requested。fast APK SHA25686231f2438a1927e8dca2c450a85bcea6bc5e9aed43bcb8d2b73b2fcfacd49ad；测试APK b3fb782fee290cabc0ceb30992b001332f53e66fb025007a571591d34b085d95。已实际查看visual-check/player-rust-playing.png、player-rust-paused.png和layout-check/portrait-2.0.png，人物完整、闭眼/睁眼与主题文字在这些静态图中无新明显错位；字符/字体资产未改，不称逐帧/长时/实机听感验收。

c83f0ae已正常合入main4202d33恢复指针，应用/测试与e4ea4cb1相同；后续记录仍仅文档变化。旧0.5.1 main493a9a0失败与b012298e整轮EQ失败完整保留，未将其写成通过；不宣称所有历史SystemUI Stop根因穷尽。下一步以预期head正常合入最新main→普通/自用R8与独立51项/冷启动→原始universal确切包双引擎跨应用→0.5.2 Release归档，尚未执行的新门槛不提前算成功。


## 0.5.2正常合入主线，R8独立门槛正在执行

PR22以正常merge进入main c17dc4a165702391b99d624ad281236bcc46240f，所有四父/后续文档父历史保留，未强推。PR18/19/20随祖先提交自动merged；队列b012298e也经merge-base确认是main祖先，旧PR21因base是歌词分支仍open，将关闭并保留分支/证据。PR17本地界面及签名/模块/登录不改。

CI41112575118执行一次普通/自用R8、独立51项和普通x86_64 R8冷启动，尚无结果；确切原始universal跨应用/新Release未执行。已通过分支e4ea4cb1与main应用/测试相同，但不能借其50通过/1跳过（103.810秒）冒充主线结果。后续仅文档更新，不重复触发大构建。31根检查在此恢复提交前复核。


## 0.5.4独立数据边界：本地33 JVM/构建通过，设备待执行

从main639a19dc（应用c17dc4a/0.5.2）开agent/eplayer-data-boundaries，0.5.3本地PR17不动。梳理旧app/LibraryStore.kt四职责后，原逻辑分别移到core/library/Track、LibraryStore、RecentStore和core/queue/QueueStore；MusicService直接引用核心队列/历史，UI/source/download/设备检查更新导入。四核心文件无Compose/ViewModel/app引用，无重复持久化或旧包转发别名；SourceStore/ListeningStats边界尚未迁出，不能声称完全解耦。

保留原JSON键/偏好键/文件名/内容URI与source/remoteId、原索引恢复算法。TrackSchemaTest使用旧版字面JSON而不是新实现生成输入，检查导入ID/日文标题/时长/URI/源/远端ID及原字段完整往返；原32项不删，总33 JVM全部通过。应用和设备包本地构建34秒通过，R8精确Track/LibraryStore规则同步迁移，但本阶段R8未执行，不能把规则存在当混淆通过。整轮51项设备尚未执行。固定0.5.2主线CI41112575118仍独立运行，两个版本结果不得互换。


## 0.5.4独立数据核心51项通过，R8仍未执行

固定04d60cec9b514e82cf2983df018045080bf4c2cc /CI41113754192，build124262515729/device124264207759成功：51项50通过/1真实登录条件跳过，103.113秒。旧字面JSON的33项JVM、本地应用/设备包34秒、31根检查已通过；导入/源元数据/下载/队列恢复/最近播放清理与其余核心51项实际通过，R8 not_requested，不能据此声称新包已混淆验收。

设备artifact13005706329 ZIP SHA256c7eb038d1bea85f525cda7b877cd5a7e382dbaf1bbcf48842a3d35fb4283dd4a已下载核对。真实依赖复核纠正此前过粗表述：MusicService当前直接反向引用app.ListeningClock/ListeningStatsStore；app.SourceStore的实际调用在service/BackendDataSource，provider两处只是文档引用。核心库/队列四文件无app/Compose/ViewModel依赖；没有因包名删SourceStore或音乐源能力。主线0.5.2/c17dc4a的R8与独立设备门槛仍单独运行，版本结果不得混用。


## 恢复浏览契约修复前：测试已增加，产品尚未修改

agent/eplayer-media-resumption从数据边界32479af开0.5.5。RecentBrowserTest遵循SystemUI所用传统MediaBrowserService接口，要求服务可发现且导出、EXTRA_RECENT连接/子项可播放/当前项ID和标题，并验证不prepare/不启动音频。只用UUID专用音频/队列，先要求用户队列空，finally移除本次历史/文件并清自己队列。原MusicService/Manifest尚未改，因此缺失契约的设备失败尚待取得，不能提前称红绿。预计52项，33 JVM/应用及设备包本地构建24秒通过，设备52项待执行，R8未执行；不修改媒体系统设置或过滤Stop。


## 最近浏览修复前红色与MediaLibrary候选

098e2501f0055133fe8d19bd1e3055b72a5d0f7a /CI41116950332：build124273543573成功，device124275009533失败；52项50通过/1浏览服务发现失败/1真实登录跳过（109.592秒）。唯一RecentBrowserTest.systemRecentBrowserCanReadPlayableMediaWithoutStartingIt断言“SystemUI must discover an exported recent-media browser service”；旧51项本轮通过，不能把未复现EQ Stop当成根因已解决。artifact13007535717 ZIP SHA25612d6cd11b395dbb747d694aed695b529f6cb40aa3515dfb43ca9a58d48f42846已下载核对。

候选保留MusicService组件与旧MediaSessionService action，新增MediaLibraryService/传统MediaBrowserService接口；唯一MediaLibrarySession继续绑定SessionPlayer。按同UID/平台isTrusted授权连接，正常Stop没有按来源过滤。RecentMediaCatalog只读元数据，不prepare/源请求/音频输出；只有显式onPlaybackResumption返回现有队列/位置或最近项。补充MediaBrowser只读日志与双引擎显式Play清队列后恢复30秒合成WAV进度/AudioManager输出检查。浏览检查保持不自动播放断言；既有合法Stop/永久失焦/1秒不复活检查保留。

33 JVM/应用与设备包首轮构建23秒通过，新增恢复检查增量6秒通过，31仓库检查提交前执行。预计53项设备尚未执行，R8/确切包/Release未执行，不以旧51/52或本地编译代替。Root/最近项JSON不加入用户凭据，不上传录音/模块/签名，人物/字体和Android Cloud入口不改。


显式恢复夹具补强：两引擎均先让30秒合成WAV真正推进且AudioManager输出激活，再停止/清空，随后显式Play并再次要求真实进度/输出。避免把尚未实际播放、其RecentStore过渡事件可能被合并的队列项当成“最近播放”。共用同一等待 helper，不延长15秒期限、不重试失败；产品代码与3914ba6相同，53项设备待执行。

## 0.5.2主线独立失败：恢复浏览契约缺口已确认，发布继续阻断

c17dc4a165702391b99d624ad281236bcc46240f /CI41112575118：build124258420042普通/自用R8成功（8分28秒），device124264682607为51项49通过/1 LiveEqualizer失败/1登录跳过，106.585秒。不是SDK离线准备失败；前期adb离线属于开机等待，实际51项已运行。普通R8冷启动因仪表失败未执行，原始包跨应用未执行，未发布0.5.2。

设备artifact13004693255 ZIP SHA25621fa42fb93ac61bd1ec63d6059f1793f6094e9cf6d16c9015abe2bb77feac283已核对。即时system-media-check/equalizer-1e9959c6-d640-4abc-a9af-62e81b24264a.txt对齐19:24:26.078非用户移除、26.084原始SystemUI Stop、26.088原生Stop，position1100ms；useMediaResumption=true/hasCheckedForResume=true/resumeAction=null。源Manifest未声明android.media.browse.MediaBrowserService且MusicService exported=false，明确缺少SystemUI所查的恢复入口。补齐MediaLibraryService/最近项浏览与显式恢复的效果仍需红绿与正常Stop验证，不直接断言穷尽根因。

已下载构建artifact13004232952并核对ZIP SHA2567ca7909c2d001698accf5e54027897c1ebdb276d39d924b30dbba57407d010ba。原始通用APK44,773,259字节，0.5.2/code164，SHA2565c259827cafff31d2d8c933e2cc449d66a9e3aa04a803bcd0d77cbf431d6eaac；证书bc19ace988ec3ff6381851c4699039ebd137a2f6819880e2d7f1aa91514dc5bf，V2有效，min28/target36、三ABI。静态签名不是播放验收；准备的blackbox manifest只在Git外，未用失败main触发确切包门禁。GitHub步骤字段曾前后不一致，按完成日志确认实际R8构建成功，未把步骤滞后误计失败或重跑。


## 0.5.5第一轮真实失败与接收器修正

3914ba6 /[CI41118331969](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/41118331969)：53项49通过/3失败/1真实登录跳过，118.318秒。EQ被系统Stop，传统浏览构造发生在无Looper测试线程，显式恢复前置assertFalse与Exo.stop保留playWhenReady语义不符。设备artifact13006654026 ZIP SHA256dcabfd2a867d8a8ab2b2e1d14c894af4ea837690244fe575e06264b393b6e3f6已下载校验；SystemUI发现并连接服务，但resumeAction仍null。截图可用不代表整轮通过；本轮未修改图像或字体。

14ee342 /CI41119090920构建通过/设备失败，artifact13007288524服务端digest dcfba0fd5fd1a1e18be61fd2e666497acf92922909445372d903e15c77be0590；下载与原始job日志接口返回内部错误，未核验ZIP或取得项数。1d18d3b只修主线程构造，不能代替后续产品候选。

cbe699f补标准MediaButtonReceiver：实际Media3 1.10的canResumePlaybackOnStart依赖Manifest接收器，因此此前SystemUI EXTRA_RECENT在到达自定义浏览前已被拒绝。三参数恢复回调按isForPlayback规范化只读最近项/保留主动恢复队列，固定日志增加MediaBrowser/legacy/MediaSessionImpl。测试先实际播放，再pause/stop/清空/主动Play，不放宽时限或输出要求。33 JVM及应用/设备包14秒、最终增量2秒、31仓库测试通过。远端53项/R8/确切原包/Release待真实结果，未宣称Stop根因解决。


## 2026-10-31 PR25组合恢复验证与TTML缓存红绿

基线28ab907c/62285a6首轮49 JVM为46通过/3失败，1分40秒；失败均为新增fetchedAt字段与旧零值完整对象断言不符，后续应用/设备任务未完成。注入固定时钟后，e1f8bff6目标6项5通过/1真实产品失败（24秒）：普通LRC为空但逐词/TTML有效的歌词首次写入后，离线读回变成暂无歌词。不是简单放宽字段比较。

候选用可空CachedLyrics DTO验证/归一旧JSON和丰富字段，读写同一内容判断允许TTML-only；原子替换/并发唯一半文件/取消检查保留，精确R8保留规则更新。Android UUID文件夹检查也覆盖丰富缓存离线读。完整51 JVM零失败零跳过、主程序及设备测试包36秒通过；当前设备/R8/确切原包/Release未执行。浏览夹具在连接前明确pause/stop并断言PWR=false、IDLE且无音频输出，之后保持原子项/不自启断言与原超时。

旧cbe699f /CI41120514646为53项51通过/1浏览暂停断言失败/1真实登录跳过（109.435秒），设备artifact13007919336已核对SHA256373287ab3993d3ccad9d6cd71a3280217aeb33d96e30f01abb0df7b3f5bfdf67。其SystemUI快照已有非null resumeAction，日志确认双引擎显式最近播放进度；不能拿该轮EQ通过证明全部历史停止根因解决。当前PR25组合仍需独立设备验收。


## 听歌统计数据归属与原生三ABI基线

ListeningClock/ListeningStatsStore及模型归core/listening，唯一统计StateFlow/偏好日期键/毫秒累计与去重逻辑原样保留，MusicService不再引用app统计类。原两项实际时钟语义测试同步归核心；完整51 JVM及应用/设备包33秒通过。设备与R8未执行，不借前一0780028的在途设备检查冒充本次源码通过。

Rust tree3ee21a635d50079652c6c803b70451ffd300fa0f的API28离线release基线实际三ABI编译，各12警告；31个RustEngine/JNI入口一致，ELF架构核对。以下不是APK替换或运行验收，也没有Trace优化改动。

| ABI | 字节 | SHA256 | 编译耗时 |
| --- | --- | --- | --- |
| ARM64 | 5807552 | 58c58afeb0cb136c07e2b1e7b75076f1b7ff1e23702041c9416bebe333a40540 | 1分55秒 |
| ARMv7 | 4367888 | a4802361349a1c5f518cbe1390e5df08246126a69b182073891a71c3248b8111 | 1分49秒 |
| x86_64 | 5606560 | 1888fef2f957d8fcdfc557890ed18870d6cc36dfa6c044b7318e78e3a4ee14d2 | 1分59秒 |

原生库/原始日志保留仓库外待相应Release归档，未提交Git；已清理先前被替代的生成app/build以释放空间，源码、用户私有包与证据压缩包均保留。


## PR25首轮组合68项真实失败及双曲入口恢复

0780028 /[CI41227861235](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/41227861235)：68项65通过/2失败/1真实登录跳过，115.091秒；设备artifact13052508017 ZIP SHA2563e29d9a39e6144d4ea6544d86f0e9b1e4d708328bff0ccdb9b4b890c0a30d750已下载校验。失败一在浏览尚未连接前的AudioManager active基线，另一为LightNavigationTest找不到默认魔女分组。不能把前者当浏览自启，也不能删掉默认入口断言来通过。该轮含真实双引擎播放/进度/通知、丰富歌词与新界面等通过项，不等于整轮验收。R8/确切原包未执行。

已实际查看visual-check/player-rust-playing.png：人物/帽/手与闭眼状态同素材，当前灰紫背景仍偏离纸白蓝天目标，后续统一；未据静态图断言连续动画或修复新面部纹理。

候选恢复默认双曲元数据和独立导入/更换入口，旧音频与全曲库/扫描/下载功能保留，不附录音、无参考截图封面。浏览前的公开IDLE不证明异步原生输出已停，增加最多5秒的仅连接前输出排空等待；保留前后状态及AudioManager断言、原浏览时限，不在浏览后暂停掩盖副作用。完整51 JVM及应用/设备包最终13秒通过，新68项待执行。


## 纸白蓝天视觉候选（尚未设备验收）

统一PaperColors/NightColors为纸白/天空蓝/深墨与夜间蓝灰，现有Medium中日文字和合体底栏/导航/预测返回保持。PlayerScreen在songArtwork为空时恢复原WitchPlayerView天空层，存在封面仍用原前景人物/外延腿脚；未修改图像资源、脸/发/手图层或逐帧表情。51 JVM及应用/设备包19秒通过。主要语义文字8对静态对比度5.51至14.58达标；具体页面、连续动画与实机未验收，不能用旧灰紫截图证明新主题。当前629d5a7设备仍待结果，最终新主题须合并后按完整门槛截图核对。


## 629组合仍失败：原生输出未释放与测试契约修正

629d5a7 /CI41229078993：68项64通过/3失败/1真实登录跳过，106.161秒。artifact13053445692已核验b4e729bb9d9b10b5092299a8afb960543975c79ee83f7c2fafd6bd2c1f6f453e。默认双曲旧导航检查通过；剩余为浏览前等5秒仍AudioManager active、LibraryFlow旧无默认曲目断言与当前用户要求冲突、Espresso对未获焦Activity根注入Back失败。均保留，未记为绿色。

实际源码Stop只清空音源、没有关闭音频线程；新增nativeShutdown接既有audio_shutdown，适配器释放时在原串行调度器等待线程结束，非在浏览后强制暂停。显式恢复检查新增退出原生适配器后的实际输出消失要求；曲库契约保留默认双曲，面板等待实际Dialog获焦后只注入一次Back。release JNI Trace降到Info，但运行减少尚待设备。

新原生API28源码构建ARM64/ARMv7/x86_64均通过（7.44/7.05/18.80秒，各12警告），三ABI32接口一致；实际旧31接口基线被来源收集器拒绝。新原始库（非AGP剥离后的APK库）SHA256依次eca075bd3109478b18f8b503ed2d9ba385ad87651b9e5a152271ccab944288e9、d9f083d5256e74402da6fab0f50f547a61d822cf22f6968ebdf8ea1dfb99fd6d、01a42596b90fa32bd6fd9f28420d56a58a71ac8320628ab383c89bf2f8fbd3da。本地完整51 JVM及应用/设备包最终7秒通过；新设备、R8、确切原包与Release未执行。CI改为从当前源码实际编译原生库，另存来源JSON/原始库/日志，不再用旧0.1.0库证明新实现。
