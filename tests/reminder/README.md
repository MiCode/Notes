# B 模块回归测试

更新：2026-11-02。已合并 origin/develop 的 cbb6ccd（含 A 字数统计与 C 兼容修复）。生产功能代码位于 `src/`，本目录是独立验收源码。

## 时间边界测试

JDK 9 以上即可（本次 JDK 21，编译为 Java 8 字节码），不依赖 Android 或第三方测试库：

```powershell
javac --release 8 -encoding UTF-8 -d ../.work/b-jvm-tests src/net/micode/notes/tool/QuickReminder.java tests/reminder/QuickReminderTest.java
java -cp ../.work/b-jvm-tests net.micode.notes.tool.QuickReminderTest
```

共 26 个断言，固定输入时间与时区，覆盖跨天、月末、年末、闰年、夏令时、清零秒与毫秒，以及过去、当前和未来时间。

## Android 验收构建

现在直接在 Notes 仓库根目录构建实际合并分支。使用团队统一的 AGP 8.7.3、Gradle 8.9、JDK 21、compile/target SDK 34，设置 ANDROID_HOME 或 local.properties 指向 SDK（本次 E:/Sdk）。不再使用固定 C 提交叠加副本。

```powershell
.\gradlew.bat clean assembleDebug lintDebug --console=plain
.\gradlew.bat -I tests/reminder/acceptance.gradle assembleDebug assembleDebugAndroidTest --console=plain
```

第二条命令按需加载验收配置，通过 debug 专用 Manifest 注册 WidgetHostActivity，承载真实 AppWidgetHost/RemoteViews；生产 Manifest、源码及资源均不被替换。Instrumentation 使用系统框架，无需 AndroidX/JUnit 网络依赖。

prepare_integration.py 仅用于可选导出当前已提交分支 HEAD 的验收副本，不再选择历史 C 基线；本轮测试直接使用根目录构建产物，没有调用该导出脚本。

在专用 Android API 36 模拟器上安装两个 APK，预先授予通知权限、精确闹钟权限和小组件宿主绑定权限，再执行：

```powershell
adb shell pm grant net.micode.notes android.permission.POST_NOTIFICATIONS
adb shell appops set net.micode.notes SCHEDULE_EXACT_ALARM allow
adb shell appwidget grantbind --package net.micode.notes --user 0
adb shell am instrument -w net.micode.notes.test/net.micode.notes.tests.ReminderInstrumentation
```

默认模式 51 个断言（原有 37 项，加 14 项字数统计、清单、旋转及提醒共存检查），包含真实 AlarmManager 在后台触发通知。`-e mode inexact`（先撤销精确闹钟权限）有 2 个断言，`-e mode notification_denied`（先 revoke 通知权限，并用 `pm clear-permission-flags net.micode.notes android.permission.POST_NOTIFICATIONS user-set user-fixed` 清除专用测试设备的重复拒绝标记）有 4 个断言。运行后恢复权限。`-e mode trigger` 可仅重跑 4 个取消/触发断言并补抓截图，不额外加入默认验收总数。

`-e mode after_restart` / `after_reboot` 各检查 1 个持久化断言，依赖默认模式保存的固定便签。**Instrumentation 启动和结束会强停目标进程**，所以系统闹钟恢复必须在实际重启完成后、再次开始 Instrumentation 之前，用 `dumpsys alarm` 检查。测试前正常打开应用，确认 `stopped=false`，执行真实 `adb reboot`，等待恢复的 `RTC_WAKEUP` 出现；不能只等待 `sys.boot_completed=1`，也不能把强停场景等同普通退出。

运行截图保存在应用外部私有目录 `files/b-evidence`，原始记录见 `doc/members/b/test-record.md` 和 `evidence/logs/`。测试会创建名称以 B 开头的便签，只应在专用模拟器上运行。

完成默认模式后、权限拒绝模式前，可在专用模拟器复现普通退出和真实设备重启：

```powershell
python tests/reminder/verify_restarts.py --adb E:/Sdk/platform-tools/adb.exe --serial emulator-5556 --out ../.work/b-merged-restarts-20261102
```

脚本检查普通进程退出后系统闹钟仍在，真实 reboot 后 boot_id 已改变，开机接收器重新注册相同提醒时间戳，最后才执行两个持久化断言。
