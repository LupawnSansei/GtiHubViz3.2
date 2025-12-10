package com.beginsecure;

import com.beginsecure.util.SourceUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Computes abstractness and instability metrics for repository squares.
 */
public final class AIMetricsCalculator {

    private AIMetricsCalculator() { }

    public static void computeAll(List<Square> squares) {
        if (squares == null || squares.isEmpty()) return;

        Map<String, Square> byName = new HashMap<>();
        Map<String, String> strippedSource = new HashMap<>();
        Set<String> peerNames = new HashSet<>();

        for (Square square : squares) {
            if (square == null) continue;
            String path = String.valueOf(square.getPath()).replace('\\', '/');
            if (!path.endsWith(".java")) continue;

            String name = SourceUtils.simpleName(path);
            byName.put(name, square);
            peerNames.add(name);
            strippedSource.put(name, SourceUtils.stripCommentsAndStrings(square.getSource()));
            square.resetMetrics();
        }

        Map<String, Set<String>> efferent = new HashMap<>();
        for (Map.Entry<String, String> entry : strippedSource.entrySet()) {
            String me = entry.getKey();
            String code = entry.getValue();
            efferent.put(me, findPeerDeps(me, code, peerNames));
        }

        Map<String, Set<String>> afferent = new HashMap<>();
        for (String peer : peerNames) {
            afferent.put(peer, new HashSet<>());
        }
        for (Map.Entry<String, Set<String>> entry : efferent.entrySet()) {
            for (String dep : entry.getValue()) {
                afferent.getOrDefault(dep, Collections.emptySet()).add(entry.getKey());
            }
        }

        for (String name : peerNames) {
            Square square = byName.get(name);
            if (square == null) continue;
            String code = strippedSource.getOrDefault(name, "");
            boolean isInterface = Pattern.compile("\\binterface\\s+" + Pattern.quote(name) + "\\b")
                    .matcher(code).find();
            boolean isAbstractClass = Pattern.compile("\\babstract\\s+class\\s+" + Pattern.quote(name) + "\\b")
                    .matcher(code).find();
            square.setAbstractness(isInterface || isAbstractClass ? 1.0 : 0.0);

            Set<String> eff = efferent.getOrDefault(name, Collections.emptySet());
            Set<String> aff = afferent.getOrDefault(name, Collections.emptySet());
            square.setEfferentPeers(eff);
            square.setAfferentPeers(aff);

            int ce = eff.size();
            int ca = aff.size();
            square.setInstability((ca + ce) == 0 ? 0.0 : (double) ce / (ca + ce));
        }
    }

    private static Set<String> findPeerDeps(String self, String code, Set<String> peers) {
        Set<String> deps = new HashSet<>();
        if (code == null) code = "";
        for (String peer : peers) {
            if (peer.equals(self)) continue;
            String rx = "\\b(extends\\s+" + Pattern.quote(peer) + "\\b"
                    + "|implements\\s+[^;{]*\\b" + Pattern.quote(peer) + "\\b"
                    + "|new\\s+" + Pattern.quote(peer) + "\\s*\\("
                    + "|" + Pattern.quote(peer) + "\\s+[A-Za-z_][A-Za-z0-9_]*\\b"
                    + "|" + Pattern.quote(peer) + "\\s*\\.)";
            if (Pattern.compile(rx).matcher(code).find()) {
                deps.add(peer);
            }
        }
        return deps;
    }
}
