package eval32;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;

class Cell {
    int row, col, regionId = -1;
    public Cell(int row, int col) { this.row = row; this.col = col; }
}

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

// map generation
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

    public Cell[][] getGrid(){ 
        return grid; 
    }
    public int getGridRows(){ 
        return gridRows; 
    }
    public int getGridCols(){ 
        return gridCols; 
    }
}

// graph with uncoloured regions
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

    public List<Region>getRegions(){
        return regions;
    }
    public Set<Integer>getNeighbors(int rid){
        return adj.get(rid);
    }
    public int getNumColors(){
        return numColors; 
    }
}

// DivideAndConquerBot
class DivideAndConquerBot {

    static final int BASE_SIZE = 6;
    GameGraph graph;
    int numColors;
    public Set<Integer> lastPartitionA      = new HashSet<>();
    public Set<Integer> lastPartitionB      = new HashSet<>();
    public Set<Integer> lastBoundaryRegions = new HashSet<>();

    private int  statesExplored   = 0;  // counts backtrack calls (base-case entries)
    private int  statesSkipped    = 0;  // counts seam regions skipped after merge fix
    private int  colorTrials      = 0;  // total (region, color) pairs tried
    private int  colorRejections  = 0;  // pairs rejected by adjacency constraint
    private long solveTimeMs      = 0;  // time(ms)
    private int  reconstructSteps = 0;  // counts divide/merge steps
    private int  freeCount        = 0;

    public DivideAndConquerBot(GameGraph graph) {
        this.graph     = graph;
        this.numColors = graph.getNumColors();
    }

    public Map<Integer, Integer> solve() {
        statesExplored  = 0;
        statesSkipped   = 0;
        colorTrials     = 0;
        colorRejections = 0;
        reconstructSteps= 0;

        long t0 = System.currentTimeMillis();
        List<Region> regions = graph.getRegions();
        Map<Integer, Integer> assignment = new HashMap<>();
        for (Region r : regions) assignment.put(r.id, r.color);
        for (Region r : regions) {
            if (r.color == -1) continue;
            for (int n : graph.getNeighbors(r.id)) {
                if (regions.get(n).color == r.color) {
                    System.out.println("Constraint check failed: Region " + r.id + " conflicts with " + n);
                    solveTimeMs = System.currentTimeMillis() - t0;
                    return null;
                }
            }
        }

        List<Integer> free = new ArrayList<>();
        for (Region r : regions)
            if (!r.isLocked && r.color == -1) free.add(r.id);

        freeCount = free.size();
        if (free.isEmpty()) {
            solveTimeMs = System.currentTimeMillis() - t0;
            return isFullyValid(assignment) ? assignment : null;
        }

        lastPartitionA      = new HashSet<>();
        lastPartitionB      = new HashSet<>();
        lastBoundaryRegions = new HashSet<>();
        printBanner();
        System.out.println("Starting D&C solver on " + free.size() + " regions...");
        boolean ok = dcSolve(free, assignment, 0);
        solveTimeMs = System.currentTimeMillis() - t0;
        printStats();
        if (!ok || !isFullyValid(assignment)) return null;
        return assignment;
    }

    private boolean dcSolve(List<Integer> free, Map<Integer, Integer> assignment, int depth) {
        if (free.size() <= BASE_SIZE) {
            statesExplored++;
            return backtrack(new ArrayList<>(free), 0, assignment);
        }

        reconstructSteps++; // counts a divide step

        List<Integer>[] parts = graphBisect(free);
        List<Integer> left  = parts[0];
        List<Integer> right = parts[1];

        if (left.isEmpty() || right.isEmpty()) {
            int mid = free.size() / 2;
            left  = new ArrayList<>(free.subList(0, mid));
            right = new ArrayList<>(free.subList(mid, free.size()));
        }

        if (depth == 0) {
            lastPartitionA = new HashSet<>(left);
            lastPartitionB = new HashSet<>(right);
        }

        if (!dcSolve(left,  assignment, depth + 1)) return false;
        if (!dcSolve(right, assignment, depth + 1)) return false;
        Set<Integer> seam = findSeamConflicts(left, right, assignment);
        lastBoundaryRegions.addAll(seam);
        if (!seam.isEmpty()) {
            reconstructSteps++; // counts a merge step
            List<Integer> seamList = new ArrayList<>(seam);
            for (int rid : seamList) assignment.put(rid, -1);
            statesSkipped += seamList.size();
            if (!backtrack(seamList, 0, assignment)) return false;
        }

        return true;
    }

    private List<Integer>[] graphBisect(List<Integer> free) {
        Set<Integer> freeSet = new HashSet<>(free);

        java.util.function.Function<Integer, Map<Integer, Integer>> bfsDist = (src) -> {
            Map<Integer, Integer> dist = new HashMap<>();
            Queue<Integer> q = new LinkedList<>();
            dist.put(src, 0); q.add(src);
            while (!q.isEmpty()) {
                int cur = q.poll();
                for (int nb : graph.getNeighbors(cur)) {
                    if (freeSet.contains(nb) && !dist.containsKey(nb)) {
                        dist.put(nb, dist.get(cur) + 1); q.add(nb);
                    }
                }
            }
            return dist;
        };

        int seedA = free.get(0);
        Map<Integer, Integer> distFromSeed = bfsDist.apply(seedA);
        int nodeA = seedA, maxD = -1;
        for (Map.Entry<Integer, Integer> e : distFromSeed.entrySet())
            if (e.getValue() > maxD) { maxD = e.getValue(); nodeA = e.getKey(); }
        Map<Integer, Integer> distFromA = bfsDist.apply(nodeA);
        int nodeB = free.get(free.size() / 2); maxD = -1;
        for (Map.Entry<Integer, Integer> e : distFromA.entrySet())
            if (!e.getKey().equals(nodeA) && e.getValue() > maxD) { maxD = e.getValue(); nodeB = e.getKey(); }
        Map<Integer, Integer> distFromB = bfsDist.apply(nodeB);
        List<Integer> partA = new ArrayList<>(), partB = new ArrayList<>();
        for (int rid : free) {
            int dA = distFromA.getOrDefault(rid, Integer.MAX_VALUE / 2);
            int dB = distFromB.getOrDefault(rid, Integer.MAX_VALUE / 2);
            if (dA <= dB) partA.add(rid); else partB.add(rid);
        }
        return new List[]{ partA, partB };
    }

