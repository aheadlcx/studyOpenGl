package com.example.studyopengl.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.example.studyopengl.R;
import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.engine.DemoEngine;
import com.example.studyopengl.gl.GLThread;
import com.example.studyopengl.gl.RenderSurface;
import com.example.studyopengl.param.ParamSpec;

import java.util.List;
import java.util.Locale;

/**
 * 需求 5/6/7：单个技术点的演示界面。
 *  - 全屏 RenderSurface：自建 GL 线程在其上渲染；
 *  - 底部面板：技术讲解 TextView + 按 ParamSpec 动态生成的参数控件；
 *  - 参数变更经 engine.setParamFromUi -> GLThread.postGLTask 调度到 GL 线程生效。
 */
public class DemoActivity extends Activity {

    public static final String EXTRA_DEMO_ID = "demo_id";
    public static final String EXTRA_SUB_ID = "sub_id";

    private GLThread mGLThread;
    private DemoEngine mEngine;
    private DemoInfo mInfo;

    private TextView mFpsView;
    private TextView mGlInfoView;
    private View mPanelScroll;
    private TextView mTabDetail;
    private TextView mTabCode;
    private TextView mTabParams;
    private TextView mDetailView;
    private TextView mCodeView;
    private View mParamsHeader;
    private View mParamContainer;
    private int mCurTab = 0;
    private SubDemo mSub;

