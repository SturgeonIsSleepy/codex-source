# ePlayer 转移交接

0.4.0 基础功能已由本地 agent 交付：源码 `1d74691c36e17a519d4bacea087c79e3f9d38b0e`，分支 `codex/ncm-basic-completion` / PR #11。纳入 main 38692c9，并修复旧异步 Stop 回执造成的启动空状态和真实 NCM 歌词 JSON 直出。CI 40686266872 完整成功（39 通过/1 登录条件跳过）；最终固定签名普通包另经双引擎 2 项跨应用测试，自用包覆盖安装和真实歌词显示通过。双版本 versionCode120，同签名保护既有登录；自用模块未公开上传。包摘要、修复证据及边界见 [0.4.0 交付记录](../projects/eplayer/docs/DELIVERY-0.4.0.md)。本轮未连接手机、不代替实机或功耗验收；后续不要用历史 0.3.x 临时 CI 签名覆盖用户自用版。

已发布的软件基线为 `331d31241159737aec196fe922431898764f6909`：已合入 PR #3/#5 的 JNI 顺序、取消过滤与公开 COMMAND_RELEASE 契约修复。主线 [CI 40379791181](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40379791181) 30 通过/1 登录跳过（76.436 秒），普通/自用 R8 构建与普通 R8 冷启动 1720ms 通过。a25a90b、fb48458 的历史失败保持原结论，不再作为当前待修断点。

**交付 0.3.1** 固定上述源码与原始 universal APK，SHA256 `92b6b3d1f28940db408b033646b9a77233a0d1cd749c7e6c8b1ab6d0623973b3`，versionCode52，证书 `c92db3ca59c4ec286cea2f2b54f085b38d676b8e956a34b17c103fc61e04680a`。确切 R8 两种引擎均通过系统文件选择器导入、播放/暂停/12 秒定位、后台与真实通知点击返回（CI 40383667514、40385418880）。加载截图和约 2.8 秒无声动作预览已查看抽帧。PR #6 已合入匹配源码/构建/APK 的交付门禁。

[0.3.1 Release](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.1) 已由 [CI 40387722240](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40387722240) 发布（ID424674767），五个附件 uploaded/digest 已逐项核对。之前两次 prepare 因 SDK 验签文字前缀失败；第三次精确安装/冷启动 2511ms 及证据校验通过，但创建后列表延迟导致发布失败，均如实记入计划。已按实际公开验签摘要修复并保留草稿 ID。0.3.0 旧附件不改；临时 CI 证书不同，不能保证无缝升级，先备份导入/设置。

