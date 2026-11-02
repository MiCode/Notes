# B 模块源代码结构图

图示依据基线及本阶段阅读；只展示 B 负责的功能和相关依赖，不代表整个应用的完整 UML。PNG 在 assets 下，可供 Word 报告和后续小组 PPT 复用。箭头含义分别标明，不能将依赖箭头当成继承。

## 范围内用例

![范围内用例](assets/use-cases.png)

用例参与者为用户；范围内功能包括设置自定义提醒、取消提醒、查看提醒、通过桌面小组件查看和编辑便签。快捷提醒是下一阶段需求，未列为现有实现。

## 包依赖

![包依赖](assets/packages.png)

```mermaid
flowchart LR
  ui[ui 提醒和编辑界面] --> model[model 工作模型]
  ui --> tool[tool 数据查询和资源工具]
  ui --> widget[widget 桌面小组件]
  model --> data[data 便签数据与契约]
  widget --> data
  widget --> tool
  widget --> ui
  tool --> data
  tool --> ui
```

此图是 B 范围的源码包依赖视图，不包含全部 gtask 依赖。tool → ui 的证据是 AppWidgetAttribute 与偏好键引用，widget → ui 的证据是点击目标 Activity 引用。没有独立 Widget Service。

## 关键类关系

![关键类关系](assets/classes.png)

```mermaid
classDiagram
  AppWidgetProvider <|-- NoteWidgetProvider
  NoteWidgetProvider <|-- NoteWidgetProvider_2x
  NoteWidgetProvider <|-- NoteWidgetProvider_4x
  NoteWidgetProvider ..> ResourceParser : 背景资源
  NoteWidgetProvider ..> NoteEditActivity : 点击Intent
  NoteWidgetProvider : +onDeleted(context, ids)
  NoteWidgetProvider : #update(context, manager, ids)
  NoteWidgetProvider : #getLayoutId()*
  NoteWidgetProvider : #getBgResourceId(bgId)*
  NoteWidgetProvider : #getWidgetType()*
  AlertDialog <|-- DateTimePickerDialog
  DateTimePickerDialog --> DateTimePicker : 持有控件
  NoteEditActivity ..> DateTimePickerDialog : 创建和回调
  NoteEditActivity --> WorkingNote : 工作状态
  WorkingNote ..> NoteEditActivity : 状态监听回调
  NoteEditActivity ..> AlarmReceiver : PendingIntent
  AlarmInitReceiver ..> AlarmReceiver : 开机重新注册
  AlarmReceiver ..> AlarmAlertActivity : 启动
  AlarmAlertActivity ..> DataUtils : 摘要和可见性
```

## 提醒流程

![提醒流程](assets/reminder-flow.png)

```mermaid
flowchart TD
  A[编辑页 设置提醒] --> B[DateTimePickerDialog 自定义时间]
  B -->|确定| C[WorkingNote.setAlertDate 标记待保存值]
  C --> D[onClockAlertChanged 未保存便签先尝试保存]
  D --> E{有有效便签ID}
  E -->|否| F[显示空便签错误提示]
  E -->|是| G[按便签URI建立PendingIntent并注册RTC_WAKEUP]
  C -.编辑页onPause.-> H[saveNote 经Note.syncNote持久化 按需刷新组件]
  G -->|到时| I[AlarmReceiver]
  I --> J[AlarmAlertActivity 查询可见性 展示并播放铃声]
  J --> K[关闭对话框 停止并释放播放器]
  L[取消提醒菜单] --> M[setAlertDate 0 false]
  M --> N[相同便签URI的PendingIntent 调用AlarmManager.cancel]
  M -.编辑页onPause.-> H
  O[设备BOOT_COMPLETED] --> P[AlarmInitReceiver 查询未来普通便签提醒]
  P --> G
```

现有便签设置提醒后，系统注册发生在回调中，数据库持久化依赖保存流程；两者不是同一个操作。普通应用重启与 BOOT_COMPLETED 恢复路径应分别验证。
