package eval34;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;

// Cell class
class Cell {
    int row, col;
    int regionId = -1;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }
}

// Region class
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

// Map generation
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

// Bot move tracking
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

// Heuristic Bot Solver (MRV + Degree)
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

            // MRV check; Tie-breaker is Degree Heuristic
            if (remainingValues < minRemainingValues || 
               (remainingValues == minRemainingValues && uncoloredNeighbors > maxUncoloredNeighbors)) {
                bestRegion = region.id;
                minRemainingValues = remainingValues;
                maxUncoloredNeighbors = uncoloredNeighbors;
            }
        }

        if (bestRegion != -1) {
            Set<Integer> available = graph.getAvailableColors(bestRegion);
            int chosenColor = available.iterator().next();
            graph.colorRegion(bestRegion, chosenColor);
            return new BotMove(bestRegion, chosenColor, false);
        }

        // 2. TRY RECOLORING
        for (Region uncoloredRegion : regions) {
            if (uncoloredRegion.color != -1 || uncoloredRegion.isLocked) continue;

            Set<Integer> available = graph.getAvailableColors(uncoloredRegion.id);
            if (available.isEmpty()) {
                for (int neighborId : graph.getNeighbors(uncoloredRegion.id)) {
                    Region neighbor = regions.get(neighborId);
                    if (!neighbor.isLocked && neighbor.color != -1) {
                        int oldColor = neighbor.color;
                        neighbor.color = -1; 
                        
                        Set<Integer> neighborColors = graph.getAvailableColors(neighborId);
                        neighborColors.remove(oldColor); 

                        if (!neighborColors.isEmpty()) {
                            int newColor = neighborColors.iterator().next();
                            neighbor.color = newColor;
                            return new BotMove(neighborId, newColor, true);
                        } else {
                            neighbor.color = oldColor; 
                        }
                    }
                }
            }
        }

        // 3. General recoloring
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

        return null;
    }
}


// GUI Application
class CooperativeGameGUI extends JFrame {
    private final GameGraph graph;
    private final Bot bot;
    private final Cell[][] grid;
    private final int gridRows, gridCols, cellSize = 20;

    private int lastColoredRegion = -1;
    private int moveCount = 0;
    private int recolorCount = 0;

    private JPanel mapPanel;
    private JLabel statusLabel, phaseLabel, moveLabel, statsLabel;
    private JButton startBtn, pauseBtn, restartBtn;
    private JTextArea logArea;
    private Timer stepTimer;
    private boolean paused = false;
    private JSpinner regionSpinner;

    private static final int STEP_DELAY_MS = 250;

    private final Color[] COLORS = {
        new Color(220, 30,  75),
        new Color(50,  175, 70),
        new Color(0,   120, 195),
        new Color(245, 130, 45),
    };

