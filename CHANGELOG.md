# Changelog

本文件记录每次发布的重要变更。版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)（主.次.修订）。

## [1.18] - 2026-08-14

### 新增
- 设置页横屏分栏 UI 重做：左侧导航栏 + 右侧分栏圆角面板，竖屏余白大幅减少
- 游戏快捷启动面板：首页底部全宽面板，支持原神 / 鸣潮 / 崩坏星穹铁道 / 异环 / 绝区零一键启动；未安装时显示官方 Logo 并 Toast 提示，已安装时显示系统图标
- 首次启动新手引导：权限引导页 + 8 步功能引导；竖屏进入，完成后平滑过渡到横屏主界面；引导完成持久化，二次打开直达主界面
- 游戏图标改用官方 Logo 占位（未安装时），铺满圆角方块

### 优化
- 方向字等全部开关按钮统一配色：开启时蜜桃色轨道 + 白色滑块（修复默认深棕滑块）
- 新手引导步骤切换增加错峰淡入过渡动画，切换更顺滑
- 新手引导权限页视觉重做：圆角渐变品牌卡、马卡龙权限条目、状态胶囊按钮

## [1.17] - 2026-08-13

### 修复
- 修复冷启动后服务被系统恢复（START_STICKY）时崩溃：`Prefs` 初始化移至 `App.onCreate` 兜底，不再依赖 MainActivity
- 修复设置页每次返回都重复重建全部悬浮窗：`syncUi()` 统一用同步标志保护，避免程序化刷新触发 listener 导致重复 `rebuildAll` / 重复启动服务
- 修复字号 / 背景透明度滑块拖动中反复重建窗口导致闪烁：拖动中仅刷新样式与窗口尺寸（`OverlayService.refreshStyle`），不再重建窗口
- 修复间距缩放过小（隐藏方向字坐标不参与缩放）：`scaleSpacingInternal` 现在把隐藏字也纳入包围盒并同步缩放
- 签名密码从 build.gradle 移出：改由 `keystore.properties`（已 gitignore）读取，避免公开仓库泄露
- 更新检查源迁移到 GitHub raw 固定地址，不再依赖临时预览域名
- `allowBackup` 关闭并补充 `dataExtractionRules`，默认不备份应用数据
- 拖拽阈值改用系统标准 `ViewConfiguration.scaledTouchSlop`，适配高 DPI 设备
- 一键十字 / 八方在服务运行时增加确认提示，防止误触覆盖手动摆放的位置

### 新增
- 深色模式适配（DayNight 主题 + values-night 配色）
- 设置页所有文案提取为字符串资源，方便后续国际化
- 字号 / 不透明度滑块实时显示当前数值
- 罗盘几何计算抽离为纯函数 `CompassGeometry` 并补充单元测试
- GitHub Actions 持续集成：push / PR 自动跑测试、lint、构建 debug APK，打 tag 自动构建签名 release 并发布

## [1.16] - 2026-08-04

### 修复
- 修复「从后台滑掉应用后弹屡次停止运行」：更新检查网络线程返回时对已销毁的 Activity 弹窗，抛 `BadTokenException` 崩溃；改为弱引用持有 Activity，弹窗前双重检查是否已销毁，并 try-catch 兜底
- `startForeground` 增加异常兜底，防止服务被系统后台重启时崩溃

### 新增
- 首次进入弹「允许后台运行」引导：申请系统电池优化白名单（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`），MIUI 对应「省电策略-无限制」，降低悬浮窗服务被系统清理的概率

## [1.15] - 2026-08-04

### 移除
- 移除友盟统计 SDK（common/asms 依赖、AppKey、初始化与 app_open 上报），不再上报任何使用数据
- APK 体积从 2.8MB 降回 1.6MB

## [1.14] - 2026-08-04

### 修复
- 修复「关闭整体移动后，单字拖动仍整体移动」：v1.11 重构帧回调时丢失了 `Prefs.groupMove` 判断，非整体模式下帧回调和 `onDragEnd` 无条件更新全部标签；已恢复按 `groupMove` 决定是否联动

## [1.13] - 2026-08-04

### 修复
- 修复友盟「SDK 初始化失败」：asms 版本从 1.8.7.2 修正为 1.2.3（common 9.x 必须配套 asms 1.2.x）
- 友盟初始化移到 `Application.onCreate`（App.kt），仅主进程执行一次
- 补充友盟 proguard keep 规则

## [1.12] - 2026-08-04

### 新增
- 填入友盟 AppKey，启用使用统计上报

## [1.11] - 2026-08-04

### 新增
- 刷新率自适应：根据 `refreshRateHz` 自动调整更新频率，锚点字每帧跟手、其余字按 `followInterval = max(1, refreshRate/60)` 帧跟随一次
- 强制更新检查：`UpdateChecker` 启动时读取 version.json，`force=true` 时弹出不可关闭的更新框
- 友盟统计（common 9.9.6 + asms）接入
- 开启 R8 + shrinkResources 压缩

### 优化
- dirty 标志跳过无效 IPC（位移未变化时不重复更新窗口）
- 8 个方向字共享同一背景 drawable，减少内存占用
- APK 体积 4.6MB → 2.8MB

### 修复
- 帧回调泄漏：修复首次 move 前未启动、onClick / onDestroy 未停止的问题

## [1.10] - 2026-08-04

### 修复
- 帧回调泄漏（首次 move 才启动，onClick / onDestroy 停止）
- 单字拖动统一走帧合并
- 间距滑块改为松手后按包围盒中心 `scaleSpacingInternal` 相对缩放，保留手动拖过的位置
- `onConfigurationChanged` + `clampToScreen`：横竖屏切换自动把罗盘夹回屏幕内
- 通知文案更新

## [1.9] - 2026-08-04

### 新增
- 一键十字自动隐藏四斜角、一键八方自动恢复
- 排列模式改为 MaterialButtonToggleGroup 分段按钮

## [1.8] - 2026-08-04

### 修复
- 修复「整体拖动时其他字消失」：translation 对悬浮窗根 View 无效、被 Surface 裁剪，改为 Choreographer 帧合并批量真实 `updateViewLayout`

## [1.7] - 2026-08-04

### 修复
- 修复间距 / 字号滑块双重偏移：XML 里 `android:min` 与代码 +40 叠加导致最低只能调到 80dp；去掉 XML min，代码直接映射

## [1.6] - 2026-08-04

### 变更
- 独立小窗替代单容器（修复文字裁剪 + 触摸拦截）
- 平板老用户重置默认间距 40dp / 字号 12sp（一次性迁移）

### 新增
- 按设备自适应默认间距与字号

## [1.0] - 2026-08-04

### 新增
- 首个版本：单容器悬浮窗显示八方向文字
- 基础拖拽、字号 / 颜色 / 背景设置
