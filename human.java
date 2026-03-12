package third_human;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import java.awt.event.*;

// ============================================================================
// CELL
// ============================================================================
class Cell {
    int row, col, regionId = -1;
    public Cell(int row, int col) { this.row = row; this.col = col; }
}

// ============================================================================
// REGION
// ============================================================================
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

// ============================================================================
// MAP GENERATION  — unchanged from original
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
// GAME GRAPH  — unchanged from original
// ============================================================================
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

// ============================================================================
// HUMAN-ONLY GUI
// Click a region to select it, then click a color button to paint it.
// Locked regions cannot be changed. Conflicts are highlighted in red.
// ============================================================================
class GameGUI extends JFrame {

    private final GameGraph graph;
    private final Cell[][]  grid;
    private final int       gridRows, gridCols;
    private final int       cellSize = 20;

    private int selectedRegion = -1;   // currently selected region id (-1 = none)

    private JPanel    mapPanel;
    private JLabel    statusLabel;
    private JLabel    progressLabel;
    private JButton[] colorButtons;
    private JButton   restartBtn;
    private JSpinner  regionSpinner;

    // The 4 colors
    private final Color[] COLORS = {
        new Color(220, 30,  75),   // 0 = Red
        new Color(50,  175, 70),   // 1 = Green
        new Color(0,   120, 195),  // 2 = Blue
        new Color(245, 130, 45),   // 3 = Orange
    };
    private final String[] COLOR_NAMES = { "Red", "Green", "Blue", "Orange" };

    // Highlight tint for selected region
    private static final Color TINT_SELECTED = new Color(255, 255, 100, 120);
    // Conflict border color
    private static final Color COLOR_CONFLICT = Color.RED;

    public GameGUI(int numRegions, int numColors, int gridRows, int gridCols) {
        mapgeneration gen = new mapgeneration(gridRows, gridCols);
        List<Region> regions = gen.generateRegions(numRegions);
        this.grid     = gen.getGrid();
        this.gridRows = gridRows;
        this.gridCols = gridCols;
        this.graph    = new GameGraph(regions, grid, gridRows, gridCols, numColors);
        lockInitialRegions();
        buildGUI();
    }

