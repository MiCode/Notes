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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;


// 列表单行视图：自定义 LinearLayout，加载 note_item 布局，负责一行的展示与背景渲染
public class NotesListItem extends LinearLayout {
    private ImageView mAlert;         // 提醒/通话记录图标
    private TextView mTitle;          // 标题（笔记摘要或文件夹名+计数）
    private TextView mTime;           // 相对修改时间
    private TextView mCallName;       // 通话记录笔记的联系人姓名
    private NoteItemData mItemData;   // 当前行数据（供 Activity 取回）
    private CheckBox mCheckBox;       // 多选模式的勾选框

    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this); // 加载单行布局
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    // 核心绑定方法：依据是否多选、条目类型、父级归属来配置各控件
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // 仅多选模式且为笔记条目才显示勾选框
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 情况一：这就是“通话记录夹”本身
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record); // 使用通话记录图标
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 情况二：通话记录夹内的某条通话笔记，显示来电姓名作为主标题
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context,R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock); // 有时钟提醒
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        } else {
            // 情况三：普通文件夹或普通笔记
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            if (data.getType() == Notes.TYPE_FOLDER) {
                // 文件夹：标题为“名称 + 文件数”，无提醒图标
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                                data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                // 笔记：标题为摘要，必要时显示时钟提醒图标
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }
        // 统一显示相对时间（如“5 分钟前”）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        setBackground(data); // 依据类型/颜色/列表位置选择背景图
    }

    // 背景选择：笔记依据在列表中的位置（单条/首/尾/中）选不同九宫格；文件夹用统一背景
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId(); // 笔记颜色（红/黄/蓝/绿/白）
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id)); // 独立一条
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));   // 末尾
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));  // 首条
            } else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id)); // 中间
            }
        } else {
            setBackgroundResource(NoteItemBgResources.getFolderBgRes()); // 文件夹背景
        }
    }

    // 供 Activity 在点击/长按时取回该行数据
    public NoteItemData getItemData() {
        return mItemData;
    }
}
