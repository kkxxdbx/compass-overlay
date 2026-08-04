# Changelog

本文件记录每次发布的重要变更。版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)（主.次.修订）。

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