    public CooperativeGameGUI(int numRegions, int numColors, int gridRows, int gridCols) {
        mapgeneration gen = new mapgeneration(gridRows, gridCols);
        List<Region> regions = gen.generateRegions(numRegions);
        this.grid = gen.getGrid();
        this.gridRows = gridRows;
        this.gridCols = gridCols;
        this.graph = new GameGraph(regions, grid, gridRows, gridCols, numColors);
        
        lockInitialRegions();
        this.bot = new Bot(graph);
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
            if (graph.getAvailableColors(rid).contains(color)) { r.color = color; r.isLocked = true; }
        }
        while (!avail.isEmpty() && countLocked() < numToLock) {
            int rid = avail.remove(0); Region r = regions.get(rid);
            Set<Integer> ok = graph.getAvailableColors(rid);
            if (!ok.isEmpty()) { 
                int c = ok.toArray(new Integer[0])[rnd.nextInt(ok.size())]; 
                r.color = c; r.isLocked = true; 
            }
        }
        System.out.println("\nLocked regions:");
        for (Region r : regions) if (r.isLocked) System.out.println("   Region " + r.id + " -> Color " + r.color);
    }

    private int countLocked() { 
        int n=0; 
        for (Region r:graph.getRegions()) if(r.isLocked) n++; 
        return n; 
    }

    private void buildGUI() {
        setTitle("CSP Bot Solver - MRV & Degree Heuristic");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        // Top status bar
        JPanel top = new JPanel(new GridLayout(4, 1, 1, 1));
        top.setBackground(new Color(240, 240, 240));
        top.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        statusLabel = makeLabel("Press Start to run Heuristic CSP Bot", 14, Font.BOLD, Color.BLACK);
        phaseLabel  = makeLabel("Heuristics: MRV (Minimum Remaining Values) -> Degree Heuristic -> Recoloring", 12, Font.BOLD, new Color(0, 100, 200));
        moveLabel   = makeLabel("Moves made: 0   |   Recolors: 0", 11, Font.PLAIN, Color.BLACK);
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
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        logArea.setText("CSP Bot Live Action Log\n------------------------------------------\n");
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Bot Decision Log", 0, 0,
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
        spinnerPanel.add(spinnerLabel);
        spinnerPanel.add(regionSpinner);

        controls.add(startBtn); controls.add(pauseBtn); controls.add(restartBtn);
        JPanel south = new JPanel(new GridLayout(2, 1));
        south.setBackground(new Color(250, 250, 250));
        south.add(controls); south.add(spinnerPanel);
        add(south, BorderLayout.SOUTH);

        // Timer
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

    private void onStart() {
        startBtn.setEnabled(false);
        pauseBtn.setEnabled(true);
        statusLabel.setText("Bot is actively analyzing the map...");
        stepTimer.start();
    }

    private void onPause() {
        if (!paused) {
            stepTimer.stop(); paused = true;
            pauseBtn.setText("Resume");
            statusLabel.setText("Paused - click Resume to continue");
        } else {
            stepTimer.start(); paused = false;
            pauseBtn.setText("Pause");
            statusLabel.setText("Resuming computation...");
        }
    }

    private void onRestart() {
        int newRegions = (Integer) regionSpinner.getValue();
        stepTimer.stop(); dispose();
        SwingUtilities.invokeLater(() -> new CooperativeGameGUI(newRegions, 4, 20, 25).setVisible(true));
    }

    private void stepBot() {
        if (graph.isComplete()) {
            stepTimer.stop(); lastColoredRegion = -1; onFinished(true); return;
        }

        BotMove move = bot.makeMove();

        if (move == null) {
            stepTimer.stop(); onFinished(false); return;
        }

        moveCount++;
        lastColoredRegion = move.regionId;
        
        if (move.isRecolor) {
            recolorCount++;
            appendLog(String.format("RECOLOR: Region %2d changed to Color %d", move.regionId, move.color + 1));
        } else {
            appendLog(String.format("COLOR: Region %2d colored %d (MRV/Degree)", move.regionId, move.color + 1));
        }

        moveLabel.setText(String.format("Moves made: %d   |   Recolors applied: %d", moveCount, recolorCount));
        statusLabel.setText(String.format("Bot Action -> Region %d %s to Color %d", 
                move.regionId, move.isRecolor ? "recolored" : "colored", move.color + 1));
        statsLabel.setText(statsText());

        mapPanel.repaint();
    }

    private void onFinished(boolean success) {
        appendFinalStats(success);
        
        if (success) {
            statusLabel.setText("Puzzle Solved! All regions colored successfully.");
            phaseLabel.setText("Target reached   |   No conflicts detected");
            JOptionPane.showMessageDialog(this,
                "CSP Bot Solved the Map!\n\n"
                + "All regions legally colored.\n\n"
                + String.format("  Total Moves Made : %d%n", moveCount)
                + String.format("  Recolors Needed  : %d%n", recolorCount),
                "Victory!", JOptionPane.INFORMATION_MESSAGE);
        } else {
            statusLabel.setText("Bot got completely stuck. No valid moves or recolors left.");
            phaseLabel.setText("Conflict prevents completion");
        }
        statsLabel.setText(statsText());
        startBtn.setEnabled(false); pauseBtn.setEnabled(false);
        mapPanel.repaint();
    }

    private void appendLog(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void appendFinalStats(boolean success) {
        long conflicts = graph.getRegions().stream().filter(r -> graph.inConflict(r.id)).count();
        appendLog(String.format(
            "\n--- FINAL STATS ------------------------------\n" +
            "  Total moves applied    : %d\n" +
            "  Recoloring trigger count: %d\n" +
            "  Conflicts remaining    : %d\n" +
            "  Puzzle solved          : %s\n" +
            "----------------------------------------------\n",
            moveCount, recolorCount, conflicts, success ? "YES" : "NO"
        ));
    }

    private String statsText() {
        int total = graph.getRegions().size();
        int locked = countLocked();
        int colored = 0;
        int conflicts = 0;
        for (Region r : graph.getRegions()) { 
            if (r.color != -1) { 
                colored++; 
                if (graph.inConflict(r.id)) conflicts++; 
            } 
        }
        return String.format(
            "Progress: %d/%d colored  |  Locked initial constraints: %d  |  Conflicts: %d",
            colored, total, locked, conflicts);
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
            g2.setColor(new Color(255, 255, 100, 200)); 
            g2.setStroke(new BasicStroke(3));
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

public class mrv {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("-------------------------------------------------------------");
            System.out.println("  MAP COLORING - GREEDY CSP BOT");
            System.out.println("  Strategy: MRV -> Degree Heuristic -> Backtracking Recolor");
            System.out.println("-------------------------------------------------------------");
            new CooperativeGameGUI(25, 4, 20, 25).setVisible(true);
        });
    }
}
