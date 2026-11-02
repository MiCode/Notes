/* Copyright 2026 The Notes contributors. Licensed under the Apache License, Version 2.0. */
package net.micode.notes.tool;

import java.util.Calendar;
import java.util.TimeZone;

/** Pure calendar calculations shared by quick reminders and their boundary tests. */
public final class QuickReminder {
    public enum Preset { IN_TWO_HOURS, TOMORROW_MORNING, NEXT_WEEK }

    private QuickReminder() { }

    /** Returns a minute-aligned future time in the supplied device time zone. */
    public static long calculate(Preset preset, long now, TimeZone zone) {
        if (preset == null || zone == null) {
            throw new IllegalArgumentException("Preset and time zone are required");
        }
        Calendar date = Calendar.getInstance(zone);
        date.setTimeInMillis(now);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        switch (preset) {
            case IN_TWO_HOURS:
                date.add(Calendar.HOUR_OF_DAY, 2);
                break;
            case TOMORROW_MORNING:
                date.add(Calendar.DAY_OF_YEAR, 1);
                date.set(Calendar.HOUR_OF_DAY, 9);
                date.set(Calendar.MINUTE, 0);
                break;
            case NEXT_WEEK:
                // Calendar days preserve local clock time across DST and year boundaries.
                date.add(Calendar.DAY_OF_YEAR, 7);
                break;
            default:
                throw new IllegalArgumentException("Unknown reminder preset");
        }
        return date.getTimeInMillis();
    }

    public static boolean isFuture(long date, long now) {
        return date > now;
    }
}
