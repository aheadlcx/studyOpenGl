package com.example.studyopengl.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.example.studyopengl.R;

import java.util.ArrayList;

/**
 * 需求 8：列表界面。ListView 上下滑动浏览全部技术点，点击进入对应演示界面。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ArrayList<DemoInfo> demos = DemoCatalog.all();
        ListView listView = (ListView) findViewById(R.id.demo_list);
        final DemoAdapter adapter = new DemoAdapter(demos, getLayoutInflater());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DemoInfo info = adapter.itemAt(position); // 列表含阶段标题行，需换算
                if (info == null) return;
                Intent intent;
                if (info.subs != null && !info.subs.isEmpty()) {
                    // 二级界面：章节目录页（列出全部微知识点小节）
                    intent = new Intent(MainActivity.this, ChapterActivity.class);
                    intent.putExtra(ChapterActivity.EXTRA_CHAPTER_ID, info.id);
                } else {
                    // 单屏章节：直接进入演示
                    intent = new Intent(MainActivity.this, DemoActivity.class);
                    intent.putExtra(DemoActivity.EXTRA_DEMO_ID, info.id);
                }
                startActivity(intent);
            }
        });
    }
}
