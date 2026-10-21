# 本仓库的工作约定

1. 修改或上传前，先阅读 README.md、catalog.json 和 docs/CONTRIBUTING.md，并检查现有目录。保留其他条目的内容与未提交更改。
2. 新工具放在 tools/<kebab-case-name>/；独立项目放在 projects/<name>/；小型静态资源放在 assets/<name>/。禁止把新内容散放根目录，也不要为同一工具再建带日期、final、copy 的平行目录。
3. 每个条目登记在 catalog.json 的唯一 id 与 path 下，维护自己的 README.md 和 VERSION，并更新根 README 的索引。已有工具在原目录更新。
4. 本仓库只存源码、说明和小型静态资源。不要提交 SDK、AVD、系统镜像、运行状态、日志、缓存、密钥、APK 或下载/构建生成的 ZIP。打包文件放对应工具的 GitHub Release，而非再提交一份副本。
5. 文件重命名、删除或索引替换前，确认不会破坏其他条目的固定下载入口。需要兼容旧入口时保留一个明确的转发入口；禁止无关覆盖或强制推送。
6. 提交前运行 python3 .github/scripts/check_layout.py 与改动工具的相关测试。检查脚本扫描 git 已跟踪/已暂存文件，需先暂存新文件。
7. Android Cloud 默认后台启动，返回 starting 后结束任务；除非用户要求，不等待开机、不持续轮询、不截图、不构建或安装 APK。不得将配置完成说成 Android 已就绪。
8. 不要把本机路径、SDK 缓存和本会话状态写入复用入口。给其他会话提供仓库中的真实下载 URL；需要稳定版本时提供完整 commit SHA 的固定链接。
