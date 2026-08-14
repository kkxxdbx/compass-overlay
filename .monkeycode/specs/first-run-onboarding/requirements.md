# Requirements Document

Feature Name: first-run-onboarding
Updated: 2026-08-14

## Introduction

应用首次启动时展示「权限引导 + 功能引导」新手向导流程。用户依次完成悬浮窗权限与读取应用列表权限的授权校验后，进入功能介绍引导页，最终到达主业务界面。非首次启动跳过向导，直接进入主界面。

## Glossary

- **系统**: compass-overlay 应用
- **悬浮窗权限**: SYSTEM_ALERT_WINDOW 权限（特殊权限，需跳转系统设置页面授权）
- **读取应用列表权限**: QUERY_ALL_PACKAGES / 包可见性权限（Android 11+，用于游戏快捷启动检测已安装应用）
- **新手向导**: 首次启动时展示的引导流程，包含权限引导页与功能引导页
- **主业务界面**: MainActivity 悬浮窗设置页
- **独立引导 Activity**: OnboardingActivity，冷启动未完成引导时先进入的独立页面，完成后跳转 MainActivity

## Requirements

### Requirement 1: 首次运行触发向导

**User Story:** AS 新用户, I want 首次启动时自动进入新手向导, so that 我能正确完成权限授权并了解应用功能

#### Acceptance Criteria

1. WHEN 系统冷启动且本地无「引导已完成」标记, 系统 SHALL 展示新手向导首页
2. WHEN 系统冷启动且本地存在「引导已完成」标记, 系统 SHALL 直接进入主业务界面
3. WHEN 用户完成引导流程, 系统 SHALL 持久化「引导已完成」标记, 避免后续启动重复触发向导

### Requirement 2: 权限引导首页

**User Story:** AS 新用户, I want 向导首页列出应用所需权限, so that 我能逐项授权

#### Acceptance Criteria

1. WHEN 用户进入向导首页, 系统 SHALL 展示悬浮窗权限与读取应用列表权限两项权限条目
2. WHEN 用户点击某权限条目, 系统 SHALL 为该权限提供授权申请入口
3. WHILE 悬浮窗权限未授予, 系统 SHALL 显示该权限状态为未授权
4. WHILE 读取应用列表权限未授予, 系统 SHALL 显示该权限状态为未授权
5. WHEN 读取应用列表权限由系统自动授予（QUERY_ALL_PACKAGES 为安装时授予的普通权限）, 系统 SHALL 将该权限条目标记为已授权，仅作信息展示

### Requirement 3: 下一步按钮状态联动

**User Story:** AS 新用户, I want 权限未齐全时下一步按钮不可点击, so that 避免跳过权限授权

#### Acceptance Criteria

1. WHILE 两项权限未全部授予, 系统 SHALL 置「下一步」按钮为禁用状态
2. WHEN 两项权限全部授予, 系统 SHALL 将「下一步」按钮切换为可交互状态

### Requirement 4: 权限状态实时监听

**User Story:** AS 新用户, I want 在系统设置完成授权后页面即时更新, so that 无需重启页面即可继续流程

#### Acceptance Criteria

1. WHEN 用户在系统设置中授予某权限并返回应用, 系统 SHALL 即时更新对应权限条目的校验结果
2. WHEN 权限状态更新导致两项权限全部授予, 系统 SHALL 自动解除「下一步」按钮的禁用状态
3. WHEN 权限状态轮询或监听, 系统 SHALL 避免阻塞主线程

### Requirement 5: 功能引导页

**User Story:** AS 新用户, I want 权限齐全后进入功能引导页, so that 我能分步了解应用核心功能

#### Acceptance Criteria

1. WHEN 用户点击可用的「下一步」按钮, 系统 SHALL 跳转至功能介绍引导页面
2. WHEN 用户进入功能引导页, 系统 SHALL 按以下顺序分步演示应用核心功能：总开关 → 快速排列与方向字 → 游戏快捷启动 → 文字样式 → 文字颜色 → 背景样式与背景不透明度 → 排列间距 → 整体移动
3. WHEN 引导流程全部结束, 系统 SHALL 跳转进入主业务界面

### Requirement 6: 权限拒绝与永久拒绝处理

**User Story:** AS 新用户, I want 权限被拒绝时得到明确指引, so that 我能手动开启权限完成流程

#### Acceptance Criteria

1. IF 用户拒绝某权限, 系统 SHALL 保持该权限状态为未授权并允许重新申请
2. IF 用户勾选「永久拒绝」（不再询问）, 系统 SHALL 提示用户前往系统设置手动开启该权限
3. WHEN 权限申请流程出现异常, 系统 SHALL 捕获异常并保持页面可用, 避免卡死或崩溃
