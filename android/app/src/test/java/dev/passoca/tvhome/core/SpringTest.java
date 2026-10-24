package dev.passoca.tvhome.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class SpringTest {
    private static float settle(Spring s, float from, float to, float[] maxOut) {
        Spring.Value v = new Spring.Value();
        v.jump(from);
        v.target = to;
        float max = from;
        int frames = 0;
        while (v.step(s, 1f / 60f) && frames < 600) { frames++; max = Math.max(max, v.position); }
        maxOut[0] = max;
        return frames;
    }

    @Test public void paramsMatchTheLauncher() {
        Config c = Config.parse(null);
        Spring s = Spring.forConfig(c);
        assertEquals(260f, s.stiffness, 0f);
        assertEquals(0.58f, s.damping, 0f);
        c.advancedEffects = true;
        assertEquals(320f, Spring.forConfig(c).stiffness, 0f);
        c.focusOvershoot = false;
        assertEquals(1f, Spring.forConfig(c).damping, 0f);
        c.reducedMotion = true;
        assertEquals(1400f, Spring.forConfig(c).stiffness, 0f);
    }

    @Test public void overshootsThenSettlesExactly() {
        float[] max = new float[1];
        float frames = settle(new Spring(260f, 0.58f), 0f, 100f, max);
        assertTrue("springs past the target", max[0] > 100f);
        assertTrue("settles within two seconds", frames < 120);
    }

    @Test public void criticallyDampedNeverOvershoots() {
        float[] max = new float[1];
        settle(new Spring(440f, 1f), 0f, 100f, max);
        assertTrue(max[0] <= 100f + Spring.REST_POSITION);
    }

    @Test public void longFrameIsClamped() {
        Spring.Value v = new Spring.Value();
        v.jump(0f);
        v.target = 100f;
        v.step(new Spring(260f, 0.58f), 5f);   // a 5 s hitch must not explode
        assertTrue(Math.abs(v.position) < 200f);
    }

    @Test public void focusMoveEndsWithinAboutHalfASecond() {
        float[] max = new float[1];
        assertTrue("default spring, one tile (210 dp)", settle(new Spring(260f, 0.58f), 0f, 210f, max) <= 40);
        assertTrue("advanced effects", settle(new Spring(320f, 0.76f), 0f, 210f, max) <= 30);
    }
}
