package dev.passoca.tvhome.core;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class HomeModelTest {
    private HomeModel model(int apps) {
        String[] a = new String[apps];
        for (int i = 0; i < apps; i++) a[i] = "app" + i;
        return new HomeModel(List.of(a), 4, 3);
    }

    @Test public void moveAcrossAndDown() {
        HomeModel m = model(10);
        assertTrue(m.move(HomeModel.Dir.RIGHT));
        assertEquals(1, m.tile);
        assertTrue(m.move(HomeModel.Dir.DOWN));
        assertEquals(5, m.tile);
        assertTrue(m.scroll > 0f);
    }

    @Test public void edgesStop() {
        HomeModel m = model(10);
        assertFalse(m.move(HomeModel.Dir.LEFT));
        m.focusTile(3);
        assertFalse(m.move(HomeModel.Dir.RIGHT));
    }

    @Test public void downFromAShortLastRowLandsOnTheLastApp() {
        HomeModel m = model(6);   // row 1 holds app4, app5
        m.focusTile(3);
        assertTrue(m.move(HomeModel.Dir.DOWN));
        assertEquals(5, m.tile);
    }

    @Test public void upFromTheDockGoesToTheTopBarAndBack() {
        HomeModel m = model(8);
        m.focusTile(3);
        assertTrue(m.move(HomeModel.Dir.UP));
        assertEquals(HomeModel.Zone.TOP, m.zone);
        assertEquals(2, m.top);
        assertTrue(m.moveTop(HomeModel.Dir.LEFT));
        assertEquals(1, m.top);
        assertTrue(m.moveTop(HomeModel.Dir.DOWN));
        assertEquals(HomeModel.Zone.TILES, m.zone);
        assertEquals(3, m.tile);
    }

    @Test public void pickedTileSwapsAsItMoves() {
        HomeModel m = model(8);
        m.reorder = true;
        m.picked = true;
        m.move(HomeModel.Dir.RIGHT);
        assertEquals("app1", m.order.get(0));
        assertEquals("app0", m.order.get(1));
        assertEquals(1, m.tile);
        assertFalse("a picked tile stays in the grid", m.move(HomeModel.Dir.UP) && m.zone == HomeModel.Zone.TOP);
    }

    @Test public void backFromAGridRowReturnsToTheDock() {
        HomeModel m = model(8);
        m.focusTile(6);
        assertTrue(m.back());
        assertEquals(2, m.tile);
        assertEquals(0f, m.scroll, 0f);
    }

    @Test public void fewerColumnsKeepFocusInRange() {
        HomeModel m = model(5);
        m.focusTile(4);
        m.setColumns(6);
        assertEquals(4, m.tile);
        assertEquals(0f, m.scroll, 0f);   // all five now fit in the dock row
    }

    @Test public void menuMoveSkipsSeparators() {
        boolean[] seps = {false, false, true, false, true, false};
        assertEquals(3, HomeModel.menuMove(seps, 1, 1));
        assertEquals(5, HomeModel.menuMove(seps, 3, 1));
        assertEquals(5, HomeModel.menuMove(seps, 5, 1));
        assertEquals(1, HomeModel.menuMove(seps, 3, -1));
    }

    @Test public void arrangeUsesSavedOrderThenDock() {
        List<String> installed = List.of("a", "b", "c", "d");
        assertEquals(List.of("c", "a", "b", "d"), HomeModel.arrange(installed, List.of(), List.of("c", "x", "a")));
        assertEquals(List.of("d", "b", "a", "c"), HomeModel.arrange(installed, List.of("d", "gone", "b"), List.of("c")));
    }
}
