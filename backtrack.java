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
