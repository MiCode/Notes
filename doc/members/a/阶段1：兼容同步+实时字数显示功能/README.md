# 钟萱第一阶段成果

姓名：钟萱　　学号：202430224015　　日期：2026-10-22（北京时间）

分工：页面流程与 UI 关系梳理、遗留 API 兼容性修复、编辑页实时字数显示功能，以及多处 Bug 修复。个人分支：`member/a-word-count`。仓库：[https://github.com/ttt587/Notes](https://github.com/ttt587/Notes)。起点为 `origin/develop` 的 `81a66024fe48eaa2206b854430856fe3875ff3ba`，建立分支时与 `origin/master` 相同。

本阶段完成补齐 Gradle 构建系统并配置版本控制忽略、在 Android Studio 成功运行工程；针对 compileSdk 34 / targetSdk 34 完成 Manifest 与遗留 API 适配；梳理全项目页面流程与 UI 关系；实现编辑页实时字数显示；修复垃圾桶多选 Bug、恢复顶部 ActionBar / overflow 菜单、修复设置页闪退；并完成功能与兼容性测试。

| 成果 | 位置 |
| --- | --- |
| Gradle 兼容构建改造与 git 忽略配置 | `settings.gradle`、`build.gradle`、`gradle.properties`、`gradle/wrapper/`、`gradlew`、`gradlew.bat`、`.gitignore` |
| Manifest 与遗留 API 兼容性修复清单 | `AndroidManifest.xml`、`build.gradle`、`GTaskASyncTask.java`、`NoteEditActivity.java`、`NotesListActivity.java` |
| 页面流程与 UI 关系梳理 | 见本文「二、页面流程与 UI 关系」 |
| 实时字数显示功能 | `res/layout/note_edit.xml`、`res/values*/strings.xml`、`NoteEditActivity.java` |
| 垃圾桶按钮多选 Bug 修复 | `src/net/micode/notes/ui/NotesListActivity.java` |
| 顶部 ActionBar / overflow 菜单恢复 | `res/values/styles.xml` |
| 设置页闪退修复 | `src/net/micode/notes/ui/NotesPreferenceActivity.java` |
| 功能与兼容性测试记录 | 见本文「四、此次测试」 |

## 一、在 Android Studio 运行（Gradle 兼容改造）

小米便签为 2011 年的项目，当时使用 Eclipse 而非 Gradle，需要为该项目补齐 Gradle 依赖，并在 `.gitignore` 中配置忽略，便于 git 合并。

在项目根目录下新建 `settings.gradle`、`build.gradle`、`gradle.properties` 文件，然后通过终端命令安装或直接从其他安卓项目中把 Gradle 依赖复制过来，包括：`gradle` 文件夹但只留下其中的 `wrapper` 文件夹，还有 `gradlew`、`gradlew.bat` 两个文件。然后重新用 Android Studio 打开该项目，会自动识别并询问是否进行同步。

同步之后，源文件有些冲突需要更改。此次修改文件清单（compileSdk 34 / targetSdk 34 适配）：

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `AndroidManifest.xml` | 为 5 个带 `intent-filter` 的组件补 `android:exported`（Activity / Receiver 设 `true`，Provider / AlarmReceiver 设 `false`） | targetSdk 31+ 强制要求带 `intent-filter` 的组件显式声明 `exported`，否则 Manifest 合并失败 |
| `build.gradle` | 在 `android{}` 加 `useLibrary 'org.apache.http.legacy'` | Apache HttpClient（`org.apache.http`）自 API 23 起从框架移除，compileSdk 34 下 `GTaskClient` 编译报「包不存在」 |
| `GTaskASyncTask.java` | `showNotification` 改用 `Notification.Builder`，移除已删除的 `setLatestEventInfo`，`PendingIntent` 加 `FLAG_IMMUTABLE` | `setLatestEventInfo` API 23 已移除导致编译失败；targetSdk 31+ 下 `PendingIntent` 不加可变性标志会崩溃 |
| `NoteEditActivity.java` | 1）修正第 433 行误加的 `-`；2）`onOptionsItemSelected` 的 `switch(R.id)` 改为 if-else | 笔误导致语义错误；AGP 8 生成的应用 R 字段不再是 final 常量，`case R.id` 报「需要常量表达式」 |
| `NotesListActivity.java` | 3 处含 `case R.id` 的 switch（`onMenuItemClick`、`onClick`、`onOptionsItemSelected`）改为 if-else | 同上，应用 R 资源 id 非编译期常量，switch-case 报「需要常量表达式」 |

## 二、页面流程与 UI 关系

### 首页 · 笔记列表页（`NotesListActivity.java`）

从上到下有组件：标题栏（`tv_title_bar`，子文件夹内才显示）、笔记 / 文件夹列表（`notes_list`）、正下方「新建笔记」按钮（`btn_new_note`）。

- 点击「笔记条目」→ 跳转编辑页（`NoteEditActivity.java`），返回时列表刷新。
- 点击「文件夹 / 系统条目」→ 进入子文件夹（仍是本页，标题栏显示文件夹名，列表变为该夹内容）。
- 点击「通话记录夹」→ 进入通话记录（本页，隐藏新建按钮）。
- 点击「新建笔记按钮」→ 跳转编辑页（新建模式）。
- 长按「笔记条目」→ 进入多选模式（底部出现全选下拉菜单 + 顶部删除 / 移动）。
- 长按「文件夹条目」→ 弹出菜单：查看 / 删除 / 重命名。
- 菜单「新建文件夹」→ 弹出命名对话框。
- 菜单「搜索」→ 系统搜索，选中结果 → 跳转编辑页（高亮关键词）。
- 菜单「设置」/「同步（未配置）」→ 跳转设置页（`NotesPreferenceActivity.java`）。
- 菜单「同步（已配置）」→ 触发同步服务（`GTaskSyncService.java`）。
- 子文件夹 / 通话记录内按返回键 → 回到首页根列表；根列表再按返回键 → 退出应用。

### 编辑页（`NoteEditActivity.java`）

从上到下有组件：标题栏（`note_title`：修改时间、提醒图标 / 文字、背景色按钮 `btn_set_bg_color`）、编辑区（`sv_note_edit`：普通模式单编辑器 `note_edit_view`，或清单模式多行列表 `note_edit_list`）、隐藏面板（背景色选择面板、字号选择面板）。

- 点击「背景色按钮」→ 弹出背景色面板，选色即应用并收起。
- 菜单「字号」→ 弹出字号面板，选择即应用并持久化。
- 菜单「清单模式」→ 在普通编辑器 ⇄ 清单多行编辑器间切换。
- 菜单「设置提醒」→ 弹出日期时间选择框（`DateTimePickerDialog.java`），确认后注册闹钟；到期触发 `AlarmReceiver` → 提醒页（`AlarmAlertActivity.java`）。
- 菜单「分享」→ 系统分享选择器（外部应用）。
- 菜单「桌面快捷方式」→ 发送广播在桌面创建图标。
- 菜单「新建」→ 保存当前笔记后打开新的编辑页。
- 菜单「删除」→ 确认后删除并返回首页。
- 按返回键 / 离开页面 → 自动保存笔记，回到首页。

### 设置页（`NotesPreferenceActivity.java`）

配置云同步账号等；完成后返回首页。

### 提醒页（`AlarmAlertActivity.java`）

闹钟到点弹出，展示笔记提醒内容。

### 桌面小部件（`NoteWidgetProvider_2x/4x.java`）

主屏 2x / 4x 显示笔记，点击进入编辑页；笔记被删改时通过广播刷新。

## 三、字数统计功能与 Bug 修复

### 1. 垃圾桶按钮 Bug 修复

- **文件**：`src/net/micode/notes/ui/NotesListActivity.java`
- **改动**：`ModeCallback.onMenuItemClick` 里删除确认框的「取消」负按钮，从 `null` 改为点击时调用 `mModeCallBack.finishActionMode()`。
- **原因**：长按便签进入多选后，点垃圾桶 → 确认框按「取消」时多选模式不退出，导致底部删除按钮一直滞留。

### 2. 编辑页底部新增留空行 + 实时字数统计器

- **文件**：`res/layout/note_edit.xml`
  - **改动**：在编辑区 ScrollView 下方、底部遮罩上方新增 `word_count_bar` 行，内含左对齐的 `tv_word_count` 文本。
  - **原因**：为编辑页底部增加一行留空，并在该行左侧放字数统计器。
- **文件**：`res/values/strings.xml` / `res/values-zh-rCN/strings.xml` / `res/values-zh-rTW/strings.xml`
  - **改动**：各新增一条 `note_word_count` 格式串（`Characters: %1$d` / `字数：%1$d` / `字數：%1$d`）。
  - **原因**：为统计器提供跟随系统语言的文案，避免硬编码。
- **文件**：`src/net/micode/notes/ui/NoteEditActivity.java`
  - **改动**：新增 `Editable` / `TextWatcher` 导入、`mWordCount` 字段、`refreshWordCount()` 方法；在 `initResources` 绑定视图并给编辑器加监听；在 `initNoteScreen`、`switchToListMode`、`getListItem`、`onCheckListModeChanged` 处刷新字数。
  - **原因**：让字数在普通模式与清单模式下都随输入实时更新（不含换行）。

### 3. 恢复顶部 ActionBar / overflow 菜单

- **文件**：`res/values/styles.xml`
- **改动**：`NoteActionBarStyle` 删除 `android:visibility">gone`，`displayOptions` 由空改为 `showTitle`。
- **原因**：原主题把 ActionBar 整体隐藏，导致首页 / 编辑页 overflow（新建文件夹、设置、字号、清单模式、提醒、分享等菜单）无法访问。

### 4. 设置页闪退修复

- **文件**：`src/net/micode/notes/ui/NotesPreferenceActivity.java`
- **改动**：新增 `android.os.Build` 导入；`registerReceiver(mReceiver, filter)` 改为按 `Build.VERSION.SDK_INT` 判断，Android 13+ 使用 `RECEIVER_NOT_EXPORTED` 标志的重载。
- **原因**：targetSdk 34 下注册自定义广播接收器未指定 exported 标志会抛 `SecurityException`，导致一进设置页即崩溃。

### 5. 阅读与注释文件

- **文件**：
`src/net/micode/notes/ui/NotesListActivity.java`，
`src/net/micode/notes/ui/NotesListAdapterActivity.java`，
`src/net/micode/notes/ui/NotesEditActivity.java`，
`src/net/micode/notes/ui/NotesListItem.java`


## 四、此次测试

- 便签新增、删除、编辑（包括模式转化、背景色变化、字号选择、长文本）可行。
- 字数实时显示可行。
- 横屏自动适应可行。
- 补齐语言后随系统切换语言可行。

---

> **首次提交 SHA：
7d82c16b3bd7e0411ea1e14725bb437555daa5de