package net.micode.notes.tests;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.test.mock.MockContentResolver;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;
import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.QuickReminder;
import net.micode.notes.tool.ReminderScheduler;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.AlarmAlertActivity;
import net.micode.notes.ui.AlarmReceiver;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePickerDialog;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.TimeZone;

/** Real API-36 acceptance tests. Test-only APK: not included in the application sources. */
@SuppressLint("NewApi")
public class ReminderInstrumentation extends Instrumentation {
    private Bundle args;
    private Context context;
    private int passed;
    private final StringBuilder log = new StringBuilder();
    private static final String NOTE_TEXT = "B 快捷提醒运行测试";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }
    private void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        passed++;
        log.append("PASS ").append(name).append('\n');
        Bundle progress = new Bundle();
        progress.putString("stream", "PASS " + name + "\n");
        sendStatus(0, progress);
    }
    private void main(Runnable action) {
        runOnMainSync(action);
        waitForIdleSync();
    }
    private long createNote(String text, int widget, int type) {
        WorkingNote note = WorkingNote.createEmptyNote(context, Notes.ID_ROOT_FOLDER,
                widget, type, ResourceParser.YELLOW);
        note.setWorkingText(text);
        if (!note.saveNote()) throw new AssertionError("Cannot save fixture note");
        return note.getNoteId();
    }
    private NoteEditActivity edit(long id) {
        Intent intent = new Intent(context, NoteEditActivity.class).setAction(Intent.ACTION_VIEW)
                .putExtra(Intent.EXTRA_UID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        NoteEditActivity activity = (NoteEditActivity) startActivitySync(intent);
        waitForIdleSync();
        main(() -> ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0));
        return activity;
    }
    private void menu(NoteEditActivity activity, int id) {
        main(() -> activity.onOptionsItemSelected(new PopupMenu(activity,
                activity.getWindow().getDecorView()).getMenu().add(0, id, 0, "test")));
    }
    private void count(NoteEditActivity activity,String name,int expected) {
        TextView counter=activity.findViewById(R.id.tv_word_count);
        check(name,counter.getText().toString().equals(activity.getString(R.string.note_word_count,expected)));
    }
    private void wordCountTests(NoteEditActivity activity) throws Exception {
        main(() -> {
            TextView counter=activity.findViewById(R.id.tv_word_count);
            check("merged-word-count-visible",counter!=null && counter.isShown());
            EditText editor=activity.findViewById(R.id.note_edit_view);
            editor.setText("");count(activity,"merged-word-count-empty",0);
            editor.setText("中文测试");count(activity,"merged-word-count-chinese",4);
            editor.setText("中文ABC 12");count(activity,"merged-word-count-mixed",8);
            editor.setText("中文\nABC");count(activity,"merged-word-count-excludes-newline",5);
            editor.setText("中");count(activity,"merged-word-count-delete",1);
            editor.setText("");count(activity,"merged-word-count-clear",0);
            editor.setText("甲乙\nABC");
        });
        menu(activity,R.id.menu_list_mode);
        main(() -> {
            count(activity,"merged-word-count-checklist",5);
            LinearLayout list=activity.findViewById(R.id.note_edit_list);
            ((EditText)list.getChildAt(0).findViewById(R.id.et_edit_text)).setText("中");
            count(activity,"merged-word-count-checklist-edit",4);
        });
        menu(activity,R.id.menu_list_mode);
        main(() -> count(activity,"merged-word-count-normal-mode",4));
        main(() -> ((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0));
        getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_FREEZE_90);
        SystemClock.sleep(1500);
        main(() -> count(activity,"merged-word-count-landscape",4));
        shot("merged-word-count-landscape");
        getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_FREEZE_0);
        SystemClock.sleep(1500);
        main(() -> {
            count(activity,"merged-word-count-portrait",4);
            ((EditText)activity.findViewById(R.id.note_edit_view)).setText(NOTE_TEXT);
        });
        shot("merged-word-count-portrait");
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String prefix) {
        if (root == null) return null;
        if (root.getText() != null && root.getText().toString().startsWith(prefix)) return root;
        for (int i=0;i<root.getChildCount();i++) {
            AccessibilityNodeInfo result = find(root.getChild(i), prefix);
            if (result != null) return result;
        }
        return null;
    }
    private void click(String prefix) {
        for (int attempt=0;attempt<30;attempt++) {
            AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), prefix);
            if (node != null) {
                // Adapter rows may expose text without ACTION_CLICK. Tap the bounds
                // of the observed text node, rather than clicking its ListView parent.
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                if (bounds.isEmpty()) throw new AssertionError("Empty UI bounds: "+prefix);
                long time=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
                MotionEvent up=MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
                getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);
                down.recycle();up.recycle();
                waitForIdleSync();SystemClock.sleep(250);return;
            }
            SystemClock.sleep(150);
        }
        throw new AssertionError("No clickable UI text: " + prefix);
    }
    private void shot(String name) throws Exception {
        waitForIdleSync();SystemClock.sleep(1000);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        File folder = context.getExternalFilesDir("b-evidence");
        folder.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(folder,name+".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }
        bitmap.recycle();
    }
    private void cursorTests() {
        MockContentResolver resolver = new MockContentResolver();
        FakeProvider provider = new FakeProvider();
        ProviderInfo info=new ProviderInfo();info.authority=Notes.AUTHORITY;
        provider.attachInfo(context,info);
        resolver.addProvider(Notes.AUTHORITY,provider);
        TrackingCursor c=new TrackingCursor();c.addRow(new Object[]{71L});provider.next=c;
        check("cursor-hit-returns-and-closes",DataUtils.getNoteIdByPhoneNumberAndCallDate(resolver,"123",0)==71 && c.closed);
        c=new TrackingCursor();provider.next=c;
        check("cursor-empty-closes",DataUtils.getNoteIdByPhoneNumberAndCallDate(resolver,"123",0)==0 && c.closed);
        c=new TrackingCursor();c.addRow(new Object[]{71L});c.fail=true;provider.next=c;
        check("cursor-read-error-closes",DataUtils.getNoteIdByPhoneNumberAndCallDate(resolver,"123",0)==0 && c.closed);
        provider.next=null;
        check("cursor-null-safe",DataUtils.getNoteIdByPhoneNumberAndCallDate(resolver,"123",0)==0);
        c=new TrackingCursor();provider.next=c;
        check("phone-cursor-empty-closes",DataUtils.getCallNumberByNoteId(resolver,1).equals("") && c.closed);
    }
    public static class TrackingCursor extends MatrixCursor {
        boolean closed, fail;
        TrackingCursor() {super(new String[]{"note_id"});}
        @Override public long getLong(int column) {
            if (fail) throw new IndexOutOfBoundsException("injected read failure");
            return super.getLong(column);
        }
        @Override public void close() {closed=true;super.close();}
    }
    public static class FakeProvider extends ContentProvider {
        Cursor next;
        @Override public boolean onCreate(){return true;}
        @Override public Cursor query(Uri u,String[] p,String s,String[] a,String order){return next;}
        @Override public String getType(Uri u){return null;}
        @Override public Uri insert(Uri u,ContentValues v){return null;}
        @Override public int delete(Uri u,String s,String[] a){return 0;}
        @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
    }
    private void pickerTests(NoteEditActivity activity) throws Exception {
        main(() -> {
            DateTimePicker picker = new DateTimePicker(activity);
            picker.setCurrentDate(2026,Calendar.JANUARY,31,23,59);
            picker.setCurrentDate(2026,Calendar.FEBRUARY,1,9,0);
            check("picker-january-to-february",picker.getCurrentMonth()==Calendar.FEBRUARY && picker.getCurrentDay()==1);
            picker.setCurrentDate(2028,Calendar.FEBRUARY,29,15,30);
            check("picker-leap-day",picker.getCurrentDay()==29 && picker.getCurrentMonth()==Calendar.FEBRUARY);
            picker.set24HourView(true);
            try {
                Field field=DateTimePicker.class.getDeclaredField("mOnHourChangedListener");field.setAccessible(true);
                NumberPicker.OnValueChangeListener listener=(NumberPicker.OnValueChangeListener)field.get(picker);
                NumberPicker hours=picker.findViewById(R.id.hour);
                hours.setValue(18);listener.onValueChange(hours,15,18);
                check("picker-24-hour-evening",picker.getCurrentHourOfDay()==18);
                picker.setCurrentDate(2027,Calendar.JANUARY,2,0,0);
                check("picker-midnight",picker.getCurrentHourOfDay()==0);
                picker.setCurrentDate(2027,Calendar.JANUARY,2,12,0);
                check("picker-noon",picker.getCurrentHourOfDay()==12);
                picker.setCurrentDate(2027,Calendar.JANUARY,2,23,59);
                check("picker-last-minute",picker.getCurrentHourOfDay()==23 && picker.getCurrentMinute()==59);
                picker.setCurrentDate(2026,Calendar.DECEMBER,31,23,50);
                hours.setValue(0);listener.onValueChange(hours,23,0);
                check("picker-midnight-cross-year",picker.getCurrentYear()==2027 && picker.getCurrentMonth()==0 && picker.getCurrentDay()==1 && picker.getCurrentHourOfDay()==0);
            } catch (ReflectiveOperationException error) {throw new AssertionError(error);}
        });
        final DateTimePickerDialog[] holder=new DateTimePickerDialog[1];
        final int[] callbacks={0};
        main(() -> {
            DateTimePickerDialog dialog=new DateTimePickerDialog(activity,System.currentTimeMillis()-120000);
            holder[0]=dialog;
            dialog.setOnDateTimeSetListener((d,date)->callbacks[0]++);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            check("custom-past-rejected-and-kept-open",callbacks[0]==0 && dialog.isShowing());
        });
        shot("05-past-time-rejected");
        main(() -> {
            try {
                Field field=DateTimePickerDialog.class.getDeclaredField("mDateTimePicker");field.setAccessible(true);
                DateTimePicker picker=(DateTimePicker)field.get(holder[0]);
                Calendar date=Calendar.getInstance();date.add(Calendar.DAY_OF_YEAR,1);
                date.set(Calendar.HOUR_OF_DAY,15);date.set(Calendar.MINUTE,30);
                picker.setCurrentDate(date.getTimeInMillis());
                int titleId=activity.getResources().getIdentifier("alertTitle","id","android");
                holder[0].set24HourView(true);
                TextView title=holder[0].findViewById(titleId);
                check("dialog-title-24-hour",title.getText().toString().contains("15:30") && picker.is24HourView());
                holder[0].set24HourView(false);
                check("dialog-title-12-hour",title.getText().toString().contains("3:30") && !title.getText().toString().contains("15:30") && !picker.is24HourView());
            } catch (ReflectiveOperationException error) {throw new AssertionError(error);}
        });
        shot("06-twelve-hour-format");
        main(() -> {holder[0].getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            check("custom-cancel-no-callback",callbacks[0]==0);});
        main(() -> {
            try {
                Calendar from=Calendar.getInstance();from.clear();from.set(2027,0,31,15,30);
                Calendar target=Calendar.getInstance();target.clear();target.set(2027,1,1,9,0);
                DateTimePickerDialog dialog=new DateTimePickerDialog(activity,from.getTimeInMillis());
                final long[] received={0};dialog.setOnDateTimeSetListener((d,date)->received[0]=date);
                dialog.show();
                Field field=DateTimePickerDialog.class.getDeclaredField("mDateTimePicker");field.setAccessible(true);
                ((DateTimePicker)field.get(dialog)).setCurrentDate(target.getTimeInMillis());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check("custom-month-end-callback-timestamp",received[0]==target.getTimeInMillis() && !dialog.isShowing());
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
    }
    private void quickUiTests(NoteEditActivity activity,long id) throws Exception {
        int[] names={R.string.reminder_in_two_hours,R.string.reminder_tomorrow_morning,R.string.reminder_next_week};
        QuickReminder.Preset[] presets=QuickReminder.Preset.values();
        for(int i=0;i<names.length;i++) {
            menu(activity,R.id.menu_alert);
            if (i==0) shot("02-quick-options");
            long expected=QuickReminder.calculate(presets[i],System.currentTimeMillis(),TimeZone.getDefault());
            click(context.getString(names[i]));
            long saved=WorkingNote.load(context,id).getAlertDate();
            check("quick-choice-"+i+"-saved-immediately",Math.abs(saved-expected)<60000);
            if(i==1) {
                main(() -> count(activity,"merged-word-count-with-reminder",10));
                shot("03-tomorrow-saved");
            }
            menu(activity,R.id.menu_delete_remind);
            check("quick-cancel-"+i+"-persisted",WorkingNote.load(context,id).getAlertDate()==0);
        }
        shot("07-cancelled");
        main(() -> count(activity,"merged-word-count-after-reminder-cancel",10));
        Intent stale=new Intent(context,AlarmReceiver.class).setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,id));
        check("cancelled-broadcast-rejected",!ReminderScheduler.isCurrentReminder(context,stale));
        menu(activity,R.id.menu_alert);
        click(context.getString(R.string.reminder_custom_time));
        shot("04-custom-time-preserved");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
    }
    private int dp(Activity activity,int value) {return (int)(value*activity.getResources().getDisplayMetrics().density);}
    private void widgets(NoteEditActivity editor) throws Exception {
        main(editor::finish);
        Activity activity=startActivitySync(new Intent(context,WidgetHostActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        AppWidgetHost host=new AppWidgetHost(context,4343);
        int small=host.allocateAppWidgetId(),large=host.allocateAppWidgetId();
        check("bind-2x2",manager.bindAppWidgetIdIfAllowed(small,new ComponentName(context,NoteWidgetProvider_2x.class)));
        check("bind-4x4",manager.bindAppWidgetIdIfAllowed(large,new ComponentName(context,NoteWidgetProvider_4x.class)));
        long smallNote=createNote("B 2x2 小组件 原内容",small,Notes.TYPE_WIDGET_2X);
        long largeNote=createNote("B 4x4 小组件 内容正常",large,Notes.TYPE_WIDGET_4X);
        final AppWidgetHostView[] views=new AppWidgetHostView[2];
        main(() -> {
            LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12));root.setBackgroundColor(Color.LTGRAY);
            TextView title=new TextView(activity);title.setText("B 小组件回归测试宿主\n真实 AppWidgetHost 与 RemoteViews");title.setTextSize(18);title.setTextColor(Color.BLACK);
            root.addView(title);
            int[] ids={small,large};
            for(int i=0;i<2;i++) {
                TextView label=new TextView(activity);label.setText(i==0?"2 × 2 小组件":"4 × 4 小组件");label.setTextColor(Color.BLACK);root.addView(label);
                views[i]=host.createView(activity,ids[i],manager.getAppWidgetInfo(ids[i]));
                root.addView(views[i],new LinearLayout.LayoutParams(dp(activity,i==0?180:350),dp(activity,i==0?150:240)));
            }
            activity.setContentView(root);host.startListening();
        });
        sendWidgetUpdate(small,NoteWidgetProvider_2x.class);sendWidgetUpdate(large,NoteWidgetProvider_4x.class);
        SystemClock.sleep(1000);waitForIdleSync();
        main(() -> {
            check("2x2-content-displayed",((TextView)views[0].findViewById(R.id.widget_text)).getText().toString().contains("原内容"));
            check("4x4-content-displayed",((TextView)views[1].findViewById(R.id.widget_text)).getText().toString().contains("内容正常"));
        });
        shot("08-widgets-before-edit");
        NoteEditActivity edit=edit(smallNote);
        main(() -> {((EditText)edit.findViewById(R.id.note_edit_view)).setText("B 2x2 小组件 更新成功");edit.onBackPressed();});
        SystemClock.sleep(1000);waitForIdleSync();
        main(() -> check("widget-updated-through-editor-save",((TextView)views[0].findViewById(R.id.widget_text)).getText().toString().contains("更新成功")));
        shot("09-widgets-after-edit");
        main(() -> {host.stopListening();host.deleteHost();});
        SystemClock.sleep(500);
        check("widget-delete-keeps-note",WorkingNote.load(context,smallNote).getContent().contains("更新成功"));
        check("widget-delete-unbinds",WorkingNote.load(context,smallNote).getWidgetId()==AppWidgetManager.INVALID_APPWIDGET_ID);
        main(activity::finish);
    }
    private void sendWidgetUpdate(int id,Class<?> provider) {
        context.sendBroadcast(new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setClass(context,provider).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,new int[]{id}));
    }
    private boolean notification(long id) {
        for(StatusBarNotification n:((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications()) {
            if(("note:"+id).equals(n.getTag())) return true;
        }
        return false;
    }
    private void trigger(long id) throws Exception {
        WorkingNote note=WorkingNote.load(context,id);
        long cancelledDue=System.currentTimeMillis()+5000;
        note.setAlertDate(cancelledDue,true);note.saveNote();
        ReminderScheduler.schedule(context,id,cancelledDue);
        note.setAlertDate(0,false);note.saveNote();ReminderScheduler.cancel(context,id);
        SystemClock.sleep(6500);
        check("cancelled-alarm-does-not-notify-after-due",!notification(id) && WorkingNote.load(context,id).getAlertDate()==0);
        long due=System.currentTimeMillis()+15000;
        note.setAlertDate(due,true);note.saveNote();
        ReminderScheduler.schedule(context,id,due);
        sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);waitForIdleSync();
        long limit=SystemClock.elapsedRealtime()+35000;
        while(!notification(id) && SystemClock.elapsedRealtime()<limit) SystemClock.sleep(300);
        check("real-background-alarm-notification",notification(id));
        getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        SystemClock.sleep(1800);shot("10-background-reminder-notification");
        ActivityMonitor monitor=addMonitor(AlarmAlertActivity.class.getName(),null,false);
        click(NOTE_TEXT);
        Activity alert=waitForMonitorWithTimeout(monitor,5000);
        check("notification-opens-existing-alert",alert!=null);
        // onCreate is observed before the notification shade finishes closing.
        SystemClock.sleep(1800);
        shot("11-reminder-alert");
        click(context.getString(R.string.notealert_ok));
        check("alert-dismiss-clears-notification",!notification(id));
        removeMonitor(monitor);
    }
    private void prepareRestart(long id) {
        WorkingNote note=WorkingNote.load(context,id);
        long date=QuickReminder.calculate(QuickReminder.Preset.IN_TWO_HOURS,System.currentTimeMillis(),TimeZone.getDefault());
        note.setAlertDate(date,true);note.saveNote();ReminderScheduler.schedule(context,id,date);
        context.getSharedPreferences("b_reminder_test",Context.MODE_PRIVATE).edit().putLong("id",id).putLong("date",date).commit();
        check("restart-fixture-persisted",WorkingNote.load(context,id).getAlertDate()==date);
    }
    @Override public void onStart() {
        context=getTargetContext();Bundle result=new Bundle();
        try {
            String mode=args.getString("mode","all");
            if (mode.equals("trigger")) {
                long id=createNote(NOTE_TEXT,AppWidgetManager.INVALID_APPWIDGET_ID,Notes.TYPE_WIDGET_INVALIDE);
                edit(id);trigger(id);
            } else if (mode.equals("inexact")) {
                check("exact-alarm-access-denied",!ReminderScheduler.canScheduleExactly(context));
                long id=createNote("B 精确闹钟权限回退测试",AppWidgetManager.INVALID_APPWIDGET_ID,Notes.TYPE_WIDGET_INVALIDE);
                NoteEditActivity activity=edit(id);menu(activity,R.id.menu_alert);
                shot("12-exact-permission-explanation");
                click(context.getString(R.string.reminder_allow_delay));
                click(context.getString(R.string.reminder_in_two_hours));
                check("inexact-fallback-saved-without-crash",WorkingNote.load(context,id).getAlertDate()>System.currentTimeMillis());
                shot("13-inexact-fallback-saved");main(activity::finish);
            } else if (mode.equals("notification_denied")) {
                check("notification-permission-not-granted",context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED);
                long id=createNote("B 通知权限拒绝测试",AppWidgetManager.INVALID_APPWIDGET_ID,Notes.TYPE_WIDGET_INVALIDE);
                NoteEditActivity activity=edit(id);menu(activity,R.id.menu_alert);
                android.accessibilityservice.AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();
                info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
                getUiAutomation().setServiceInfo(info);
                java.util.List<AccessibilityNodeInfo> buttons=java.util.Collections.emptyList();
                for(int attempt=0;attempt<40;attempt++) {
                    AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                    if(root!=null) buttons=root.findAccessibilityNodeInfosByViewId(
                            "com.android.permissioncontroller:id/permission_deny_button");
                    if(!buttons.isEmpty())break;
                    SystemClock.sleep(150);
                }
                check("notification-deny-button-present",!buttons.isEmpty());
                shot("14-notification-permission-request");
                check("notification-deny-button-clicked",buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
                SystemClock.sleep(700);waitForIdleSync();
                check("denied-notification-does-not-save-reminder",WorkingNote.load(context,id).getAlertDate()==0);
                shot("15-notification-denied-no-reminder");main(activity::finish);
            } else if (!mode.equals("all")) {
                long id=context.getSharedPreferences("b_reminder_test",0).getLong("id",0);
                long date=context.getSharedPreferences("b_reminder_test",0).getLong("date",0);
                check(mode+"-database-survives",id>0 && date>System.currentTimeMillis() && WorkingNote.load(context,id).getAlertDate()==date);
                NoteEditActivity activity=edit(id);shot(mode);main(activity::finish);
            } else {
                check("exact-alarm-access-granted-for-device-test",ReminderScheduler.canScheduleExactly(context));
                cursorTests();
                long id=createNote(NOTE_TEXT,AppWidgetManager.INVALID_APPWIDGET_ID,Notes.TYPE_WIDGET_INVALIDE);
                NoteEditActivity activity=edit(id);
                wordCountTests(activity);quickUiTests(activity,id);pickerTests(activity);widgets(activity);
                activity=edit(id);trigger(id);prepareRestart(id);
            }
            result.putInt("failures",0);
        } catch(Throwable error) {
            result.putInt("failures",1);
            log.append("FAIL ").append(android.util.Log.getStackTraceString(error));
            try{shot("failure");}catch(Exception ignored){}
        }
        result.putInt("passed",passed);
        result.putString("stream",log.toString()+"RESULT: "+passed+" passed, "+result.getInt("failures")+" failed\n");
        finish(result.getInt("failures")==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
