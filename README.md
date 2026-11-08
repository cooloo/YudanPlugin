# 鱼蛋插件

鱼蛋桌面的插件 SDK 与音乐插件项目，提供通用插件接口、开发示例，以及云音乐、QQ 音乐和酷狗音乐插件。

音乐插件针对车机和桌面卡片场景设计，支持横屏、竖屏与宽屏布局，在鱼蛋桌面中使用统一的界面和播放体验。开发者也可以通过 SDK 为桌面扩展页面、音乐及车模能力。

## 功能介绍

### 音乐插件

- **三个音乐来源**：云音乐、QQ 音乐、酷狗音乐，分别管理账号、设置、播放记录和缓存。
- **扫码登录**：通过对应的手机应用扫码并确认登录，二维码过期后可重新获取。
- **音乐发现**：推荐歌单、每日推荐、排行榜及个人歌单入口。
- **个人音乐**：我喜欢的音乐、最近播放、创建与收藏的歌单；具体内容随音乐平台及登录账号提供。
- **搜索**：按关键词查找歌曲或歌单，进入列表后播放。
- **播放页面**：专辑封面、封面取色背景、滚动歌词、翻译及逐字歌词；歌词内容取决于歌曲提供的数据。
- **播放控制**：上一首、下一首、进度跳转、播放队列，以及顺序、单曲循环和随机模式。
- **播放恢复**：保存播放队列和进度，重新打开后恢复播放上下文。
- **音质设置**：选择平台支持的音质，权限不足时尝试可用音质；实际可播放内容由账号权限和版权状态决定。
- **缓存管理**：缓存音频、封面和歌词，可查看占用并清理缓存。
- **桌面音乐联动**：通过媒体会话同步歌曲信息、播放状态与控制操作。
- **界面适配**：跟随宿主深浅主题，适配不同页面尺寸和系统字体缩放。

### 插件 SDK

SDK 为插件提供统一的入口、页面生命周期和宿主能力接口：

| 能力 | 说明 |
| --- | --- |
| 基础接口 | 插件初始化、资源释放和宿主环境访问 |
| 页面接口 | 创建页面、保存状态、处理返回和页面销毁 |
| 数据与资源 | 插件数据目录、缓存、偏好设置及资源访问 |
| 文件传输 | 文件下载、图片预览和媒体流读取接口 |
| 音乐接口 | 播放准备、媒体会话和桌面播放协调 |
| 界面服务 | 主题、字体和图标等界面能力 |
| 车模接口 | 车模描述及只读状态接口 |

`example-plugin` 提供一个简单的原生页面示例，适合作为新插件的起点。

## 安装与使用

### 运行要求

- 安装支持鱼蛋插件的鱼蛋桌面，并使用与插件兼容的桌面版本。
- 三个音乐插件最低支持 Android 9（API 28）。
- 登录、搜索及在线播放需要可用的网络连接。

### 安装插件

1. 准备与当前桌面版本适配的 `.ydplugin` 文件，并将其放到设备可访问的目录。
2. 打开鱼蛋桌面的插件导入入口，选择该文件，按照提示确认安装。
3. 安装完成后从桌面打开插件，按页面提示登录账号。

`.ydplugin` 是由鱼蛋桌面加载的功能包，不能作为 APK 通过 Android 系统安装器安装。

### 登录与播放

| 插件 | 扫码方式 |
| --- | --- |
| 云音乐 | 使用手机网易云音乐 App 扫码 |
| QQ 音乐 | 使用手机 QQ 音乐 App 扫码 |
| 酷狗音乐 | 根据登录页选择酷狗音乐、QQ 或微信扫码 |

在手机端确认登录后，可进入推荐、歌单或搜索页面选择歌曲。在播放页调整进度和播放模式，通过队列切换歌曲；音质与缓存管理位于设置页面。

## 本地构建

### 环境要求

| 工具 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK | 34 |
| Python | 3.9 或以上 |
| Gradle | 使用工程自带的包装器 |

通过 `ANDROID_HOME` 或根目录的 `local.properties` 配置 Android SDK 路径。

### 构建步骤

在项目根目录依次执行：

```powershell
python tools/generate-signing.py
.\gradlew.bat publishSdk
.\gradlew.bat packageMusicPlugins
```

单独构建某个插件或示例：

| 目标 | 命令 |
| --- | --- |
| 云音乐 | `.\gradlew.bat :app:packageReleasePlugin` |
| QQ 音乐 | `.\gradlew.bat :qqmusic:packageReleasePlugin` |
| 酷狗音乐 | `.\gradlew.bat :kugoumusic:packageReleasePlugin` |
| 页面示例 | `.\gradlew.bat :example-plugin:packageReleasePlugin` |

在 macOS 或 Linux 中，将上述 `gradlew.bat` 调用替换为 `./gradlew`。

### 构建产物

| 产物 | 目录 |
| --- | --- |
| SDK 本地 Maven 仓库 | `build/yd-plugin-sdk/maven/` |
| 音乐插件与示例功能包 | `build/distribution/music-plugins/` |

本工程以 SDK 和插件功能包为构建目标，音乐模块不提供可独立运行的 APK。

