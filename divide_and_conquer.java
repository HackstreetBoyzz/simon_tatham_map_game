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


class botplay {
    private final GameGraph graph;
    private final DivideAndConquerBot solver;

    private Map<Integer, Integer> fullSolution  = null;
    private final List<int[]>moveSequence  = new ArrayList<>();
    private int moveIndex= 0;
    private final List<String> moveLog = new ArrayList<>();

    // Per-step partition snapshots (one entry per move, built during solve)
    private final List<Set<Integer>> stepPartitionA  = new ArrayList<>();
    private final List<Set<Integer>> stepPartitionB  = new ArrayList<>();
    private final List<Set<Integer>> stepBoundary    = new ArrayList<>();

    public botplay(GameGraph graph) {
        this.graph  = graph;
        this.solver = new DivideAndConquerBot(graph);
    }

    public boolean initialize() {
        printBanner();
        long t0 = System.currentTimeMillis();
        fullSolution = solver.solve();
        long elapsed = System.currentTimeMillis() - t0;
        printSolveStats(elapsed);

        if (fullSolution == null) {
            System.out.println("[AUTO] D&C returned null - unsolvable.");
            return false;
        }

        for (Region r : graph.getRegions()) {
            if (!r.isLocked) {
                Integer c = fullSolution.get(r.id);
                if (c != null && c != -1) moveSequence.add(new int[]{ r.id, c });
            }
        }

        buildStepPartitions();

        System.out.printf("%n AUTO %d moves queued for playback%n", moveSequence.size());
        System.out.println("-----------------------------------------------------------------");
        return true;
    }

    private void buildStepPartitions() {
        Set<Integer> remaining = new LinkedHashSet<>();
        for (int[] m : moveSequence) remaining.add(m[0]);

        for (int i = 0; i < moveSequence.size(); i++) {
            int colored = moveSequence.get(i)[0];
            remaining.remove(colored);

            if (remaining.size() >= 2) {
                List<Integer> rem = new ArrayList<>(remaining);
                List<Integer>[] parts = bisect(rem);
                stepPartitionA.add(new HashSet<>(parts[0]));
                stepPartitionB.add(new HashSet<>(parts[1]));
                Set<Integer> bSet = new HashSet<>(parts[1]);
                Set<Integer> boundary = new HashSet<>();
                for (int rid : parts[0])
                    for (int nb : graph.getNeighbors(rid))
                        if (bSet.contains(nb)) { boundary.add(rid); boundary.add(nb); break; }
                stepBoundary.add(boundary);
            } else {
                stepPartitionA.add(new HashSet<>(remaining));
                stepPartitionB.add(new HashSet<>());
                stepBoundary.add(new HashSet<>());
            }
        }
    }

    /** BFS bisection — same logic as DivideAndConquerBot.graphBisect, self-contained here. */
    @SuppressWarnings("unchecked")
    private List<Integer>[] bisect(List<Integer> free) {
        if (free.size() < 2) return new List[]{ free, new ArrayList<>() };
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
        Map<Integer, Integer> d0 = bfsDist.apply(seedA);
        int nodeA = seedA, maxD = -1;
        for (Map.Entry<Integer, Integer> e : d0.entrySet())
            if (e.getValue() > maxD) { maxD = e.getValue(); nodeA = e.getKey(); }

        Map<Integer, Integer> distFromA = bfsDist.apply(nodeA);
        int nodeB = free.get(free.size() / 2); maxD = -1;
        for (Map.Entry<Integer, Integer> e : distFromA.entrySet())
            if (!e.getKey().equals(nodeA) && e.getValue() > maxD) { maxD = e.getValue(); nodeB = e.getKey(); }

        Map<Integer, Integer> distFromB = bfsDist.apply(nodeB);
        List<Integer> pA = new ArrayList<>(), pB = new ArrayList<>();
        for (int rid : free) {
            int dA = distFromA.getOrDefault(rid, Integer.MAX_VALUE / 2);
            int dB = distFromB.getOrDefault(rid, Integer.MAX_VALUE / 2);
            if (dA <= dB) pA.add(rid); else pB.add(rid);
        }
        return new List[]{ pA, pB };
    }

