package net.micode.notes.tool;

import java.util.Calendar;
import java.util.TimeZone;

/** Dependency-free JVM unit tests; compile and execute the production calculation class. */
public final class QuickReminderTest {
    private static int passed;
    private static final TimeZone CHINA = TimeZone.getTimeZone("Asia/Shanghai");
    private static long at(TimeZone zone, int y, int m, int d, int h, int minute) {
        Calendar c = Calendar.getInstance(zone);
        c.clear();
        c.set(y, m - 1, d, h, minute);
        return c.getTimeInMillis();
    }
    private static void check(String name, long actual, long expected) {
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
        System.out.println("PASS " + name);
        passed++;
    }
    private static void preset(String name, QuickReminder.Preset p, int[] from, int[] to) {
        check(name, QuickReminder.calculate(p, at(CHINA,from[0],from[1],from[2],from[3],from[4]), CHINA),
                at(CHINA,to[0],to[1],to[2],to[3],to[4]));
    }
    public static void main(String[] args) {
        QuickReminder.Preset later=QuickReminder.Preset.IN_TWO_HOURS;
        QuickReminder.Preset tomorrow=QuickReminder.Preset.TOMORROW_MORNING;
        QuickReminder.Preset week=QuickReminder.Preset.NEXT_WEEK;
        preset("later-normal",later,new int[]{2026,10,27,14,20},new int[]{2026,10,27,16,20});
        preset("later-cross-midnight",later,new int[]{2026,10,27,23,50},new int[]{2026,10,28,1,50});
        preset("later-cross-year",later,new int[]{2026,12,31,23,50},new int[]{2027,1,1,1,50});
        preset("tomorrow-before-nine",tomorrow,new int[]{2026,10,27,8,0},new int[]{2026,10,28,9,0});
        preset("tomorrow-at-nine",tomorrow,new int[]{2026,10,27,9,0},new int[]{2026,10,28,9,0});
        preset("tomorrow-after-nine",tomorrow,new int[]{2026,10,27,23,50},new int[]{2026,10,28,9,0});
        preset("january-end",tomorrow,new int[]{2026,1,31,12,0},new int[]{2026,2,1,9,0});
        preset("april-end",tomorrow,new int[]{2026,4,30,12,0},new int[]{2026,5,1,9,0});
        preset("non-leap-february",tomorrow,new int[]{2026,2,28,12,0},new int[]{2026,3,1,9,0});
        preset("leap-day",tomorrow,new int[]{2028,2,28,12,0},new int[]{2028,2,29,9,0});
        preset("leap-february-end",tomorrow,new int[]{2028,2,29,12,0},new int[]{2028,3,1,9,0});
        preset("tomorrow-cross-year",tomorrow,new int[]{2026,12,31,23,59},new int[]{2027,1,1,9,0});
        preset("week-keeps-local-time",week,new int[]{2026,10,27,15,42},new int[]{2026,11,3,15,42});
        preset("week-cross-year",week,new int[]{2026,12,28,15,42},new int[]{2027,1,4,15,42});
        long now=at(CHINA,2026,10,27,14,22)+33987;
        check("clears-seconds-and-millis",QuickReminder.calculate(later,now,CHINA),at(CHINA,2026,10,27,16,22));
        TimeZone ny=TimeZone.getTimeZone("America/New_York");
        long spring=at(ny,2026,3,7,9,0);
        long springNext=QuickReminder.calculate(tomorrow,spring,ny);
        check("DST-spring-clock",springNext,at(ny,2026,3,8,9,0));
        check("DST-spring-23-hours",springNext-spring,23L*60*60*1000);
        long autumn=at(ny,2026,10,31,9,0);
        check("DST-autumn-25-hours",QuickReminder.calculate(tomorrow,autumn,ny)-autumn,25L*60*60*1000);
        check("DST-two-elapsed-hours",QuickReminder.calculate(later,at(ny,2026,3,8,1,30),ny),at(ny,2026,3,8,4,30));
        check("DST-week-clock",QuickReminder.calculate(week,at(ny,2026,3,1,9,0),ny),at(ny,2026,3,8,9,0));
        check("past-rejected",QuickReminder.isFuture(now-1,now)?1:0,0);
        check("now-rejected",QuickReminder.isFuture(now,now)?1:0,0);
        check("future-accepted",QuickReminder.isFuture(now+1,now)?1:0,1);
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            check("explicit-zone-independent-of-default",QuickReminder.calculate(tomorrow,now,CHINA),at(CHINA,2026,10,28,9,0));
        } finally {TimeZone.setDefault(original);}
        for (int i=0;i<2;i++) {
            try {
                QuickReminder.calculate(i==0?null:later,now,i==0?CHINA:null);
                throw new AssertionError("Null argument accepted");
            } catch (IllegalArgumentException expected) {
                System.out.println("PASS null-argument-"+i);passed++;
            }
        }
        System.out.println("RESULT: "+passed+" passed, 0 failed");
    }
}
