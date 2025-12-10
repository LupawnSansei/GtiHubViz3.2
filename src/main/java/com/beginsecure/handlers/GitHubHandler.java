package com.beginsecure.handlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal GitHub API client for listing repository files and downloading raw content.
 * @author @NickGottwald
 * @author @Muska Said
 */
public class GitHubHandler {

    private final String token;

    public GitHubHandler(String token) {
        this.token = token;
    }


    public static class RepoRef {
        public final String owner;
        public final String repo;
        public final String branch;
        public final String prefix;

        public RepoRef(String owner, String repo, String branch, String prefix) {
            this.owner = owner;
            this.repo = repo;
            this.branch = branch;
            this.prefix = prefix == null ? "" : prefix;
        }


        public static RepoRef fromUrl(String url) {
            String u = url;
            if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            int i = u.indexOf("github.com/");
            if (i >= 0) {
                u = u.substring(i + "github.com/".length());
            }
            String[] parts = u.split("/");
            String owner = parts.length > 0 ? parts[0] : "";
            String repo = parts.length > 1 ? parts[1] : "";
            String branch = "main";
            String prefix = "";
            if (parts.length >= 3) {
                if ("tree".equals(parts[2]) || "blob".equals(parts[2])) {
                    branch = parts.length > 3 ? parts[3] : "main";
                    if (parts.length > 4) {
                        StringBuilder sb = new StringBuilder();
                        for (int k = 4; k < parts.length; k++) {
                            sb.append(parts[k]);
                            if (k < parts.length - 1) sb.append("/");
                        }
                        prefix = sb.toString();
                    }
                }
            }
            return new RepoRef(owner, repo, branch, prefix);
        }
    }


    public List<String> listFilesRecursive(String url) throws IOException {
        RepoRef ref = RepoRef.fromUrl(url);
        String api = "https://api.github.com/repos/" + ref.owner + "/" + ref.repo + "/git/trees/" + ref.branch + "?recursive=1";
        String json = httpGet(api, true);
        List<String> paths = new ArrayList<>();
        if (json == null || json.isEmpty()) return paths;

        String normalizedPrefix = ref.prefix.replace("\\", "/");
        if (normalizedPrefix.startsWith("/")) normalizedPrefix = normalizedPrefix.substring(1);
        if (normalizedPrefix.length() > 0 && !normalizedPrefix.endsWith("/")) normalizedPrefix += "/";

        // extremely small JSON "parser" matching tree entries: {"path":"...","mode":"...","type":"blob|tree"...}
        int idx = 0;
        while ((idx = json.indexOf("\"path\":", idx)) != -1) {
            int start = json.indexOf('"', idx + 7) + 1;
            int end = json.indexOf('"', start);
            if (start <= 0 || end <= start) break;
            String path = json.substring(start, end);

            int objEnd = json.indexOf('}', end);
            if (objEnd == -1) objEnd = end + 1;
            int typeIdx = json.indexOf("\"type\":", end);
            if (typeIdx != -1 && typeIdx < objEnd) {
                int tStart = json.indexOf('"', typeIdx + 7) + 1;
                int tEnd = json.indexOf('"', tStart);
                if (tStart > 0 && tEnd > tStart) {
                    String type = json.substring(tStart, tEnd);
                    if ("blob".equals(type)) {
                        if (normalizedPrefix.isEmpty() || path.startsWith(normalizedPrefix)) {
                            paths.add(path);
                        }
                    }
                }
            }
            idx = objEnd + 1;
        }
        return paths;
    }

    public String getFileContentFromUrl(String rawUrl) throws IOException {
        // If the URL points to GitHub HTML with '?raw=1' or raw.githubusercontent.com, this will return file content.
        return httpGet(rawUrl, false);
    }

    private String httpGet(String url, boolean api) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "GitHubViz/1.0");
        if (api) {
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
        } else {
            // For raw files, GitHub may require this header when going through the API endpoint.
            if (url.contains("api.github.com") && (token != null && !token.isEmpty())) {
                conn.setRequestProperty("Accept", "application/vnd.github.raw");
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
