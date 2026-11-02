# 提醒 小组件和工具模块阅读说明

胡嘉豪　202430224010　B 同学　2026-10-21

本次阅读基线为 `81a66024fe48eaa2206b854430856fe3875ff3ba`。个人分支为 `member/b-quick-reminder`。以下结论来自源代码阅读，未把静态推断写成应用实测结果。除特别注明“原始行号”外，行号均对应本阶段注释后的文件。

## 阅读范围和整体功能

MiCode Notes 是 Android 便签应用，具有便签编辑、文件夹、清单模式、提醒、桌面小组件、文本导出和任务同步等功能。本次负责 `src/net/micode/notes/widget/`、`src/net/micode/notes/tool/` 以及 `ui` 中提醒选择、注册、恢复和触发相关代码。

基线共有 39 个 Java 文件、8 个包、10,002 个物理行（包含注释和空行，不等于可执行代码行）。`widget` 有 3 个顶层类，`tool` 有 4 个顶层类，本次还梳理 6 个相关 UI 顶层类，共 13 个。本仓库没有单独的 Widget Service；小组件由 `AppWidgetProvider`、`RemoteViews` 和编辑页发出的更新广播配合完成。

架构分为 UI、工作模型、数据访问、工具、小组件以及三个 `gtask` 包。UI 通过 `WorkingNote` 修改工作状态，经 `Note` 和 `ContentResolver` 写入数据；小组件读取便签表并显示摘要。包依赖不是严格的单向分层，例如 `DataUtils` 依赖 `NotesListAdapter.AppWidgetAttribute`，`ResourceParser` 依赖 UI 偏好设置键。

## 类与职责清单

各路径均以 `src/net/micode/notes/` 为前缀。清单列出顶层类；内部类单独注明，不混入顶层类统计。

| 包或路径 | 顶层类 | 主要职责和关键入口 |
| --- | --- | --- |
| widget | NoteWidgetProvider | 小组件公共刷新骨架；`onDeleted()` 解除绑定，`update()` 查询数据并绑定 RemoteViews 和点击 PendingIntent。 |
| widget | NoteWidgetProvider_2x | 2×2 小组件入口；`onUpdate()` 委托父类，提供布局、背景及 `TYPE_WIDGET_2X`。 |
| widget | NoteWidgetProvider_4x | 4×4 小组件入口；与 2×2 共用更新逻辑，提供另一组资源和类型。 |
| tool | DataUtils | 批量删除、移动、可见性、通话便签和摘要查询；`getFolderNoteWidget()` 为组件更新收集 ID 和类型。 |
| tool | ResourceParser | 颜色、字体索引到资源 ID 的映射，以及默认背景策略；内部类 NoteBgResources、NoteItemBgResources、WidgetBgResources、TextAppearanceResources 分别服务不同界面。 |
| tool | BackupUtils | 便签文本导出、导出结果及文件路径管理；内部 TextExport 执行查询、格式化和文件写入。 |
| tool | GTaskStringUtils | Google Tasks 协议 JSON 键、动作名称及元数据常量；本阶段只记录职责，不修改同步协议。 |
| ui | NoteEditActivity | 编辑页提醒入口；`setReminder()` 打开选择器，`onClockAlertChanged()` 注册或取消闹钟，`showAlertHeader()` 更新提示，`updateWidget()` 广播刷新。 |
| ui | DateTimePickerDialog | 包装选择控件、维护所选 Calendar、格式化标题；确定后通过 OnDateTimeSetListener 返回毫秒时间戳。 |
| ui | DateTimePicker | 日期、小时、分钟、AM/PM 滚轮；管理 12/24 小时模式、日期进退和 OnDateTimeChangedListener 回调。 |
| ui | AlarmReceiver | 收到提醒广播后将 Intent 转给 AlarmAlertActivity，并添加新任务标记。 |
| ui | AlarmInitReceiver | 接收开机广播；查询未来的提醒时间并重新注册 AlarmManager 闹钟。不会恢复已经过期的提醒。 |
| ui | AlarmAlertActivity | 从 URI 读取便签 ID 和摘要、检查可见性，展示提醒并播放铃声；关闭对话框时停止并释放播放器。 |

