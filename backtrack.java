package eval31;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;

// Cell
class Cell {
    int row, col, regionId = -1;
    public Cell(int row, int col) { this.row = row; this.col = col; }
}

// Region
class Region {
    int id, color = -1;
    boolean isLocked = false;
    Set<Cell> cells = new HashSet<>();

    public Region(int id) { this.id = id; }
    public void addCell(Cell c) { cells.add(c); }

    public Point getCentroid() {
        int sr = 0, sc = 0;
        for (Cell c : cells) { sr += c.row; sc += c.col; }
        return new Point(sc / cells.size(), sr / cells.size());
    }
}

// Map Generation
class mapgeneration {
    private final Random random = new Random();
    private final int gridRows, gridCols;
    private final Cell[][] grid;
    private final List<Region> regions = new ArrayList<>();
    private static final int MIN_REGION_SIZE = 8;

    public mapgeneration(int gridRows, int gridCols) {
        this.gridRows = gridRows;
        this.gridCols = gridCols;
        this.grid = new Cell[gridRows][gridCols];
        for (int r = 0; r < gridRows; r++)
            for (int c = 0; c < gridCols; c++)
                grid[r][c] = new Cell(r, c);
    }

    public List<Region> generateRegions(int numRegions) {
        boolean[][] visited = new boolean[gridRows][gridCols];
        int avg = (gridRows * gridCols) / numRegions;
        int min = Math.max(MIN_REGION_SIZE, avg / 2);
        List<Cell> seeds = pickSeeds(numRegions);

        for (int i = 0; i < numRegions; i++) {
            Region region = new Region(i);
            Cell seed = seeds.get(i);
            int target = Math.max(min, (int)(avg * (0.7 + random.nextDouble() * 0.6)));

            Queue<Cell> q = new LinkedList<>();
            q.add(seed); visited[seed.row][seed.col] = true;

            while (!q.isEmpty() && region.cells.size() < target) {
                Cell cur = q.poll();
                cur.regionId = i; region.addCell(cur);
                List<Cell> nbrs = neighbors(cur);
                Collections.shuffle(nbrs);
                for (Cell nb : nbrs) {
                    if (!visited[nb.row][nb.col]) {
                        if (random.nextDouble() < (region.cells.size() < min ? 0.95 : 0.65)) {
                            visited[nb.row][nb.col] = true; q.add(nb);
                        }
                    }
                }
            }
            while (region.cells.size() < min) {
                Cell exp = nearestUnvisited(region, visited);
                if (exp == null) break;
                visited[exp.row][exp.col] = true; exp.regionId = i; region.addCell(exp);
            }
            regions.add(region);
        }

        for (int r = 0; r < gridRows; r++)
            for (int c = 0; c < gridCols; c++) {
                Cell cell = grid[r][c];
                if (cell.regionId == -1) {
                    int nr = nearestRegion(cell);
                    cell.regionId = nr; regions.get(nr).addCell(cell);
                }
            }
        return regions;
    }

    private List<Cell> pickSeeds(int n) {
        List<Cell> seeds = new ArrayList<>();
        int minDist = (int) Math.sqrt((gridRows * gridCols) / n);
        int attempts = 1000;
        while (seeds.size() < n && attempts-- > 0) {
            Cell c = grid[random.nextInt(gridRows)][random.nextInt(gridCols)];
            boolean ok = true;
            for (Cell s : seeds)
                if (Math.sqrt(Math.pow(c.row-s.row,2)+Math.pow(c.col-s.col,2)) < minDist) { ok=false; break; }
            if (ok) seeds.add(c);
        }
        while (seeds.size() < n) {
            Cell c = grid[random.nextInt(gridRows)][random.nextInt(gridCols)];
            if (!seeds.contains(c)) seeds.add(c);
        }
        return seeds;
    }

    private Cell nearestUnvisited(Region region, boolean[][] visited) {
        for (Cell rc : region.cells)
            for (Cell nb : neighbors(rc))
                if (!visited[nb.row][nb.col]) return nb;
        return null;
    }

