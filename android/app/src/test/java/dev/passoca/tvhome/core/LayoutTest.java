package dev.passoca.tvhome.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class LayoutTest {
    private static final float E = 0.01f;

    @Test public void tileSizesFollowColumns() {
        assertEquals(180f, Layout.tileW(4), E);
        assertEquals(144f, Layout.tileW(5), E);
        assertEquals(117f, Layout.tileW(6), E);
        assertEquals(101f, Layout.tileH(4), E);
    }

    @Test public void dockIsCentredOnItsLine() {
        Layout.Cell c = Layout.cell(0, 4);
        assertEquals(Layout.DOCK_CENTER_Y, c.y + c.h / 2f, E);
        assertEquals(0, c.row);
    }

    @Test public void cellsAreCentredInTheirSpan() {
        Layout.Cell c = Layout.cell(5, 4);
        assertEquals(1, c.row);
        assertEquals(1, c.col);
        float span = Layout.GRID_INNER_W / 4;
        assertEquals(Layout.GRID_LEFT + span + (span - 180f) / 2f, c.x, E);
    }

    @Test public void firstGridRowSitsFurtherThanLaterRows() {
        float r0 = Layout.rowTop(0, 4), r1 = Layout.rowTop(1, 4), r2 = Layout.rowTop(2, 4);
        assertEquals(101f + 56f, r1 - r0, E);
        assertEquals(101f + 45f, r2 - r1, E);
    }

    @Test public void bandWrapsTheDockWithPadding() {
        Layout.Rect b = Layout.band(4, 4);
        Layout.Cell first = Layout.cell(0, 4), last = Layout.cell(3, 4);
        assertEquals(first.y - 14f, b.y, E);
        assertEquals(101f + 28f, b.h, E);
        assertTrue(b.x < first.x);
        assertTrue(b.x + b.w > last.x + last.w);
    }

    @Test public void focusScaleFollowsMotionSettings() {
        Config c = Config.parse(null);
        assertEquals(1.10f, Layout.focusScale(c), E);
        c.advancedEffects = true;
        assertEquals(1.12f, Layout.focusScale(c), E);
        c.animations = false;
        assertEquals(1f, Layout.focusScale(c), E);
    }

    @Test public void dockParksScrollAtZero() {
        assertEquals(0f, Layout.scrollFor(Layout.cell(2, 4), 300f), E);
    }

    @Test public void gridRowScrollsJustIntoView() {
        Layout.Cell row1 = Layout.cell(4, 4);
        float s = Layout.scrollFor(row1, 0f);
        assertTrue(s > 0f);
        assertEquals(Layout.SCROLL_BOTTOM_LINE, row1.y + row1.h + 38f - s, E);
        // already visible: stays put
        assertEquals(s, Layout.scrollFor(row1, s), E);
    }

    @Test public void topAboveMapsRightColumnsToTheIcons() {
        assertEquals(0, Layout.topAbove(0, 4, 3));
        assertEquals(0, Layout.topAbove(1, 4, 3));
        assertEquals(1, Layout.topAbove(2, 4, 3));
        assertEquals(2, Layout.topAbove(3, 4, 3));
        assertEquals(2, Layout.topAbove(5, 6, 3));
    }
}
