package com.example.studyopengl.ui;

import com.example.studyopengl.engine.DemoEngine;

/**
 * 技术点元数据：编号、标题、分类、一句话简介、详细讲解、引擎工厂。
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
    public final String detail;  // 详细技术讲解（需求 7）
    public final String code;    // 代码示例（教程系列用，可为 null）
    public final Factory factory;

    public DemoInfo(int index, String id, String title, String category,
                    String brief, String detail, Factory factory) {
        this(index, id, title, category, brief, detail, null, factory);
    }

    public DemoInfo(int index, String id, String title, String category,
                    String brief, String detail, String code, Factory factory) {
        this.index = index;
        this.id = id;
        this.title = title;
        this.category = category;
        this.brief = brief;
        this.detail = detail;
        this.code = code;
        this.factory = factory;
    }
}
