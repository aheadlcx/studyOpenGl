package com.example.studyopengl.param;

/**
 * 参数描述：每个 Demo 通过返回 List&lt;ParamSpec&gt; 声明自己可调的参数，
 * UI 层根据描述动态生成滑条 / 开关 / 下拉框（需求 6）。
 *
 * 值类型对应：
 *  TYPE_FLOAT  -> Float     滑条（min..max 连续）
 *  TYPE_INT    -> Integer   滑条（min..max 整数步进）
 *  TYPE_BOOL   -> Boolean   开关
 *  TYPE_OPTION -> Integer   下拉框，值为选项下标，optionLabels 为显示文本
 */
public final class ParamSpec {

    public static final int TYPE_FLOAT = 0;
    public static final int TYPE_INT = 1;
    public static final int TYPE_BOOL = 2;
    public static final int TYPE_OPTION = 3;

    public final String key;            // 引擎内部取值用的键
    public final String label;          // UI 显示名
    public final String hint;           // 参数含义说明（显示在值旁边可省略，供调试）
    public final int type;
    public final float min;
    public final float max;
    public final Object defaultValue;   // Float / Integer / Boolean
    public final String[] optionLabels; // 仅 TYPE_OPTION 有效
    public final String format;         // 浮点显示格式

    private ParamSpec(String key, String label, String hint, int type,
                      float min, float max, Object def,
                      String[] optionLabels, String format) {
        this.key = key;
        this.label = label;
        this.hint = hint;
        this.type = type;
        this.min = min;
        this.max = max;
        this.defaultValue = def;
        this.optionLabels = optionLabels;
        this.format = format;
    }

    public static ParamSpec floatSpec(String key, String label,
                                      float min, float max, float def) {
        return new ParamSpec(key, label, "", TYPE_FLOAT, min, max,
                Float.valueOf(def), null, "%.2f");
    }

    public static ParamSpec floatSpec(String key, String label,
                                      float min, float max, float def, String format) {
        return new ParamSpec(key, label, "", TYPE_FLOAT, min, max,
                Float.valueOf(def), null, format);
    }

    public static ParamSpec intSpec(String key, String label, int min, int max, int def) {
        return new ParamSpec(key, label, "", TYPE_INT, min, max,
                Integer.valueOf(def), null, "%.0f");
    }

    public static ParamSpec boolSpec(String key, String label, boolean def) {
        return new ParamSpec(key, label, "", TYPE_BOOL, 0, 1,
                Boolean.valueOf(def), null, "%s");
    }

    public static ParamSpec optionSpec(String key, String label, String[] labels, int defIndex) {
        return new ParamSpec(key, label, "", TYPE_OPTION, 0, labels.length - 1,
                Integer.valueOf(defIndex), labels, "%s");
    }
}
