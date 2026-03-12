package eval35;

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

// Bitmask DP Solver
class BitmaskDPSolver {

    private final GameGraph graph;
    private final int       numColors;

    private List<Integer> freeIds;
    private int           n;

    private boolean[] dp;
    private int[]     parent;
    private int[]     chosenRegion;
    private int[]     chosenColor;

    private Map<Integer,Integer> solution = null;

    public Set<Integer> lastPartitionA      = new HashSet<>();
    public Set<Integer> lastPartitionB      = new HashSet<>();
    public Set<Integer> lastBoundaryRegions = new HashSet<>();

    // Stats
    private int  statesExplored   = 0;
    private int  statesSkipped    = 0;
    private int  colorTrials      = 0;
    private int  colorRejections  = 0;
    private long solveTimeMs      = 0;
    private int  reconstructSteps = 0;

    private final Map<Integer, int[]> maskColorCache = new HashMap<>();

    public BitmaskDPSolver(GameGraph graph) {
        this.graph     = graph;
        this.numColors = graph.getNumColors();
    }

    public Map<Integer, Integer> solve() {
        solution        = null;
        statesExplored  = 0;
        statesSkipped   = 0;
        colorTrials     = 0;
        colorRejections = 0;
        reconstructSteps= 0;
        maskColorCache.clear();

        long t0 = System.currentTimeMillis();
        List<Region> regions = graph.getRegions();

        // Pre-check
        for (Region r : regions) {
            if (r.color == -1) continue;
            for (int nbId : graph.getNeighbors(r.id)) {
                if (regions.get(nbId).color == r.color) {
                    System.out.printf("[DP] PRE-CHECK FAIL: Region %d conflicts Region %d%n", r.id, nbId);
                    solveTimeMs = System.currentTimeMillis() - t0;
                    return null;
                }
            }
        }

        // Collect free regions
        freeIds = new ArrayList<>();
        for (Region r : regions)
            if (!r.isLocked && r.color == -1) freeIds.add(r.id);
        n = freeIds.size();

        if (n == 0) { solveTimeMs = System.currentTimeMillis() - t0; return buildFullAssignment(); }

        if (n > 26) {
            System.out.printf("[DP] n=%d > 26 - fallback to backtracking%n", n);
            solveTimeMs = System.currentTimeMillis() - t0;
            return fallbackBacktrack();
        }

        int size = 1 << n;
        dp           = new boolean[size];
        parent       = new int[size];
        chosenRegion = new int[size];
        chosenColor  = new int[size];
        Arrays.fill(parent,       -1);
        Arrays.fill(chosenRegion, -1);
        Arrays.fill(chosenColor,  -1);

        dp[0] = true;

        for (int mask = 0; mask < size; mask++) {
            if (!dp[mask]) continue;
            statesExplored++;

            int bestBit = -1, bestLegal = Integer.MAX_VALUE;
            for (int bit = 0; bit < n; bit++) {
                if ((mask & (1 << bit)) != 0) continue;
                int legal = countLegalColors(bit, mask);
                if (legal < bestLegal) { bestLegal = legal; bestBit = bit; }
            }
            if (bestBit == -1) continue;

            int bit = bestBit;
            for (int color = 0; color < numColors; color++) {
                colorTrials++;
                if (!isColorValid(bit, color, mask)) { colorRejections++; continue; }

                int newMask = mask | (1 << bit);
                if (!dp[newMask]) {
                    dp[newMask]           = true;
                    parent[newMask]       = mask;
                    chosenRegion[newMask] = bit;
                    chosenColor[newMask]  = color;
                } else {
                    statesSkipped++;
                }
            }
        }

        int fullMask = size - 1;
        solveTimeMs = System.currentTimeMillis() - t0;

        if (!dp[fullMask]) return null;

        int[] assignment = new int[n];
        Arrays.fill(assignment, -1);
        int cur = fullMask;
        while (cur != 0) {
            reconstructSteps++;
            assignment[chosenRegion[cur]] = chosenColor[cur];
            cur = parent[cur];
        }

        for (int bit = 0; bit < n; bit++) {
            if (assignment[bit] == -1) {
                System.out.printf("[DP] Reconstruction error: bit %d unassigned!%n", bit);
                return null;
            }
        }

        computePartitions();
        solution = buildFullAssignment();
        for (int bit = 0; bit < n; bit++)
            solution.put(freeIds.get(bit), assignment[bit]);

        return solution;
    }


