package dev.passoca.tvhome.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Focus and order state of the home screen, with the D-pad rules of the web prototype's store and focus
 * engine (web/index.html sections 3 and 6). Pure Java: the activity feeds keys in and draws the result.
 */
public final class HomeModel {

    public enum Zone { TILES, TOP, MENU, SHEET, HELP }
    public enum Dir { UP, DOWN, LEFT, RIGHT }

    public final List<String> order = new ArrayList<>();
    public int columns;
    public final int topCount;

    public Zone zone = Zone.TILES;
    public int tile = 0, top = 0, dockReturn = 0;
    public float scroll = 0f;
    public boolean reorder = false, picked = false;

    public HomeModel(List<String> order, int columns, int topCount) {
        this.order.addAll(order);
        this.columns = columns;
        this.topCount = topCount;
    }

    public int dockCount() { return Math.min(columns, order.size()); }

    public String focusedApp() { return order.isEmpty() ? null : order.get(Math.min(tile, order.size() - 1)); }

    /** Moves focus over the tiles; returns true when focus or order changed. Up from the dock goes to the top bar. */
    public boolean move(Dir d) {
        int n = order.size();
        if (n == 0) return false;
        Layout.Cell q = Layout.cell(tile, columns);
        int t = tile;
        switch (d) {
            case LEFT: if (q.col > 0) t--; break;
            case RIGHT: if (q.col < columns - 1 && t + 1 < n) t++; break;
            case DOWN:
                if (tile + columns < n) t = tile + columns;
                else if ((n - 1) / columns > q.row) t = n - 1;
                break;
            case UP:
                if (q.row == 0) {
                    if (picked) return false;
                    dockReturn = tile;
                    zone = Zone.TOP;
                    top = Layout.topAbove(q.col, columns, topCount);
                    return true;
                }
                t = tile - columns;
                break;
        }
        if (t == tile) return false;
        if (picked) {
            String a = order.get(tile);
            order.set(tile, order.get(t));
            order.set(t, a);
        }
        focusTile(t);
        return true;
    }

    public void focusTile(int index) {
        zone = Zone.TILES;
        tile = Math.max(0, Math.min(index, order.size() - 1));
        scroll = Layout.scrollFor(Layout.cell(tile, columns), scroll);
    }

    /** Left / right along the top bar; down returns to the dock tile focus came from. */
    public boolean moveTop(Dir d) {
        switch (d) {
            case LEFT: if (top > 0) { top--; return true; } return false;
            case RIGHT: if (top < topCount - 1) { top++; return true; } return false;
            case DOWN: focusTile(dockReturn); return true;
            default: return false;
        }
    }

    /** New row width: the focused tile and scroll follow, but focus stays in its zone (the sheet that changed it). */
    public void setColumns(int columns) {
        this.columns = columns;
        tile = Math.max(0, Math.min(tile, order.size() - 1));
        scroll = Layout.scrollFor(Layout.cell(tile, columns), scroll);
        dockReturn = Math.min(dockReturn, Math.max(0, Math.min(columns, order.size()) - 1));
    }

    /** Back on a grid row jumps to the dock tile in the same column. */
    public boolean back() {
        Layout.Cell q = Layout.cell(tile, columns);
        if (zone == Zone.TILES && q.row > 0) { focusTile(q.col); return true; }
        if (zone == Zone.TOP) { focusTile(dockReturn); return true; }
        return false;
    }

    public void home() {
        zone = Zone.TILES; tile = 0; scroll = 0f; reorder = false; picked = false;
    }

    /** Menu rows: moves over separators; returns the new selection, or {@code sel} when at an end. */
    public static int menuMove(boolean[] separators, int sel, int delta) {
        int k = sel;
        do { k += delta; } while (k >= 0 && k < separators.length && separators[k]);
        return k >= 0 && k < separators.length ? k : sel;
    }

    /** Puts saved order first, then dock apps, then the rest as given; unknown saved names are dropped. */
    public static List<String> arrange(List<String> installed, List<String> saved, List<String> dock) {
        List<String> out = new ArrayList<>();
        for (String s : saved) if (installed.contains(s) && !out.contains(s)) out.add(s);
        if (out.isEmpty()) for (String s : dock) if (installed.contains(s) && !out.contains(s)) out.add(s);
        for (String s : installed) if (!out.contains(s)) out.add(s);
        return out;
    }
}
