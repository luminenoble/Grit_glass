# Grit Glass

基于 [Grit](https://github.com/shub39/Grit) 修改的待办应用（Android 与 Windows 桌面端），保留原项目的 GPL-3.0 许可。从 [Grhinet](https://github.com/luminenoble/Grit) 6.2.4 分出，去掉了 Rhine 场景和任务插图，只保留待办本身；Grhinet 继续单独维护。

正式版：[Grit Glass 1.1.1.3](https://github.com/luminenoble/Grit_glass/releases/tag/1.1.1.3) · [下载 APK](https://github.com/luminenoble/Grit_glass/releases/download/1.1.1.3/GritGlass-1.1.1.3-foss.apk) · [Windows 免安装版](https://github.com/luminenoble/Grit_glass/releases/download/1.1.1.3/GritGlass-1.1.1.3-windows.zip)。可以与 Grhinet 同时安装。

数据以 Category → Task → Section 组织：类别和任务有独立描述，类别、任务和 Section 各有文字颜色（纯色或至多 5 色渐变，可输入 RGB、复制粘贴），Section 支持工作内容和手动排序。任务有 DDL（按天，可以没有）和优先级，可以挂起到某一天或转入“以后再说”；完成即归档。保留今天视图、提醒、任务小组件和 JSON 备份；备份是 Grit Glass 自己的格式，仍可导入 1.0.x 和 Grhinet 导出的文件。

手机负责随身执行：Task 页、提醒和小组件，总览页给出按 DDL 排好的临期清单。Windows 桌面端负责规划：任务像便签一样散落在一张可平移缩放的纸面桌面上，越重要卡片越大，DDL 越近纸色越深，可以随手拖动，也能一键按类别或紧迫度整理。没有 DDL 的任务久没动会沉下去，问一句还要不要做。两端在同一局域网里用 LAN Sync 整库互传（扫码配对、一次性 PIN），被覆盖的一方先留回滚快照。

界面是米色纸面上的液态玻璃控件：按钮是透明玻璃，边缘会弯折后面滚过的内容（Android 13+），按下时被光照亮而不是变暗；所有按钮、类别芯片和导航项同为 44 dp，添加任务是 56 dp 的透明透镜，底部导航胶囊浮在页面上。

文档：[项目概览](docs/项目概览.md) · [开发与发布](docs/开发与发布.md)。

构建：`./gradlew :androidApp:assembleFossDebug`；桌面端 `./gradlew :desktopApp:run`，或 `./gradlew :desktopApp:windowsJar` 打出可在 Windows 上运行的 jar。
测试：`./gradlew :androidApp:testFossDebugUnitTest :desktopApp:test`；数据库测试需 Android 设备：`./gradlew :androidApp:connectedFossDebugAndroidTest`。
