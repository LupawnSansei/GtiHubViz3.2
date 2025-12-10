[README.md](https://github.com/user-attachments/files/24065847/README.md)
# GitHubViz 3.0

GitHubViz is a desktop visualization companion for GitHub repositories. Point the app at any public repo and it will fetch every Java file, analyze dependencies and Carlos metrics, then render interactive views that help you reason about the code base faster.

---

## ✨ Key Features

- **Repository Fetching & Analysis**  
  Background worker pulls every `.java` file from GitHub (raw API), counts LOC, caches the raw source, and computes Uncle Bob’s A/I (Abstractness vs Instability) metrics plus dependency graphs.

- **Interactive Workspace**  
  *Top Bar* accepts a GitHub URL, *Repo Tree* mirrors the folder layout, and *Tabs* expose four visualizations:
  1. **Heat Grid** – LOC-based coloring of every file.
  2. **Metrics Scatter Plot** – Abstractness vs Instability with tooltips.
  3. **PlantUML Diagram** – Shows extends/implements/aggregation/dependency injections, including external framework classes.
  4. **ChatGPT Panel** – Context-aware chat (OpenAI API) grounded by repository summaries.

- **Real-time Status Bar**  
  Watches the blackboard bus for `statusMessage`, `error`, `loading`, and `squares` events to inform the user exactly what is happening (e.g., “Analyzing GitHub repository…”, “Loaded 42 files”). All loggers output in white for clarity.

- **Crisp Branding**  
  The Stephen Okita-inspired icon is bundled at multiple resolutions (16 → full size) for sharp window/taskbar representation.

---

## 🧱 Architecture

- `Blackboard` – Application event bus + shared storage for squares/metrics. Panels listen to property changes.
- `Delegate` – Background worker that downloads repo content, updates the blackboard, and logs analysis progress.
- `Square` + `AIMetricsCalculator` – Data representation of files and centralized A/I computation.
- `RelationshipExtractor` – Regex based analyzer that detects inheritance/implementation/composition/DI edges for PlantUML.
- `Panels` – Swing UI layer; every major component is its own class (`GridPanel`, `MetricsPanel`, `DiagramPanel`, `StatusBarPanel`, etc.).
- `RepositoryContextBuilder` – Generates the summary context fed into ChatGPT.

Communication is event-driven: when squares change (after analysis), the blackboard fires events, panels refresh themselves, and the status bar updates user-facing messages.

---

## 🚀 Getting Started

1. **Prerequisites**
   - JDK 21+
   - Maven 3.9+
   - Optional: OpenAI API key (set `openaikey` env var) for the chat panel.

2. **Clone & Build**
   ```bash
   git clone https://github.com/<you>/GitHubViz3.0.git
   cd GitHubViz3.0
   mvn clean package
   ```

3. **Run**
   ```bash
   mvn exec:java -Dexec.mainClass="com.beginsecure.Main"
   ```
   or run `Main` from your IDE.

4. **Use**
   - Enter a GitHub repo URL (e.g., `https://github.com/openjdk/jdk`).
   - Watch the logs/status bar for progress (“Analyzing GitHub repository…”).
   - Explore the grid, metrics plot, diagram, and chat tabs.

---

## 🛠️ Configuration Cheatsheet

- **OpenAI Chat**: Set `openaikey` in your environment before launching to enable responses.
- **GitHub Token**: Optionally set `token` (env var) for higher API rate limits.
- **Logging Theme**: All loggers (AppFrame, RepositoryContextBuilder, Delegate) output in white to ensure readability even on terminals that default errors to red.

---

## 📊 Visual Highlights

- *Heat Grid* shifts color from green → yellow → red based on LOC. Hash offsets prevent overlap.
- *Metrics Plot* jittered points with tooltips, axes labeled “instability (I)” and “abstractness (A)”.
- *Diagram Panel* uses PlantUML with orthogonal layout, shows extends/implements/compositions/dependencies even for external classes (JFrame, MouseListener, etc.).
- *Status Bar* transitions through “Loading…”, “Ready”, “Loaded N files”, or displayed error text.

---

## 🤖 Chat Integration

ChatGPT panel summarizes the current repo (including top files, A/I averages, dependency hubs) and feeds that into an OpenAI conversation so you can ask “Which packages are unstable?” or “Explain the diagram tab” and receive short, grounded answers.

---

## ❤️ Credits

- UI & overall architecture by **@NickGottwald** and **@Muska Said**.
- Icon art inspired by Stephen Okita’s GitHub pixel portrait.

---

## 📄 License

MIT-style (insert your license here). Update this section to match your actual licensing requirements before publishing.

---

Happy Visualizing!
