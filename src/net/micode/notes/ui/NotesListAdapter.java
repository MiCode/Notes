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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


// 主列表适配器：继承 CursorAdapter，将游标每一行渲染为 NotesListItem，并维护多选状态
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";
    private Context mContext;
    private HashMap<Integer, Boolean> mSelectedIndex; // position -> 是否被勾选
    private int mNotesCount;                           // 当前游标中“笔记”类型条目总数（用于全选判断）
    private boolean mChoiceMode;                       // 是否处于多选模式

    // 用于向 Activity 回传被选笔记所绑定的桌面小部件信息
    public static class AppWidgetAttribute {
        public int widgetId;
        public int widgetType;
    };

    public NotesListAdapter(Context context) {
        super(context, null); // 游标初始为 null，后续 changeCursor 提供
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    // 创建一行视图：每行都是自定义的 NotesListItem
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    // 绑定数据：用当前游标行构造 NoteItemData，交由 NotesListItem 渲染，并带上选择模式/勾选状态
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    // 记录/更新某个 position 的勾选状态并刷新列表
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    // 是否处于多选模式
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    // 进入/退出多选模式：进入前清空历史勾选
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    // 全选/取消全选：仅对“笔记”类型条目生效（文件夹不参与勾选）
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    // 收集所有被勾选条目对应的笔记 id（过滤掉异常的根文件夹 id）
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }

        return itemSet;
    }

    // 收集所有被勾选笔记所绑定的桌面小部件（id+类型），供删除后刷新
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                    /**
                     * Don't close cursor here, only the adapter could close it
                     * 游标由适配器统一管理，此处不能关闭
                     */
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    // 统计被勾选（值为 true）的条目数量
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    // 是否“全选”：有勾选且勾选数等于笔记总数
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    // 查询某 position 是否被勾选（无记录视为未选）
    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    @Override
    protected void onContentChanged() {
        // 数据集内容变化后重新计算笔记总数
        super.onContentChanged();
        calcNotesCount();
    }

    @Override
    public void changeCursor(Cursor cursor) {
        // 更换游标后重新计算笔记总数
        super.changeCursor(cursor);
        calcNotesCount();
    }

    // 遍历当前数据，统计 TYPE_NOTE 条目数量，缓存到 mNotesCount
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}