**0.3.2 当前候选**：[PR #13](https://github.com/SturgeonIsSleepy/codex-source/pull/13) / `agent/eplayer-stream-cancellation` 接续 [PR #4](https://github.com/SturgeonIsSleepy/codex-source/pull/4) 的原生 DSP、预缓冲暂停、手动 EQ、缓存边界与 USB 独占桥接清理。20 JVM、28 根脚本及本地 debug 主程序/设备包构建通过；0.3.2 尚未发布。main已正常合入0.3.2候选113acb0，已发布包仍是0.3.1；主线CI40570928840已通过普通/自用R8、37项36通过/1登录跳过及普通x86_64 R8冷启动1378ms。确切通用包跨应用CI40573577516的系统/原生2项也已通过；Release发布的最终安装/归档门禁仍待执行。

引擎交接已有真实框架红绿：修复前2de55f3 /[CI40565941188](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40565941188) 36项34通过/1失败/1登录跳过，音频播放及两次引擎往返后框架多次收到STATE_NONE。修复6120be5 /[CI40567120983](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40567120983) 36项35通过/1登录跳过（95.368秒），先恢复替代播放器队列/位置/播放请求再交接，空/停止状态断言通过，合法framework Stop仍在2秒内停止真实原生音频。三轮EQ和其余回归同时通过。设备artifact12764217339 ZIP SHA256c2292ff6a0cac691acda242cd4dc702673649ded5820e73a1072975690c1f08d已下载核对。

历史组合4f35cbb、7f57819、bce1153曾被MediaSessionLegacyStub.onStop中断，失败记录仍完整保留。Media3会把系统UI请求的回调身份映射为应用通知控制器，同UID/应用包名不能证明原始发送者就是应用；没有按来源屏蔽Stop，不能把单轮通过称为所有历史Stop根因已解决。

PR13新增下载取消资源契约：读循环检查取消、异常/取消关闭输出、只删本次未完成文件，成功文件保留。37项候选已通过：f3fb52a /CI40568783358，36通过/1登录跳过（76.455秒），artifact12763938192 ZIP SHA256db13411d737674567c994b3f4d3749d477eaebdf1db5cece3b545206c59283ea已核对；主线R8和确切原始通用包2项跨应用已通过；最终发布安装/归档门禁仍待执行。修复前CI40568511349 attempt1因Emulator下载“Error on ZipFile unknown archive”在设备准备阶段失败，测试未执行、无device artifact；已仅重跑失败job。修复前attempt2已实际执行：37项35通过/1失败/1登录跳过（65.993秒），唯一失败是取消后仍通知ready，artifact12765421803已核对，现与修复后同检查形成红绿证据。跨曲下载所有权与旧回调隔离仍待下一阶段，不能因资源关闭修复宣称已全部解决。

已完成独立红绿：跨44.1→48kHz导致保存EQ丢失，修复后3轮真实幅度保留（CI40392967562，32通过/1跳过，73.496秒，证据12685077511）；联网预缓冲暂停后擅自播放，修复后完成下载保持原生不播放、关闭上游后主动恢复有真实进度/时长/幅度（CI40394832829，33通过/1跳过，83.024秒，证据12686274869）。修复前949c9e3同时有旧Stop与预缓冲两项失败，30通过/2失败/1跳过，完整保留。不能因分项红绿把组合失败写成通过。

其他分项：频率/增益/Q编辑与取消/非法保护，设备32通过/1跳过、截图已查看；USB独占桥接残留删除，32通过/1跳过（证据12684602201）；缓存延迟初始化、真实file/content旁路和断网重读，33通过/1跳过（证据12685806249）。完整源SHA、APK/测试包摘要与截图索引见项目验证文档。人物/表情/动画资源没有改动。原始0.3.1附件保留；其确切R8证据不覆盖新增的两项网络/采样率回归。

独立[PR11](https://github.com/SturgeonIsSleepy/codex-source/pull/11) /codex/ncm-basic-completion的0.4.0账号、歌词和队列成果仍在独立分支；已读取其收尾交接，未合入本候选，不接触固定本地签名、已登录会话或真实账号写入。恢复顺序：修复前失败与候选37项通过均已保存，PR13正常合入main并保留文档→主线普通/自用R8→确切原始包跨应用验收→0.3.2 Release与日志归档。之后再做依赖精简和跨曲下载所有权。未新增实机结果，当前后续CI证据仍待新版本Release归档。

云端未操作实机；本地 agent 继续独占实机安装、音质/路由、焦点打断和真实账号验收。缓存清理界面、淡入淡出、新页面流程及长期动画检查仍有缺口，不能声称完整迁移已完成。Android Cloud 固定下载入口及无关项目保持不变。

历史迁移记录（2026-10-24）：主线为 [projects/eplayer](../projects/eplayer/)，原独立参考版为 [projects/witch-player](../projects/witch-player/)。原 ePlayer 工作树最后提交 `d196c0d`；迁移后的后续工作以本仓库提交为准。

## 首轮云端开发历史（2026-10-25 北京时间）

[已合入 PR #1](https://github.com/SturgeonIsSleepy/codex-source/pull/1)，分支 `agent/eplayer-20261025-playback-layout`；功能基线 `888f1a4`，最新功能提交 `ffad7b3b6c00259635c5c798ba4e16cef18ab6f2`。上述已验证软件基线已合入 main；后续新功能使用单独分支，未操作实机。下一轮先同步此分支和 main，保护本地 agent 的新增更改。

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

## 0.3.0 发布断点（2026-10-26 北京时间）

主线验证：[CI 40222561908](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40222561908)，源码 a62e0fb；25 通过/1 登录条件跳过，双引擎播放/暂停/定位/重播/错误恢复、真实 PCM 静音恢复、后台及系统通知返回已通过。

本地 Release 上传先在 uploads.github.com 返回 401，随后 CLI API 也凭据失效；连接器仍可操作源码。eplayer-v0.3.0 目前是无附件空草稿（ID 423603768），不能称为已发布。新增专用发布工作流从上述成功 CI 获取原始 universal R8 APK，校验 SHA256，API36 安装/启动通过后才归档历史失败/通过证据、原生库并发布；不向 Git 传二进制或私钥。APK SHA256 9106fbbee4154a4c2a4c4b877a4976d364d9e65ba514ff110b6272d69bb49d40；CI 临时证书 8f89883030d7d64aecb6358b43eddeb8e9cd5ac323f8ab9916af4070186f08dc，与 0.1.1 不兼容，未来稳定签名交付仍待解决。

不要发布本地重新签名的中间候选，也不要将其 c5b1… 证书或 aa781… 哈希误记为最终 CI 原始 APK。新增发布工作流尚未运行，最终附件、交付 smoke 和发布状态需回查。

发布更新：上述空草稿已在 [CI 40226257524](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40226257524) 完成原始 universal APK 的精确安装/冷启动检查后发布：[eplayer-v0.3.0](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.0)。五个附件（APK、native-libs、validation、notes、SHA256SUMS）已通过 GitHub API 核对名称、大小与 digest。APK SHA256 与上述 CI 原始值一致。此前发布工作流 40225961951 的精确安装/启动已通过，但按未创建 tag 查询草稿返回 404，发布失败；随后改按 Release ID 核对目标，未覆盖附件。

## 当前 0.3.1 开发断点

PR #2 已实现实时 DSP、整页声音设置、系统能力感知、AutoEQ 有界读取/预览，移除经检索无引用的旧外观访问器。54ba908 的真实 EQ 回归 26 通过/1 登录跳过；ed74b9b 声音页 28 通过/1 跳过；361dd59 AutoEQ 首版 28 通过/1 跳过。19 项 JVM 与本地构建通过。最新 b34e679d5bfb33552b2d59fe3c77a03f967bfd7f 在 [CI 40227528360](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40227528360)，待执行 URI→预览→确认→实际音频、完整设置返回与旧 UI 剥离回归，暂不合入主线。

中间 8c69e39 的 CI 40227292073 在等待队列被后续提交替换而取消，未执行设备检查，不标通过；后续提交包含其全部检查。缓存入口的旧方法会释放仍被 DataSource 引用的对象，旧 clearAll 还涉及历史/本地曲库，已记录审计，不直接接 UI。所有后续操作以分支最新交接为准，保护本地 agent 实机职责。

项目分支的计划与验证索引保存证据 hash；不要重做或覆盖 0.3.0 已发布附件。此处是截至当前的运行断点，后续结果按源码 SHA 回写。

运行更新：b34e679 的 CI 40227528360 已通过 29 项/1 登录条件跳过，73.918 秒，整页往返导航、真实 URI 到应用层确认/渲染通过，整页截图已查看。证据 SHA 与限制见项目验证索引；系统文件选择器/确认弹窗点击仍未验收。当前 PR #2 准备合入主线，主线再执行 R8 构建与启动检查；暂没有 0.3.1 Release。

## 2026-10-27：0.3.1 确切交付候选

主线 331d312 /[CI 40379791181](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40379791181) 为 30 通过/1 登录跳过（76.436 秒），普通/自用 R8 构建通过，普通 R8 冷启动 1720ms。设备 artifact 12678359715 的 ZIP SHA256 为 164c2accbb8f518735a7e513bdc8efb4e60a38adaa01872901ca31b6778b35cd。

确切原始 universal 0.3.1/code52：APK SHA256 92b6b3d1f28940db408b033646b9a77233a0d1cd749c7e6c8b1ab6d0623973b3；证书 c92db3ca59c4ec286cea2f2b54f085b38d676b8e956a34b17c103fc61e04680a，与 0.3.0 不同，备份后可能需要卸载，不能保证无缝升级。API28 起/target36/三 ABI，未重签、未附录音。

独立 R8 两次验收均通过系统/原生两项：CI 40383667514（35.902 秒，artifact 12680016792，ZIP c6df3277cb34cdf12e3caa6f24a3b9dd34acaa0d453f95d37bfa4c172384b68e），[CI 40385418880](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/40385418880)（artifact 12680541471，ZIP 9dcc2e40288d911a956c43b7d11c1cb7000fb98620e46e46ee8a7299d1950634）。报告源码/构建/APK 均匹配上述候选。实际系统选择器导入合成 WAV、暂停/12 秒定位/后台推进/真实系统通知点击通过。日志使用框架位置，不是 PCM 音质测量。

截图证据索引：12680541471 根目录 native/system 的 imported、playing、paused、notification、returned-paused PNG；播放闭眼/暂停睁眼已查看。native-motion.mp4（2.899 秒）、system-motion.mp4（2.769 秒）为有效无声录像，ffprobe 已核对；已抽查 native 6 帧，头帽/手/发线随共同姿态保持，未见白色接缝。不等于每帧/实机/长时动画验收。主线证据的 release-home.png 为默认双曲导入首页。

PR #6 已合入发布防错配门禁；27 项根脚本通过，原始候选 prepare 实际验签/版本/ABI 通过。发布清单已更新，但 Release 发布/精确交付 smoke 尚待自动工作流结果。a25a90b 与 fb48458 两次主线失败保留在新证据归档，不将后续通过改写为旧提交通过。下一步完成交付，再合并主线到 PR #4 的能力/缓存边界分支，保留实机验收由本地 agent 执行。

## 0.3.2 发布准备断点

已准备交付manifest：源码113acb0/CI40570928840，versionCode106，原始universal APK SHA25613c1401d2121e2cde477c2b3d08064366c65b34357e210253c641aa775f9a27c，证书c9ccdf76dd260a4cc48a595e21b940958580b3fe318b75ce3d3e482bbc7a37c0。跨应用证明artifact12766192574 /CI40573577516（2通过55.117秒）匹配确切文件，原始Main设备artifact12765768463。发布门禁还须安装/冷启动该通用包并完整归档；尚未发布不能宣称已有0.3.2附件。

31项根脚本通过。大字号/长标题的竖屏目前可滚动查看全身，但首屏人物脚部未显示；列为发布后首项适配优化。随后处理跨曲下载所有权和无调用UI依赖；Rust默认Trace级日志已确认，尚未修改原生.so或声称耗电改善。实机/真实账号、路由和固定签名仍由本地agent独占，PR11未合入。

首次0.3.2发布CI40575569334已通过确切包安装/冷启动1677ms，日志归档因CLI默认拒绝ANSI控制符失败；artifact12766293038和原job122440962811已索引，未发布。只修正捕获原始日志为ZIP bytes，不渲染到终端；21个artifact/6个job的发布门禁将重跑，应用APK13c1401不变，不重编R8。
