# ePlayer 0.5.8

输入法可见时暂停应用的预见式返回处理，让系统先消费返回并关闭输入法；MainActivity 明确 adjustResize 以接收输入法 Insets。关闭键盘后的下一次返回才处理搜索/页面层级。

播放队列与歌单详情共用 SongReorder：整行长按直接进入编辑并拖动，手柄可直接拖动；临时顺序只影响画面，松手一次提交，取消不提交；保留占位动画与仅拖动期间运行的边缘自动滚动。自有歌单可移除歌曲、同步网易顺序；他人歌单可编辑个人播放顺序，按账号/音源/歌单保存到本机，不写入他人云端歌单。

服务端排序使用 [ncm-api-rs song_order_update](https://github.com/SPlayer-Dev/ncm-api-rs/blob/main/src/api/song_order_update.rs) 的 song/order/update，参数 pid 与完整 JSON 数组 ids。提交前重新读取 playlist/detail 的全部 trackIds 并核对 trackCount，移动对应歌曲后提交完整顺序，不以当前加载的 200 首覆盖其余歌曲。失败回滚并显示错误。内置 1.1.0 模块静态包含该路由与符号；未使用用户账号实际写入验收。

详情窗口内挂载与主页相同的悬浮底栏，可查看当前曲、暂停/切歌、打开播放页/队列及导航，不依赖被详情窗口遮挡的主页底栏。

间奏行在解析合并和渲染两处都抑制翻译/音译，只显示三点。

按用户要求只运行交付必要的 internal Release 构建和固定签名打包核验，不启动额外设备或完整回归。主 agent 分析/实现；Terra high 仅执行明确的读取、构建和打包命令。

最终源码 `a2d8cdaa0cb675ffca9450bbda45923bd09653c5` / [CI 41267837930](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/41267837930) internal Release 构建成功。较早 4e41f71 构建已取消，最后补充首页冷启动直接进入歌单时先识别账号，账号加载期间禁用排序，避免自有歌单误按本机保存。最终包 `ePlayer-v0.5.8-internal.apk`，56,970,344 bytes，SHA256 `208071f1386f9bcd1d953dd5afdb57337722970a08783b4593df9c38b6452a0f`；固定签名、16KiB 对齐、三 ABI、模块摘要与 CI 代码一致性核验通过。未进行设备或账号写入测试。
