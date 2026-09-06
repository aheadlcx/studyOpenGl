package com.example.studyopengl.ui;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 GLSL/Java 语法高亮：关键词紫、类型蓝、数字橙、内建黄、注释绿。
 * 输出 SpannableStringBuilder 直接喂给 TextView（等宽字体）。
 */
public final class CodeHighlighter {

    private static final int COLOR_TEXT = 0xFFE3E9EF;
    private static final int COLOR_KEYWORD = 0xFFC792EA;
    private static final int COLOR_TYPE = 0xFF82AAFF;
    private static final int COLOR_NUMBER = 0xFFF78C6C;
    private static final int COLOR_COMMENT = 0xFF8FAE7B;
    private static final int COLOR_BUILTIN = 0xFFFFCB6B;

    private static final String[] KEYWORDS = {
            "uniform", "in", "out", "inout", "void", "return", "if", "else",
            "for", "while", "discard", "precision", "layout", "location",
            "flat", "smooth", "new", "true", "false", "static", "final"
    };
    private static final String[] TYPES = {
            "float", "int", "bool", "vec2", "vec3", "vec4", "mat2", "mat3",
            "mat4", "sampler2D", "samplerCube", "sampler2DArray",
            "FloatBuffer", "ByteBuffer", "String"
    };
    private static final String[] BUILTINS = {
            "gl_Position", "gl_FragCoord", "gl_FragColor", "gl_PointSize",
            "gl_InstanceID", "gl_VertexID", "GLES30", "GL10", "Matrix"
    };

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\.?\\d*f?\\b");

    private static final Map<String, Integer> COLOR_MAP = new HashMap<String, Integer>();

    static {
        for (String k : KEYWORDS) COLOR_MAP.put(k, COLOR_KEYWORD);
        for (String t : TYPES) COLOR_MAP.put(t, COLOR_TYPE);
        for (String b : BUILTINS) COLOR_MAP.put(b, COLOR_BUILTIN);
    }

    private CodeHighlighter() {
    }

    public static CharSequence highlight(String code) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = code.split("\n", -1);
        for (String line : lines) {
            int start = builder.length();
            builder.append(line);
            builder.append('\n');

            String trimmed = line.trim();
            // 整行注释
            int commentIdx = trimmed.startsWith("//") ? 0 : indexOfComment(line);
            if (commentIdx >= 0) {
                builder.setSpan(new ForegroundColorSpan(COLOR_COMMENT),
                        start + commentIdx, builder.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (commentIdx == 0) {
                    continue;
                }
            }
            int limit = commentIdx >= 0 ? start + commentIdx : builder.length() - 1;
            highlightWords(builder, start, limit);
            highlightNumbers(builder, start, limit);
        }
        return builder;
    }

    /** 找行内 // 注释起点（忽略出现在引号里的 //，粗略处理即可）。 */
    private static int indexOfComment(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (!inQuote && c == '/' && line.charAt(i + 1) == '/') {
                return i;
            }
        }
        return -1;
    }

    private static void highlightWords(SpannableStringBuilder builder, int from, int limit) {
        String text = builder.toString().substring(from, limit);
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            Integer color = COLOR_MAP.get(m.group());
            if (color != null) {
                builder.setSpan(new ForegroundColorSpan(color),
                        from + m.start(), from + m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void highlightNumbers(SpannableStringBuilder builder, int from, int limit) {
        String text = builder.toString().substring(from, limit);
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            builder.setSpan(new ForegroundColorSpan(COLOR_NUMBER),
                    from + m.start(), from + m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