## 开发插件

建议从 [example-plugin](example-plugin) 开始，先完成页面插件，再根据业务需要接入其他能力。

1. 参考示例创建模块，配置插件名称、图标和 `plugin.json`。
2. 实现 `YdPlugin`，在初始化和关闭时管理插件资源。
3. 页面插件实现 `UiPlugin`，处理页面创建、状态保存及销毁。
4. 通过 `PluginEnvironment` 获取存储、资源、传输和宿主能力。
5. 使用打包任务生成 `.ydplugin`，导入兼容的鱼蛋桌面验证。

常用入口：

| 接口或示例 | 位置 |
| --- | --- |
| 基础插件接口 | [YdPlugin.java](yd-plugin-api/src/main/java/com/egogame/plugin/api/YdPlugin.java) |
| 页面插件接口 | [UiPlugin.java](yd-plugin-api/src/main/java/com/egogame/plugin/api/UiPlugin.java) |
| 宿主环境接口 | [PluginEnvironment.java](yd-plugin-api/src/main/java/com/egogame/plugin/api/PluginEnvironment.java) |
| 音乐插件接口 | [MusicPlugin.java](yd-plugin-music-api/src/main/java/com/egogame/plugin/music/api/MusicPlugin.java) |
| 页面示例实现 | [ExamplePlugin.java](example-plugin/src/main/java/com/egogame/example/plugin/ExamplePlugin.java) |
| 示例清单 | [plugin.json](example-plugin/plugin.json) |

插件的页面生命周期与运行生命周期分别管理，关闭页面时应释放页面资源；耗时的网络、文件及数据处理应安排到后台执行，避免阻塞界面。

## 项目结构

| 模块 | 用途 |
| --- | --- |
| `yd-plugin-api` | 插件基础接口 |
| `yd-plugin-runtime` | 插件加载、兼容检查与运行管理 |
| `yd-plugin-ui` | 插件界面接口 |
| `yd-plugin-music-api` | 音乐插件接口 |
| `yd-plugin-vehicle-model-api` | 车模插件接口 |
| `example-plugin` | 插件开发示例 |
| `app`、`qqmusic`、`kugoumusic` | 云音乐、QQ 音乐和酷狗音乐插件 |
| `music-plugin-common`、`shared/nowplaying` | 音乐公共实现与播放页面 |
| `build-logic`、`tools` | 构建、打包与源码归档工具 |

## 技术栈

| 方面 | 技术 |
| --- | --- |
| 语言 | Java、Kotlin |
| 界面 | Jetpack Compose、Material 3、Android 原生 View |
| 播放 | AndroidX Media3 / ExoPlayer、MediaSession |
| 异步任务 | Kotlin Coroutines、StateFlow |
| 网络与图片 | OkHttp、Coil |
| 其他 | Palette 封面取色、ZXing 二维码 |
| 构建 | Gradle、Android Gradle Plugin |

## 常见问题

### 插件可以单独安装在手机上吗？

不能通过系统安装器独立安装。手机、模拟器或车机上需要先安装兼容的鱼蛋桌面，再由桌面导入 `.ydplugin`。

### 导入时提示版本或运行库不兼容怎么办？

确认插件与桌面版本是否匹配，使用配套版本重新导入。开发者应保留工程配置的依赖版本，升级运行库后重新核对兼容性。

### 为什么扫码没有反应？

确认使用了页面要求的手机应用，并在手机端完成登录确认。二维码过期后重新获取；仍然失败时检查设备网络和页面提示。

### 登录后为什么部分歌曲无法播放或选择高音质？

歌曲版权、会员权益、地区和平台接口状态都会影响播放及音质。插件不改变音乐平台的账号权限。

### 清理缓存会退出登录吗？

设置中的缓存清理针对音频、图片和歌词缓存；退出账号使用独立的退出登录操作。

## 后续计划

以下能力待开发，后续由插件宿主提供接口，供插件调用：

- **多车型数据接口**：统一获取不同车型的车辆数据，便于插件展示和使用。
- **车控接口**：通过宿主提供的接口调用适配车型支持的车辆控制能力。

## 反馈与贡献

反馈问题时，请提供桌面与插件版本、Android 版本、复现步骤，以及必要的截图或经过脱敏的日志。不要公开账号登录信息或其他个人数据。

欢迎提交功能改进、问题修复和文档补充。提交改动时说明用途、影响范围和已验证的场景，涉及界面时可附上效果截图。

## 开源许可与致谢

本项目采用 [GNU LGPL-3.0](LICENSE)，其引用的 GPL-3.0 正文见 [COPYING.GPL](COPYING.GPL)。第三方内容保留各自许可，详见[许可归属](docs/许可归属.md)。

感谢以下项目提供的实现和基础能力：

- [xuqian-git/cloudmusic-car](https://github.com/xuqian-git/cloudmusic-car)：音乐插件的上游项目。
- [missuo/kumone](https://github.com/missuo/kumone)：上游注明的网易云接口与 weapi/eapi 加密实现来源。
- AndroidX、Kotlin、OkHttp、Coil、ZXing 等开源项目。
