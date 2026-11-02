/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import net.micode.notes.tool.QuickReminder;

/**
 * B 阶段一阅读标注 胡嘉豪 202430224010
 * 包装日期时间控件，通过监听器向编辑页交付毫秒时间戳。
 * 本类只负责选择与标题展示，保存便签和注册闹钟在模型及编辑页中完成。
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    private Calendar mDate = Calendar.getInstance();
    private boolean mIs24HourView;
    private OnDateTimeSetListener mOnDateTimeSetListener;
    private DateTimePicker mDateTimePicker;

    public interface OnDateTimeSetListener {
        // B 阅读标注：date 是 Unix 毫秒时间戳，月份转换由 Calendar 和控件处理。
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    public DateTimePickerDialog(Context context, long date) {
        super(context);
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                // B 阅读标注：每次滚轮变化同步 Calendar，并刷新对话框标题。
                // Assign the complete date together: setting February while the
                // previous day is January 31 would otherwise normalize into March.
                mDate.clear();
                mDate.set(year, month, dayOfMonth, hourOfDay, minute, 0);
                updateTitle(mDate.getTimeInMillis());
            }
        });
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);
        mDate.set(Calendar.MILLISECOND, 0);
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        updateTitle(mDate.getTimeInMillis());
    }

    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
        mDateTimePicker.set24HourView(is24HourView);
        updateTitle(mDate.getTimeInMillis());
    }

    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    private void updateTitle(long date) {
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |
            DateUtils.FORMAT_SHOW_DATE |
            DateUtils.FORMAT_SHOW_TIME;
        // Q-B-01: keep the title consistent with the picker and the system clock format.
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_12HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    public void onClick(DialogInterface arg0, int arg1) {
        mDateTimePicker.clearFocus();
        if (!QuickReminder.isFuture(mDate.getTimeInMillis(), System.currentTimeMillis())) {
            Toast.makeText(getContext(), R.string.reminder_time_in_past, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
        dismiss();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // AlertDialog's default listener dismisses even when validation fails.
        getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                DateTimePickerDialog.this.onClick(DateTimePickerDialog.this, BUTTON_POSITIVE);
            }
        });
    }

}
