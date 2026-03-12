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