    private List<Cell> neighbors(Cell cell) {
        List<Cell> list = new ArrayList<>();
        for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            int nr = cell.row+d[0], nc = cell.col+d[1];
            if (nr>=0 && nr<gridRows && nc>=0 && nc<gridCols) list.add(grid[nr][nc]);
        }
        return list;
    }

    private int nearestRegion(Cell cell) {
        for (Cell nb : neighbors(cell)) if (nb.regionId != -1) return nb.regionId;
        int best = 0; double bestD = Double.MAX_VALUE;
        for (Region r : regions) {
            Point cen = r.getCentroid();
            double d = Math.sqrt(Math.pow(cell.row-cen.y,2)+Math.pow(cell.col-cen.x,2));
            if (d < bestD) { bestD = d; best = r.id; }
        }
        return best;
    }

    public Cell[][] getGrid()  { return grid; }
    public int getGridRows()   { return gridRows; }
    public int getGridCols()   { return gridCols; }
}

// Game Graph
class GameGraph {
    private final List<Region> regions;
    private final Map<Integer, Set<Integer>> adj = new HashMap<>();
    private final int numColors;

    public GameGraph(List<Region> regions, Cell[][] grid, int gridRows, int gridCols, int numColors) {
        this.regions   = regions;
        this.numColors = numColors;
        for (Region r : regions) adj.put(r.id, new HashSet<>());

        for (int r = 0; r < gridRows; r++)
            for (int c = 0; c < gridCols; c++) {
                int rid = grid[r][c].regionId;
                for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    int nr = r+d[0], nc = c+d[1];
                    if (nr>=0 && nr<gridRows && nc>=0 && nc<gridCols) {
                        int nid = grid[nr][nc].regionId;
                        if (nid != rid) adj.get(rid).add(nid);
                    }
                }
            }
    }

    public Set<Integer> availableColors(int regionId) {
        Set<Integer> avail = new HashSet<>();
        for (int i = 0; i < numColors; i++) avail.add(i);
        for (int n : adj.get(regionId)) {
            int c = regions.get(n).color;
            if (c != -1) avail.remove(c);
        }
        return avail;
    }

    public boolean inConflict(int regionId) {
        int c = regions.get(regionId).color;
        if (c == -1) return false;
        for (int n : adj.get(regionId))
            if (regions.get(n).color == c) return true;
        return false;
    }

    public List<Region>   getRegions()           { return regions; }
    public Set<Integer>   getNeighbors(int rid)  { return adj.get(rid); }
    public int            getNumColors()          { return numColors; }
}

// Backtracking Solver
class BacktrackingSolver {

    private final GameGraph    graph;
    private final int          numColors;

    // Stats
    private int  colorTrials      = 0;  // total (region, color) pairs tried
    private int  colorRejections  = 0;  // pairs rejected by isSafe check
    private int  backtrackCount   = 0;  // times we undid a color assignment
    private int  callDepth        = 0;  // max recursion depth reached
    private int  maxDepthReached  = 0;
    private long solveTimeMs      = 0;

    // Solution: regionId -> color (0-indexed)
    private Map<Integer, Integer> solution = null;

    // Ordered list of free region IDs to color
    private List<Integer> freeIds;
    private int           n;

    // Move sequence for step-by-step playback: each int[] = {regionId, color}
    private final List<int[]> moveSequence = new ArrayList<>();

    public BacktrackingSolver(GameGraph graph) {
        this.graph     = graph;
        this.numColors = graph.getNumColors();
    }

    public Map<Integer, Integer> solve() {
        solution       = null;
        colorTrials    = 0;
        colorRejections= 0;
        backtrackCount = 0;
        maxDepthReached= 0;
        moveSequence.clear();

        long t0 = System.currentTimeMillis();
        List<Region> regions = graph.getRegions();

        // Pre-check: locked regions must not already conflict
        for (Region r : regions) {
            if (r.color == -1) continue;
            for (int nbId : graph.getNeighbors(r.id)) {
                if (regions.get(nbId).color == r.color) {
                    System.out.printf("[BT] PRE-CHECK FAIL: Region %d conflicts Region %d%n", r.id, nbId);
                    solveTimeMs = System.currentTimeMillis() - t0;
                    return null;
                }
            }
        }

        // Collect free (uncolored, unlocked) regions
        freeIds = new ArrayList<>();
        for (Region r : regions)
            if (!r.isLocked && r.color == -1) freeIds.add(r.id);
        n = freeIds.size();

        // Initialize color array
        int[] colorArray = new int[n];
        Arrays.fill(colorArray, -1);

        printBanner();

        // Start backtracking
        boolean found = backtrack(colorArray, 0);

        solveTimeMs = System.currentTimeMillis() - t0;
        printSolveStats();

        if (!found) return null;

        // Build full solution map
        solution = new HashMap<>();
        for (Region r : regions) solution.put(r.id, r.color);
        for (int i = 0; i < n; i++) solution.put(freeIds.get(i), colorArray[i]);

        // Build move sequence
        for (int i = 0; i < n; i++)
            moveSequence.add(new int[]{ freeIds.get(i), colorArray[i] });

        return solution;
    }

