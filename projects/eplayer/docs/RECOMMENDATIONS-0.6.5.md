# ePlayer 0.6.5 推荐与漫游

0.6.4 已先交付。此版本增加首页「漫游」、播放器漫游开关及播放设置内的推荐来源选择；默认网易云，YTM 为可选元数据推荐后端，实际歌单和音频始终留在网易云。

首页按歌单、歌手、单曲、专辑交错排序，结合最近播放与种子历史；屏蔽不感兴趣歌曲，补充服务端推荐歌单。旧种子不再独占首屏。歌单种子从完整网易云 ID 列表抽样，避免仅使用前 200 首。

漫游由播放服务持有，退出界面后继续随播放事件工作；保留原始种子并轮换，提前补队列。按音源和歌曲 ID 去重，保留已听记录，过滤原歌单、当前队列和不感兴趣项。同名歌曲不会被当作相同 ID。队列修剪后仍保留本轮排重记录。暂停会取消在途请求，请求失败有冷却；关闭漫游或切换推荐后端会取消在途工作，旧结果不能重新入队。没有新增后台轮询计时器。

YTM 只访问匿名首页、search 与 next Radio；没有请求播放流、内置 Python 或创建远端歌单，也没有 Google/Spotify 登录凭据。先按网易云 ID 刷新种子元数据（旧队列可能只保留第一位歌手），再严格匹配成 YTM 歌曲，最后将推荐严格匹配回网易云。匹配核对完整歌名、全部歌手、版本文字、时长和可用专辑；乐队名中的 AC/DC、and 等不被当作多位歌手拆分。有歧义、缺时长、不同版本或专辑不符则跳过。无法保证覆盖所有跨语言别名，优先保守跳过。YTM 失败或无可靠匹配时回到网易云，并提示一次。缓存只保留有界的解析后元数据，不缓存整份 Radio 页面 JSON。

参考 [ytmusicapi MIT 源码](https://github.com/sigma67/ytmusicapi/tree/488dbf31d8f9173331c80375fdc2fb6de6d45f6d)，独立实现最小 Kotlin 协议与解析，未复制 GPL 的 Metrolist 实现。整张私有歌单 Suggestions 需要授权和拥有相应歌单，本版只做多首种子 Radio，不能将其称为完整歌单 Suggestions。

Spotify 调查见 [官方新应用限制](https://developer.spotify.com/blog/2024-11-27-changes-to-the-web-api) 和 [librespot Radio/Autoplay 源码](https://github.com/librespot-org/librespot/blob/dev/core/src/spclient.rs)：存在内部 Radio 实现，但依赖 Spotify 会话，项目要求 Premium。没有将它包装为免 Premium 的可用推荐接口。

匿名 YTM 本机首页返回地区不可用页面。最初实现过度依赖首页提取版号，后按参考库 `initialize_context` 改为 UTC 日期默认上下文：首页只作为可选 visitor 来源。使用库同样的公开匿名同意状态标志 `SOCS=CAI`，不读取浏览器账户 Cookie。修正后本机能请求 search，但示例查询返回 No results，尚未执行成功 Radio，不能把它算作推荐链路已联通。可选后端仍取决于 YTM 网络与服务可用性；默认网易云不依赖它。

首轮 CI 因缺少 `testBuildType=fastRelease` 参数未创建目标单测任务，未执行测试；修正后 f006944 的 13 项逻辑检查通过。dbf031b 的 14 项通过，入口检查 1/2 通过；主页入口测试夹具缺少 Column，顶层控件重叠，已补容器，保留原单击计数断言。

## 最终交付

应用源码 `be458624478b1e408e8c73ed7406d9ecbd2ad890`，对应 [CI 42219531243](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/42219531243)。15 项定向 JVM 检查、2 项模拟器入口检查全部通过，没有以此代替无关的完整回归或实机功耗测试。

最终 R8 包在原 TravelTest36 保留数据安装，已手动查看首页漫游入口、播放设置推荐来源切换、播放器「漫游→漫游中→漫游」状态；设置与开关恢复原值。检查在暂停状态进行，没有借此宣称已连续播放或完成本机跨平台映射。三张交付截图均为最终应用界面。

最终 CI 网络环境的匿名实测成功返回 20 个搜索歌曲条目和 50 个 Radio 队列歌曲条目，`authenticated=false`、`playlistWrites=false`。该结果证明匿名搜索/Radio 元数据链路可用，不能等同于全部条目已匹配回网易云。本机网络的示例搜索仍无结果；本机完整 YTM→网易云映射→实际播放尚未得到成功的端到端实测。默认网易云，保留可选 YTM 和失败回退，记录两个环境结果。

自用通用 APK 为 `ePlayer-v0.6.5-internal.apk`，57,035,823 bytes，比 0.6.4 增加 32 KiB。SHA256 `9f4202addeeb9176ac4ee737ed7992db93660b58a16373823bfa0a51b15f2463`。固定签名、16 KB 对齐、三 ABI、内置音源与 CI 代码载荷一致性已核验。

[下载与验证记录](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.6.5)。公开归档仅含本轮报告、定向测试结果、包摘要和匿名接口可用性摘要；不包含界面图片、UI XML、用户登录数据、签名密钥、原始网络响应或歌曲录音。三张界面截图保留本地，避免公开听歌信息。首次含截图的发布被自动审批拒绝，发布脚本未执行；已收紧公开内容后重新审查。
