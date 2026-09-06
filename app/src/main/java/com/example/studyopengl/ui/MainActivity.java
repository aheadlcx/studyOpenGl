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
        listView.setAdapter(new DemoAdapter(demos, getLayoutInflater()));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(MainActivity.this, DemoActivity.class);
                intent.putExtra(DemoActivity.EXTRA_DEMO_ID, demos.get(position).id);
                startActivity(intent);
            }
        });
    }
}
