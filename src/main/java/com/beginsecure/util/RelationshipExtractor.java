package com.beginsecure.util;

import com.beginsecure.Square;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses source snippets to infer UML relationships between repository squares.
 */
public final class RelationshipExtractor {

    private RelationshipExtractor() { }

    public enum Type {
        GENERALIZATION,   // class extends class
        IMPLEMENTATION,   // class implements interface
        COMPOSITION,      // strong ownership (final field)
        AGGREGATION,      // field reference
        DEPENDENCY        // constructor injection / method parameter
    }

    public static final class Relationship {
        private final String from;
        private final String to;
        private final Type type;

        public Relationship(String from, String to, Type type) {
            this.from = from;
            this.to = to;
            this.type = type;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public Type getType() {
            return type;
        }

        public String toPlantUml(String fromAlias, String toAlias) {
            return switch (type) {
                case GENERALIZATION -> fromAlias + " --|> " + toAlias;
                case IMPLEMENTATION -> fromAlias + " ..|> " + toAlias;
                case COMPOSITION -> fromAlias + " *-- " + toAlias;
                case AGGREGATION -> fromAlias + " o-- " + toAlias;
                case DEPENDENCY -> fromAlias + " ..> " + toAlias;
            };
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Relationship)) return false;
            Relationship that = (Relationship) o;
            return from.equals(that.from) && to.equals(that.to) && type == that.type;
        }

        @Override
        public int hashCode() {
            int result = from.hashCode();
            result = 31 * result + to.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }
    }

    public static List<Relationship> extract(List<Square> squares) {
        Map<String, String> codeByName = new HashMap<>();
        for (Square square : squares) {
            if (square == null) continue;
            String name = square.getSimpleName();
            if (name == null || name.isBlank()) continue;
            codeByName.put(name, SourceUtils.stripCommentsAndStrings(square.getSource()));
        }

        Set<Relationship> relationships = new LinkedHashSet<>();
        for (Square square : squares) {
            if (square == null) continue;
            String name = square.getSimpleName();
            String code = codeByName.get(name);
            if (name == null || name.isBlank() || code == null) continue;

            relationships.addAll(findInheritance(name, code));
            relationships.addAll(findFieldAssociations(name, code));
            relationships.addAll(findConstructorDependencies(name, code));
        }
        return new ArrayList<>(relationships);
    }

    private static Set<Relationship> findInheritance(String simpleName, String code) {
        Set<Relationship> rels = new LinkedHashSet<>();
        Pattern classExtends = Pattern.compile("\\bclass\\s+" + Pattern.quote(simpleName) + "\\s+extends\\s+([A-Za-z0-9_$.<>]+)");
        Matcher extendsMatcher = classExtends.matcher(code);
        if (extendsMatcher.find()) {
            String parent = normalizeType(extendsMatcher.group(1));
            if (parent != null) {
                rels.add(new Relationship(simpleName, parent, Type.GENERALIZATION));
            }
        }

        Pattern classImplements = Pattern.compile("\\bclass\\s+" + Pattern.quote(simpleName) + "[^{]*implements\\s+([^\\{]+)");
        Matcher implMatcher = classImplements.matcher(code);
        if (implMatcher.find()) {
            for (String raw : implMatcher.group(1).split(",")) {
                String iface = normalizeType(raw);
                if (iface != null) {
                    rels.add(new Relationship(simpleName, iface, Type.IMPLEMENTATION));
                }
            }
        }

        Pattern interfaceExtends = Pattern.compile("\\binterface\\s+" + Pattern.quote(simpleName) + "\\s+extends\\s+([^\\{]+)");
        Matcher ifaceMatcher = interfaceExtends.matcher(code);
        if (ifaceMatcher.find()) {
            for (String raw : ifaceMatcher.group(1).split(",")) {
                String parent = normalizeType(raw);
                if (parent != null) {
                    rels.add(new Relationship(simpleName, parent, Type.GENERALIZATION));
                }
            }
        }
        return rels;
    }

    private static Set<Relationship> findFieldAssociations(String simpleName, String code) {
        Set<Relationship> rels = new LinkedHashSet<>();
        Pattern fieldPattern = Pattern.compile("(?m)^(?:\\s*)(public|protected|private)\\s+(static\\s+)?(final\\s+)?([A-Z][A-Za-z0-9_$.<>]*)\\s+[A-Za-z_][A-Za-z0-9_]*(\\s*[=;,)])?");
        Matcher matcher = fieldPattern.matcher(code);
        while (matcher.find()) {
            boolean isStatic = matcher.group(2) != null;
            boolean isFinal = matcher.group(3) != null;
            String type = normalizeType(matcher.group(4));
            if (type == null || type.equals(simpleName)) continue;
            if (isStatic) continue; // static references don't represent ownership
            Type relType = isFinal ? Type.COMPOSITION : Type.AGGREGATION;
            rels.add(new Relationship(simpleName, type, relType));
        }
        return rels;
    }

    private static Set<Relationship> findConstructorDependencies(String simpleName, String code) {
        Set<Relationship> rels = new LinkedHashSet<>();
        Pattern ctorPattern = Pattern.compile("(?:public|protected|private)\\s+" + Pattern.quote(simpleName) + "\\s*\\(([^)]*)\\)");
        Matcher matcher = ctorPattern.matcher(code);
        while (matcher.find()) {
            String params = matcher.group(1);
            for (String rawParam : params.split(",")) {
                String cleaned = rawParam.trim();
                if (cleaned.isEmpty()) continue;
                cleaned = cleaned.replaceAll("@[A-Za-z0-9_$.]+", "").trim();
                cleaned = cleaned.replaceAll("\\bfinal\\b", "").trim();
                String[] tokens = cleaned.split("\\s+");
                if (tokens.length < 2) continue;
                String typeCandidate = tokens[tokens.length - 2];
                String type = normalizeType(typeCandidate);
                if (type != null && !type.equals(simpleName)) {
                    rels.add(new Relationship(simpleName, type, Type.DEPENDENCY));
                }
            }
        }
        return rels;
    }

    private static String normalizeType(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("<.*?>", "");
        cleaned = cleaned.replace("[]", "");
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0) cleaned = cleaned.substring(dot + 1);
        cleaned = cleaned.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty()) return null;
        if (!Character.isUpperCase(cleaned.charAt(0))) return null;
        return cleaned;
    }
}