    private boolean isSafe(int regionId, int color, int[] colorArray) {
        for (int neighborId : graph.getNeighbors(regionId)) {
            Region neighbor = graph.getRegions().get(neighborId);
            int neighborColor;

            if (neighbor.isLocked || neighbor.color != -1) {
                neighborColor = neighbor.color;
            } else {
                int idx = freeIds.indexOf(neighborId);
                if (idx == -1) continue;
                neighborColor = colorArray[idx];
            }

            if (neighborColor == color) return false;  // conflict
        }
        return true;  // safe
    }

    private boolean backtrack(int[] colorArray, int index) {
        // Base case
        if (index == n) return true;

        callDepth++;
        if (callDepth > maxDepthReached) maxDepthReached = callDepth;

        int regionId = freeIds.get(index);

        for (int c = 0; c < numColors; c++) {
            colorTrials++;

            if (isSafe(regionId, c, colorArray)) {
                colorArray[index] = c;

                if (backtrack(colorArray, index + 1)) {
                    callDepth--;
                    return true;
                }

                colorArray[index] = -1;
                backtrackCount++;

            } else {
                colorRejections++;
            }
        }

        callDepth--;
        return false;
    }

    private void printBanner() {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("  BACKTRACKING SOLVER - MAP COLORING");
        System.out.println("  Step 1 : isSafe(v, color) - check all neighbors");
        System.out.println("  Step 2 : solve(region) - try -> recurse -> undo");
        System.out.println("  Step 3 : mapColoring(G, k) - init + solve(0)");
        System.out.printf ("  Regions: %d free   |   Colors: %d   |   Worst case: O(k^n) = O(%d^%d)%n",
            n, numColors, numColors, n);
        System.out.println("-----------------------------------------------------------------");
    }

    private void printSolveStats() {
        System.out.printf("%n--- BACKTRACKING SOLVE STATISTICS ---------------------------%n");
        System.out.printf("  Free regions (n)          : %-6d%n", n);
        System.out.printf("  Colors (k)                : %-6d%n", numColors);
        System.out.printf("  Worst-case O(k^n)         : k=%d, n=%d%n", numColors, n);
        System.out.printf("  Color trials              : %-10d%n", colorTrials);
        System.out.printf("  isSafe rejections         : %-10d (%.1f%% pruned)%n",
            colorRejections,
            colorTrials == 0 ? 0.0 : 100.0 * colorRejections / colorTrials);
        System.out.printf("  Backtrack steps           : %-10d%n", backtrackCount);
        System.out.printf("  Max recursion depth       : %-10d%n", maxDepthReached);
        System.out.printf("  Wall-clock solve time     : %-6d ms%n", solveTimeMs);
        System.out.printf("  Solution found            : %-6s%n",
            solution != null || colorTrials > 0 ? "YES" : "checking...");
        System.out.printf("-------------------------------------------------------------%n");
    }

    public int  getColorTrials()      { return colorTrials; }
    public int  getColorRejections()  { return colorRejections; }
    public int  getBacktrackCount()   { return backtrackCount; }
    public int  getMaxDepth()         { return maxDepthReached; }
    public long getSolveTimeMs()      { return solveTimeMs; }
    public int  getFreeCount()        { return n; }
    public int  getNumColors()        { return numColors; }
    public List<int[]> getMoveSequence() { return moveSequence; }
}


// Auto Solver (coordinates the logic and the move sequence)
class AutoSolver {
    private final GameGraph         graph;
    private final BacktrackingSolver solver;

    private Map<Integer, Integer> fullSolution = null;
    private List<int[]>           moveSequence;
    private int                   moveIndex    = 0;
    private final List<String>    moveLog      = new ArrayList<>();

