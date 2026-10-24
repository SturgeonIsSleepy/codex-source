# CPPlayer 接入评估

2026-10-23。用户尚未给出具体地址，本报告按搜索找到的 Aurora-Nasa-1/CPPlayer 与 CPPlayer-KMP 评估。已查阅文档和接口实现，没有连接用户设备、安装 CPPlayer 或验证真实歌曲能否取得音频。

## 结论

可行。优先将 **CPPlayer-KMP 作为音源服务**，由魔女播放器保留播放、进度、音量、固定双曲目和动画。不要将音源 Provider ZIP 当成 UI 皮肤：它解决的是音乐数据来源，并不承载这个人物界面。

依据版本：CPPlayer-KMP main `045c9a5fb3646c2ef207166483ff456117578ed9`（2026-10-18）。具体安装版本必须通过 `/api/v1/meta` 协商实际能力，旧版 CPPlayer Android 不能据此假定兼容。

## 建议方案：CPPlayer 提供音源，本播放器负责播放

1. 用户在 CPPlayer-KMP 的「本地服务器输出」中开启服务、媒体面与数据面。Android 端手动配置其显示的地址与令牌，同一台手机通常是 `http://127.0.0.1:8080`。不能读取另一个应用的私有配置文件。
2. 调 `GET /api/v1/meta` 确认 `apiVersion` 和 `search`、`track`、`stream` 等能力，正确处理 401/403/404/501/502。
3. 仅为「灰色のサーガ」「リテラチュア」各绑定一个经过确认的 `mediaId`；查询结果可能包含翻唱和伴奏，曲名不能作为唯一标识。不添加通用搜索与任意歌单入口。
4. `POST /api/v1/search`、`GET /api/v1/tracks/{mediaId}` 提供曲目信息和 `streamUrl`；使用返回的流地址，交给本播放器的 MediaPlayer 播放。进度与音量仍由本地播放实例控制；Range、时长解析和断流恢复需要实测。
5. 保留本地导入作为离线回退。当前音乐动作分析只接受本地文件；流播放需增加受控缓存或流解码分析，未准备好时使用平缓基础节奏，不能把网络状态或 SSE 当成音频强弱数据。

接入时需增加网络访问及目标 HTTP 地址的 Android 配置、后台请求和超时处理。数据接口优先 Bearer 令牌；`/stream` 按上游协议使用 URL 令牌，不能把完整带令牌地址写进日志。CPPlayer 必须保持服务可用，网络 API 不会自动提供歌曲版权或账号权限。

## 其他方案与取舍

| 方式 | 可行性 | 对当前播放器的影响 |
|---|---|---|
| CPPlayer-KMP HTTP 音源 → 本机播放 | 推荐验证 | 保留双曲目限制、进度、音量；增加连接与缓存适配 |
| CPPlayer 播放 → 我们只做遥控与动画 | 部分可行 | SSE 能提供播放状态；远程只支持 play/pause/next/previous，不能 seek/setVolume/指定曲目/设置队列，难以完整满足当前要求 |
| 源码依赖 CPPlayer-KMP 的 core | 可行但改动较大 | 需接入 Kotlin/KMP、MusicBackend 和平台播放生命周期，替换当前简单播放服务与 SDK 构建方式；可继续保留 Canvas 画面 |

远程播放状态没有 RMS/PCM 能量端点。只做遥控时，精确随音乐强弱摆动需要额外音频分析通道；目前只能按播放状态和时间轴驱动基础动作。传统 Android 仓库的 MusicService 在清单中 `exported=false`，不能直接当跨应用绑定服务使用。

## 已核实的来源

- [CPPlayer-KMP 集成 API v1](https://github.com/Aurora-Nasa-1/CPPlayer-KMP/blob/045c9a5fb3646c2ef207166483ff456117578ed9/docs/dev/INTEGRATION_API.md)：握手、音源搜索、流地址、SSE、播控边界与 Android 配置。
- [架构](https://github.com/Aurora-Nasa-1/CPPlayer-KMP/blob/045c9a5fb3646c2ef207166483ff456117578ed9/docs/dev/ARCHITECTURE.md)：core 与 UI 分离，MusicBackend 是统一入口。
- [路由实现](https://github.com/Aurora-Nasa-1/CPPlayer-KMP/blob/045c9a5fb3646c2ef207166483ff456117578ed9/core/src/commonMain/kotlin/cp/player/core/integration/IntegrationRouting.kt)。
- [传统 Android 清单](https://github.com/Aurora-Nasa-1/CPPlayer/blob/main/app/src/main/AndroidManifest.xml)。
- [旧 Android 版 MIT](https://github.com/Aurora-Nasa-1/CPPlayer/blob/main/LICENSE)、[KMP 版 Apache-2.0](https://github.com/Aurora-Nasa-1/CPPlayer-KMP/blob/045c9a5fb3646c2ef207166483ff456117578ed9/LICENSE)：若复制代码或分发依赖，分别保留对应许可与通知。

本次交付仅包含动作改进和评估，未添加虚假的 CPPlayer 连接入口；下一步应使用目标 CPPlayer 安装版本验证握手、两首歌曲绑定、拉流、跳转和断线恢复。
