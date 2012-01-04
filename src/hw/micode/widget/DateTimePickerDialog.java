package hw.micode.widget;

import hw.micode.widget.DateTimePicker.OnDateTimeChangedListener;

import java.util.Calendar;
import java.util.Date;

import net.micode.notes.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

public class DateTimePickerDialog extends AlertDialog implements OnClickListener {
	
	LinearLayout mLayout;
	Calendar mDate = Calendar.getInstance();
	private boolean mIs24HourView;
	OnDateTimeSetListener mOnDateTimeSetListener;
	DateTimePicker mDateTimePicker;
	
	public interface OnDateTimeSetListener {
		void OnDateTimeSet(AlertDialog dialog, int year, int month, int dayOfMonth, int hourOfDay, int minute);
	}
	
	public DateTimePickerDialog(Context context, long date) {
		this(context, new Date(date));
	}
	
	public DateTimePickerDialog(Context context, int year, int month, 
			int dayOfMonth, int hourOfDay, int minute) {
		this(context, new Date(year - 1900, month, dayOfMonth, hourOfDay, minute));
	}
	
	public DateTimePickerDialog(Context context, Date date) {
		super(context);
		LayoutInflater inflater = LayoutInflater.from(context);
		View view = inflater.inflate(R.layout.datetime_picker_dialog, null);
		mLayout = (LinearLayout) view.findViewById(R.id.datetime_picker_dialog_Layout);
		setView(view);
		mDateTimePicker = new DateTimePicker(context);
		mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
			public void onDateTimeChanged(DateTimePicker view, int year, int month,
					int dayOfMonth, int hourOfDay, int minute) {
				mDate.set(Calendar.YEAR, year);
				mDate.set(Calendar.MONTH, month);
				mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
				mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
				mDate.set(Calendar.MINUTE, minute);
				updateTitle(mDate.getTimeInMillis());
			}
		});
		mLayout.addView(mDateTimePicker);
		mDate.setTime(date);
		mDate.set(Calendar.SECOND, 0);
		mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
		setButton(context.getString(R.string.datetime_dialog_ok), this);
		setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
		setIs24HourView(DateFormat.is24HourFormat(this.getContext()));
		updateTitle(mDate.getTimeInMillis());
	}
	
	public void setIs24HourView(boolean is24HourView) {
		mIs24HourView = is24HourView;
	}
	
	public void setOnDateTimeSetListener(OnDateTimeSetListener callBack)
	{
		mOnDateTimeSetListener = callBack;
	}
	
	private void updateTitle(long date)
	{
		int flag = 
			DateUtils.FORMAT_SHOW_YEAR |
			DateUtils.FORMAT_SHOW_DATE |
			DateUtils.FORMAT_SHOW_TIME;
		if (mIs24HourView) {
			flag |= DateUtils.FORMAT_24HOUR;
		} else {
			flag |= DateUtils.FORMAT_12HOUR;
		}
		String s = DateUtils.formatDateTime(this.getContext(), date, flag);
		setTitle(s);
	}

	public void onClick(DialogInterface arg0, int arg1) {
		// TODO Auto-generated method stub
		if (mOnDateTimeSetListener != null) {
			mOnDateTimeSetListener.OnDateTimeSet(this,
					mDate.get(Calendar.YEAR),
					mDate.get(Calendar.MONTH), 
					mDate.get(Calendar.DAY_OF_MONTH),
					mDate.get(Calendar.HOUR_OF_DAY), 
					mDate.get(Calendar.MINUTE));
		}
	}

}