    private int[] getPartialColors(int mask) {
        if (maskColorCache.containsKey(mask)) return maskColorCache.get(mask);
        int[] colors = new int[n];
        Arrays.fill(colors, -1);
        int cur = mask;
        while (cur != 0 && parent[cur] != -1) {
            colors[chosenRegion[cur]] = chosenColor[cur];
            cur = parent[cur];
        }
        maskColorCache.put(mask, colors);
        return colors;
    }

    private boolean isColorValid(int bit, int color, int mask) {
        int regionId = freeIds.get(bit);
        int[] partial = getPartialColors(mask);
        for (int nbId : graph.getNeighbors(regionId)) {
            Region nb = graph.getRegions().get(nbId);
            int nbColor;
            if (nb.isLocked || nb.color != -1) {
                nbColor = nb.color;
            } else {
                int nbBit = freeIds.indexOf(nbId);
                if (nbBit == -1 || (mask & (1 << nbBit)) == 0) continue;
                nbColor = partial[nbBit];
            }
            if (nbColor == color) return false;
        }
        return true;
    }

    private int countLegalColors(int bit, int mask) {
        int count = 0;
        for (int c = 0; c < numColors; c++) if (isColorValid(bit, c, mask)) count++;
        return count;
    }

    private Map<Integer, Integer> buildFullAssignment() {
        Map<Integer, Integer> map = new HashMap<>();
        for (Region r : graph.getRegions()) map.put(r.id, r.color);
        return map;
    }

    private void computePartitions() {
        lastPartitionA = new HashSet<>(); lastPartitionB = new HashSet<>(); lastBoundaryRegions = new HashSet<>();
        if (freeIds.isEmpty()) return;
        int half = freeIds.size() / 2;
        for (int i = 0; i < freeIds.size(); i++) {
            if (i < half) lastPartitionA.add(freeIds.get(i));
            else          lastPartitionB.add(freeIds.get(i));
        }
    }

    private Map<Integer, Integer> fallbackBacktrack() {
        Map<Integer, Integer> assignment = new HashMap<>();
        for (Region r : graph.getRegions()) assignment.put(r.id, r.color);
        List<Integer> free = new ArrayList<>(freeIds);
        if (!backtrack(free, 0, assignment)) return null;
        return assignment;
    }

    private boolean backtrack(List<Integer> ids, int index, Map<Integer, Integer> assignment) {
        if (index == ids.size()) return isFullyValid(assignment);
        int bestIdx = index, bestCount = Integer.MAX_VALUE;
        for (int i = index; i < ids.size(); i++) {
            int cnt = legalColorsFromMap(ids.get(i), assignment).size();
            if (cnt < bestCount) { bestCount = cnt; bestIdx = i; }
        }
        Collections.swap(ids, index, bestIdx);
        int rid = ids.get(index);
        for (int color : legalColorsFromMap(rid, assignment)) {
            assignment.put(rid, color);
            if (backtrack(ids, index + 1, assignment)) return true;
            assignment.put(rid, -1);
        }
        Collections.swap(ids, index, bestIdx);
        return false;
    }

    private List<Integer> legalColorsFromMap(int rid, Map<Integer,Integer> assignment) {
        Set<Integer> used = new HashSet<>();
        for (int nb : graph.getNeighbors(rid)) { int c = assignment.getOrDefault(nb,-1); if (c!=-1) used.add(c); }
        List<Integer> legal = new ArrayList<>();
        for (int i = 0; i < numColors; i++) if (!used.contains(i)) legal.add(i);
        return legal;
    }

    private boolean isFullyValid(Map<Integer, Integer> assignment) {
        for (Region r : graph.getRegions()) {
            int c = assignment.getOrDefault(r.id, -1);
            if (c == -1) return false;
            for (int nb : graph.getNeighbors(r.id)) if (assignment.getOrDefault(nb,-1) == c) return false;
        }
        return true;
    }

    public int findSimpleLocalColor(int rid) {
        Set<Integer> used = new HashSet<>();
        for (int nb : graph.getNeighbors(rid)) { int c = graph.getRegions().get(nb).color; if (c!=-1) used.add(c); }
        for (int i = 0; i < numColors; i++) if (!used.contains(i)) return i;
        return -1;
    }