    // Seam detection 
    private Set<Integer> findSeamConflicts(List<Integer> left, List<Integer> right, Map<Integer, Integer> assignment) {
        Set<Integer> rightSet = new HashSet<>(right);
        Set<Integer> result   = new HashSet<>();
        for (int rid : left) {
            int cl = assignment.getOrDefault(rid, -1);
            if (cl == -1) continue;
            for (int n : graph.getNeighbors(rid))
                if (rightSet.contains(n) && assignment.getOrDefault(n, -1) == cl) {
                    result.add(rid); result.add(n);
                }
        }
        return result;
    }

    // Backtracking with MRV  
    private boolean backtrack(List<Integer> ids, int index, Map<Integer, Integer> assignment) {
        if (index == ids.size()) return true;

        int bestIdx = index, bestCount = Integer.MAX_VALUE;
        for (int i = index; i < ids.size(); i++) {
            int cnt = legalColors(ids.get(i), assignment).size();
            if (cnt < bestCount) { bestCount = cnt; bestIdx = i; }
        }
        Collections.swap(ids, index, bestIdx);
        int rid = ids.get(index);

        for (int color : legalColors(rid, assignment)) {
            colorTrials++;
            assignment.put(rid, color);
            if (backtrack(ids, index + 1, assignment)) return true;
            assignment.put(rid, -1);
            colorRejections++;
        }
        Collections.swap(ids, index, bestIdx);
        return false;
    }

    private List<Integer> legalColors(int rid, Map<Integer, Integer> assignment) {
        Set<Integer> used = new HashSet<>();
        for (int n : graph.getNeighbors(rid)) {
            int c = assignment.getOrDefault(n, -1);
            if (c != -1) used.add(c);
        }
        List<Integer> legal = new ArrayList<>();
        for (int i = 0; i < numColors; i++) if (!used.contains(i)) legal.add(i);
        return legal;
    }

    private boolean isFullyValid(Map<Integer, Integer> assignment) {
        for (Region r : graph.getRegions()) {
            int c = assignment.getOrDefault(r.id, -1);
            if (c == -1) return false;
            for (int n : graph.getNeighbors(r.id))
                if (assignment.getOrDefault(n, -1) == c) return false;
        }
        return true;
    }

    public int findSimpleLocalColor(int rid) {
        Set<Integer> used = new HashSet<>();
        for (int n : graph.getNeighbors(rid)) {
            int c = graph.getRegions().get(n).color;
            if (c != -1) used.add(c);
        }
        for (int i = 0; i < numColors; i++) if (!used.contains(i)) return i;
        return -1;
    }

    private void printBanner() {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("  DIVIDE & CONQUER AUTO-SOLVER");
        System.out.println("  Divide  : BFS bisection (pseudo-peripheral pair)");
        System.out.println("  Conquer : Recurse on each partition independently");
        System.out.println("  Merge   : Detect & re-solve seam conflicts");
        System.out.println("  Base    : MRV backtracking when size <= " + BASE_SIZE);
        System.out.println("-----------------------------------------------------------------");
    }

    private void printStats() {
        System.out.printf("%n--- D&C SOLVE STATISTICS ---------------------------------------%n");
        System.out.printf("   Free regions (n)           : %-6d%n", freeCount);
        System.out.printf("   Divide + merge steps       : %-10d%n", reconstructSteps);
        System.out.printf("   Backtrack base calls       : %-10d%n", statesExplored);
        System.out.printf("   Seam regions re-solved     : %-10d%n", statesSkipped);
        System.out.printf("   Color (region,col) trials  : %-10d%n", colorTrials);
        System.out.printf("   Color rejections           : %-10d (%.1f%% pruned)%n",
            colorRejections,
            colorTrials == 0 ? 0.0 : 100.0 * colorRejections / colorTrials);
        System.out.printf("   Wall-clock solve time      : %-6d ms%n", solveTimeMs);
        System.out.printf("-----------------------------------------------------------------%n");
    }

    public int  getStatesExplored(){
        return statesExplored; 
    }
    public int  getStatesSkipped(){
        return statesSkipped; 
    }
    public int  getColorTrials(){
        return colorTrials; 
    }
    public int  getColorRejections(){ 
        return colorRejections; 
    }
    public long getSolveTimeMs(){
        return solveTimeMs; 
    }
    public int  getReconstructSteps() {
        return reconstructSteps; 
    }
    public int  getFreeCount(){
        return freeCount; 
    }
    public int  getTotalStates() {
        return -1; 
    } 
    public int  getDpTableSize(){
        return statesExplored; 
    }
}

