package dev.passoca.tvhome.core;

/**
 * The focus frame's spring, with the stiffness and damping the CleanYourTV launcher gives its glass
 * highlight (MainActivity: 260 / 0.58 normally, 320 / 0.76 with advanced effects, critically damped
 * when focus_overshoot is off, near-instant under reduced motion). Unit mass, semi-implicit Euler with
 * two half steps per frame, the same integrator as the web prototype.
 */
public final class Spring {

    /** At rest within half a dp (one pixel on a 1080p TV) and below 5 dp/s: the tail after that is invisible,
     *  and on her TV every extra frame costs a full 4K GPU composite. */
    public static final float REST_POSITION = 0.5f, REST_VELOCITY = 5f;

    public final float stiffness, damping;

    public Spring(float stiffness, float damping) { this.stiffness = stiffness; this.damping = damping; }

    public static Spring forConfig(Config c) {
        if (c.reducedMotion) return new Spring(1400f, 1f);
        if (!c.focusOvershoot) return new Spring(c.advancedEffects ? 520f : 440f, 1f);
        return c.advancedEffects ? new Spring(320f, 0.76f) : new Spring(260f, 0.58f);
    }

    /** One animated value. {@link #step} returns whether it is still moving. */
    public static final class Value {
        public float position, velocity, target;

        public void jump(float to) { position = to; target = to; velocity = 0f; }

        public boolean step(Spring s, float dtSeconds) {
            float dt = Math.min(0.032f, Math.max(0f, dtSeconds));
            float c = 2f * s.damping * (float) Math.sqrt(s.stiffness);
            for (int i = 0; i < 2; i++) {
                float h = dt / 2f;
                float a = -s.stiffness * (position - target) - c * velocity;
                velocity += a * h;
                position += velocity * h;
            }
            if (Math.abs(position - target) <= REST_POSITION && Math.abs(velocity) <= REST_VELOCITY) {
                position = target;
                velocity = 0f;
                return false;
            }
            return true;
        }
    }
}