    private void printBanner() {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("  DIVIDE & CONQUER AUTO-SOLVER");
        System.out.println("  Divide  : BFS bisection (pseudo-peripheral pair)");
        System.out.println("  Conquer : Recurse each partition independently");
        System.out.println("  Merge   : Detect & re-solve seam conflicts (MRV backtrack)");
        System.out.println("  Base    : Backtracking when size <= " + DivideAndConquerBot.BASE_SIZE);
        System.out.println("-----------------------------------------------------------------");
    }

    private void printSolveStats(long elapsed) {
        DivideAndConquerBot s = solver;
        System.out.printf("%n--- D&C SOLVE STATISTICS ---------------------------------------%n");
        System.out.printf("   Free regions (n)          : %-6d%n", s.getFreeCount());
        System.out.printf("   Divide + merge steps      : %-10d%n", s.getReconstructSteps());
        System.out.printf("   Backtrack base calls      : %-10d%n", s.getStatesExplored());
        System.out.printf("   Seam regions re-solved    : %-10d%n", s.getStatesSkipped());
        System.out.printf("   Color (region,col) trials : %-10d%n", s.getColorTrials());
        System.out.printf("   Color rejections          : %-10d (%.1f%% pruned)%n",
            s.getColorRejections(),
            s.getColorTrials()==0 ? 0.0 : 100.0*s.getColorRejections()/s.getColorTrials());
        System.out.printf("   Wall-clock solve time     : %-6d ms%n", elapsed);
        System.out.printf("   Solution found            : %-6s%n",
            fullSolution != null ? "YES" : "NO");
        System.out.printf("-----------------------------------------------------------------%n");
    }

    /** Apply next move. Returns int[]{rid, color} or null if done. */
    public int[] nextmove() {
        if (moveIndex >= moveSequence.size()) return null;
        int[] move = moveSequence.get(moveIndex++);
        int rid = move[0];
        int color = move[1];
        graph.getRegions().get(rid).color = color;

        // Update live partition overlay for this step
        int stepIdx = moveIndex - 1;
        if (stepIdx < stepPartitionA.size()) {
            solver.lastPartitionA      = stepPartitionA.get(stepIdx);
            solver.lastPartitionB      = stepPartitionB.get(stepIdx);
            solver.lastBoundaryRegions = stepBoundary.get(stepIdx);
        }

        int neighborCount  = graph.getNeighbors(rid).size();
        int legalRemaining = graph.availableColors(rid).size();
        String log = String.format(
            "Move %3d/%3d | Region %2d -> Color %d | Adj regions: %d | Free colors after: %d | Conflict: %s",
            moveIndex, moveSequence.size(), rid, color + 1,
            neighborCount, legalRemaining,
            graph.inConflict(rid) ? "YES" : "NO"
        );
        moveLog.add(log);
        System.out.println("[MOVE] " + log);
        return move;
    }

    public boolean isDone(){ 
        return moveIndex >= moveSequence.size(); }
    public int getMoveIndex() {
        return moveIndex; 
    }
    public int getTotalMoves() {
        return moveSequence.size(); 
    }
    public List<String> getMoveLog() {
        return moveLog; 
    }
    public boolean isPuzzleSolved() {
        for (Region r : graph.getRegions())
            if (r.color == -1 || graph.inConflict(r.id)) return false;
        return true;
    }

    public DivideAndConquerBot getSolver() { return solver; }

