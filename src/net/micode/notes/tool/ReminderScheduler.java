/* Copyright 2026 The Notes contributors. Licensed under the Apache License, Version 2.0. */
package net.micode.notes.tool;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.AlarmAlertActivity;
import net.micode.notes.ui.AlarmReceiver;

/** One identity and scheduling policy for editing, cancellation, and boot restoration. */
public final class ReminderScheduler {
    private static final String CHANNEL_ID = "note_reminders";
    private static final int NOTIFICATION_ID = 1002;
    private static final String EXTRA_ALERT_TIME = "net.micode.notes.ALERT_TIME";
    private static final String TAG = "ReminderScheduler";

    private ReminderScheduler() { }

    public static boolean canScheduleExactly(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms();
    }

    public static void schedule(Context context, long noteId, long date) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));
        intent.putExtra(EXTRA_ALERT_TIME, date);
        PendingIntent alarm = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            if (canScheduleExactly(context)) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, alarm);
                    return;
                } catch (SecurityException revokedWhileScheduling) {
                    Log.w(TAG, "Exact alarm access changed; using the documented inexact fallback");
                }
            }
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, alarm);
        } else if (Build.VERSION.SDK_INT >= 19) {
            manager.setExact(AlarmManager.RTC_WAKEUP, date, alarm);
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, date, alarm);
        }
    }

    public static void cancel(Context context, long noteId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));
        PendingIntent alarm = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (alarm != null) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(alarm);
            alarm.cancel();
        }
        dismissNotification(context, noteId);
    }

    public static void dismissNotification(Context context, long noteId) {
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel("note:" + noteId, NOTIFICATION_ID);
    }

    /** Reject cancelled, deleted, trashed, rescheduled, or premature broadcasts. */
    public static boolean isCurrentReminder(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return false;
        }
        Cursor cursor = context.getContentResolver().query(intent.getData(),
                new String[] { NoteColumns.ALERTED_DATE },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_NOTE),
                        String.valueOf(Notes.ID_TRASH_FOLER) }, null);
        if (cursor == null) {
            return false;
        }
        try {
            if (!cursor.moveToFirst()) {
                return false;
            }
            long saved = cursor.getLong(0);
            long expected = intent.getLongExtra(EXTRA_ALERT_TIME, saved);
            return saved > 0 && saved == expected && saved <= System.currentTimeMillis();
        } finally {
            cursor.close();
        }
    }

    /** Android 8+ uses a visible notification, avoiding blocked background Activity starts. */
    public static void showNotification(Context context, Intent received) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Reminder notification permission is not granted");
            return;
        }
        long noteId = ContentUris.parseId(received.getData());
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        manager.createNotificationChannel(channel);
        Intent open = new Intent(context, AlarmAlertActivity.class).setData(received.getData());
        PendingIntent content = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_reminder_notification)
                .setContentTitle(context.getString(R.string.reminder_channel_name))
                .setContentText(DataUtils.getSnippetById(context.getContentResolver(), noteId))
                .setContentIntent(content)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build();
        manager.notify("note:" + noteId, NOTIFICATION_ID, notification);
    }
}
