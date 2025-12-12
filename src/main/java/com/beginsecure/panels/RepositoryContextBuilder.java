package com.beginsecure.panels;

import com.beginsecure.AIMetricsCalculator;
import com.beginsecure.Blackboard;
import com.beginsecure.Square;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Builds a concise textual summary of the currently loaded repository so
 * the chat model can answer context-aware questions.
 */
final class RepositoryContextBuilder {

    private static final Logger LOGGER = Logger.getLogger(RepositoryContextBuilder.class.getName());
    static {
        try {
            LOGGER.setUseParentHandlers(false);
            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new WhiteFormatter());
            handler.setLevel(java.util.logging.Level.INFO);
            LOGGER.addHandler(handler);
        } catch (SecurityException ignored) { }
    }

    ChatMessage buildContextMessage() {
        List<Square> squares = snapshotSquares();
        if (squares.isEmpty()) {
            return null;
        }
        AIMetricsCalculator.computeAll(squares);

        String prefix = getSelectedPrefix();
        List<Square> focused = filterByPrefix(squares, prefix);
        if (focused.isEmpty()) {
            focused = squares;
        }

        int totalLoc = squares.stream().mapToInt(Square::getLinesOfCode).sum();
        int focusLoc = focused.stream().mapToInt(Square::getLinesOfCode).sum();
        double avgInstability = averageMetric(focused, Square::getInstability);
        double avgAbstractness = averageMetric(focused, Square::getAbstractness);

        StringBuilder sb = new StringBuilder();
        String repoUrl = getRepositoryUrl();
        if (repoUrl != null && !repoUrl.isBlank()) {
            sb.append("Repository URL: ").append(repoUrl).append('\n');
        }
        sb.append("Loaded ").append(squares.size()).append(" Java files (")
                .append(totalLoc).append(" LOC). Focus set: ").append(focused.size())
                .append(" files (").append(focusLoc).append(" LOC) in ")
                .append((prefix == null || prefix.isBlank()) ? "entire tree" : prefix).append(".\n");
        sb.append("Largest files: ").append(formatTopFiles(focused)).append('\n');
        sb.append("Instability avg=").append(formatDouble(avgInstability))
                .append(", abstractness avg=").append(formatDouble(avgAbstractness))
                .append("; extremes: ").append(formatTopInstability(focused)).append('\n');
        sb.append("Dependency hubs: ").append(formatTopDependencies(focused)).append('\n');
        sb.append("Sample edges: ").append(formatSampleEdges(focused));

        String summary = sb.toString();
        LOGGER.info(summary);
        return new ChatMessage("system", summary);
    }

    private static final class WhiteFormatter extends Formatter {
        private static final String WHITE = "\u001B[37m";
        private static final String RESET = "\u001B[0m";

        @Override
        public String format(LogRecord record) {
            return WHITE + formatMessage(record) + RESET + System.lineSeparator();
        }
    }

    private static List<Square> snapshotSquares() {
        try {
            List<Square> source = Blackboard.getInstance().getSquares();
            return (source == null) ? new ArrayList<>() : new ArrayList<>(source);
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static String getSelectedPrefix() {
        try {
            return Blackboard.getInstance().getSelectedPrefix();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String getRepositoryUrl() {
        try {
            return Blackboard.getInstance().getLastRepositoryUrl();
        } catch (Throwable t) {
            return "";
        }
    }

    private static List<Square> filterByPrefix(List<Square> squares, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return new ArrayList<>(squares);
        }
        String normalized = prefix.replace('\\', '/');
        return squares.stream()
                .filter(sq -> {
                    String path = String.valueOf(sq.getPath()).replace('\\', '/');
                    return path.contains(normalized + "/") || path.endsWith("/" + normalized) || path.equals(normalized);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static double averageMetric(List<Square> squares, MetricAccessor accessor) {
        double sum = 0;
        int count = 0;
        for (Square square : squares) {
            Double value = accessor.apply(square);
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static String formatTopFiles(List<Square> squares) {
        String result = squares.stream()
                .sorted(Comparator.comparingInt(Square::getLinesOfCode).reversed())
                .limit(5)
                .map(s -> s.getSimpleName() + "(" + s.getLinesOfCode() + ")")
                .collect(Collectors.joining(", "));
        return result.isBlank() ? "n/a" : result;
    }

    private static String formatTopInstability(List<Square> squares) {
        String result = squares.stream()
                .filter(s -> s.getInstability() != null)
                .sorted((a, b) -> Double.compare(b.getInstability(), a.getInstability()))
                .limit(3)
                .map(s -> s.getSimpleName() + "=" + formatDouble(s.getInstability()))
                .collect(Collectors.joining(", "));
        return result.isBlank() ? "n/a" : result;
    }

    private static String formatTopDependencies(List<Square> squares) {
        String result = squares.stream()
                .filter(s -> s.getCout() > 0)
                .sorted(Comparator.comparingInt(Square::getCout).reversed())
                .limit(4)
                .map(s -> s.getSimpleName() + "(" + s.getCout() + ")")
                .collect(Collectors.joining(", "));
        return result.isBlank() ? "no dependency data" : result;
    }

    private static String formatSampleEdges(List<Square> squares) {
        List<String> edges = new ArrayList<>();
        for (Square sq : squares) {
            for (String dep : sq.getEfferentPeers()) {
                edges.add(sq.getSimpleName() + "->" + dep);
                if (edges.size() >= 8) break;
            }
            if (edges.size() >= 8) break;
        }
        if (edges.isEmpty()) {
            return "no dependency edges captured";
        }
        return String.join(", ", edges);
    }

    private static String formatDouble(double value) {
        return String.format("%.2f", value);
    }

    private interface MetricAccessor {
        Double apply(Square square);
    }
}
