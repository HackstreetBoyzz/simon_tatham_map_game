package third_auto_mrv;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;

// ============================================================================
// CELL
// ============================================================================
class Cell {
    int row, col;
    int regionId = -1;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }
}

// ============================================================================
// REGION
// ============================================================================
class Region {
    int id;
    Set<Cell> cells;
    int color = -1;
    boolean isLocked = false; 

    public Region(int id) {
        this.id = id;
        this.cells = new HashSet<>();
    }

    public void addCell(Cell cell) {
        cells.add(cell);
    }

    public Point getCentroid() {
        int sumRow = 0, sumCol = 0;
        for (Cell cell : cells) {
            sumRow += cell.row;
            sumCol += cell.col;
        }
        return new Point(sumCol / cells.size(), sumRow / cells.size());
    }
}

// ============================================================================
// MAP GENERATION (Using original robust generation)
// ============================================================================
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

// ============================================================================
// GAME GRAPH
// ============================================================================
class GameGraph {
    private final List<Region> regions;
    private final Map<Integer, Set<Integer>> adjacencyList;
    private final int numColors;

    public GameGraph(List<Region> regions, Cell[][] grid, int gridRows, int gridCols, int numColors) {
        this.regions = regions;
        this.numColors = numColors;
        this.adjacencyList = new HashMap<>();

        for (Region r : regions) adjacencyList.put(r.id, new HashSet<>());

        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                Cell cell = grid[r][c];
                int regionId = cell.regionId;
                for (int[] dir : dirs) {
                    int nr = r + dir[0];
                    int nc = c + dir[1];
                    if (nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols) {
                        Cell neighbor = grid[nr][nc];
                        int neighborRegion = neighbor.regionId;
                        if (regionId != neighborRegion) {
                            adjacencyList.get(regionId).add(neighborRegion);
                            adjacencyList.get(neighborRegion).add(regionId);
                        }
                    }
                }
            }
        }
    }

    public Set<Integer> getNeighbors(int regionId) {
        return adjacencyList.get(regionId);
    }

    public Set<Integer> getAvailableColors(int regionId) {
        Set<Integer> available = new HashSet<>();
        for (int c = 0; c < numColors; c++) available.add(c);

        for (int neighbor : adjacencyList.get(regionId)) {
            int neighborColor = regions.get(neighbor).color;
            if (neighborColor != -1) {
                available.remove(neighborColor);
            }
        }
        return available;
    }

    public boolean isValidMove(int regionId, int color) {
        if (regions.get(regionId).isLocked) return false;
        for (int neighbor : adjacencyList.get(regionId)) {
            if (regions.get(neighbor).color == color) return false;
        }
        return true;
    }

    public boolean colorRegion(int regionId, int color) {
        if (isValidMove(regionId, color)) {
            regions.get(regionId).color = color;
            return true;
        }
        return false;
    }

    public boolean isComplete() {
        for (Region r : regions) if (r.color == -1) return false;
        return true;
    }

    public boolean inConflict(int regionId) {
        int c = regions.get(regionId).color;
        if (c == -1) return false;
        for (int n : adjacencyList.get(regionId))
            if (regions.get(n).color == c) return true;
        return false;
    }

    public List<Region> getRegions() { return regions; }
    public int getNumColors() { return numColors; }
}

// ============================================================================
// BOT MOVE RECORD
// ============================================================================
class BotMove {
    int regionId;
    int color;
    boolean isRecolor;

    public BotMove(int regionId, int color, boolean isRecolor) {
        this.regionId = regionId;
        this.color = color;
        this.isRecolor = isRecolor;
    }
}

// ============================================================================
// HEURISTIC BOT SOLVER
// CSP solver using MRV + Degree Heuristic + Fallback Recoloring
// ============================================================================
class Bot {
    private final GameGraph graph;

    public Bot(GameGraph graph) {
        this.graph = graph;
    }

    public BotMove makeMove() {
        // 1. Try to color an uncolored region using MRV + Degree Heuristic
        List<Region> regions = graph.getRegions();
        int bestRegion = -1;
        int minRemainingValues = Integer.MAX_VALUE;
        int maxUncoloredNeighbors = -1;

        for (Region region : regions) {
            if (region.color != -1 || region.isLocked) continue;

            Set<Integer> availableColors = graph.getAvailableColors(region.id);
            int remainingValues = availableColors.size();

            if (remainingValues == 0) continue; // Handled by recoloring fallback

            int uncoloredNeighbors = 0;
            for (int neighbor : graph.getNeighbors(region.id)) {
                if (regions.get(neighbor).color == -1) uncoloredNeighbors++;
            }

            // MRV check; Tie-breaker is Degree Heuristic (max uncolored neighbors)
            if (remainingValues < minRemainingValues || 
               (remainingValues == minRemainingValues && uncoloredNeighbors > maxUncoloredNeighbors)) {
                bestRegion = region.id;
                minRemainingValues = remainingValues;
                maxUncoloredNeighbors = uncoloredNeighbors;
            }
        }

        if (bestRegion != -1) {
            Set<Integer> available = graph.getAvailableColors(bestRegion);
            int chosenColor = available.iterator().next(); // First valid color
            graph.colorRegion(bestRegion, chosenColor);
            return new BotMove(bestRegion, chosenColor, false);
        }

        // 2. If no uncolored regions can be colored, TRY RECOLORING
        // First, check if a stuck uncolored region can be freed by recoloring a neighbor
        for (Region uncoloredRegion : regions) {
            if (uncoloredRegion.color != -1 || uncoloredRegion.isLocked) continue;

            Set<Integer> available = graph.getAvailableColors(uncoloredRegion.id);
            if (available.isEmpty()) {
                for (int neighborId : graph.getNeighbors(uncoloredRegion.id)) {
                    Region neighbor = regions.get(neighborId);
                    if (!neighbor.isLocked && neighbor.color != -1) {
                        int oldColor = neighbor.color;
                        neighbor.color = -1; // Temporarily remove color to check alternatives
                        
                        Set<Integer> neighborColors = graph.getAvailableColors(neighborId);
                        neighborColors.remove(oldColor); // Must change to a different color

                        if (!neighborColors.isEmpty()) {
                            int newColor = neighborColors.iterator().next();
                            neighbor.color = newColor;
                            return new BotMove(neighborId, newColor, true);
                        } else {
                            neighbor.color = oldColor; // Restore if no alternatives
                        }
                    }
                }
            }
        }

        // 3. General recoloring (if specific neighbor logic fails)
        for (Region region : regions) {
            if (region.isLocked || region.color == -1) continue;

            int oldColor = region.color;
            region.color = -1;
            Set<Integer> availableColors = graph.getAvailableColors(region.id);
            availableColors.remove(oldColor);

            if (!availableColors.isEmpty()) {
                int newColor = availableColors.iterator().next();
                region.color = newColor;
                return new BotMove(region.id, newColor, true);
            } else {
                region.color = oldColor;
            }
        }

        return null; // Bot is completely stuck
    }
}

