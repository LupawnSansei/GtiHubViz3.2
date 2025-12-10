package com.beginsecure.handlers;

import com.beginsecure.AIMetricsCalculator;
import com.beginsecure.Blackboard;
import com.beginsecure.Square;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Background worker that fetches repository files and populates the blackboard.
 * @author @NickGottwald
 * @author @Muska Said
 */
public class Delegate implements Runnable {

    private String url;
    private static final Logger LOGGER = Logger.getLogger(Delegate.class.getName());
    static {
        try {
            LOGGER.setUseParentHandlers(false);
            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new WhiteFormatter());
            handler.setLevel(java.util.logging.Level.INFO);
            LOGGER.addHandler(handler);
        } catch (SecurityException ignored) { }
    }

    public Delegate(String url) {
        this.url = url;
    }

    @Override
    public void run() {
        Blackboard board = Blackboard.getInstance();
        board.clear();
        try {
            LOGGER.info("Analyzing GitHub repository: " + url);
            String token = System.getenv("token");
            GitHubHandler gh = new GitHubHandler(token == null ? "" : token);

            java.util.List<String> allPaths = gh.listFilesRecursive(url);
            java.util.List<Square> loaded = new java.util.ArrayList<>();
            for (String path : allPaths) {
                if (!path.endsWith(".java")) continue;
                String content = gh.getFileContentFromUrl(convertToBlobUrl(url, path));
                int lines = countLines(content);
                Square square = new Square(path, lines);
                square.setSource(content);
                loaded.add(square);
            }
            AIMetricsCalculator.computeAll(loaded);
            board.updateSquares(loaded);
            board.setStatusMessage("Loaded " + loaded.size() + " Java files.");
            LOGGER.info("Repository analysis complete. Files processed: " + loaded.size());
        } catch (Exception e) {
            board.reportError("Unable to load repository: " + e.getMessage());
        } finally {
            board.setLoading(false);
        }
    }

    private int countLines(String content) {
        if (content == null || content.isEmpty()) return 0;
        int count = 0;
        for (String line : content.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private String convertToBlobUrl(String url, String path) {
        GitHubHandler.RepoRef ref = GitHubHandler.RepoRef.fromUrl(url);
        StringBuilder sb = new StringBuilder();

        sb.append("https://raw.githubusercontent.com/")
          .append(ref.owner).append("/").append(ref.repo).append("/")
          .append(ref.branch).append("/")
          .append(path);
        return sb.toString();
    }

    private static final class WhiteFormatter extends Formatter {
        private static final String WHITE = "\u001B[37m";
        private static final String RESET = "\u001B[0m";

        @Override
        public String format(LogRecord record) {
            return WHITE + formatMessage(record) + RESET + System.lineSeparator();
        }
    }
}
