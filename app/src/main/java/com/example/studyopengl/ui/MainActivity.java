package com.example.studyopengl.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.studyopengl.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 需求 8：列表界面。ListView 上下滑动浏览全部技术点，点击进入对应演示界面。
 * 顶部还有术语速查入口；进入过的章节会打上"已学"标记（存 SharedPreferences）。
 */
public class MainActivity extends Activity {

    static final String PREF_PROGRESS = "learning_progress";
    static final String KEY_VISITED = "visited";

    /** 供其它界面标记"这一章学过了"。 */
    static void markVisited(MainActivity activity, String chapterId) {
        SharedPreferences sp = activity.getSharedPreferences(PREF_PROGRESS, MODE_PRIVATE);
        Set<String> visited = new HashSet<String>(sp.getStringSet(KEY_VISITED, new HashSet<String>()));
        visited.add(chapterId);
        sp.edit().putStringSet(KEY_VISITED, visited).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView glossary = (TextView) findViewById(R.id.btn_glossary);
        glossary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GlossaryActivity.class));
            }
        });

        final ArrayList<DemoInfo> demos = DemoCatalog.all();
        ListView listView = (ListView) findViewById(R.id.demo_list);
        final DemoAdapter adapter = new DemoAdapter(demos, getLayoutInflater(), visitedSet());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DemoInfo info = adapter.itemAt(position); // 列表含阶段标题行，需换算
                if (info == null) return;
                markVisited(MainActivity.this, info.id);
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

    private Set<String> visitedSet() {
        return getSharedPreferences(PREF_PROGRESS, MODE_PRIVATE)
                .getStringSet(KEY_VISITED, new HashSet<String>());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从章节回来后刷新"已学"标记
        ListView listView = (ListView) findViewById(R.id.demo_list);
        DemoAdapter adapter = (DemoAdapter) listView.getAdapter();
        adapter.resetVisited(visitedSet());
        adapter.notifyDataSetChanged();
    }
}