    public AutoSolver(GameGraph graph) {
        this.graph  = graph;
        this.solver = new BacktrackingSolver(graph);
    }

    public boolean initialize() {
        fullSolution = solver.solve();

        if (fullSolution == null) {
            System.out.println("[AUTO] Backtracking returned null - no solution exists.");
            return false;
        }

        moveSequence = solver.getMoveSequence();
        System.out.printf("%n[AUTO] %d moves queued for playback%n", moveSequence.size());
        System.out.println("-----------------------------------------------------------------");
        return true;
    }

    public int[] applyNextMove() {
        if (moveIndex >= moveSequence.size()) return null;

        int[] move  = moveSequence.get(moveIndex++);
        int rid     = move[0];
        int color   = move[1];
        graph.getRegions().get(rid).color = color;

        int neighborCount  = graph.getNeighbors(rid).size();
        int legalRemaining = graph.availableColors(rid).size();
        String log = String.format(
            "Move %3d/%3d | Region %2d -> Color %d | Adj regions: %d | Free colors after: %d | Conflict: %s",
            moveIndex, moveSequence.size(), rid, color + 1,
            neighborCount, legalRemaining,
            graph.inConflict(rid) ? "YES" : "none"
        );
        moveLog.add(log);
        System.out.println("[MOVE] " + log);
        return move;
    }

    public boolean isDone()          { return moveIndex >= moveSequence.size(); }
    public int     getMoveIndex()    { return moveIndex; }
    public int     getTotalMoves()   { return moveSequence.size(); }
    public List<String> getMoveLog() { return moveLog; }

    public boolean isPuzzleSolved() {
        for (Region r : graph.getRegions())
            if (r.color == -1 || graph.inConflict(r.id)) return false;
        return true;
    }

    public BacktrackingSolver getSolver() { return solver; }

    public void printFinalStats() {
        long conflicts = graph.getRegions().stream().filter(r -> graph.inConflict(r.id)).count();
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("  FINAL BACKTRACKING STATISTICS");
        System.out.println("-----------------------------------------------------------------");
        System.out.printf("  %-30s : %d%n", "Total regions",           graph.getRegions().size());
        System.out.printf("  %-30s : %d%n", "Locked (pre-colored)",    graph.getRegions().stream().filter(r->r.isLocked).count());
        System.out.printf("  %-30s : %d%n", "Free regions solved",     solver.getFreeCount());
        System.out.printf("  %-30s : %d%n", "Colors available (k)",    graph.getNumColors());
        System.out.printf("  %-30s : %d%n", "Total color trials",      solver.getColorTrials());
        System.out.printf("  %-30s : %d%n", "isSafe rejections",       solver.getColorRejections());
        System.out.printf("  %-30s : %.1f%%%n","Pruning efficiency",
            solver.getColorTrials()==0?0.0:100.0*solver.getColorRejections()/solver.getColorTrials());
        System.out.printf("  %-30s : %d%n", "Backtrack steps",         solver.getBacktrackCount());
        System.out.printf("  %-30s : %d%n", "Max recursion depth",     solver.getMaxDepth());
        System.out.printf("  %-30s : %d ms%n","Solve time",            solver.getSolveTimeMs());
        System.out.printf("  %-30s : %d%n", "Moves applied",           moveSequence.size());
        System.out.printf("  %-30s : %d%n", "Conflicts remaining",     conflicts);
        System.out.printf("  %-30s : %s%n", "Puzzle valid",            isPuzzleSolved() ? "YES" : "NO");
        System.out.println("-----------------------------------------------------------------");
    }
}

// GUI - auto-only, step delay, live log panel
class CooperativeGameGUI extends JFrame {
    private final GameGraph  graph;
    private final AutoSolver autoSolver;
    private final Cell[][]   grid;
    private final int        gridRows, gridCols, cellSize = 20;

    private int lastColoredRegion = -1;

    private JPanel    mapPanel;
    private JLabel    statusLabel, phaseLabel, moveLabel, statsLabel;
    private JButton   startBtn, pauseBtn, restartBtn;
    private JTextArea logArea;
    private Timer     stepTimer;
    private boolean   paused = false;
    private JSpinner  regionSpinner;

    private static final int STEP_DELAY_MS = 600;

