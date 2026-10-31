# ePlayer 当前实施计划

## 2026-10-31交付指令覆盖本轮验证安排

用户提供原NCM 1.1.0模块（SHA256 f885009aaeaa6e2aa5832101517623903832f4c4f37b352d637463025af92209），要求仅给内置音源安装包、不检查功能；明确说明有Root并授权不沿用旧签名。当前正常汇合PR17/e8e732f最新界面与PR24/7a1e968核心，不覆盖源分支，不运行功能测试、不安装模拟器。保留原包名app.eplayer.music.internal、原数据与登录偏好；使用本环境签名，普通Android安装器不能保证覆盖，Root安装方式和保留数据由用户控制。模块/密钥/成品APK不上传公开Git或Release，交付私有附件。

合并保留0.5.3合体底栏/角色封面舞台/预测返回/逐词及TTML歌词、0.5.5唯一会话与核心数据；歌词仅保留一套core/lyrics模型/解析/缓存，通过Android SourceStore适配器加载，兼容丰富歌词字段并保留取消检查/唯一临时文件/原子替换。原生事件读取仍使用唯一命令调度器，合并暂停低频轮询与按需能量。只构建internal release通用包，不把未执行功能检查写成通过。首轮合并编译39秒失败于WitchPlaylist导入与已替代的awaitingNativeStart变量，已修正；歌词回调的Main调度留在Android适配器，核心不依赖UI主调度器。最终构建待结果。下方为先前验证安排和历史阻断。

## 当前源码与阻断

主线应用0.5.2/c17dc4a的CI41112575118：两版R8构建通过；独立51项49通过/1 EQ系统Stop失败/1真实登录跳过，106.585秒。冷启动、确切原始APK检查和Release阻断，不发布该失败包。0.3.2仍是公开预览，附件不覆盖。

当前开发PR24/agent/eplayer-media-resumption，0.5.5产品cbe699fca5e17f278bd034f0096443a01cf0506c，包含PR23数据边界。正在正常合并main b0220c9文档；人物、Medium中日字体、普通Android路由、源/下载/队列/歌词/DSP不删，USB独占桥接不恢复。PR17本地界面/模块/签名/登录不覆盖，Android Cloud固定入口不改。

## 本阶段实际改动

- core/library接收Track、曲库与最近播放；core/queue保存队列，原JSON/偏好键/URI不变，精确Gson规则同步；TrackSchemaTest验证旧字面JSON往返。
- 同一MusicService升级MediaLibraryService/MediaLibrarySession，保留组件/旧action/唯一SessionPlayer，onConnect仅接受同UID或平台可信控制器。
- RecentMediaCatalog只读当前曲或已有历史，输出可浏览根/可播放子项；不prepare、不联网、不自启音频。Media3三参数onPlaybackResumption区分只读查询和主动播放，后者保留实际完整队列/位置。
- Media3 1.10实际实现对SystemUI最近浏览要求canResumePlaybackOnStart；该值依赖MEDIA_BUTTON广播接收器。已补标准MediaButtonReceiver，避免可发现服务仍拒绝恢复；不增加自定义接收器/权限/第二队列。
- 传统浏览测试在主线程构造Android MediaBrowser，验证发现导出服务/媒体按键接收器、连接EXTRA_RECENT、可播放子项及不自启音频。主动恢复测试先真实播放，再明确pause/stop/清空并主动Play，双引擎必须获得真实进度和AudioManager活动输出。ExoPlayer.stop按API保留playWhenReady，不能拿它冒充pause。
- 固定只读日志补MediaBrowser/MediaSessionImpl/legacy标签，保留原真实Stop/失焦、超时、断言及失败瞬间取证，不按来源屏蔽Stop、不改系统媒体设置。

## 可复核结果

| 源码 / CI | 实际结果 |
| --- | --- |
| 数据边界04d60ce /41113754192 | 33 JVM与构建通过；51项50通过/1登录跳过，103.113秒；R8未请求 |
| 浏览修复前098e2501 /41116950332 | 52项50通过/1导出浏览服务发现失败/1登录跳过，109.592秒；EQ本轮通过不代表历史停止已修复 |
| 初次产品3914ba6 /41118331969 | 53项49通过/3失败/1登录跳过，118.318秒；EQ系统Stop、MediaBrowser构造线程、恢复前置状态失败；artifact13006654026 SHA256已核对dcabfd2a867d8a8ab2b2e1d14c894af4ea837690244fe575e06264b393b6e3f6 |
| 夹具14ee342 /41119090920 | 构建通过、设备失败；日志和artifact下载接口内部错误，具体项数尚未取得，不推测通过范围 |
| 线程修正1d18d3b /41120075599 | 本地33 JVM/构建16秒通过；远端构建与设备待结果 |
| 接收器/查询修正cbe699f | 本地33 JVM及应用/设备包14秒、最终增量2秒、31仓库测试通过；远端待结果，R8/确切包/Release未执行 |

3914失败现场已确认SystemUI发现并尝试连接服务，但resumeAction仍null。实际补齐接收器是否解决全部停止原因，必须看新设备/SysUI恢复动作及正常Stop结果，不能提前认定。所有红绿、跳过、未执行保留在[验证索引](VALIDATION-2026-10-25.md)；集成前完整规划归入[历史](history/DEVELOPMENT-STAGES-2026-10-30.md)，不从其中旧“待执行”继续。

## 接续顺序

1. 取得最终53项真实结果及MediaBrowser/SystemUI恢复Action证据；失败先检查具体回调/元数据/原始Stop时序，修产品或有证据的夹具错误，不重跑同源码凑绿色。
2. 通过后正常汇合主线，执行最终版本一次普通/自用R8、完整设备与R8冷启动；不能借旧分支绿色代替主线。
3. 提取未经重签/重打包的原始universal APK，核对版本/证书/SHA/三ABI，再执行双引擎跨应用文件选择器导入、播放/暂停、12秒定位、后台与实际通知点击返回；检查全页视觉证据。
4. 所有门槛真实通过才按最终版本发布Release，归档APK/原生库/红绿日志/截图索引/已知缺口；不把产物写入Git，0.3.2资产不动。
5. 持续推进SourceStore协议数据接口、ListeningStats边界、淡入淡出与安全缓存入口，以及原生Trace真实三ABI构建验证。先梳理依赖再迁移，保留完整能力；不恢复无人调用的旧双播放器。

## 未完成与验证边界

当前新版R8、确切原包和Release未执行；设备53项尚未通过。实机/耳机/音质/长时/真实账号写入尚未验收，不把合成WAV或模拟器AudioManager当实机听感。原生库沿用已校验0.1.0，没有本轮Rust重建或Trace优化通过证据。Crossfade与安全缓存管理入口仍有缺口。角色素材未改；已往轮检查的静态截图不代表新版本逐帧动画验收。
