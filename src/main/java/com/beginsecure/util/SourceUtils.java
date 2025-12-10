package com.beginsecure.util;

/**
 * Utility helpers for dealing with repository paths and Java source snippets.
 */
public final class SourceUtils {

    private SourceUtils() { }

    public static String simpleName(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        String file = (idx >= 0) ? normalized.substring(idx + 1) : normalized;
        return file.endsWith(".java") ? file.substring(0, file.length() - 5) : file;
    }

    public static String stripCommentsAndStrings(String src) {
        if (src == null) return "";
        String result = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        result = result.replaceAll("(?m)//.*", " ");
        result = result.replaceAll("\"([^\"\\\\]|\\\\.)*\"", "\"\"");
        result = result.replaceAll("'([^'\\\\]|\\\\.)*'", "''");
        return result;
    }

    public static boolean declaresInterface(String simpleName, String code) {
        if (simpleName == null || simpleName.isBlank() || code == null) return false;
        String stripped = stripCommentsAndStrings(code);
        return java.util.regex.Pattern.compile("\\binterface\\s+" + java.util.regex.Pattern.quote(simpleName) + "\\b")
                .matcher(stripped).find();
    }

    public static boolean declaresAbstractClass(String simpleName, String code) {
        if (simpleName == null || simpleName.isBlank() || code == null) return false;
        String stripped = stripCommentsAndStrings(code);
        return java.util.regex.Pattern.compile("\\babstract\\s+class\\s+" + java.util.regex.Pattern.quote(simpleName) + "\\b")
                .matcher(stripped).find();
    }
}