    public int  getStatesExplored()   { return statesExplored; }
    public int  getStatesSkipped()    { return statesSkipped; }
    public int  getColorTrials()      { return colorTrials; }
    public int  getColorRejections()  { return colorRejections; }
    public long getSolveTimeMs()      { return solveTimeMs; }
    public int  getReconstructSteps() { return reconstructSteps; }
    public int  getFreeCount()        { return n; }
    public int  getTotalStates()      { return n <= 26 ? (1 << n) : -1; }
    public int  getDpTableSize()      { return statesExplored; }
}

// Auto Solver
class AutoSolver {
    private final GameGraph       graph;
    private final BitmaskDPSolver solver;

    private Map<Integer, Integer> fullSolution  = null;
    private final List<int[]>     moveSequence  = new ArrayList<>();
    private int                   moveIndex     = 0;
    private final List<String>    moveLog       = new ArrayList<>();

    public AutoSolver(GameGraph graph) {
        this.graph  = graph;
        this.solver = new BitmaskDPSolver(graph);
    }

    public boolean initialize() {
        printBanner();
        long t0 = System.currentTimeMillis();
        fullSolution = solver.solve();
        long elapsed = System.currentTimeMillis() - t0;
        printSolveStats(elapsed);

        if (fullSolution == null) {
            System.out.println("[AUTO] DP returned null - unsolvable.");
            return false;
        }

        // Build move list
        for (Region r : graph.getRegions()) {
            if (!r.isLocked) {
                Integer c = fullSolution.get(r.id);
                if (c != null && c != -1) moveSequence.add(new int[]{ r.id, c });
            }
        }

        System.out.printf("%n[AUTO] %d moves queued for playback%n", moveSequence.size());
        System.out.println("-----------------------------------------------------------------");
        return true;
    }

    private void printBanner() {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("  BITMASK DP AUTO-SOLVER");
        System.out.println("  State   : dp[mask]  (bitmask over free regions)");
        System.out.println("  Base    : dp[0] = true");
        System.out.println("  Trans   : dp[mask] -> dp[mask|(1<<v)] for valid color v");
        System.out.println("  Goal    : dp[(1<<n)-1] = true");
        System.out.println("  Heurist : MRV - color region with fewest legal choices first");
        System.out.println("-----------------------------------------------------------------");
    }

    private void printSolveStats(long elapsed) {
        int n     = solver.getFreeCount();
        int total = solver.getTotalStates();
        System.out.printf("%n--- DP SOLVE STATISTICS -------------------------------------%n");
        System.out.printf("  Free regions (n)          : %-6d%n", n);
        System.out.printf("  State space size (2^n)    : %-12s%n",
            total < 0 ? "fallback" : String.valueOf(total));
        System.out.printf("  DP states explored        : %-10d%n", solver.getStatesExplored());
        System.out.printf("  States deduped (skipped)  : %-10d%n", solver.getStatesSkipped());
        System.out.printf("  Color (region,col) trials : %-10d%n", solver.getColorTrials());
        System.out.printf("  Color rejections          : %-10d (%.1f%% pruned)%n",
            solver.getColorRejections(),
            solver.getColorTrials()==0 ? 0.0 : 100.0*solver.getColorRejections()/solver.getColorTrials());
        System.out.printf("  Reconstruction steps      : %-10d%n", solver.getReconstructSteps());
        System.out.printf("  Wall-clock solve time     : %-6d ms%n", elapsed);
        System.out.printf("  Solution found            : %-6s%n",
            fullSolution != null ? "YES" : "NO");
        System.out.printf("-------------------------------------------------------------%n");
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

    public BitmaskDPSolver getSolver() { return solver; }

    public void printFinalStats() {
        long conflicts = graph.getRegions().stream().filter(r -> graph.inConflict(r.id)).count();
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("  FINAL COMPARISON STATISTICS");
        System.out.println("-----------------------------------------------------------------");
        System.out.printf("  %-30s : %d%n", "Total regions",            graph.getRegions().size());
        System.out.printf("  %-30s : %d%n", "Locked (pre-colored)",     graph.getRegions().stream().filter(r->r.isLocked).count());
        System.out.printf("  %-30s : %d%n", "Free regions solved by DP",solver.getFreeCount());
        System.out.printf("  %-30s : %d%n", "Colors available (k)",     graph.getNumColors());
        System.out.printf("  %-30s : %d / %s%n","DP states explored / 2^n",
            solver.getStatesExplored(),
            solver.getTotalStates() < 0 ? "fallback" : String.valueOf(solver.getTotalStates()));
        System.out.printf("  %-30s : %d%n", "State dedup saves",        solver.getStatesSkipped());
        System.out.printf("  %-30s : %d%n", "Total color trials",       solver.getColorTrials());
        System.out.printf("  %-30s : %d%n", "Color rejections (pruned)",solver.getColorRejections());
        System.out.printf("  %-30s : %.1f%%%n","MRV pruning efficiency",
            solver.getColorTrials()==0 ? 0.0 : 100.0*solver.getColorRejections()/solver.getColorTrials());
        System.out.printf("  %-30s : %d%n", "Reconstruction steps",    solver.getReconstructSteps());
        System.out.printf("  %-30s : %d ms%n","DP solve time",          solver.getSolveTimeMs());
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

    private int          lastColoredRegion = -1;
    private Set<Integer> hlA = new HashSet<>(), hlB = new HashSet<>();
    private boolean      showOverlay = false;

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
    private static final Color TINT_A    = new Color(255, 220, 60,  80);
    private static final Color TINT_B    = new Color(150, 80,  255, 80);

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
            if (!ok.isEmpty()) { int c = ok.toArray(new Integer[0])[rnd.nextInt(ok.size())]; r.color = c; r.isLocked = true; }
        }
        System.out.println("\nLocked regions:");
        for (Region r : regions) if (r.isLocked) System.out.println("   Region " + r.id + " -> Color " + r.color);
    }

