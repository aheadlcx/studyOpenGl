package com.example.studyopengl.ui;

import com.example.studyopengl.engine.DemoEngine;

import java.util.ArrayList;

/**
 * 技术点元数据：编号、标题、分类、一句话简介、详细讲解、代码示例、引擎工厂，
 * 以及可选的小节列表（一个章节拆成多个微知识点界面）。
 */
public final class DemoInfo {

    /** 引擎工厂：点击列表项时才实例化对应引擎。 */
    public interface Factory {
        DemoEngine create();
    }

    public final int index;      // 展示序号
    public final String id;      // 路由 id
    public final String title;   // 技术点名
    public final String category;
    public final String brief;   // 列表副标题
    public final String detail;  // 详细技术讲解
    public final String code;    // 代码示例（可为 null）
    public final ArrayList<SubDemo> subs;   // 小节列表（null = 单屏章节）
    public final Factory factory;

    /** 单屏章节：无代码、无小节。 */
    public DemoInfo(int index, String id, String title, String category,
                    String brief, String detail, Factory factory) {
        this(index, id, title, category, brief, detail, null, null, factory);
    }

    /** 有代码示例的单屏章节。 */
    public DemoInfo(int index, String id, String title, String category,
                    String brief, String detail, String code, Factory factory) {
        this(index, id, title, category, brief, detail, code, null, factory);
    }

    /** 有小节的章节（detail 显示在二级目录页）。 */
    public DemoInfo(int index, String id, String title, String category,
                    String brief, String detail, String code,
                    ArrayList<SubDemo> subs, Factory factory) {
        this.index = index;
        this.id = id;
        this.title = title;
        this.category = category;
        this.brief = brief;
        this.detail = detail;
        this.code = code;
        this.subs = subs;
        this.factory = factory;
    }
}
