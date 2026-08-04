# 方向罗盘（CompassOverlay）

一个 Android 悬浮窗小工具，在屏幕任意位置显示「上北下南 左西右东」，可自由拖拽移动，专为原神等小地图游戏设计。

## 功能

- 悬浮窗常驻显示，可叠加在任意 App（原神、王者等）之上
- 十字罗盘样式：上北、下南、左西、右东，与原神小地图方位一致
- 单行文字样式：`上北 下南 左西 右东`
- 长按拖动自由移动位置，松开自动保存
- 可调文字大小（12~40sp）、文字颜色（7 种预设）
- 可选深色圆角背景，背景不透明度可调
- 点击悬浮窗快速回到设置页

## 构建

需要 JDK 17 + Android SDK（compileSdk 34）。

```bash
# 生成 debug 包（位于 app/build/outputs/apk/debug/）
./gradlew assembleDebug

# 生成 release 包
./gradlew assembleRelease
```

没有 Gradle 环境时，可直接用 Android Studio 打开本目录构建。

## 安装与使用

1. 将生成的 APK 传到手机（或扫码下载），点击安装
2. 首次打开点击「悬浮窗开关」，允许系统弹窗授权「显示在其他应用上层」
3. 开启后返回游戏，拖动罗盘到小地图旁即可

## 项目结构

```
app/src/main/java/com/compassoverlay/
├── MainActivity.kt        设置页
├── OverlayService.kt      前台服务 + 悬浮窗管理
├── CompassOverlayView.kt  罗盘控件（渲染 + 拖拽手势）
└── Prefs.kt               设置存储
```

> 注：方向标注为「上北下南左西右东」的静态方位参考，与原神小地图方向一致，不依赖设备传感器。
