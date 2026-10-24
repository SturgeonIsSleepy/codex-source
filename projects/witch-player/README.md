# 魔女の旅々 · 手绘播放器

原生 Android 8+ 播放器，当前版本 1.8。固定歌单为「灰色のサーガ / ChouCho」与「リテラチュア / 上田麗奈」。不附带歌曲录音。

点击画面曲名或长按人物，分别导入两首歌的本地 MP3、M4A、FLAC、WAV 或 OGG。原生文件选择器授权读取，导入后保存在应用内。覆盖安装保留音频，卸载或清除应用数据会删除音频。

## 操作

- 中间手绘按钮播放/暂停，左右箭头在两首歌间循环。
- 上方滑杆拖动进度，下方滑杆调整音量；左侧喇叭切换静音。
- 支持后台播放、系统通知、锁屏控制、耳机断开暂停。
- 播放时闭眼，暂停时睁眼；躯干和发束等部件有惯性随动。

## 图像与动作结构

人物使用统一的缓存底图。`CharacterArtwork` 在加载时将 `skin-restoration.png` 的高分辨率皮肤修复合成到 `elaina-layer.png`，并按原底图的三处肤色基准校准修复图；合成只执行一次。所有表情共用这个结果，头发、眉毛、嘴和脸部轮廓不随表情切换。

`CharacterRig` 缓存躯干、腿和少量随动部件。`CharacterMotion` 管理连续的姿态与速度。`EyeBlink` 让上眼睑沿曲线落下与抬起，逐渐遮住或露出固定的眼球纹理，不缩放虹膜，也不从下方用色块擦除瞳孔。它只影响两个眼睛区域，使用缓存底图的相邻皮肤颜色。暂停后的运动收敛后停止请求动画帧。

音乐动作由本地音频解码的能量与估计节拍驱动。分析不可用时采用柔和基础节奏。不使用麦克风，不需要联网。

曲名与时间使用同一套 Yuji Boku 毛笔字，两个曲名固定同字号，没有伪粗体与文字描边。中文辅助界面使用系统字体。随包字库按应用字符集裁剪，遵循 SIL OFL，见 `app/src/main/assets/fonts/YujiBoku-OFL.txt`；原字库来自 Google Fonts `ofl/yujiboku`。

图片主要由用户参考图和图像生成修复得到，保留手绘风格，不宣称逐像素复刻或完整 Live2D 建模。原参考图为 `player-reference.jpg`，天空为 `player-sky.png`。

## 构建与验证

需要 JDK 17+、Android SDK 36 和 Build Tools 36.0.0。Android Studio 可打开此工程，或运行无需下载 Gradle 的本地 SDK 脚本：

```sh
ANDROID_HOME=/path/to/android-sdk ./tools/build.sh
```

SDK 默认 `$HOME/.cache/android-cloud/sdk`。输出 `app/build/manual/witch-player.apk`。构建会清理中间类目录，保留原签名密钥，以免旧删除类残留在 APK。

```sh
./tools/check-motion.sh
WITCH_TEST_BUILD=1 ./tools/build.sh
adb install --no-incremental -r app/build/test-manual/witch-player.apk
adb shell pm grant com.wanderingwitch.player android.permission.POST_NOTIFICATIONS
adb shell am instrument -w com.wanderingwitch.player/.PlayerIntegrationTest
adb shell am instrument -w -e expressions true com.wanderingwitch.player/.PlayerIntegrationTest
```

完整集成检查使用测试设备内生成的 WAV，不是两首歌录音。视觉专项对比睁眼、半闭和闭眼：两个眼睛以外的头发、眉毛、脸、嘴、脖子像素必须完全一致，同时检查上眼睑闭合时虹膜可见面积逐步减少、完全闭眼不残留瞳孔；导出原生渲染图及 60 FPS 开合眼预览供检查。交付 APK 不包含测试入口或测试音频。

CPPlayer 源码与协议评估见 `docs/CPPLAYER-ASSESSMENT.md`，当前尚未接入。历史变更见 `docs/CHANGELOG-ARCHIVE.md`，验证记录见 `VALIDATION.md`。

1.8 的开合眼预览为原生渲染器按时间步长导出的 150 帧、60 FPS、2.5 秒素材，用于观察眼睑轨迹和局部修复，不代表模拟器或手机的实际帧率。

## 交接状态

这是保留的 1.8 独立参考版，只播放固定两首歌。继续开发的主线为相邻的 `eplayer`，已经接入 CPPlayer。安装包、历史打包和视觉诊断归档见 [统一交接入口](../../docs/EPLAYER-HANDOFF.md)。

目录索引版本为 1.8.0，应用内版本名沿用 1.8。不提交签名密钥、编译目录或运行日志。
