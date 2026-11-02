# 第一阶段环境和验证记录

胡嘉豪　202430224010　2026-10-21（Asia/Shanghai）

## 环境与仓库

| 项目 | 本次记录 |
| --- | --- |
| 系统与终端 | Windows，PowerShell |
| Git | 2.53.0.windows.2 |
| 仓库和个人分支 | ttt587/Notes；member/b-quick-reminder |
| 基线 | 81a66024fe48eaa2206b854430856fe3875ff3ba；建立分支时 develop 与 master 一致 |
| 源码 | Android Java，Manifest versionName 0.1，minSdkVersion 14；未声明 targetSdkVersion |
| JDK | C:\jdk-24.0.2；直接执行 java.exe -version 显示 24.0.2 |
| Android SDK | ANDROID_HOME=E:\Sdk；platforms 有 android-34、35、36、36.1；build-tools 有 34.0.0、36.0.0、36.1.0 |
| 构建入口 | 无 gradlew、build.gradle、settings.gradle、build.xml；project.properties 被 .gitignore 忽略且本次未提供 |

## 实际尝试与完整错误

拉取命令：`git clone https://github.com/ttt587/Notes.git Notes`。首次受沙箱网络限制：

```text
Cloning into 'Notes'...
fatal: unable to access 'https://github.com/ttt587/Notes.git/': getaddrinfo() thread failed to start
```

在允许 Git 访问网络后重试成功。随后执行：

```powershell
git switch -c member/b-quick-reminder origin/develop
```

结果：成功创建个人分支，起点为上述基线。完成提交后将 upstream 指向同名远程个人分支。

在 Notes 根目录尝试 `& .\gradlew.bat assembleDebug`，PowerShell 返回：

```text
&:
Line |
   2 |  …  ('ANDROID_SDK_ROOT='+$env:ANDROID_SDK_ROOT); & .\gradlew.bat assembl …
     |                                                    ~~~~~~~~~~~~~
     | The term '.\gradlew.bat' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
```

这是构建入口缺失，尚未进入 Java 编译，不等于“编译器已发现源码错误”。现有较新 SDK 也不能单独证明旧工程可构建或不兼容。下一阶段需与负责构建的成员协调可重复的构建方案及 JDK/SDK 组合，再执行 APK 构建和设备验证。本次未擅自改造全组构建配置。

文档排版验证：打包的 render_docx.py 首次报 `FileNotFoundError: LibreOffice soffice.exe was not found on PATH`。改为通过已安装的 Word 只读导出 PDF，再使用同一打包渲染器与 Poppler 生成页面图片检查；未修改原始模板。

## 本阶段已执行检查

| 检查 | 结果与意义 |
| --- | --- |
| 原始代码清点 | 39 个 Java 文件，8 个包，10,002 物理行，包含注释和空行；由 git show 基线读取并统计 |
| 三个注释文件的 Java token 对比 | 通过；剥离注释和空白后与基线逐 token 相同，字符串和字符字面量保留 |
| 注释数量 | NoteWidgetProvider 13 行、DataUtils 15 行、DateTimePickerDialog 11 行，共 39 个净新增注释物理行 |
| git diff --check | 通过；无补丁空白错误 |
| Q-B-01 静态检查 | 两个分支均 FORMAT_24HOUR；Android 展示待实测 |
| Q-B-02 控制流检查 | 成功 return 跳过 cursor.close；Android 资源检测待执行 |
| APK 构建 | 阻塞于缺少 gradlew.bat；未生成 APK |
| Android 单元测试、设备测试、SonarQube | 未执行，不填报通过率或扫描指标 |

复核命令：`python doc/members/b/verify_phase_one.py`（Python 3，标准库）。脚本为只读，不修改源码；本次输出见 [verification.txt](verification.txt)。本阶段仅增加注释，因此没有添加与注释实现镜像的单元测试。

## 第二阶段测试计划

以下均为待执行用例，尚无通过结论。快捷项具体时间规则在实现前确定；时区默认跟随设备设置，测试固定 now 并记录时区。

| 编号 | 场景 | 输入或操作 | 预期检查 | 状态 |
| --- | --- | --- | --- | --- |
| B-T01 | 跨天 | 23:50 选择“明天上午 9:00” | 次日 09:00，秒及毫秒按约定归零 | 待执行 |
| B-T02 | 月末 | 1 月 31 日选择明天 | 进入 2 月 1 日，不产生非法日期 | 待执行 |
| B-T03 | 年末 | 12 月 31 日选择明天 | 次年 1 月 1 日 09:00 | 待执行 |
| B-T04 | 一周后 | 月末或年末选择一周后 | Calendar 跨界正确，时分规则符合约定 | 待执行 |
| B-T05 | 过去时间 | 自定义过去时间或当天快捷时刻已过 | 按明确策略提示或调整，不静默设错 | 待执行 |
| B-T06 | 取消提醒 | 设提醒后取消并退出编辑页 | 数据库时间清零，对应闹钟取消，原时刻不触发 | 待执行 |
| B-T07 | 应用重启 | 设置后正常退出，再启动应用 | 数据持久化、界面提示及计划提醒一致；与强制停止区分 | 待执行 |
| B-T08 | 设备重启 | 设置未来提醒后重启设备 | BOOT_COMPLETED 后恢复未来闹钟，已过期策略符合设计 | 待执行 |
| B-T09 | 提醒触发 | 设近期提醒，等待到时 | 显示正确便签和铃声，关闭后释放播放资源 | 待执行 |
| B-T10 | 小组件回归 | 分别放置 2×2 和 4×4，编辑内容和提醒 | 摘要、背景、点击目标及删除解绑正常 | 待执行 |
| B-T11 | 空白便签 | 新建空白便签并设提醒 | 给出原有错误提示，不注册无有效便签 ID 的提醒 | 待执行 |
| B-T12 | 12/24 小时 | 切换设置，测试 00:00、12:00、15:30 | 标题、滚轮与保存时间一致，回归 Q-B-01 | 待执行 |
| B-T13 | Cursor 释放 | 通话查询命中、未命中、null 和读取异常 | 可关闭对象均在完成后关闭，回归 Q-B-02 | 待执行 |

下一阶段需保留原界面和修改后界面截图、每项测试的设备版本、操作步骤、实际结果和对应 commit SHA。本阶段没有应用运行截图；图示是源代码结构图，不能替代运行证据。
