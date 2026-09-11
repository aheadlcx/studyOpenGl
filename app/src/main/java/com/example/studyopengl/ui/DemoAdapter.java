package com.example.studyopengl.ui;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.studyopengl.R;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 列表适配器：按 9 个难度阶段分组，组间插入阶段标题行，
 * 让新手一眼看到"从哪里开始、学到哪里了"（需求 8 + 学习路线可视化）。
 * 条目类型：0 = 阶段标题（不可点击），1 = 章节条目。
 */
public class DemoAdapter extends BaseAdapter {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private static final String STAGE1 = "第 1 阶段 · 入门概念：先看懂 GPU 怎么工作（01~06）";
    private static final String STAGE2 = "第 2 阶段 · 基础动手：写下第一批 GL 代码（07~09）";
    private static final String STAGE3 = "第 3 阶段 · 简单渲染状态（10~11）";
    private static final String STAGE4 = "第 4 阶段 · 矩阵与相机（12~13）";
    private static final String STAGE5 = "第 5 阶段 · 进阶状态：深度 / 混合 / 模板（14~16）";
    private static final String STAGE6 = "第 6 阶段 · 纹理（17~20）";
    private static final String STAGE7 = "第 7 阶段 · 光照与材质（21~24）";
    private static final String STAGE8 = "第 8 阶段 · GPU 管线进阶（25~28）";
    private static final String STAGE9 = "第 9 阶段 · 帧缓冲与后处理（29~32）";

    /** 显示条目：String = 阶段标题，DemoInfo = 章节 */
    private final ArrayList<Object> mDisplay = new ArrayList<>();
    private final ArrayList<DemoInfo> mItems;
    private final LayoutInflater mInflater;

    public DemoAdapter(ArrayList<DemoInfo> items, LayoutInflater inflater) {
        mItems = items;
        mInflater = inflater;
        String lastCategory = null;
        for (DemoInfo info : items) {
            if (!info.category.equals(lastCategory)) {
                mDisplay.add(stageHeader(info.category));
                lastCategory = info.category;
            }
            mDisplay.add(info);
        }
    }

    private static String stageHeader(String category) {
        switch (category) {
            case "入门·概念":      return STAGE1;
            case "基础·动手":      return STAGE2;
            case "基础·状态":      return STAGE3;
            case "进阶·矩阵":      return STAGE4;
            case "进阶·状态":      return STAGE5;
            case "纹理":           return STAGE6;
            case "光照与材质":     return STAGE7;
            case "高级·GPU 管线":  return STAGE8;
            case "高级·帧缓冲":    return STAGE9;
            default:               return category;
        }
    }

    @Override
    public int getCount() {
        return mDisplay.size();
    }

    @Override
    public boolean isEnabled(int position) {
        return mDisplay.get(position) instanceof DemoInfo; // 阶段标题不可点击
    }

    @Override
    public DemoInfo getItem(int position) {
        Object o = mDisplay.get(position);
        return o instanceof DemoInfo ? (DemoInfo) o : null;
    }

    /** 供点击处理使用：把列表位置换成章节（跳过标题行）。 */
    public DemoInfo itemAt(int position) {
        return getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return mDisplay.get(position) instanceof DemoInfo ? TYPE_ITEM : TYPE_HEADER;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (getItemViewType(position) == TYPE_HEADER) {
            TextView header = (TextView) convertView;
            if (header == null) {
                header = new TextView(parent.getContext());
                header.setPadding(dp(16), dp(14), dp(16), dp(6));
                header.setTextColor(0xFF4FC3F7);
                header.setTextSize(13);
                header.setTypeface(null, Typeface.BOLD);
            }
            header.setText((String) mDisplay.get(position));
            return header;
        }

        View view = convertView;
        if (view == null) {
            view = mInflater.inflate(R.layout.item_demo, parent, false);
        }
        DemoInfo info = getItem(position);
        TextView index = (TextView) view.findViewById(R.id.tv_index);
        TextView title = (TextView) view.findViewById(R.id.tv_title);
        TextView brief = (TextView) view.findViewById(R.id.tv_brief);
        index.setText(String.format(Locale.US, "%02d", info.index));
        title.setText(info.title);
        brief.setText(String.format(Locale.US, "%s", info.brief));
        return view;
    }

    private int dp(int v) {
        return Math.round(v * mInflater.getContext().getResources().getDisplayMetrics().density);
    }
}
