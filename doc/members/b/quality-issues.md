# 原始代码质量问题记录

> 第二阶段更新（2026-10-27）：Q-B-01 与 Q-B-02 已修复并通过设备断言。下文保留第一阶段原始证据与当时状态；当前结果见 [第二阶段维护](phase-two-implementation.md) 和 [测试记录](test-record.md)。

记录人：胡嘉豪　202430224010　日期：2026-10-21

分析基线：`81a66024fe48eaa2206b854430856fe3875ff3ba`。本阶段仅新增阅读注释；以下两个问题尚未修复。证据来自源码和控制流检查，尚未在 Android 设备上复现。

## Q-B-01 提醒时间标题忽略 12 小时设置

- 类和方法：`ui/DateTimePickerDialog.java` 的 `updateTitle(long date)`。
- 位置：基线第 80 行；注释后第 90 行。
- 严重程度：中。影响提醒时间展示一致性，不据此推断时间戳被错误保存。
- 状态：静态确认，设备复现待执行，修复待第二阶段。

原始证据：

```java
flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
```

预期：系统使用 12 小时时，标题与选择控件采用一致的 12 小时表示；24 小时时显示 24 小时。实际代码在布尔值两种取值下都加入 FORMAT_24HOUR，`mIs24HourView` 对标题格式不起区分作用。

建议复现步骤：在设备关闭“使用 24 小时制”；新建有内容的便签，打开提醒选择器并选择下午 15:30；比较 AM/PM 控件与标题；再开启 24 小时制重复。预期设备现象是标题在两种设置下都显示 15:30，该现象尚未实测。

修复建议：false 分支使用适当的 12 小时格式标志，并检查 `set24HourView()` 是否需要同步内部控件及刷新标题。第二阶段分别覆盖上午、下午、00:00、12:00、23:59；校验标题、控件、回调时间戳三者一致。

## Q-B-02 通话便签查询成功时未关闭 Cursor

- 类和方法：`tool/DataUtils.java` 的 `getNoteIdByPhoneNumberAndCallDate(...)`。
- 位置：基线第 254～262 行，成功返回在第 257 行；注释后成功返回在第 270 行。
- 严重程度：中。正常命中查询即可走到泄漏路径，重复调用有资源积累风险；未测量实际内存或句柄增长。
- 状态：控制流静态确认，资源检测待执行，修复待后续维护。

原始证据：

```java
if (cursor != null) {
    if (cursor.moveToFirst()) {
        try {
            return cursor.getLong(0);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "Get call note id fails " + e.toString());
        }
    }
    cursor.close();
}
```

控制流：query 返回非空 Cursor → moveToFirst 为 true → getLong 成功 → return 结束方法 → 后面的 close 不执行。相比之下，空结果路径会执行 close。该问题不依赖异常路径。

建议复现步骤：准备一条号码和通话时间可匹配的通话便签，调用该方法；用可记录 close 调用的 Cursor 或资源监测确认成功返回路径未关闭；对空结果、null Cursor、读取异常路径分别验证。尚未执行这些 Android 测试。

修复建议：将整个非空 Cursor 使用范围置于 try/finally，确保成功返回、空结果与异常都会关闭。保留当前返回语义（未命中返回 0），并协调共享工具类的其他调用方。相邻 `getCallNumberByNoteId()` 的空结果关闭路径也值得一并检查，但不混同为已测试缺陷。

## 阅读结论的限制

以上两项均可从原始代码定位，不是新增注释引入的问题。本阶段没有运行 SonarQube，也没有完成 APK 构建、模拟器运行或界面截图；不填报扫描分数、通过率或设备测试结果。下一阶段修复应单独提交并补充回归证据。
