package com.example.studyopengl.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.studyopengl.R;

import java.util.ArrayList;
import java.util.Locale;

/** 列表适配器（需求 8：上下滑动浏览技术点）。 */
public class DemoAdapter extends BaseAdapter {

    private final ArrayList<DemoInfo> mItems;
    private final LayoutInflater mInflater;

    public DemoAdapter(ArrayList<DemoInfo> items, LayoutInflater inflater) {
        mItems = items;
        mInflater = inflater;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public DemoInfo getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
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
        brief.setText(String.format(Locale.US, "【%s】%s", info.category, info.brief));
        return view;
    }
}
