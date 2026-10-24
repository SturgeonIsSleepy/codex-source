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

## 尚未完成

第四阶段的定时停止、实际 Session PendingIntent 返回、无障碍进度操作及新进度条截图已加入源码，尚待对应提交的设备运行。实机焦点、耳机路由、真实源账号、长时间播放、完整 R8 音乐功能和全页视觉均未验收。DSP 编辑和淡入淡出入口仍需迁移。

早先 CI 40120164035 因 R8 目标剔除测试共享 Kotlin 类，未执行用例；CI 40121564864 为 23 通过、1 初始化失败，不能把这些失败覆盖为全通过。历史 0.1.1 具体公开交付仍保持原审批断点，本轮未重新发布该包。
