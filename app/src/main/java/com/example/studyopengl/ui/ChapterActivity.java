package com.example.studyopengl.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.studyopengl.R;

import java.util.Locale;

/**
 * 二级界面：章节目录页。
 * 一个章节拆成多个"小节"时，先到这里列出全部微知识点，
 * 点击某个小节 → 进入三级界面（该小节的演示）。
 */
public class ChapterActivity extends Activity {

    static final String EXTRA_CHAPTER_ID = "chapter_id";

    private DemoInfo mInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapter);

        String id = getIntent().getStringExtra(EXTRA_CHAPTER_ID);
        mInfo = DemoCatalog.byId(id);
        if (mInfo == null || mInfo.subs == null || mInfo.subs.isEmpty()) {
            finish();
            return;
        }

        TextView title = (TextView) findViewById(R.id.tv_chapter_title);
        title.setText(String.format(Locale.US, "%02d · %s", mInfo.index, mInfo.title));
        TextView detail = (TextView) findViewById(R.id.tv_chapter_detail);
        detail.setText(mInfo.detail);
        TextView count = (TextView) findViewById(R.id.tv_sub_count);
        count.setText(String.format(Locale.US, "本章共 %d 个小节，每节只聚焦一个微知识点",
                mInfo.subs.size()));

        ListView list = (ListView) findViewById(R.id.sub_list);
        list.setAdapter(new SubAdapter(mInfo, getLayoutInflater()));
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                    int position, long id) {
                SubDemo sub = mInfo.subs.get(position);
                Intent intent = new Intent(ChapterActivity.this, DemoActivity.class);
                intent.putExtra(DemoActivity.EXTRA_DEMO_ID, mInfo.id);
                intent.putExtra(DemoActivity.EXTRA_SUB_ID, sub.id);
                startActivity(intent);
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private static class SubAdapter extends BaseAdapter {
        private final DemoInfo mInfo;
        private final LayoutInflater mInflater;

        SubAdapter(DemoInfo info, LayoutInflater inflater) {
            mInfo = info;
            mInflater = inflater;
        }

        @Override
        public int getCount() {
            return mInfo.subs.size();
        }

        @Override
        public SubDemo getItem(int position) {
            return mInfo.subs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = mInflater.inflate(R.layout.item_sub, parent, false);
            }
            SubDemo sub = mInfo.subs.get(position);
            ((TextView) v.findViewById(R.id.tv_sub_num))
                    .setText(String.format(Locale.US, "%d", position + 1));
            ((TextView) v.findViewById(R.id.tv_sub_title)).setText(sub.title);
            ((TextView) v.findViewById(R.id.tv_sub_brief)).setText(sub.brief);
            return v;
        }
    }
}