跨模块依赖：`model/WorkingNote.java` 的 `setAlertDate()` 保存待写入值并回调编辑页，`saveNote()` 持久化且按需通知组件；`data/Notes.java` 定义 URI、列名、便签及组件类型；`model/Note.java` 执行数据同步写入。这些依赖用于解释调用链，不计入上述 13 类。

布局与声明：`res/layout/datetime_picker.xml`、`widget_2x.xml`、`widget_4x.xml`；菜单 `res/menu/note_edit.xml`、`call_note_edit.xml`；组件元数据 `res/xml/widget_2x_info.xml`、`widget_4x_info.xml`；组件及提醒 Receiver 在 `AndroidManifest.xml` 中声明。

## 关键类一 NoteWidgetProvider

文件：[NoteWidgetProvider.java](../../../src/net/micode/notes/widget/NoteWidgetProvider.java)。父类是 Android `AppWidgetProvider`；两个具体 Provider 继承它。`getLayoutId()`、`getBgResourceId()`、`getWidgetType()` 是尺寸差异的扩展点，共享的 `update()` 则承担更新骨架。

1. `onDeleted()` 将指定组件关联记录的 `WIDGET_ID` 设为无效值，保留便签。
2. `getNoteWidgetInfo()` 按 widgetId 查询 ID、背景色、摘要，排除回收站；调用者拥有 Cursor 的关闭责任。
3. `update()` 遍历组件。存在唯一绑定便签时，点击 Intent 为 ACTION_VIEW，并携带便签 ID；没有绑定时显示占位文本，点击进入 ACTION_INSERT_OR_EDIT。
4. 根据尺寸选择布局，根据颜色索引调用 `ResourceParser.WidgetBgResources` 获取背景，然后以 widgetId 作为 PendingIntent requestCode，最后更新 RemoteViews。
5. 私有重载保留 privacyMode 分支，但公开的受保护重载固定传 false；仅凭 Manifest 中的隐私广播声明，不能推断隐私模式已经生效。

边界：无效组件 ID 被跳过；多条便签错误地绑定同一组件时关闭 Cursor 后直接 return，整批后续组件也不会刷新；无绑定和已移入回收站都走占位分支。后续快捷提醒不能改变 widgetId、widgetType、背景映射和摘要刷新行为。

本次增加 13 行注释，覆盖类职责、解除绑定、查询过滤、线程位置、异常批次退出、无数据分支、资源映射、隐私分支和 PendingIntent 区分方式。未改变行为。

## 关键类二 DataUtils

文件：[DataUtils.java](../../../src/net/micode/notes/tool/DataUtils.java)。该类集中使用 ContentResolver，避免 UI 重复构造查询与批量操作。

`batchDeleteNotes()` 将多个 ID 转换为 ContentProviderOperation 并 applyBatch，跳过系统根目录；`batchMoveToFolder()` 更新父目录和 LOCAL_MODIFIED。两者只验证结果数组及首项，不能由返回 true 推导每个目标都被实际修改。

`visibleInNoteDatabase()` 同时检查便签类型和非回收站状态，被提醒弹窗用于判断是否继续显示。`getFolderNoteWidget()` 返回目录下的组件属性集合，未查到记录时可能为 null，调用方不能直接假设为空集合。

`getSnippetById()` 用于提醒摘要读取：非空 Cursor 但没有记录时返回空字符串，null Cursor 时抛 IllegalArgumentException。`getFormattedSnippet()` 对内容 trim 后只保留首行，null 输入仍返回 null。

资源管理重点：`getCallNumberByNoteId()` 的空结果路径不进入 finally；`getNoteIdByPhoneNumberAndCallDate()` 在成功读取后直接 return，跳过 cursor.close()。后者作为 Q-B-02 记录，需在后续阶段统一 Cursor 生命周期。

