package net.micode.notes.ui;

import android.app.Activity;
import android.Manifest;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.QuickReminder;
import net.micode.notes.tool.ReminderScheduler;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 便签编辑页面 Activity
 * 负责便签的创建、编辑、显示，支持背景色/字号设置、清单模式、提醒、分享、桌面小部件等功能
 * 同时实现了点击监听、便签设置变更监听、文本编辑行变更监听三个接口
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    // 头部视图控件持有者，缓存标题栏上的各个控件引用，避免重复 findViewById
    private class HeadViewHolder {
        public TextView tvModified;        // 显示便签最后修改时间

        public ImageView ivAlertIcon;      // 提醒（时钟）图标

        public TextView tvAlertDate;       // 提醒日期/剩余时间文本

        public ImageView ibSetBgColor;     // 设置背景色的按钮图标
    }

    // 背景色选择面板中"色块按钮ID"到"颜色资源常量"的映射表
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    // "颜色资源常量"到"该颜色选中态标识视图ID"的映射表
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    // 字号选择面板中"字号按钮ID"到"字号资源常量"的映射表
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    // "字号资源常量"到"该字号选中态标识视图ID"的映射表
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    // 日志标签，用于 Logcat 输出时标识当前类
    private static final String TAG = "NoteEditActivity";
    private static final int REQUEST_REMINDER_NOTIFICATION = 41;
    private static final int REQUEST_EXACT_REMINDER = 42;

    private HeadViewHolder mNoteHeaderHolder;   // 头部控件持有者实例

    private View mHeadViewPanel;                // 标题栏整体面板

    private View mNoteBgColorSelector;          // 背景色选择浮层

    private View mFontSizeSelector;             // 字号选择浮层

    private EditText mNoteEditor;               // 普通模式下的便签编辑框

    private View mNoteEditorPanel;              // 编辑区域整体面板（用于设置背景）

    private TextView mWordCount;                 // 底部实时字数统计器

    private WorkingNote mWorkingNote;           // 当前正在编辑的便签数据模型

    private SharedPreferences mSharedPrefs;     // 偏好设置存储（用于持久化字号等）
    private int mFontSizeId;                    // 当前选中的字号资源ID

    // 偏好设置中保存字号的 key
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    // 桌面快捷方式图标标题的最大长度
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    public static final String TAG_CHECKED = String.valueOf('\u221A');    // 清单已勾选项前缀标记"√"
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');  // 清单未勾选项前缀标记"□"

    private LinearLayout mEditTextList;         // 清单模式下的多行编辑列表容器

    private String mUserQuery;                  // 来自搜索结果的用户查询关键词
    private Pattern mPattern;                   // 用于高亮匹配的正则表达式

    /**
     * Activity 创建入口：加载布局、初始化便签状态与控件资源
     * 若是首次创建（非状态恢复）且便签状态初始化失败，则直接关闭页面
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state
     */
    // 从被系统回收后的状态恢复：根据保存的 noteId 重新构建 Intent 并初始化便签状态
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    /**
     * 根据 Intent 的 Action 初始化便签工作状态
     * ACTION_VIEW：从数据库加载已有便签（含从搜索结果跳转的场景）
     * ACTION_INSERT_OR_EDIT：新建便签，或解析通话记录便签
     * 其他 Action 不支持，直接关闭页面
     * @return 初始化成功返回 true，失败并已 finish() 返回 false
     */
    private boolean initActivityState(Intent intent) {
        /**
         * If the user specified the {@link Intent#ACTION_VIEW} but not provided with id,
         * then jump to the NotesListActivity
         */
        mWorkingNote = null;
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            /**
             * Starting from the searched result
             */
            // 若来自搜索结果，则从搜索附加数据中取出 noteId 和用户查询词
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            // 校验该便签是否存在于数据库中，不存在则提示并跳转到列表页
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                // 从数据库加载便签数据
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            // 查看已有便签时：隐藏软键盘，窗口自适应大小
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
            // 新建便签：从 Intent 中读取所属目录、小部件信息、背景色等参数
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // Parse call-record note
            // 解析通话记录便签：若同时带有通话日期和电话号码，则按记录创建或加载
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                // 已存在相同通话记录便签则加载，否则新建并转换为通话便签
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                // 普通新建空便签
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            // 新建/编辑时：窗口自适应大小并自动弹出软键盘
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            // 不支持的 Action，记录错误并关闭
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        // 注册便签设置变更监听器，设置变化时回调本 Activity
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * 每次回到前台时重新初始化便签界面，保证显示与数据一致
     */
    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    /**
     * 初始化便签界面显示：设置字号、内容（普通/清单模式）、背景色、修改时间、提醒信息，并同步字数
     */
    private void initNoteScreen() {
        // 应用当前字号外观到编辑框
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        // 根据是否清单模式选择展示方式：清单切换为多行列表，普通模式直接设文本并高亮搜索词
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        // 初始隐藏所有背景色的选中标识
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }
        // 应用标题栏与编辑区背景
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        // 格式化并显示便签的修改日期时间
        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker
         * is not ready
         */
        showAlertHeader();
        refreshWordCount(); // 初始化时同步一次字数
    }

    /**
     * 显示/隐藏头部提醒信息：有提醒时根据是否过期显示相应文案
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                // 提醒时间已过，显示已过期
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                // 未过期，显示相对剩余时间
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            // 无提醒，隐藏提醒图标与文本
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        };
    }

    /**
     * 当 Activity 已存在而再次收到新 Intent 时（singleTop），重新初始化便签状态
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    /**
     * 保存实例状态：若便签尚未入库则先保存以生成 noteId，再将 id 存入 Bundle 供恢复使用
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * 分发触摸事件：点击到背景色/字号选择浮层以外的区域时，自动收起该浮层
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }

        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 判断触摸点是否落在指定视图的屏幕范围内
     * @return 在范围内返回 true，否则返回 false
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int []location = new int[2];
        view.getLocationOnScreen(location); // 获取视图在屏幕上的左上角坐标
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;
        }
        return true;
    }

    /**
     * 初始化界面控件资源：查找各类视图、设置点击监听、读取偏好字号，并为编辑框绑定字数实时刷新
     */
    private void initResources() {
        // 标题栏及其内部控件
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        // 背景色选择浮层及其中每个色块按钮的点击监听
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        // 字号选择浮层及每个字号按钮的点击监听
        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        };
        // 读取持久化的字号设置
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        // 防御：保存的字号ID越界时回退为默认值
        if(mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);
        mWordCount = (TextView) findViewById(R.id.tv_word_count);
        // 普通模式下监听编辑器文本变化，实时刷新字数
        mNoteEditor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                refreshWordCount();
            }
        });
    }

    /**
     * 页面暂停时保存便签内容，并清除浮层设置状态
     */
    @Override
    protected void onPause() {
        super.onPause();
        if(saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    /**
     * 更新与便签关联的桌面小部件：根据小部件类型发送对应的更新广播
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        // 指定要刷新的小部件ID
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 统一点击事件处理：
     * 点击背景色按钮展开选择面板；点击色块切换便签背景；点击字号按钮切换并持久化字号
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            // 展开背景色选择面板，并高亮当前背景色的选中标识
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            // 选中某个颜色：取消旧标识、应用新背景色、收起面板
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            // 选中某个字号：更新选中态、持久化，并按当前模式刷新编辑区外观
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                // 清单模式需重新构建各行列表以应用新字号
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    /**
     * 返回键处理：若存在展开的浮层则先收起，否则保存便签并退出
     */
    @Override
    public void onBackPressed() {
        if(clearSettingState()) {
            return;
        }

        saveNote();
        super.onBackPressed();
    }

    /**
     * 清除设置浮层状态：收起背景色或字号选择面板
     * @return 有浮层被收起返回 true，否则返回 false
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /**
     * 背景色变更回调：高亮新颜色标识并刷新编辑区与标题栏背景
     */
    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 准备选项菜单：根据便签类型（是否通话记录）、清单模式、是否有提醒动态构建与显隐菜单项
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        // 通话记录便签使用专用菜单，其余使用普通便签菜单
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        // 清单模式与普通模式之间切换菜单标题
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        // 根据是否已设置提醒，显示"设置提醒"或"删除提醒"
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    /**
     * 菜单项点击处理：分发新建、删除、字号、清单模式、分享、发送到桌面、设置/删除提醒等操作
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_delete) {
            // 删除前弹出确认对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_note));
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteCurrentNote();
                            finish();
                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (itemId == R.id.menu_font_size) {
            // 展开字号选择面板并高亮当前字号
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
        } else if (itemId == R.id.menu_list_mode) {
            // 在清单模式与普通模式间切换
            mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                    TextNote.MODE_CHECK_LIST : 0);
        } else if (itemId == R.id.menu_share) {
            // 获取当前文本并分享给其他应用
            getWorkingText();
            sendTo(this, mWorkingNote.getContent());
        } else if (itemId == R.id.menu_send_to_desktop) {
            sendToDesktop();
        } else if (itemId == R.id.menu_alert) {
            setReminder();
        } else if (itemId == R.id.menu_delete_remind) {
            // 清除提醒设置
            mWorkingNote.setAlertDate(0, false);
        }
        return true;
    }

    /**
     * 设置提醒：校验权限后显示快捷选项，并保留自定义日期时间入口
     */
    private void setReminder() {
        getWorkingText();
        if (TextUtils.isEmpty(mWorkingNote.getContent())) {
            showToast(R.string.error_note_empty_for_clock);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    REQUEST_REMINDER_NOTIFICATION);
            return;
        }
        if (!ReminderScheduler.canScheduleExactly(this) && Build.VERSION.SDK_INT >= 31) {
            new AlertDialog.Builder(this).setTitle(R.string.reminder_exact_title)
                    .setMessage(R.string.reminder_exact_message)
                    .setPositiveButton(R.string.reminder_open_settings, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            startActivityForResult(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName())), REQUEST_EXACT_REMINDER);
                        }
                    })
                    .setNegativeButton(R.string.reminder_allow_delay, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            showReminderChoices();
                        }
                    }).setNeutralButton(android.R.string.cancel, null).show();
            return;
        }
        showReminderChoices();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_REMINDER_NOTIFICATION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                setReminder();
            } else {
                showToast(R.string.reminder_notification_denied);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXACT_REMINDER) {
            showReminderChoices();
        }
    }

    private void showReminderChoices() {
        final QuickReminder.Preset[] presets = QuickReminder.Preset.values();
        int[] names = { R.string.reminder_in_two_hours, R.string.reminder_tomorrow_morning,
                R.string.reminder_next_week };
        CharSequence[] items = new CharSequence[presets.length + 1];
        long now = System.currentTimeMillis();
        for (int i = 0; i < presets.length; i++) {
            long date = QuickReminder.calculate(presets[i], now, TimeZone.getDefault());
            items[i] = getString(names[i]) + "\n" + DateUtils.formatDateTime(this, date,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        }
        items[presets.length] = getString(R.string.reminder_custom_time);
        new AlertDialog.Builder(this).setTitle(R.string.reminder_choose_time)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == presets.length) {
                            showCustomReminder();
                        } else {
                            // Recalculate at selection time, rather than using an old dialog preview.
                            applyReminder(QuickReminder.calculate(presets[which],
                                    System.currentTimeMillis(), TimeZone.getDefault()));
                        }
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showCustomReminder() {
        long initial = mWorkingNote.getAlertDate() > System.currentTimeMillis()
                ? mWorkingNote.getAlertDate() : QuickReminder.calculate(
                        QuickReminder.Preset.IN_TWO_HOURS, System.currentTimeMillis(), TimeZone.getDefault());
        DateTimePickerDialog d = new DateTimePickerDialog(this, initial);
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                applyReminder(date);
            }
        });
        d.show();
    }

    private void applyReminder(long date) {
        if (!QuickReminder.isFuture(date, System.currentTimeMillis())) {
            showToast(R.string.reminder_time_in_past);
            return;
        }
        mWorkingNote.setAlertDate(date, true);
    }

    /**
     * Share note to apps that support {@link Intent#ACTION_SEND} action
     * and {@text/plain} type
     */
    // 通过 ACTION_SEND 隐式Intent将便签文本以纯文本形式分享出去
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    /**
     * 新建便签：先保存当前便签，再关闭本页并以 INSERT_OR_EDIT 启动新的编辑页
     */
    private void createNewNote() {
        // Firstly, save current editing notes
        saveNote();

        // For safety, start a new NoteEditActivity
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前便签：同步模式下移入回收站，非同步模式下直接删除，并标记为已删除
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            if (!isSyncMode()) {
                // 未开启同步：直接批量删除
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                // 开启同步：移动到回收站文件夹
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    /**
     * 判断当前是否处于同步模式（已配置同步账号）
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 时钟提醒变更回调：确保便签已入库后，注册或取消系统的 AlarmManager 提醒广播
     */
    public void onClockAlertChanged(long date, boolean set) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        // Persist both setting and cancellation immediately, before installing the system alarm.
        saveNote();
        if (mWorkingNote.getNoteId() > 0) {
            showAlertHeader();
            if(!set) {
                ReminderScheduler.cancel(this, mWorkingNote.getNoteId());
            } else {
                ReminderScheduler.schedule(this, mWorkingNote.getNoteId(), date);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * 小部件变更回调：刷新关联的桌面小部件
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 清单模式删除某行回调：移除该行视图，后续行索引前移，并将删除行文本合并到相邻行
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            // 仅剩一行时不允许删除
            return;
        }

        // 将被删行之后的所有行索引依次减 1
        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        // 找到合并目标行（上一行或首行），追加文本并将光标定位到末尾
        NoteEditText edit = null;
        if(index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * 清单模式回车新增某行回调：在指定位置插入新行视图，聚焦并设置光标，后续行索引顺延
     */
    public void onEditTextEnter(int index, String text) {
        /**
         * Should not happen, check for debug
         */
        if(index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        // 重新设置插入点之后各行的索引
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 切换到清单模式：清空列表，按换行拆分内容逐行构建可勾选条目，并在末尾追加一空行
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        // 末尾追加空行并聚焦，方便继续输入
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        // 隐藏普通编辑框，显示清单列表
        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
        refreshWordCount(); // 切换到清单模式后刷新字数
    }

    /**
     * 生成带搜索关键词高亮的可显示文本：对用户查询匹配到的片段添加背景色 Span
     * @return 处理后的 Spannable 文本
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            // 用查询词作为正则，逐个匹配位置施加高亮背景
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 构建清单模式下单个可编辑行视图：设置字号、复选框勾选联动删除线、解析勾选标记、绑定监听与字数刷新
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        // 勾选状态变化时切换文本删除线样式
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        // 根据行文本前缀（√/□）初始化勾选状态并剥离标记
        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        // 绑定行文本变化监听、索引、高亮文本，并注册字数实时刷新
        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        // 清单模式下监听每行文本变化，实时刷新总字数
        edit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                refreshWordCount();
            }
        });
        return view;
    }

    /**
     * 行文本内容变化回调：根据该行是否有内容显示或隐藏其复选框
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if(hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * 清单模式切换回调：进入清单模式则构建列表；回到普通模式则汇总各行文本并显示到编辑框
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            // 汇总各行文本，若没有勾选项则去除未勾选标记
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
            refreshWordCount(); // 切回普通模式后刷新字数
        }
    }

    /**
     * 从界面收集当前工作文本写回数据模型
     * 清单模式下逐行拼接（带 √/□ 前缀），普通模式直接取编辑框内容
     * @return 是否存在已勾选项
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    // 已勾选加"√"前缀，未勾选加"□"前缀
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 保存便签：先收集工作文本再写入数据库，成功后设 RESULT_OK 标识创建/编辑状态
     * @return 保存成功返回 true
     */
    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * {@link #RESULT_OK} is used to identify the create/edit state
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 发送便签到桌面快捷方式：确保入库后构造 INSTALL_SHORTCUT 广播，附带图标与标题
     */
    private void sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            // 构造快捷方式：点击时以 ACTION_VIEW 打开该便签
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成桌面快捷方式标题：去除勾选标记并截断到最大长度
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    /**
     * 显示短时 Toast 提示
     */
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    /**
     * 显示指定时长的 Toast 提示
     */
    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    // 统计当前便签字数（不含换行）并刷新到底部统计器；兼容清单模式
    private void refreshWordCount() {
        if (mWordCount == null) {
            return;
        }
        int count = 0;
        if (mWorkingNote != null && mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 清单模式：累加各行文本字数
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                NoteEditText edit = (NoteEditText) mEditTextList.getChildAt(i)
                        .findViewById(R.id.et_edit_text);
                count += edit.getText().toString().replace("\n", "").length();
            }
        } else if (mNoteEditor != null) {
            // 普通模式：直接统计编辑框文本字数
            count = mNoteEditor.getText().toString().replace("\n", "").length();
        }
        mWordCount.setText(getString(R.string.note_word_count, count));
    }
}
