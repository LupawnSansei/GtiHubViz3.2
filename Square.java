package com.beginsecure;

import com.beginsecure.util.SourceUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Data model describing a repository file along with derived metrics and dependencies.
 * @author @NickGottwald
 * @author @Muska Said
 */
public class Square {

    private String path;
    private int linesOfCode;

    public Square(String path, int linesOfCode) {
        this.path = path;
        this.linesOfCode = linesOfCode;
    }

    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public int getLinesOfCode() {
        return linesOfCode;
    }
    public void setLinesOfCode(int linesOfCode) {
        this.linesOfCode = linesOfCode;
    }

    public String getName() {
        return getSimpleName();
    }

    public String getSimpleName() {
        return SourceUtils.simpleName(path);
    }

    private String source = "";

    private Double instability;
    private Double abstractness;

    private Set<String> efferentPeers = new HashSet<>();
    private Set<String> afferentPeers = new HashSet<>();

    public String getSource() { return source; }
    public void setSource(String src) { this.source = (src == null) ? "" : src; }

    public Double getInstability() { return instability; }
    void setInstability(Double value) { this.instability = value; }

    public Double getAbstractness() { return abstractness; }
    void setAbstractness(Double value) { this.abstractness = value; }

    public int getCin()  { return afferentPeers.size(); }
    public int getCout() { return efferentPeers.size(); }

    public Set<String> getEfferentPeers() {
        return Collections.unmodifiableSet(efferentPeers);
    }

    public Set<String> getAfferentPeers() {
        return Collections.unmodifiableSet(afferentPeers);
    }

    void setEfferentPeers(Set<String> peers) {
        efferentPeers.clear();
        if (peers != null) efferentPeers.addAll(peers);
    }

    void setAfferentPeers(Set<String> peers) {
        afferentPeers.clear();
        if (peers != null) afferentPeers.addAll(peers);
    }

    void resetMetrics() {
        instability = null;
        abstractness = null;
        efferentPeers.clear();
        afferentPeers.clear();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Square)) return false;
        Square that = (Square) o;
        return String.valueOf(this.path).equals(String.valueOf(that.path));
    }
    @Override public int hashCode() {
        return String.valueOf(path).hashCode();
    }
    @Override public String toString() {
        return "Square{" + getSimpleName() + ", LOC=" + linesOfCode + "}";
    }
}