    private final Handler mUiHandler = new Handler();
    private final Runnable mFpsUpdater = new Runnable() {
        @Override
        public void run() {
            if (mGLThread != null) {
                mFpsView.setText(String.format(Locale.US, "%.1f FPS", mGLThread.getFps()));
            }
            mUiHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        String demoId = getIntent().getStringExtra(EXTRA_DEMO_ID);
        mInfo = DemoCatalog.byId(demoId);
        if (mInfo == null) {
            finish();
            return;
        }

        // 1. 创建引擎（应用小节锁参数：值固定，UI 隐藏对应控件）
        mEngine = mInfo.factory.create();
        BaseDemoEngine engine = null;
        if (mEngine instanceof BaseDemoEngine) {
            engine = (BaseDemoEngine) mEngine;
            engine.initParams();
            mSub = findSub(mInfo, getIntent().getStringExtra(EXTRA_SUB_ID));
            if (mSub != null) {
                for (int i = 0; i < mSub.lockKeys.length; i++) {
                    engine.lockParam(mSub.lockKeys[i], mSub.lockValues[i]);
                }
            }
            engine.attachCodeSectionListener(new BaseDemoEngine.OnCodeSectionListener() {
                @Override
                public void onCodeSection(final int section) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showTab(1);
                            scrollToCodeSection(section);
                        }
                    });
                }
            });
        }

        // 2. 创建自建 GL 线程，绑定到 SurfaceView
        RenderSurface surface = (RenderSurface) findViewById(R.id.render_surface);
        mGLThread = new GLThread();
        mGLThread.setEngine(mEngine);
        if (engine != null) {
            engine.attachPoster(mGLThread);   // 关键：参数变更回调由此投递到 GL 线程
        }
        mGLThread.setGlInfoListener(new GLThread.GlInfoListener() {
            @Override
            public void onGlInfo(final String info) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mGlInfoView.setText(info);
                    }
                });
            }
        });
        surface.setGLThread(mGLThread);

        // 3. 填充标题、讲解与参数面板
        TextView titleView = (TextView) findViewById(R.id.tv_demo_title);
        String title = String.format(Locale.US, "%02d · %s", mInfo.index, mInfo.title);
        if (mSub != null) title += " · " + mSub.title;
        titleView.setText(title);
        TextView detailView = (TextView) findViewById(R.id.tv_detail);
        detailView.setText(mSub != null && mSub.detail != null
                ? mSub.detail : mInfo.detail);
        mFpsView = (TextView) findViewById(R.id.tv_fps);
        mGlInfoView = (TextView) findViewById(R.id.tv_glinfo);

        // 新手提示：一行话讲清"这个界面怎么玩"
        TextView hintView = (TextView) findViewById(R.id.tv_hint);
        hintView.setText(mInfo.code != null
                ? "💡 与画面实时交互（点按 / 拖动 / 按键）；点画面元素会跳到【代码】页签对应段落"
                : "💡 拖动下方参数滑条，画面实时变化；完整图文原理见仓库 docs/ 目录对应章节");

        // 4. 页签 + 代码示例
        mDetailView = detailView;
        mCodeView = (TextView) findViewById(R.id.tv_code);
        mParamsHeader = findViewById(R.id.params_header);
        mParamContainer = findViewById(R.id.param_container);
        mTabDetail = (TextView) findViewById(R.id.tab_detail);
        mTabCode = (TextView) findViewById(R.id.tab_code);
        mTabParams = (TextView) findViewById(R.id.tab_params);
        if (mInfo.code != null) {
            mCodeView.setText(CodeHighlighter.highlight(mInfo.code));
            mTabCode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(1);
                }
            });
        } else {
            mTabCode.setVisibility(View.GONE); // 无代码示例的 Demo 隐藏代码页签
        }
        mTabDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(0);
            }
        });
        mTabParams.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(2);
            }
        });

        LinearLayout paramContainer = (LinearLayout) findViewById(R.id.param_container);
        List<ParamSpec> specs = mEngine.getParamSpecs();
        BaseDemoEngine bEngine = mEngine instanceof BaseDemoEngine
                ? (BaseDemoEngine) mEngine : null;
        if (specs != null) {
            for (ParamSpec spec : specs) {
                // 小节锁定的参数不生成控件（值固定，教学焦点唯一）
                if (bEngine != null && bEngine.isLocked(spec.key)) continue;
                paramContainer.addView(createParamRow(spec));
            }
        }

        View back = findViewById(R.id.btn_back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mPanelScroll = findViewById(R.id.panel_scroll);
        View toggle = findViewById(R.id.btn_toggle);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPanelScroll.setVisibility(
                        mPanelScroll.getVisibility() == View.VISIBLE
                                ? View.GONE : View.VISIBLE);
            }
        });
    }

    /** 按 ParamSpec 类型动态生成参数控件行（需求 6 核心）。 */
    private View createParamRow(final ParamSpec spec) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView label = new TextView(this);
        label.setText(spec.label);
        label.setTextColor(getResources().getColor(R.color.text_primary));
        label.setTextSize(13);
        label.setWidth(dp(96));
        row.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams controlLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        switch (spec.type) {
            case ParamSpec.TYPE_FLOAT:
                row.addView(createFloatControl(spec), controlLp);
                break;
            case ParamSpec.TYPE_INT:
                row.addView(createIntControl(spec), controlLp);
                break;
            case ParamSpec.TYPE_BOOL:
                row.addView(createBoolControl(spec), controlLp);
                break;
            case ParamSpec.TYPE_OPTION:
                row.addView(createOptionControl(spec), controlLp);
                break;
        }
        return row;
    }

    /** 浮点滑条：progress 0..1000 映射到 min..max。 */
    private View createFloatControl(final ParamSpec spec) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView value = new TextView(this);
        value.setTextColor(getResources().getColor(R.color.accent));
        value.setTextSize(11);
        value.setGravity(Gravity.RIGHT);
        final float def = ((Number) spec.defaultValue).floatValue();
        value.setText(String.format(Locale.US, spec.format, def));

        SeekBar seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgress((int) ((def - spec.min) / (spec.max - spec.min) * 1000f));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = spec.min + progress / 1000f * (spec.max - spec.min);
                value.setText(String.format(Locale.US, spec.format, v));
                if (fromUser) {
                    mEngine.setParamFromUi(spec, Float.valueOf(v));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        box.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** 整数滑条。 */
    private View createIntControl(final ParamSpec spec) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView value = new TextView(this);
        value.setTextColor(getResources().getColor(R.color.accent));
        value.setTextSize(11);
        value.setGravity(Gravity.RIGHT);
        final int def = ((Number) spec.defaultValue).intValue();
        value.setText(String.format(Locale.US, "%d", def));

        SeekBar seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgress((int) ((def - spec.min) / (spec.max - spec.min) * 1000f));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = Math.round(spec.min + progress / 1000f * (spec.max - spec.min));
                value.setText(String.format(Locale.US, "%d", v));
                if (fromUser) {
                    mEngine.setParamFromUi(spec, Integer.valueOf(v));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        box.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** 布尔开关。 */
    private View createBoolControl(final ParamSpec spec) {
        Switch sw = new Switch(this);
        sw.setChecked((Boolean) spec.defaultValue);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mEngine.setParamFromUi(spec, Boolean.valueOf(isChecked));
            }
        });
        return sw;
    }

    /** 枚举下拉框，选中项下标即参数值。 */
    private View createOptionControl(final ParamSpec spec) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, spec.optionLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(((Number) spec.defaultValue).intValue());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                mEngine.setParamFromUi(spec, Integer.valueOf(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return spinner;
    }

    /** 页签切换：0=讲解 1=代码 2=参数。 */
    private void showTab(int which) {
        mCurTab = which;
        setTabActive(mTabDetail, which == 0);
        setTabActive(mTabCode, which == 1);
        setTabActive(mTabParams, which == 2);
        mDetailView.setVisibility(which == 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.detail_header).setVisibility(
                which == 0 ? View.VISIBLE : View.GONE);
        mCodeView.setVisibility(
                which == 1 && mInfo.code != null ? View.VISIBLE : View.GONE);
        mParamsHeader.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
        mParamContainer.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
        findViewById(R.id.glinfo_divider).setVisibility(
                which == 0 ? View.VISIBLE : View.GONE);
    }

    private void setTabActive(TextView tab, boolean active) {
        tab.setTextColor(getResources().getColor(
                active ? R.color.accent : R.color.text_secondary));
        tab.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    /** 滚动代码区到第 section 段（段标记约定：行内含 "// 【N】"）。 */
    private void scrollToCodeSection(final int section) {
        if (mInfo.code == null) return;
        // 页签刚切换时布局可能未完成，延迟到 layout 后再滚
        mCodeView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mCodeView.getLayout() == null) return;
                int off = mInfo.code.indexOf("// 【" + section + "】");
                if (off < 0) return;
                int line = mCodeView.getLayout().getLineForOffset(off);
                int y = mCodeView.getLayout().getLineTop(line) + mCodeView.getTop();
                mPanelScroll.scrollTo(0, Math.max(0, y - 6));
            }
        }, 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 在章节的小节列表里找指定 id 的小节。 */
    private static SubDemo findSub(DemoInfo info, String subId) {
        if (info == null || info.subs == null || subId == null) return null;
        for (SubDemo s : info.subs) {
            if (s.id.equals(subId)) return s;
        }
        return null;
    }

    // ==================== 生命周期：驱动 GLThread ====================

    @Override
    protected void onResume() {
        super.onResume();
        mGLThread.resumeRendering();
        mUiHandler.post(mFpsUpdater);
    }

    @Override
    protected void onPause() {
        mUiHandler.removeCallbacks(mFpsUpdater);
        mGLThread.pauseRendering();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mGLThread.exitAndWait(); // 线程内完成引擎 GL 资源释放
        mGLThread = null;
        super.onDestroy();
    }
}