    private final Color[] COLORS = {
        new Color(220, 30,  75),
        new Color(50,  175, 70),
        new Color(0,   120, 195),
        new Color(245, 130, 45),
    };

    public CooperativeGameGUI(int numRegions, int numColors, int gridRows, int gridCols) {
        mapgeneration gen = new mapgeneration(gridRows, gridCols);
        List<Region> regions = gen.generateRegions(numRegions);
        this.grid     = gen.getGrid();
        this.gridRows = gridRows;
        this.gridCols = gridCols;
        this.graph    = new GameGraph(regions, grid, gridRows, gridCols, numColors);
        lockInitialRegions();
        this.autoSolver = new AutoSolver(graph);
        buildGUI();
    }

    private void lockInitialRegions() {
        Random rnd = new Random();
        List<Region> regions = graph.getRegions();
        int nc = graph.getNumColors();
        int numToLock = Math.max(nc, regions.size() / 5);
        List<Integer> avail = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) avail.add(i);
        Collections.shuffle(avail);
        for (int color = 0; color < nc && !avail.isEmpty(); color++) {
            int rid = avail.remove(0); Region r = regions.get(rid);
            if (graph.availableColors(rid).contains(color)) { r.color = color; r.isLocked = true; }
        }
        while (!avail.isEmpty() && countLocked() < numToLock) {
            int rid = avail.remove(0); Region r = regions.get(rid);
            Set<Integer> ok = graph.availableColors(rid);
            if (!ok.isEmpty()) {
                int c = ok.toArray(new Integer[0])[rnd.nextInt(ok.size())];
                r.color = c; r.isLocked = true;
            }
        }
        System.out.println("\nLocked regions:");
        for (Region r : regions) if (r.isLocked) System.out.println("   Region " + r.id + " -> Color " + r.color);
    }

    private int countLocked() { int n=0; for (Region r:graph.getRegions()) if(r.isLocked) n++; return n; }

    private void buildGUI() {
        setTitle("Backtracking Auto-Solver - Map Coloring");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        // Top status bar
        JPanel top = new JPanel(new GridLayout(4, 1, 1, 1));
        top.setBackground(new Color(240, 240, 240));
        top.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        statusLabel = makeLabel("Press Start to run Backtracking Solver", 14, Font.BOLD, Color.BLACK);
        phaseLabel  = makeLabel("isSafe(v,c) -> color[v]=c -> solve(v+1) -> undo if fail", 12, Font.BOLD, new Color(0, 100, 200));
        moveLabel   = makeLabel("Move: 0 / -   |   Free regions: -   |   Backtracks: -", 11, Font.PLAIN, Color.BLACK);
        statsLabel  = makeLabel(statsText(), 11, Font.ITALIC, Color.DARK_GRAY);

        top.add(statusLabel); top.add(phaseLabel); top.add(moveLabel); top.add(statsLabel);
        add(top, BorderLayout.NORTH);

        // Map
        mapPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); drawMap(g); }
        };
        mapPanel.setPreferredSize(new Dimension(gridCols*cellSize+100, gridRows*cellSize+100));
        mapPanel.setBackground(Color.WHITE);
        add(new JScrollPane(mapPanel), BorderLayout.CENTER);

        // Right log panel
        logArea = new JTextArea(22, 44);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logArea.setBackground(Color.WHITE);
        logArea.setForeground(Color.BLACK);
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        logArea.setText("Backtracking Technical Log\n------------------------------------------\n");
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Backtracking Diagnostics & Move Log", 0, 0,
            new Font("Arial", Font.BOLD, 11), Color.BLACK));
        logScroll.setPreferredSize(new Dimension(430, 0));
        add(logScroll, BorderLayout.EAST);

        // Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));
        controls.setBackground(new Color(250, 250, 250));

        startBtn   = makeBtn("Start",   new Color(60, 140, 60));
        pauseBtn   = makeBtn("Pause",   new Color(180, 120, 20));
        restartBtn = makeBtn("Restart", new Color(160, 50, 50));
        pauseBtn.setEnabled(false);

        startBtn.addActionListener(e   -> onStart());
        pauseBtn.addActionListener(e   -> onPause());
        restartBtn.addActionListener(e -> onRestart());

        // Region count spinner
        JPanel spinnerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        spinnerPanel.setBackground(new Color(250, 250, 250));
        JLabel spinnerLabel = new JLabel("Regions:");
        spinnerLabel.setFont(new Font("Arial", Font.BOLD, 12));
        regionSpinner = new JSpinner(new SpinnerNumberModel(25, 5, 60, 1));
        regionSpinner.setPreferredSize(new Dimension(60, 28));
        regionSpinner.setFont(new Font("Arial", Font.PLAIN, 12));
        ((JSpinner.DefaultEditor) regionSpinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
        JLabel spinnerHint = new JLabel("(apply with Restart)");
        spinnerHint.setFont(new Font("Arial", Font.ITALIC, 11));
        spinnerHint.setForeground(Color.GRAY);
        spinnerPanel.add(spinnerLabel);
        spinnerPanel.add(regionSpinner);
        spinnerPanel.add(spinnerHint);

        // Legend
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        legend.setBackground(new Color(248, 248, 248));
        addSwatch(legend, COLORS[0], "Color 1");
        addSwatch(legend, COLORS[1], "Color 2");
        addSwatch(legend, COLORS[2], "Color 3");
        addSwatch(legend, COLORS[3], "Color 4");
        addSwatch(legend, new Color(218, 218, 218), "Uncolored");

        controls.add(startBtn); controls.add(pauseBtn); controls.add(restartBtn);
        JPanel south = new JPanel(new GridLayout(3, 1));
        south.setBackground(new Color(250, 250, 250));
        south.add(controls); south.add(spinnerPanel); south.add(legend);
        add(south, BorderLayout.SOUTH);

        // Step timer
        stepTimer = new Timer(STEP_DELAY_MS, e -> stepBot());
        stepTimer.setRepeats(true);

        pack(); setLocationRelativeTo(null);
    }

    private JButton makeBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setBorderPainted(false); b.setFont(new Font("Arial", Font.BOLD, 13));
        b.setPreferredSize(new Dimension(120, 36));
        return b;
    }

    private JLabel makeLabel(String text, int size, int style, Color fg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Arial", style, size)); l.setForeground(fg); return l;
    }

    private void addSwatch(JPanel p, Color c, String label) {
        JLabel sw = new JLabel("  " + label + "  ");
        sw.setOpaque(true); sw.setBackground(c);
        sw.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        sw.setFont(new Font("Arial", Font.BOLD, 10)); sw.setForeground(Color.BLACK);
        p.add(sw);
    }

    private void onStart() {
        startBtn.setEnabled(false);
        pauseBtn.setEnabled(true);
        statusLabel.setText("Running Backtracking - please wait...");
        phaseLabel.setText("[BT] Calling solve(0) -> trying colors -> backtracking on failure...");

        new Thread(() -> {
            boolean ok = autoSolver.initialize();
            SwingUtilities.invokeLater(() -> {
                if (!ok) {
                    statusLabel.setText("Backtracking found NO solution - no valid coloring exists.");
                    appendLog("ERROR: No solution - backtracking exhausted all possibilities.\n");
                    startBtn.setEnabled(true); pauseBtn.setEnabled(false); return;
                }
                appendSolveStats();
                BacktrackingSolver s = autoSolver.getSolver();
                phaseLabel.setText(String.format(
                    "[BT] Solution found! %d moves queued | %d trials | %d backtracks | %d ms",
                    autoSolver.getTotalMoves(), s.getColorTrials(),
                    s.getBacktrackCount(), s.getSolveTimeMs()));
                moveLabel.setText(String.format(
                    "Move: 0 / %d   |   Free regions: %d   |   Backtracks: %d",
                    autoSolver.getTotalMoves(), s.getFreeCount(), s.getBacktrackCount()));

                mapPanel.repaint();
                stepTimer.start();
            });
        }).start();
    }

    private void onPause() {
        if (!paused) {
            stepTimer.stop(); paused = true;
            pauseBtn.setText("Resume");
            statusLabel.setText("Paused - click Resume to continue");
        } else {
            stepTimer.start(); paused = false;
            pauseBtn.setText("Pause");
            statusLabel.setText("Resuming...");
        }
    }

    private void onRestart() {
        int newRegions = (Integer) regionSpinner.getValue();
        stepTimer.stop(); dispose();
        SwingUtilities.invokeLater(() -> new CooperativeGameGUI(newRegions, 4, 20, 25).setVisible(true));
    }

    private void stepBot() {
        if (autoSolver.isDone()) { stepTimer.stop(); lastColoredRegion=-1; onFinished(); return; }

        int[] move = autoSolver.applyNextMove();
        if (move == null) { stepTimer.stop(); onFinished(); return; }

        int rid   = move[0];
        int color = move[1];
        lastColoredRegion = rid;

        int idx   = autoSolver.getMoveIndex();
        int total = autoSolver.getTotalMoves();
        BacktrackingSolver s = autoSolver.getSolver();

        moveLabel.setText(String.format(
            "Move: %d / %d   |   Free: %d   |   Trials: %d   |   Backtracks: %d   |   Pruned: %d",
            idx, total, s.getFreeCount(), s.getColorTrials(),
            s.getBacktrackCount(), s.getColorRejections()));
        statusLabel.setText(String.format(
            "Region %d -> Color %d   |   %d regions remaining", rid, color+1, total-idx));
        phaseLabel.setText(String.format(
            "[BT] isSafe passed -> color[%d]=%d | Depth: %d | Solve time: %d ms | Pruned: %.1f%%",
            rid, color+1, s.getMaxDepth(), s.getSolveTimeMs(),
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials()));
        statsLabel.setText(statsText());

        List<String> log = autoSolver.getMoveLog();
        if (!log.isEmpty()) appendLog(log.get(log.size()-1) + "\n");

        mapPanel.repaint();
    }

    private void onFinished() {
        autoSolver.printFinalStats();
        appendFinalStats();

        boolean solved = autoSolver.isPuzzleSolved();
        statusLabel.setText(solved ? "Puzzle Solved! All regions colored - no conflicts." : "Finished with conflicts.");
        phaseLabel.setText("solve(n) returned true  |  all regions colored via isSafe+backtrack");
        statsLabel.setText(statsText());
        startBtn.setEnabled(false); pauseBtn.setEnabled(false);
        mapPanel.repaint();

        if (solved) {
            BacktrackingSolver s = autoSolver.getSolver();
            JOptionPane.showMessageDialog(this,
                "Backtracking Solved the Map!\n\n"
                + "All regions legally colored - zero conflicts\n\n"
                + String.format("  Free regions (n)      : %d%n", s.getFreeCount())
                + String.format("  Colors (k)            : %d%n", s.getNumColors())
                + String.format("  Worst case O(k^n)     : k=%d, n=%d%n", s.getNumColors(), s.getFreeCount())
                + String.format("  Color trials          : %d%n", s.getColorTrials())
                + String.format("  isSafe rejections     : %d  (%.1f%% pruned)%n",
                    s.getColorRejections(),
                    s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials())
                + String.format("  Backtrack steps       : %d%n", s.getBacktrackCount())
                + String.format("  Max recursion depth   : %d%n", s.getMaxDepth())
                + String.format("  Solve time            : %d ms%n", s.getSolveTimeMs()),
                "Victory!", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void appendLog(String text) {
        logArea.append(text);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void appendSolveStats() {
        BacktrackingSolver s = autoSolver.getSolver();
        appendLog(String.format(
            "\n--- BACKTRACKING SOLVE STATS ----------------\n" +
            "  Free regions (n)       : %d\n" +
            "  Colors (k)             : %d\n" +
            "  Color trials           : %d\n" +
            "  isSafe rejections      : %d  (%.1f%% pruned)\n" +
            "  Backtrack steps        : %d\n" +
            "  Max recursion depth    : %d\n" +
            "  Solve time             : %d ms\n" +
            "---------------------------------------------\n",
            s.getFreeCount(), s.getNumColors(),
            s.getColorTrials(), s.getColorRejections(),
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials(),
            s.getBacktrackCount(), s.getMaxDepth(), s.getSolveTimeMs()
        ));
    }

    private void appendFinalStats() {
        BacktrackingSolver s = autoSolver.getSolver();
        long conflicts = graph.getRegions().stream().filter(r -> graph.inConflict(r.id)).count();
        appendLog(String.format(
            "\n--- FINAL STATS ------------------------------\n" +
            "  Total moves applied    : %d\n" +
            "  Conflicts remaining    : %d\n" +
            "  Pruning efficiency     : %.1f%%\n" +
            "  Puzzle solved          : %s\n" +
            "----------------------------------------------\n",
            autoSolver.getTotalMoves(), conflicts,
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials(),
            autoSolver.isPuzzleSolved() ? "YES" : "NO"
        ));
    }

    private String statsText() {
        int total=graph.getRegions().size(), locked=countLocked(), colored=0, conflicts=0;
        for (Region r : graph.getRegions()) { if(r.color!=-1){colored++; if(graph.inConflict(r.id)) conflicts++;} }
        BacktrackingSolver s = autoSolver.getSolver();
        return String.format(
            "Colored: %d/%d | Locked: %d | Conflicts: %d | Trials: %d | Backtracks: %d | Pruned: %.0f%% | Time: %dms",
            colored, total, locked, conflicts,
            s.getColorTrials(), s.getBacktrackCount(),
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials(),
            s.getSolveTimeMs());
    }

    private void drawMap(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int ox = 50, oy = 50;

        for (Region r : graph.getRegions()) {
            Color fill = r.color == -1 ? new Color(218, 218, 218) : COLORS[r.color];
            if (r.id == lastColoredRegion) fill = fill.brighter();
            for (Cell c : r.cells) {
                g2.setColor(fill);
                g2.fillRect(ox + c.col*cellSize, oy + c.row*cellSize, cellSize, cellSize);
            }
        }

        // Highlight last colored region border
        if (lastColoredRegion >= 0) {
            g2.setColor(new Color(255, 255, 100, 200)); g2.setStroke(new BasicStroke(3));
            for (Cell c : graph.getRegions().get(lastColoredRegion).cells)
                g2.drawRect(ox+c.col*cellSize+1, oy+c.row*cellSize+1, cellSize-2, cellSize-2);
        }

        // Region borders
        g2.setColor(Color.BLACK); g2.setStroke(new BasicStroke(2));
        for (int row = 0; row < gridRows; row++)
            for (int col = 0; col < gridCols; col++) {
                Cell cell = grid[row][col];
                int x = ox+col*cellSize, y = oy+row*cellSize;
                if (col<gridCols-1 && grid[row][col+1].regionId!=cell.regionId) g2.drawLine(x+cellSize,y,x+cellSize,y+cellSize);
                if (row<gridRows-1 && grid[row+1][col].regionId!=cell.regionId) g2.drawLine(x,y+cellSize,x+cellSize,y+cellSize);
            }

        // Labels
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        for (Region r : graph.getRegions()) {
            Point cen = r.getCentroid();
            int x = ox+cen.x*cellSize+cellSize/2, y = oy+cen.y*cellSize+cellSize/2;
            if (r.isLocked) {
                g2.setFont(new Font("Arial", Font.BOLD, 13)); g2.setColor(Color.WHITE);
                g2.drawString("L", x-5, y+5); g2.setFont(new Font("Arial", Font.BOLD, 10));
            } else if (r.color == -1) {
                g2.setColor(Color.DARK_GRAY); g2.drawString(String.valueOf(r.id), x-5, y+5);
            } else if (graph.inConflict(r.id)) {
                g2.setColor(Color.RED); g2.setFont(new Font("Arial", Font.BOLD, 15));
                g2.drawString("X", x-5, y+5); g2.setFont(new Font("Arial", Font.BOLD, 10));
            } else if (r.id == lastColoredRegion) {
                g2.setColor(new Color(140, 0, 210)); g2.setFont(new Font("Arial", Font.BOLD, 13));
                g2.drawString("*", x-3, y+5); g2.setFont(new Font("Arial", Font.BOLD, 10));
            }
        }
    }
}

public class backtrack {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("-------------------------------------------------------------");
            System.out.println("  MAP COLORING - BACKTRACKING AUTO-SOLVER");
            System.out.println("  Step 1 : isSafe(v, color) - check neighbor conflicts");
            System.out.println("  Step 2 : solve(region) - try -> recurse -> undo (BT)");
            System.out.println("  Step 3 : mapColoring(G, k) - init + solve(0)");
            System.out.println("  Time complexity : O(k^n) worst case");
            System.out.println("-------------------------------------------------------------");
            new CooperativeGameGUI(25, 4, 20, 25).setVisible(true);
        });
    }
}
