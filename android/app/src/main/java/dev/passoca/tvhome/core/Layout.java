package dev.passoca.tvhome.core;

/**
 * Home geometry in dp on the 960 x 540 dp canvas a 1080p TV reports at density 2. These are the numbers
 * the CleanYourTV launcher uses (AppAdapter, DockDecoration, MainActivity), ported once and shared by the
 * web prototype (web/index.html, section 4) and the TV build.
 */
public final class Layout {

    public static final float STAGE_W = 960f, STAGE_H = 540f;
    /** Grid content clips at this line, just under the header. */
    public static final float CLIP_TOP = 118f;
    /** Grid side padding (56 page + 10 grid) and inner width. */
    public static final float GRID_LEFT = 66f, GRID_INNER_W = 828f;
    /** Dock band padding (DockDecoration): 14 above and below, up to 22 at the sides. */
    public static final float BAND_PAD_Y = 14f, BAND_PAD_X = 22f;
    /** Where the dock row's centre sits. */
    public static final float DOCK_CENTER_Y = 411f;
    /** RecyclerView-style scroll window for grid rows. */
    public static final float SCROLL_TOP_LINE = 128f, SCROLL_BOTTOM_LINE = 500f;

    private Layout() {}

    /** Tiles are 180 dp at 4 columns and shrink from 5 up (about 144 at 5, 117 at 6). */
    public static float tileW(int columns) {
        switch (columns) {
            case 5: return 144f;
            case 6: return 117f;
            default: return 180f;
        }
    }

    public static float tileH(int columns) { return tileW(columns) * 101f / 180f; }

    public static float dockTop(int columns) { return DOCK_CENTER_Y - tileH(columns) / 2f; }

    /** Row 0 is the dock; the first grid row sits a little further down than the rows after it. */
    public static float rowTop(int row, int columns) {
        float h = tileH(columns);
        if (row == 0) return dockTop(columns);
        return dockTop(columns) + (h + 56f) + (row - 1) * (h + 45f);
    }

    public static Cell cell(int index, int columns) {
        int row = index / columns, col = index % columns;
        float span = GRID_INNER_W / columns, w = tileW(columns);
        return new Cell(GRID_LEFT + col * span + (span - w) / 2f, rowTop(row, columns), w, tileH(columns), row, col);
    }

    /** The dock band around the first {@code dockCount} tiles. */
    public static Rect band(int columns, int dockCount) {
        Cell a = cell(0, columns), b = cell(Math.max(0, Math.min(columns, dockCount) - 1), columns);
        float side = Math.min(BAND_PAD_X, a.x - 57f);
        return new Rect(a.x - side, a.y - BAND_PAD_Y, b.x + b.w - a.x + 2 * side, a.h + 2 * BAND_PAD_Y);
    }

    /** AppAdapter.focusScale: 1.10, 1.12 with advanced effects, 1 with animations off. */
    public static float focusScale(Config c) {
        if (!c.animations) return 1f;
        return c.advancedEffects ? 1.12f : 1.10f;
    }

    /**
     * Next scroll offset for a focused cell: the dock parks at 0; a grid row scrolls just far enough to be
     * fully visible between the header line and the bottom line, otherwise the scroll stays where it is.
     */
    public static float scrollFor(Cell c, float current) {
        if (c.row == 0) return 0f;
        float top = c.y - 10f, bottom = c.y + c.h + 38f, s = current;
        if (bottom - s > SCROLL_BOTTOM_LINE) s = bottom - SCROLL_BOTTOM_LINE;
        if (top - s < SCROLL_TOP_LINE) s = top - SCROLL_TOP_LINE;
        return s;
    }

    /** Which of the {@code topCount} right-aligned header buttons sits above a dock column. */
    public static int topAbove(int col, int columns, int topCount) {
        return Math.max(0, Math.min(topCount - 1, col - (columns - topCount)));
    }

    /** The app title above the dock: its bottom edge sits 26 dp above the band. */
    public static float heroBottom(int columns) { return dockTop(columns) - BAND_PAD_Y - 26f; }

    public static final class Cell {
        public final float x, y, w, h;
        public final int row, col;
        Cell(float x, float y, float w, float h, int row, int col) { this.x = x; this.y = y; this.w = w; this.h = h; this.row = row; this.col = col; }
    }

    public static final class Rect {
        public final float x, y, w, h;
        Rect(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }
}