    private int countLocked() { int n=0; for (Region r:graph.getRegions()) if(r.isLocked) n++; return n; }

    private void buildGUI() {
        setTitle("Bitmask DP Auto-Solver - Map Coloring");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        // Top status bar
        JPanel top = new JPanel(new GridLayout(4, 1, 1, 1));
        top.setBackground(new Color(240, 240, 240));
        top.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        statusLabel = makeLabel("Press Start to run Bitmask DP Auto-Solver", 14, Font.BOLD, Color.BLACK);
        phaseLabel  = makeLabel("dp[0]=true -> dp[mask|(1<<v)] -> dp[(1<<n)-1]", 12, Font.BOLD, new Color(0, 100, 200));
        moveLabel   = makeLabel("Move: 0 / -   |   Free regions: -   |   States: -", 11, Font.PLAIN, Color.BLACK);
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
        logArea.setText("Bitmask DP Technical Log\n------------------------------------------\n");
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "DP Diagnostics & Move Log", 0, 0,
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
        addSwatch(legend, TINT_A, "Partition A (first half)");
        addSwatch(legend, TINT_B, "Partition B (second half)");
        addSwatch(legend, new Color(255,255,120,160), "Last colored");

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
        statusLabel.setText("Running Bitmask DP - please wait...");
        phaseLabel.setText("[DP] Iterating masks 0 -> 2^n-1, MRV ordering, building parent[]...");

        new Thread(() -> {
            boolean ok = autoSolver.initialize();
            SwingUtilities.invokeLater(() -> {
                if (!ok) {
                    statusLabel.setText("DP found NO solution - locked regions may conflict.");
                    appendLog("ERROR: DP unsolvable.\n");
                    startBtn.setEnabled(true); pauseBtn.setEnabled(false); return;
                }
                appendSolveStats();
                BitmaskDPSolver s = autoSolver.getSolver();
                phaseLabel.setText(String.format(
                    "[DP] Solution found! %d moves queued | explored %d / %d states | %d ms",
                    autoSolver.getTotalMoves(), s.getStatesExplored(),
                    s.getTotalStates(), s.getSolveTimeMs()));
                moveLabel.setText(String.format(
                    "Move: 0 / %d   |   Free regions: %d   |   2^n states: %s",
                    autoSolver.getTotalMoves(), s.getFreeCount(),
                    s.getTotalStates() < 0 ? "fallback" : String.valueOf(s.getTotalStates())));

                hlA = s.lastPartitionA; hlB = s.lastPartitionB;
                showOverlay = true;
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
        if (autoSolver.isDone()) { stepTimer.stop(); lastColoredRegion=-1; showOverlay=false; onFinished(); return; }

        int[] move = autoSolver.applyNextMove();
        if (move == null) { stepTimer.stop(); onFinished(); return; }

        int rid   = move[0];
        int color = move[1];
        lastColoredRegion = rid;

        int idx   = autoSolver.getMoveIndex();
        int total = autoSolver.getTotalMoves();
        BitmaskDPSolver s = autoSolver.getSolver();

        moveLabel.setText(String.format(
            "Move: %d / %d   |   Free: %d   |   States: %d   |   Trials: %d   |   Rejects: %d",
            idx, total, s.getFreeCount(), s.getStatesExplored(), s.getColorTrials(), s.getColorRejections()));
        statusLabel.setText(String.format(
            "Region %d -> Color %d   |   %d regions remaining", rid, color+1, total-idx));
        phaseLabel.setText(String.format(
            "[DP] Reconstructed from parent[] chain | Solve time: %d ms | MRV pruned: %.1f%%",
            s.getSolveTimeMs(),
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
        phaseLabel.setText("dp[(1<<n)-1] = true  |  parent[] reconstructed  |  all moves applied");
        statsLabel.setText(statsText());
        startBtn.setEnabled(false); pauseBtn.setEnabled(false);
        mapPanel.repaint();

        if (solved) {
            BitmaskDPSolver s = autoSolver.getSolver();
            JOptionPane.showMessageDialog(this,
                "Bitmask DP Solved the Map!\n\n"
                + "All regions legally colored - zero conflicts\n\n"
                + String.format("  Free regions (n)     : %d%n", s.getFreeCount())
                + String.format("  State space (2^n)    : %s%n", s.getTotalStates()<0?"fallback":String.valueOf(s.getTotalStates()))
                + String.format("  States explored      : %d%n", s.getStatesExplored())
                + String.format("  Color trials         : %d%n", s.getColorTrials())
                + String.format("  Rejections (MRV)     : %d  (%.1f%% pruned)%n",
                    s.getColorRejections(),
                    s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials())
                + String.format("  Reconstruction steps : %d%n", s.getReconstructSteps())
                + String.format("  Solve time           : %d ms%n", s.getSolveTimeMs()),
                "Victory!", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void appendLog(String text) {
        logArea.append(text);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void appendSolveStats() {
        BitmaskDPSolver s = autoSolver.getSolver();
        appendLog(String.format(
            "\n--- DP SOLVE STATS --------------------------\n" +
            "  Free regions (n)       : %d\n" +
            "  State space (2^n)      : %s\n" +
            "  States explored        : %d\n" +
            "  States deduped/skipped : %d\n" +
            "  Color trials           : %d\n" +
            "  Color rejections       : %d  (%.1f%% pruned)\n" +
            "  Reconstruction steps   : %d\n" +
            "  Solve time             : %d ms\n" +
            "---------------------------------------------\n",
            s.getFreeCount(),
            s.getTotalStates()<0?"fallback":String.valueOf(s.getTotalStates()),
            s.getStatesExplored(), s.getStatesSkipped(),
            s.getColorTrials(), s.getColorRejections(),
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials(),
            s.getReconstructSteps(), s.getSolveTimeMs()
        ));
    }

    private void appendFinalStats() {
        BitmaskDPSolver s = autoSolver.getSolver();
        long conflicts = graph.getRegions().stream().filter(r -> graph.inConflict(r.id)).count();
        appendLog(String.format(
            "\n--- FINAL STATS ------------------------------\n" +
            "  Total moves applied    : %d\n" +
            "  Conflicts remaining    : %d\n" +
            "  MRV pruning efficiency : %.1f%%\n" +
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
        BitmaskDPSolver s = autoSolver.getSolver();
        return String.format(
            "Colored: %d/%d | Locked: %d | Conflicts: %d | Explored: %d | Trials: %d | Pruned: %.0f%% | Time: %dms",
            colored, total, locked, conflicts,
            s.getStatesExplored(), s.getColorTrials(),
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

        if (showOverlay) {
            for (Region r : graph.getRegions()) {
                Color tint = hlA.contains(r.id) ? TINT_A : hlB.contains(r.id) ? TINT_B : null;
                if (tint != null) {
                    g2.setColor(tint);
                    for (Cell c : r.cells) g2.fillRect(ox+c.col*cellSize, oy+c.row*cellSize, cellSize, cellSize);
                }
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

public class map_dp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("-------------------------------------------------------------");
            System.out.println("  MAP COLORING - BITMASK DP AUTO-SOLVER");
            System.out.println("  State : dp[mask]  |  Base  : dp[0] = true");
            System.out.println("  Trans : dp[mask|(1<<v)]  |  Goal : dp[(1<<n)-1]");
            System.out.println("  MRV heuristic  +  parent[] reconstruction");
            System.out.println("-------------------------------------------------------------");
            new CooperativeGameGUI(25, 4, 20, 25).setVisible(true);
        });
    }
}
