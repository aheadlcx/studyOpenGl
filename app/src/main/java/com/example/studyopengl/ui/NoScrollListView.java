package com.example.studyopengl.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * 在 ScrollView 里使用的 ListView：测量时把高度展开为"全部内容的高度"，
 * 自身不再滚动，整页交给外层 ScrollView 统一滚动。
 *
 * 为什么需要它：ScrollView 只会给子控件 AT_MOST 一屏的高度，ListView 拿到后
 * 会按"可滚动控件"处理，把自身高度压成一行——于是小节列表只能看到第一条，
 * 其余条目藏在 170px 高的小窗口里（滚起来非常反直觉）。
 */
public class NoScrollListView extends ListView {

    public NoScrollListView(Context context) {
        super(context);
    }

    public NoScrollListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoScrollListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int expand = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expand);
        getLayoutParams().height = getMeasuredHeight();
    }
}