本次增加 15 行注释，覆盖批量操作、可见性、组件信息、摘要语义以及 Cursor 泄漏路径。保留原有 API 拼写 `moveNoteToFoler`，避免阅读阶段引入调用接口变更。

## 关键类三 DateTimePickerDialog

文件：[DateTimePickerDialog.java](../../../src/net/micode/notes/ui/DateTimePickerDialog.java)。它继承 AlertDialog，封装 DateTimePicker，通过 OnDateTimeSetListener 把时间交给编辑页，不直接访问数据库或 AlarmManager。

构造时创建控件并注册变化监听。每次变化依次设置 Calendar 的年、月、日、小时和分钟，然后更新标题；Calendar.MONTH 使用 0～11，监听器传入的月份与 Calendar 一致。初始时间来自参数，清零秒后同步控件，但没有清零毫秒。

`set24HourView()` 只修改对话框状态；内部控件在自身构造时读取系统格式。`updateTitle()` 合并年月日时间标志，但三元表达式两个分支都为 FORMAT_24HOUR，导致系统设置为 12 小时时标题仍强制 24 小时。该确定性静态问题记录为 Q-B-01。

确定按钮执行 `onClick()` 并回传毫秒时间戳；取消按钮监听器为 null，不写入提醒。对话框和现有设置调用链没有拒绝过去时间，因此后续快捷选项需要明确过去时间策略。该判断为代码阅读结论，实际系统触发行为尚未实测。

本次增加 11 行注释，覆盖类边界、时间单位、控件回调、秒毫秒处理、格式设置、质量问题和确认回调。

## 提醒流程与持久化

1. 编辑页菜单 `menu_alert` → `NoteEditActivity.setReminder()`（556 行）→ `DateTimePickerDialog`。
2. 确定 → `WorkingNote.setAlertDate(date, true)`（232 行），设置 ALERTED_DATE 待写值并调用监听器。
3. `NoteEditActivity.onClockAlertChanged()`（615 行）对未保存便签先尝试保存，构造含便签 URI 的 PendingIntent，然后用 AlarmManager.RTC_WAKEUP 注册。空白未保存便签没有有效 ID 时显示错误提示。
4. 已存在便签的数据通过编辑页 `onPause()` 调用保存流程；`WorkingNote.saveNote()`（190 行）经 Note.syncNote 持久化，同时按需触发组件刷新。不能把仅注册系统闹钟当作已完成数据库持久化。
5. 到时 → AlarmReceiver → AlarmAlertActivity → DataUtils 摘要和可见性查询 → 对话框与铃声 → 关闭时停止播放并释放。
6. 取消菜单 → `setAlertDate(0, false)` → 相同便签 URI 的 PendingIntent → AlarmManager.cancel；退出编辑页后保存取消状态。
7. 设备开机 → AlarmInitReceiver 查询 ALERTED_DATE 大于当前时间的普通便签 → 重新注册。普通应用重启不等于设备开机，该 Receiver 只监听 BOOT_COMPLETED。

组件刷新链：WorkingNote.saveNote / markDeleted → 监听器 onWidgetChanged → NoteEditActivity.updateWidget（409 行）→ 对应尺寸 Provider.onUpdate → 父类 update → RemoteViews。源代码关系图见 [diagrams.md](diagrams.md)。

## 后续快捷提醒设计入口

下一阶段在 `setReminder()` 前增加快捷项选择，同时保留现有自定义时间对话框。建议用独立的时间计算工具接受明确的 now、时区和选项，便于对跨天、月底、年底做确定性测试；仍复用 `WorkingNote.setAlertDate()` 和现有注册、取消流程。

“今天晚些时候”的具体时刻或间隔、当晚间时刻已过时如何处理、“一周后”是否保留当前时分应先在小组内固定。本阶段只记录设计方向，不把这些语义写成已经实现的产品规则。测试计划见 [environment-and-checks.md](environment-and-checks.md)。