    public void printFinalStats() {
        long conflicts = graph.getRegions().stream().filter(r -> graph.inConflict(r.id)).count();
        DivideAndConquerBot s = solver;
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("  FINAL COMPARISON STATISTICS");
        System.out.println("-----------------------------------------------------------------");
        System.out.printf("  %-30s : %d%n", "Total regions",             graph.getRegions().size());
        System.out.printf("  %-30s : %d%n", "Locked (pre-colored)",      graph.getRegions().stream().filter(r->r.isLocked).count());
        System.out.printf("  %-30s : %d%n", "Free regions solved by D&C",s.getFreeCount());
        System.out.printf("  %-30s : %d%n", "Colors available (k)",      graph.getNumColors());
        System.out.printf("  %-30s : %d%n", "Divide + merge steps",      s.getReconstructSteps());
        System.out.printf("  %-30s : %d%n", "Backtrack base calls",      s.getStatesExplored());
        System.out.printf("  %-30s : %d%n", "Seam regions re-solved",    s.getStatesSkipped());
        System.out.printf("  %-30s : %d%n", "Total color trials",        s.getColorTrials());
        System.out.printf("  %-30s : %d%n", "Color rejections (pruned)", s.getColorRejections());
        System.out.printf("  %-30s : %.1f%%%n","MRV pruning efficiency",
            s.getColorTrials()==0 ? 0.0 : 100.0*s.getColorRejections()/s.getColorTrials());
        System.out.printf("  %-30s : %d ms%n","D&C solve time",          s.getSolveTimeMs());
        System.out.printf("  %-30s : %d%n", "Moves applied",             moveSequence.size());
        System.out.printf("  %-30s : %d%n", "Conflicts remaining",       conflicts);
        System.out.printf("  %-30s : %s%n", "Puzzle valid",              isPuzzleSolved() ? "YES" : "NO");
        System.out.println("-----------------------------------------------------------------");
    }
}


class CooperativeGameGUI extends JFrame {
    private final GameGraph  graph;
    private final botplay botplay;
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
        this.botplay = new botplay(graph);
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
        setTitle("Divide & Conquer Auto-Solver - Map Coloring");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        JPanel top = new JPanel(new GridLayout(4, 1, 1, 1));
        top.setBackground(new Color(240, 240, 240));
        top.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        statusLabel = makeLabel("Press Start to run Divide & Conquer Auto-Solver", 14, Font.BOLD, Color.BLACK);
        phaseLabel  = makeLabel("Divide -> Conquer Left -> Conquer Right -> Merge Seam", 12, Font.BOLD, new Color(0, 100, 200));
        moveLabel   = makeLabel("Move: 0 / -   |   Free regions: -   |   D&C Steps: -", 11, Font.PLAIN, Color.BLACK);
        statsLabel  = makeLabel(statsText(), 11, Font.ITALIC, Color.DARK_GRAY);

        top.add(statusLabel); top.add(phaseLabel); top.add(moveLabel); top.add(statsLabel);
        add(top, BorderLayout.NORTH);

        mapPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); drawMap(g); }
        };
        mapPanel.setPreferredSize(new Dimension(gridCols*cellSize+100, gridRows*cellSize+100));
        mapPanel.setBackground(Color.WHITE);
        add(new JScrollPane(mapPanel), BorderLayout.CENTER);

        logArea = new JTextArea(22, 44);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logArea.setBackground(Color.WHITE);
        logArea.setForeground(Color.BLACK);
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        logArea.setText("Divide & Conquer Technical Log\n------------------------------------------\n");
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "D&C Diagnostics & Move Log", 0, 0,
            new Font("Arial", Font.BOLD, 11), Color.BLACK));
        logScroll.setPreferredSize(new Dimension(430, 0));
        add(logScroll, BorderLayout.EAST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));
        controls.setBackground(new Color(250, 250, 250));

        startBtn   = makeBtn("Start",   new Color(60, 140, 60));
        pauseBtn   = makeBtn("Pause",   new Color(180, 120, 20));
        restartBtn = makeBtn("Restart", new Color(160, 50, 50));
        pauseBtn.setEnabled(false);

        startBtn.addActionListener(e   -> onStart());
        pauseBtn.addActionListener(e   -> onPause());
        restartBtn.addActionListener(e -> onRestart());

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
        spinnerPanel.add(spinnerLabel); spinnerPanel.add(regionSpinner); spinnerPanel.add(spinnerHint);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        legend.setBackground(new Color(248, 248, 248));
        addSwatch(legend, TINT_A, "Partition A (left half)");
        addSwatch(legend, TINT_B, "Partition B (right half)");
        addSwatch(legend, new Color(255,255,120,160), "Last colored");

        controls.add(startBtn); controls.add(pauseBtn); controls.add(restartBtn);
        JPanel south = new JPanel(new GridLayout(3, 1));
        south.setBackground(new Color(250, 250, 250));
        south.add(controls); south.add(spinnerPanel); south.add(legend);
        add(south, BorderLayout.SOUTH);

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

    // Controls
    private void onStart() {
        startBtn.setEnabled(false);
        pauseBtn.setEnabled(true);
        statusLabel.setText("Running Divide & Conquer Solver - please wait...");
        phaseLabel.setText("[D&C] Bisecting graph -> Conquering halves -> Merging seams...");

        new Thread(() -> {
            boolean ok = botplay.initialize();
            SwingUtilities.invokeLater(() -> {
                if (!ok) {
                    statusLabel.setText("D&C found NO solution - locked regions may conflict.");
                    appendLog("ERROR: D&C unsolvable.\n");
                    startBtn.setEnabled(true); pauseBtn.setEnabled(false); return;
                }
                appendSolveStats();
                DivideAndConquerBot s = botplay.getSolver();
                phaseLabel.setText(String.format(
                    "[D&C] Solution found! %d moves queued | D&C steps: %d | trials: %d | %d ms",
                    botplay.getTotalMoves(), s.getReconstructSteps(),
                    s.getColorTrials(), s.getSolveTimeMs()));
                moveLabel.setText(String.format(
                    "Move: 0 / %d   |   Free regions: %d   |   D&C steps: %d",
                    botplay.getTotalMoves(), s.getFreeCount(), s.getReconstructSteps()));

                // Show initial partition overlay
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

    // Step Execution
    private void stepBot() {
        if (botplay.isDone()) { stepTimer.stop(); lastColoredRegion=-1; showOverlay=false; onFinished(); return; }

        int[] move = botplay.nextmove();
        if (move == null) { stepTimer.stop(); onFinished(); return; }

        int rid   = move[0];
        int color = move[1];
        lastColoredRegion = rid;

        // Pull updated partition sets that nextmove() just wrote
        DivideAndConquerBot s = botplay.getSolver();
        hlA = s.lastPartitionA;
        hlB = s.lastPartitionB;

        int idx   = botplay.getMoveIndex();
        int total = botplay.getTotalMoves();

        moveLabel.setText(String.format(
            "Move: %d / %d   |   Free: %d   |   D&C steps: %d   |   Trials: %d   |   Rejects: %d",
            idx, total, s.getFreeCount(), s.getReconstructSteps(),
            s.getColorTrials(), s.getColorRejections()));
        statusLabel.setText(String.format(
            "Region %d -> Color %d   |   %d regions remaining", rid, color+1, total-idx));
        phaseLabel.setText(String.format(
            "[D&C] Divide->Conquer->Merge | Seam re-solved: %d | Solve: %d ms | MRV pruned: %.1f%%",
            s.getStatesSkipped(), s.getSolveTimeMs(),
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials()));
        statsLabel.setText(statsText());

        List<String> log = botplay.getMoveLog();
        if (!log.isEmpty()) appendLog(log.get(log.size()-1) + "\n");

        mapPanel.repaint();
    }

    private void onFinished() {
        botplay.printFinalStats();
        appendFinalStats();

        boolean solved = botplay.isPuzzleSolved();
        statusLabel.setText(solved ? "Puzzle Solved! All regions colored - no conflicts." : "Finished with conflicts.");
        phaseLabel.setText("Divide -> Conquer -> Merge complete  |  all moves applied");
        statsLabel.setText(statsText());
        startBtn.setEnabled(false); pauseBtn.setEnabled(false);
        mapPanel.repaint();

        if (solved) {
            DivideAndConquerBot s = botplay.getSolver();
            JOptionPane.showMessageDialog(this,
                "Divide & Conquer Solved the Map!\n\n"
                + "All regions legally colored - zero conflicts\n\n"
                + String.format("   Free regions (n)      : %d%n",  s.getFreeCount())
                + String.format("   Divide + merge steps  : %d%n",  s.getReconstructSteps())
                + String.format("   Backtrack base calls  : %d%n",  s.getStatesExplored())
                + String.format("   Seam regions re-solved: %d%n",  s.getStatesSkipped())
                + String.format("   Color trials          : %d%n",  s.getColorTrials())
                + String.format("   Rejections (MRV)      : %d  (%.1f%% pruned)%n",
                    s.getColorRejections(),
                    s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials())
                + String.format("   Solve time            : %d ms%n", s.getSolveTimeMs()),
                "Victory!", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void appendLog(String text) {
        logArea.append(text);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void appendSolveStats() {
        DivideAndConquerBot s = botplay.getSolver();
        appendLog(String.format(
            "\n--- D&C SOLVE STATS --------------------------\n" +
            "   Free regions (n)       : %d\n" +
            "   Divide + merge steps   : %d\n" +
            "   Backtrack base calls   : %d\n" +
            "   Seam regions re-solved : %d\n" +
            "   Color trials           : %d\n" +
            "   Color rejections       : %d  (%.1f%% pruned)\n" +
            "   Solve time             : %d ms\n" +
            "----------------------------------------------\n",
            s.getFreeCount(), s.getReconstructSteps(),
            s.getStatesExplored(), s.getStatesSkipped(),
            s.getColorTrials(), s.getColorRejections(),
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials(),
            s.getSolveTimeMs()
        ));
    }

    private void appendFinalStats() {
        DivideAndConquerBot s = botplay.getSolver();
        long conflicts = graph.getRegions().stream().filter(r -> graph.inConflict(r.id)).count();
        appendLog(String.format(
            "\n--- FINAL STATS ------------------------------\n" +
            "   Total moves applied    : %d\n" +
            "   Conflicts remaining    : %d\n" +
            "   MRV pruning efficiency : %.1f%%\n" +
            "   Puzzle solved          : %s\n" +
            "----------------------------------------------\n",
            botplay.getTotalMoves(), conflicts,
            s.getColorTrials()==0?0.0:100.0*s.getColorRejections()/s.getColorTrials(),
            botplay.isPuzzleSolved() ? "YES" : "NO"
        ));
    }

    private String statsText() {
        int total=graph.getRegions().size(), locked=countLocked(), colored=0, conflicts=0;
        for (Region r : graph.getRegions()) { if(r.color!=-1){colored++; if(graph.inConflict(r.id)) conflicts++;} }
        DivideAndConquerBot s = botplay.getSolver();
        return String.format(
            "Colored: %d/%d | Locked: %d | Conflicts: %d | D&C steps: %d | Trials: %d | Pruned: %.0f%% | Time: %dms",
            colored, total, locked, conflicts,
            s.getReconstructSteps(), s.getColorTrials(),
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

        if (lastColoredRegion >= 0) {
            g2.setColor(new Color(255, 255, 100, 200)); g2.setStroke(new BasicStroke(3));
            for (Cell c : graph.getRegions().get(lastColoredRegion).cells)
                g2.drawRect(ox+c.col*cellSize+1, oy+c.row*cellSize+1, cellSize-2, cellSize-2);
        }

        g2.setColor(Color.BLACK); g2.setStroke(new BasicStroke(2));
        for (int row = 0; row < gridRows; row++)
            for (int col = 0; col < gridCols; col++) {
                Cell cell = grid[row][col];
                int x = ox+col*cellSize, y = oy+row*cellSize;
                if (col<gridCols-1 && grid[row][col+1].regionId!=cell.regionId) g2.drawLine(x+cellSize,y,x+cellSize,y+cellSize);
                if (row<gridRows-1 && grid[row+1][col].regionId!=cell.regionId) g2.drawLine(x,y+cellSize,x+cellSize,y+cellSize);
            }

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

public class dc {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("-------------------------------------------------------------");
            System.out.println("  MAP COLORING - DIVIDE & CONQUER AUTO-SOLVER          ");
            System.out.println("  Divide  : BFS bisection (pseudo-peripheral pair)        ");
            System.out.println("  Conquer : Recurse each partition independently          ");
            System.out.println("  Merge   : Detect & re-solve seam conflicts              ");
            System.out.println("  Base    : MRV backtracking  (size <= 6)                 ");
            System.out.println("-------------------------------------------------------------");
            new CooperativeGameGUI(25, 4, 20, 25).setVisible(true);
        });
    }
}

