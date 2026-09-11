package com.example.studyopengl.ui;

import java.util.ArrayList;

/**
 * 章节内的"小节"：一个界面只讲一个微知识点。
 * 通过锁定引擎参数（lockKeys/lockValues）复用现有引擎——
 * 例如片元着色器一章拆成 6 个小节，每小节锁定一种 FS 模式。
 */
public final class SubDemo {

    public final String id;        // 小节 id（chapterId 内唯一）
    public final String title;     // 小节名（一句话知识点）
    public final String brief;     // 二级列表里的一行说明
    public final String detail;    // 本小节的讲解（null = 用章节讲解）
    public final String[] lockKeys;
    public final Object[] lockValues;
    public final ArrayList<SubDemo.Link> links;   // 小节内可跳转的相关小节（可为空）

    public static final class Link {
        public final String label;
        public final int section;  // 跳转到代码页签的第几段
        public Link(String label, int section) { this.label = label; this.section = section; }
    }

    public SubDemo(String id, String title, String brief, String detail,
                   String[] lockKeys, Object[] lockValues) {
        this(id, title, brief, detail, lockKeys, lockValues, null);
    }

    public SubDemo(String id, String title, String brief, String detail,
                   String[] lockKeys, Object[] lockValues, ArrayList<Link> links) {
        this.id = id;
        this.title = title;
        this.brief = brief;
        this.detail = detail;
        this.lockKeys = lockKeys;
        this.lockValues = lockValues;
        this.links = links;
    }

    /** 便捷工厂：键值对形式传锁定参数，如 lock("mode",2,"density",24)。 */
    public static SubDemo lock(String id, String title, String brief, String detail,
                               Object... kv) {
        String[] keys = new String[kv.length / 2];
        Object[] vals = new Object[kv.length / 2];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = (String) kv[i * 2];
            vals[i] = kv[i * 2 + 1];
        }
        return new SubDemo(id, title, brief, detail, keys, vals);
    }
}