    // ── LOCK INITIAL REGIONS (same logic as original) ─────────────────────────
    private void lockInitialRegions() {
        Random rnd = new Random();
        List<Region> regions = graph.getRegions();
        int nc = graph.getNumColors();
        int numToLock = Math.max(nc, regions.size() / 5);
        List<Integer> avail = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) avail.add(i);
        Collections.shuffle(avail);
        // Guarantee at least one of each color is locked first
        for (int color = 0; color < nc && !avail.isEmpty(); color++) {
            int rid = avail.remove(0);
            Region r = regions.get(rid);
            if (graph.availableColors(rid).contains(color)) {
                r.color = color; r.isLocked = true;
            }
        }
        // Fill remaining locked slots
        while (!avail.isEmpty() && countLocked() < numToLock) {
            int rid = avail.remove(0);
            Region r = regions.get(rid);
            Set<Integer> ok = graph.availableColors(rid);
            if (!ok.isEmpty()) {
                int c = ok.toArray(new Integer[0])[rnd.nextInt(ok.size())];
                r.color = c; r.isLocked = true;
            }
        }
        System.out.println("\n🔒 Locked regions:");
        for (Region r : regions)
            if (r.isLocked)
                System.out.println("   Region " + r.id + " → Color " + r.color);
    }

    private int countLocked() {
        int n = 0;
        for (Region r : graph.getRegions()) if (r.isLocked) n++;
        return n;
    }

    // ── BUILD GUI ─────────────────────────────────────────────────────────────
    private void buildGUI() {
        setTitle("🎨 Map Coloring — Human Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        // ── Top status bar ───────────────────────────────────────────────────
        JPanel top = new JPanel(new GridLayout(2, 1, 2, 2));
        top.setBackground(new Color(240, 240, 240));
        top.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        statusLabel   = makeLabel("Click a region to select, then choose a color below.", 13, Font.BOLD, Color.BLACK);
        progressLabel = makeLabel(progressText(), 11, Font.PLAIN, new Color(60, 60, 60));
        top.add(statusLabel);
        top.add(progressLabel);
        add(top, BorderLayout.NORTH);

        // ── Map panel ────────────────────────────────────────────────────────
        mapPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g); drawMap(g);
            }
        };
        mapPanel.setPreferredSize(new Dimension(gridCols*cellSize + 100, gridRows*cellSize + 100));
        mapPanel.setBackground(Color.WHITE);
        mapPanel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleMapClick(e.getX(), e.getY()); }
        });
        add(new JScrollPane(mapPanel), BorderLayout.CENTER);

        // ── Right panel: color picker + legend ───────────────────────────────
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(new Color(248, 248, 248));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightPanel.setPreferredSize(new Dimension(160, 0));

        JLabel pickLabel = new JLabel("Choose Color:");
        pickLabel.setFont(new Font("Arial", Font.BOLD, 13));
        pickLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(pickLabel);
        rightPanel.add(Box.createVerticalStrut(10));

        colorButtons = new JButton[graph.getNumColors()];
        for (int i = 0; i < graph.getNumColors(); i++) {
            final int colorIdx = i;
            JButton btn = new JButton(COLOR_NAMES[i]);
            btn.setBackground(COLORS[i]);
            btn.setForeground(Color.WHITE);
            btn.setOpaque(true);
            btn.setBorderPainted(false);
            btn.setFont(new Font("Arial", Font.BOLD, 13));
            btn.setMaximumSize(new Dimension(130, 40));
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.addActionListener(e -> handleColorPick(colorIdx));
            colorButtons[i] = btn;
            rightPanel.add(btn);
            rightPanel.add(Box.createVerticalStrut(8));
        }

        rightPanel.add(Box.createVerticalStrut(20));

        // Legend
        JLabel legendLabel = new JLabel("Legend:");
        legendLabel.setFont(new Font("Arial", Font.BOLD, 12));
        legendLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(legendLabel);
        rightPanel.add(Box.createVerticalStrut(6));

        String[] legendItems = { "🔒 Locked (clue)", "Selected region", "⚠ Conflict" };
        Color[]  legendColors = { new Color(200,200,200), TINT_SELECTED, new Color(255,80,80,120) };
        for (int i = 0; i < legendItems.length; i++) {
            JLabel sw = new JLabel("  " + legendItems[i] + "  ");
            sw.setOpaque(true);
            sw.setBackground(legendColors[i]);
            sw.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            sw.setFont(new Font("Arial", Font.PLAIN, 10));
            sw.setForeground(Color.BLACK);
            sw.setAlignmentX(Component.CENTER_ALIGNMENT);
            sw.setMaximumSize(new Dimension(140, 24));
            rightPanel.add(sw);
            rightPanel.add(Box.createVerticalStrut(4));
        }

        add(rightPanel, BorderLayout.EAST);

        // ── Bottom controls ───────────────────────────────────────────────────
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));
        bottom.setBackground(new Color(250, 250, 250));

        // Undo button
        JButton undoBtn = makeBtn("↩  Undo", new Color(100, 100, 180));
        undoBtn.addActionListener(e -> handleUndo());

        restartBtn = makeBtn("↺  Restart", new Color(160, 50, 50));
        restartBtn.addActionListener(e -> onRestart());

        JLabel spinnerLabel = new JLabel("Regions:");
        spinnerLabel.setFont(new Font("Arial", Font.BOLD, 12));
        regionSpinner = new JSpinner(new SpinnerNumberModel(25, 5, 60, 1));
        regionSpinner.setPreferredSize(new Dimension(60, 28));
        ((JSpinner.DefaultEditor) regionSpinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
        JLabel spinnerHint = new JLabel("(apply with ↺)");
        spinnerHint.setFont(new Font("Arial", Font.ITALIC, 11));
        spinnerHint.setForeground(Color.GRAY);

        bottom.add(undoBtn);
        bottom.add(restartBtn);
        bottom.add(Box.createHorizontalStrut(20));
        bottom.add(spinnerLabel);
        bottom.add(regionSpinner);
        bottom.add(spinnerHint);

        add(bottom, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    // ── INTERACTION HANDLERS ──────────────────────────────────────────────────

    private void handleMapClick(int px, int py) {
        int ox = 50, oy = 50;
        int col = (px - ox) / cellSize;
        int row = (py - oy) / cellSize;
        if (row < 0 || row >= gridRows || col < 0 || col >= gridCols) {
            selectedRegion = -1;
            statusLabel.setText("Click a region to select, then choose a color below.");
            mapPanel.repaint();
            return;
        }
        int rid = grid[row][col].regionId;
        if (rid < 0) return;

        Region r = graph.getRegions().get(rid);
        if (r.isLocked) {
            statusLabel.setText("⛔ Region " + rid + " is locked — it cannot be changed.");
            selectedRegion = -1;
        } else {
            selectedRegion = rid;
            Set<Integer> avail = graph.availableColors(rid);
            if (avail.isEmpty()) {
                statusLabel.setText("⚠ Region " + rid + " selected — no valid colors available! Clear a neighbour first.");
            } else {
                String avails = "";
                for (int ci : avail) avails += COLOR_NAMES[ci] + " ";
                statusLabel.setText("Region " + rid + " selected  |  Valid colors: " + avails.trim());
            }
        }
        mapPanel.repaint();
    }

    // Undo stack: each entry is int[]{regionId, previousColor}
    private final Deque<int[]> undoStack = new ArrayDeque<>();

    private void handleColorPick(int colorIdx) {
        if (selectedRegion == -1) {
            statusLabel.setText("⚠ Select a region first by clicking on the map.");
            return;
        }
        Region r = graph.getRegions().get(selectedRegion);
        if (r.isLocked) {
            statusLabel.setText("⛔ Region " + selectedRegion + " is locked — it cannot be changed.");
            return;
        }

        // Check adjacency rule — warn but still allow (player is informed)
        boolean conflict = false;
        for (int nb : graph.getNeighbors(selectedRegion)) {
            if (graph.getRegions().get(nb).color == colorIdx) { conflict = true; break; }
        }

        // Push undo
        undoStack.push(new int[]{ selectedRegion, r.color });

        r.color = colorIdx;

        if (conflict) {
            statusLabel.setText("⚠ Region " + selectedRegion + " → " + COLOR_NAMES[colorIdx]
                + "  |  CONFLICT with a neighbour!");
        } else {
            statusLabel.setText("✓ Region " + selectedRegion + " → " + COLOR_NAMES[colorIdx]
                + "  |  No conflict.");
        }

        selectedRegion = -1;
        progressLabel.setText(progressText());
        mapPanel.repaint();
        checkWin();
    }

    private void handleUndo() {
        if (undoStack.isEmpty()) {
            statusLabel.setText("Nothing to undo.");
            return;
        }
        int[] prev = undoStack.pop();
        int rid = prev[0], oldColor = prev[1];
        graph.getRegions().get(rid).color = oldColor;
        selectedRegion = -1;
        statusLabel.setText("↩ Undid color on Region " + rid + ".");
        progressLabel.setText(progressText());
        mapPanel.repaint();
    }

    private void checkWin() {
        for (Region r : graph.getRegions()) {
            if (r.color == -1) return;        // still uncolored regions
            if (graph.inConflict(r.id)) return; // still conflicts
        }
        // All colored, no conflicts
        statusLabel.setText("🎉 Puzzle Complete! All regions legally colored — congratulations!");
        JOptionPane.showMessageDialog(this,
            "🎊 You solved the map! 🎊\n\nAll " + graph.getRegions().size()
            + " regions are legally colored\nwith no two adjacent regions sharing a color.",
            "Puzzle Solved!", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onRestart() {
        int newRegions = (Integer) regionSpinner.getValue();
        dispose();
        SwingUtilities.invokeLater(() -> new GameGUI(newRegions, 4, 20, 25).setVisible(true));
    }

    // ── PROGRESS TEXT ─────────────────────────────────────────────────────────
    private String progressText() {
        int total = graph.getRegions().size();
        int locked = countLocked();
        int colored = 0, conflicts = 0;
        for (Region r : graph.getRegions()) {
            if (r.color != -1) colored++;
            if (graph.inConflict(r.id)) conflicts++;
        }
        int remaining = total - colored;
        return String.format("Total: %d  |  Locked: %d  |  Colored: %d  |  Remaining: %d  |  Conflicts: %d",
            total, locked, colored, remaining, conflicts);
    }

    // ── DRAW MAP ──────────────────────────────────────────────────────────────
    private void drawMap(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int ox = 50, oy = 50;

        // Fill region cells
        for (Region r : graph.getRegions()) {
            Color fill = (r.color == -1) ? new Color(218, 218, 218) : COLORS[r.color];
            for (Cell c : r.cells) {
                g2.setColor(fill);
                g2.fillRect(ox + c.col*cellSize, oy + c.row*cellSize, cellSize, cellSize);
            }
        }

        // Tint selected region
        if (selectedRegion >= 0) {
            for (Cell c : graph.getRegions().get(selectedRegion).cells) {
                g2.setColor(TINT_SELECTED);
                g2.fillRect(ox + c.col*cellSize, oy + c.row*cellSize, cellSize, cellSize);
            }
        }

        // Tint conflicting regions
        for (Region r : graph.getRegions()) {
            if (graph.inConflict(r.id)) {
                for (Cell c : r.cells) {
                    g2.setColor(new Color(255, 0, 0, 60));
                    g2.fillRect(ox + c.col*cellSize, oy + c.row*cellSize, cellSize, cellSize);
                }
            }
        }

        // Region border lines
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        for (int row = 0; row < gridRows; row++)
            for (int col = 0; col < gridCols; col++) {
                Cell cell = grid[row][col];
                int x = ox + col*cellSize, y = oy + row*cellSize;
                if (col < gridCols-1 && grid[row][col+1].regionId != cell.regionId)
                    g2.drawLine(x+cellSize, y, x+cellSize, y+cellSize);
                if (row < gridRows-1 && grid[row+1][col].regionId != cell.regionId)
                    g2.drawLine(x, y+cellSize, x+cellSize, y+cellSize);
            }

        // Thick yellow border around selected region
        if (selectedRegion >= 0) {
            g2.setColor(new Color(255, 220, 0));
            g2.setStroke(new BasicStroke(3));
            for (Cell c : graph.getRegions().get(selectedRegion).cells)
                g2.drawRect(ox+c.col*cellSize+1, oy+c.row*cellSize+1, cellSize-2, cellSize-2);
        }

        // Thick red border around conflicting regions
        g2.setStroke(new BasicStroke(3));
        for (Region r : graph.getRegions()) {
            if (graph.inConflict(r.id)) {
                g2.setColor(COLOR_CONFLICT);
                for (Cell c : r.cells)
                    g2.drawRect(ox+c.col*cellSize+1, oy+c.row*cellSize+1, cellSize-2, cellSize-2);
            }
        }

        // Labels inside each region centroid
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        for (Region r : graph.getRegions()) {
            Point cen = r.getCentroid();
            int x = ox + cen.x*cellSize + cellSize/2;
            int y = oy + cen.y*cellSize + cellSize/2;

            if (r.isLocked) {
                // Show lock icon + region id
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                g2.setColor(Color.WHITE);
                g2.drawString("🔒", x-8, y+4);
                g2.setFont(new Font("Arial", Font.BOLD, 10));
            } else if (r.color == -1) {
                // Uncolored: show region id
                g2.setColor(Color.DARK_GRAY);
                g2.drawString(String.valueOf(r.id), x-5, y+4);
            } else if (graph.inConflict(r.id)) {
                // Conflict: show warning
                g2.setColor(Color.RED);
                g2.setFont(new Font("Arial", Font.BOLD, 14));
                g2.drawString("⚠", x-7, y+5);
                g2.setFont(new Font("Arial", Font.BOLD, 10));
            } else if (r.id == selectedRegion) {
                // Selected and colored
                g2.setColor(new Color(80, 0, 160));
                g2.setFont(new Font("Arial", Font.BOLD, 13));
                g2.drawString("✓", x-5, y+5);
                g2.setFont(new Font("Arial", Font.BOLD, 10));
            }
            // else: colored + ok → just show the fill color, no label needed
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private JButton makeBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(Color.WHITE); b.setOpaque(true);
        b.setBorderPainted(false); b.setFont(new Font("Arial", Font.BOLD, 13));
        b.setPreferredSize(new Dimension(130, 36));
        return b;
    }

    private JLabel makeLabel(String text, int size, int style, Color fg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Arial", style, size)); l.setForeground(fg); return l;
    }
}

// ============================================================================
// MAIN
// ============================================================================
public class human {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║  🎨 MAP COLORING — HUMAN PLAYER                          ║");
            System.out.println("║  Click a region, then click a color button to paint it.  ║");
            System.out.println("║  Locked regions are pre-colored clues.                   ║");
            System.out.println("║  Goal: color all regions — no two adjacent same color.   ║");
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
            new GameGUI(25, 4, 20, 25).setVisible(true);
        });
    }
}