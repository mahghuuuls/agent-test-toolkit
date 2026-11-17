package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The direction to a point, the short way round, the pitch's limits and the steps of a slow turn. */
class AimTest {

    private static final double EPS = 1.0E-9;

    @Test
    @DisplayName("the game's angles: south is yaw 0, west 90, north 180, east -90; below is positive pitch")
    void toward() {
        assertEquals(0.0, Aim.toward(45, 0, 64, 0, 0, 64, 5)[0], EPS);
        assertEquals(90.0, Aim.toward(45, 0, 64, 0, -5, 64, 0)[0], EPS);
        assertEquals(180.0, Math.abs(Aim.toward(45, 0, 64, 0, 0, 64, -5)[0]), EPS);
        assertEquals(-90.0, Aim.toward(45, 0, 64, 0, 5, 64, 0)[0], EPS);
        assertEquals(0.0, Aim.toward(45, 0, 64, 0, 0, 64, 5)[1], EPS);
        assertEquals(45.0, Aim.toward(45, 0, 64, 0, 0, 59, 5)[1], EPS, "five down, five ahead");
        assertEquals(-45.0, Aim.toward(45, 0, 64, 0, 5, 69, 0)[1], EPS);
    }

    @Test
    @DisplayName("straight above or below keeps the current yaw, and the eye's own point turns nowhere")
    void straightUpOrDown() {
        double[] up = Aim.toward(33.0, 1, 64, 1, 1, 70, 1);
        assertEquals(33.0, up[0], EPS);
        assertEquals(-90.0, up[1], EPS);
        assertEquals(90.0, Aim.toward(33.0, 1, 64, 1, 1, 60, 1)[1], EPS);
        double[] here = Aim.toward(33.0, 1, 64, 1, 1, 64, 1);
        assertEquals(33.0, here[0], EPS);
        assertEquals(0.0, here[1], EPS);
    }

    @Test
    @DisplayName("the yaw change takes the short way round, across 180 and from any number of whole turns")
    void yawWraps() {
        assertEquals(20.0, Aim.yawChange(350, 10), EPS);
        assertEquals(-20.0, Aim.yawChange(10, 350), EPS);
        assertEquals(90.0, Aim.yawChange(0, 90), EPS);
        assertEquals(-90.0, Aim.yawChange(0, -90), EPS);
        assertEquals(180.0, Aim.yawChange(0, 180), EPS, "half a turn goes the positive way");
        assertEquals(180.0, Aim.yawChange(0, -180), EPS);
        assertEquals(10.0, Aim.yawChange(1000, 290), EPS, "1000 is 280 after two turns");
        assertEquals(0.0, Aim.yawChange(-270, 90), EPS);
        assertEquals(-10.0, Aim.yawChange(-355, -5), EPS);
    }

    @Test
    @DisplayName("pitch stays between straight up and straight down")
    void pitchClamps() {
        assertEquals(90.0, Aim.clampPitch(95), EPS);
        assertEquals(-90.0, Aim.clampPitch(-120), EPS);
        assertEquals(12.5, Aim.clampPitch(12.5), EPS);
    }

    @Test
    @DisplayName("a slow turn's steps are even and add up to the whole change")
    void steps() {
        double total = 0;
        for (int i = 1; i <= 7; i++) {
            double step = Aim.step(100.0, 7, i);
            assertEquals(100.0 / 7, step, 1.0E-9);
            total += step;
        }
        assertEquals(100.0, total, 1.0E-9);
        assertEquals(-33.0, Aim.step(-33.0, 1, 1), EPS, "one tick is the whole turn");
        assertEquals(0.0, Aim.step(-33.0, 1, 2), EPS);
        double sum = 0;
        for (int i = 1; i <= 200; i++) {
            sum += Aim.step(-179.999, 200, i);
        }
        assertEquals(-179.999, sum, 1.0E-9);
    }

    @Test
    @DisplayName("the mouse turn's amounts: the view moves by 0.15 of them, and its pitch runs the other way")
    void mouseAmounts() {
        assertEquals(90.0, Aim.mouseYaw(90.0) * 0.15, 1.0E-4);
        assertEquals(-30.0, Aim.mousePitch(30.0) * 0.15, 1.0E-4);
    }
}
