# Grit Glass

基于 [Grit](https://github.com/shub39/Grit) 修改的 Android 待办应用，保留原项目的 GPL-3.0 许可。从 [Grhinet](https://github.com/luminenoble/Grit) 6.2.4 分出，去掉了 Rhine 场景和任务插图，只保留待办本身；Grhinet 继续单独维护。

正式版：[Grit Glass 1.1.1.1](https://github.com/luminenoble/Grit_glass/releases/tag/1.1.1.1) · [下载 APK](https://github.com/luminenoble/Grit_glass/releases/download/1.1.1.1/GritGlass-1.1.1.1-foss.apk)。可以与 Grhinet 同时安装。

数据以 Category → Task → Section 组织：类别和任务有独立描述，类别、任务和 Section 各有文字颜色（纯色或至多 5 色渐变，可输入 RGB、复制粘贴），Section 支持工作内容和手动排序。任务有 DDL（按天，可以没有）和优先级，可以挂起到某一天或转入“以后再说”；完成即归档。保留今天视图、提醒、任务小组件和 JSON 备份；备份是 Grit Glass 自己的格式，仍可导入 1.0.x 和 Grhinet 导出的文件。

总览页的任务墙把所有未完成的任务排成一面卡片墙：离 DDL 越近卡片越大，优先级越低颜色越淡，从左上角读起就是该先做的事。没有 DDL 的任务越久没动排得越靠后，久到一定天数会问一句还要不要做。

界面是米色纸面上的液态玻璃控件：按钮是透明玻璃，边缘会弯折后面滚过的内容（Android 13+），按下时被光照亮而不是变暗；所有按钮、类别芯片和导航项同为 44 dp，添加任务是 56 dp 的透明透镜，底部导航胶囊浮在页面上。

文档：[项目概览](docs/项目概览.md) · [开发与发布](docs/开发与发布.md)。

构建：`./gradlew :androidApp:assembleFossDebug`。
测试：`./gradlew :androidApp:testFossDebugUnitTest`；数据库测试需 Android 设备：`./gradlew :androidApp:connectedFossDebugAndroidTest`。
