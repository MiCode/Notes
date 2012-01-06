package hw.micode.widget;

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;


import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

public class DateTimePicker extends FrameLayout {

    private static final boolean DEFAULT_ENABLE_STATE = true;

    private static final int HOURS_IN_HALF_DAY = 12;
    private static final int HOURS_IN_ALL_DAY = 24;
    private static final int DAYS_IN_ALL_WEEK = 7;

    private final int WIDTH_DATE_SPINNER_24_HOURVIEW;
    private final int WIDTH_HOUR_SPINNER_24_HOURVIEW;
    private final int WIDTH_MINUTE_SPINNER_24_HOURVIEW;
    private final int WIDTH_DATE_SPINNER_12_HOURVIEW;
    private final int WIDTH_HOUR_SPINNER_12_HOURVIEW;
    private final int WIDTH_MINUTE_SPINNER_12_HOURVIEW;

    private final NumberPicker mDateSpinner;
    private final NumberPicker mHourSpinner;
    private final NumberPicker mMinuteSpinner;
    private final NumberPicker mAmPmSpinner;
    private Calendar mDate;

    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    private boolean mIsAm;

    private boolean mIs24HourView;

    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    private boolean mInitialising;

    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    private NumberPicker.OnValueChangeListener mOnPickerValueChangedListener = new NumberPicker.OnValueChangeListener() {
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            if (picker == mDateSpinner) {
                mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
                updateDateControl();
                onDateTimeChanged();
            } else if (picker == mHourSpinner) {
                boolean isDateChanged = false;
                Calendar cal = Calendar.getInstance();
                if (!mIs24HourView) {
                    if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                        cal.setTimeInMillis(mDate.getTimeInMillis());
                        cal.add(Calendar.DAY_OF_YEAR, 1);
                        isDateChanged = true;
                    } else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                        cal.setTimeInMillis(mDate.getTimeInMillis());
                        cal.add(Calendar.DAY_OF_YEAR, -1);
                        isDateChanged = true;
                    }
                    if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                            oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                        mIsAm = !mIsAm;
                        updateAmPmControl();
                    }
                } else {
                    if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                        cal.setTimeInMillis(mDate.getTimeInMillis());
                        cal.add(Calendar.DAY_OF_YEAR, 1);
                        isDateChanged = true;
                    } else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                        cal.setTimeInMillis(mDate.getTimeInMillis());
                        cal.add(Calendar.DAY_OF_YEAR, -1);
                        isDateChanged = true;
                    }
                }
                int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
                mDate.set(Calendar.HOUR_OF_DAY, newHour);
                onDateTimeChanged();
                if (isDateChanged) {
                    setCurrentYear(cal.get(Calendar.YEAR));
                    setCurrentMonth(cal.get(Calendar.MONTH));
                    setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
                }
            } else if (picker == mMinuteSpinner) {
                int minValue = mMinuteSpinner.getMinValue();
                int maxValue = mMinuteSpinner.getMaxValue();
                int offset = 0;
                if (oldVal == maxValue && newVal == minValue) {
                    offset += 1;
                } else if (oldVal == minValue && newVal == maxValue) {
                    offset -= 1;
                }
                if (offset != 0) {
                    mDate.add(Calendar.HOUR_OF_DAY, offset);
                    mHourSpinner.setValue(getCurrentHour());
                    updateDateControl();
                    int newHour = getCurrentHourOfDay();
                    if (newHour >= HOURS_IN_HALF_DAY) {
                        mIsAm = false;
                        updateAmPmControl();
                    } else {
                        mIsAm = true;
                        updateAmPmControl();
                    }
                }
                mDate.set(Calendar.MINUTE, newVal);
                onDateTimeChanged();
            } else if (picker == mAmPmSpinner) {
                mIsAm = !mIsAm;
                if (mIsAm) {
                    mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
                } else {
                    mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
                }
                updateAmPmControl();
                onDateTimeChanged();
            }
        }
    };

    public interface OnDateTimeChangedListener {
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                int dayOfMonth, int hourOfDay, int minute);
    }

    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance();
        mInitialising = true;
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;
        inflate(context, R.layout.datetime_picker, this);

        WIDTH_DATE_SPINNER_24_HOURVIEW = getResources().getDimensionPixelSize(R.dimen.width_date_spinner_24_hourview);
        WIDTH_DATE_SPINNER_12_HOURVIEW = getResources().getDimensionPixelSize(R.dimen.width_date_spinner_12_houwview);
        WIDTH_HOUR_SPINNER_24_HOURVIEW = getResources().getDimensionPixelSize(R.dimen.width_hour_spinner_24_hourview);
        WIDTH_HOUR_SPINNER_12_HOURVIEW = getResources().getDimensionPixelSize(R.dimen.width_hour_spinner_12_hourview);
        WIDTH_MINUTE_SPINNER_24_HOURVIEW = getResources().getDimensionPixelSize(R.dimen.width_hour_spinner_24_hourview);
        WIDTH_MINUTE_SPINNER_12_HOURVIEW = getResources().getDimensionPixelSize(R.dimen.width_hour_spinner_12_hourview);

        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(0);
        mDateSpinner.setMaxValue(6);
        mDateSpinner.setOnValueChangedListener(mOnPickerValueChangedListener);

        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnPickerValueChangedListener);
        mMinuteSpinner =  (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(0);
        mMinuteSpinner.setMaxValue(59);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setOnValueChangedListener(mOnPickerValueChangedListener);

        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(0);
        mAmPmSpinner.setMaxValue(1);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnPickerValueChangedListener);

        // update controls to initial state
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        set24HourView(is24HourView);

        // set to current time
        setCurrentDate(date);

        if (!isEnabled()) {
            setEnabled(false);
        }

        // set the content descriptions
        mInitialising = false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    public void setCurrentDate(int year, int month,
            int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    public int getCurrentHour() {
        if (mIs24HourView){
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
        if (!mIs24HourView) {
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    public boolean is24HourView () {
        return mIs24HourView;
    }

    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        if (is24HourView) {
            mDateSpinner.setLayoutParams(
                    new LinearLayout.LayoutParams(WIDTH_DATE_SPINNER_24_HOURVIEW, LinearLayout.LayoutParams.WRAP_CONTENT));
            mHourSpinner.setLayoutParams(
                    new LinearLayout.LayoutParams(WIDTH_HOUR_SPINNER_24_HOURVIEW, LinearLayout.LayoutParams.WRAP_CONTENT));
            mMinuteSpinner.setLayoutParams(
                    new LinearLayout.LayoutParams(WIDTH_MINUTE_SPINNER_24_HOURVIEW, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            mDateSpinner.setLayoutParams(
                    new LinearLayout.LayoutParams(WIDTH_DATE_SPINNER_12_HOURVIEW, LinearLayout.LayoutParams.WRAP_CONTENT));
            mHourSpinner.setLayoutParams(
                    new LinearLayout.LayoutParams(WIDTH_HOUR_SPINNER_12_HOURVIEW, LinearLayout.LayoutParams.WRAP_CONTENT));
            mMinuteSpinner.setLayoutParams(
                    new LinearLayout.LayoutParams(WIDTH_MINUTE_SPINNER_12_HOURVIEW, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        int hour = getCurrentHourOfDay();
        updateHourControl();
        setCurrentHour(hour);
        updateAmPmControl();
    }

    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);
        mDateSpinner.setDisplayedValues(null);
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);
        mDateSpinner.invalidate();
    }

    private void updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(0);
            mHourSpinner.setMaxValue(23);
        } else {
            mHourSpinner.setMinValue(1);
            mHourSpinner.setMaxValue(12);
        }
    }

    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}
