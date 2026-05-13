package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderTrajectoryPlannerTest {

    @Test
    void trajectoryUsesFittsSizedOvershootAndReturnsNearTarget() {
        SliderTrajectoryPlanner planner = new SliderTrajectoryPlanner();

        SliderTrajectoryPlanner.TrajectoryPlan plan = planner.plan(260D, 1);
        List<SliderTrajectoryPlanner.TrajectoryPoint> points = plan.getPoints();
        double maxX = points.stream()
                .mapToDouble(SliderTrajectoryPlanner.TrajectoryPoint::getX)
                .max()
                .orElse(0D);
        double finalX = points.get(points.size() - 1).getX();
        long distinctRoundedY = points.stream()
                .map(point -> Math.round(point.getY() * 10D))
                .distinct()
                .count();

        assertTrue(points.size() >= 19 && points.size() <= 50);
        assertTrue(maxX > 261D);
        assertTrue(Math.abs(finalX - 260D) <= 1D);
        assertTrue(distinctRoundedY > 3);
    }

    @Test
    void perlinNoiseIsContinuousAcrossNearbySamples() {
        double previous = SliderTrajectoryPlanner.perlinNoise1d(1.20D, 123D);
        double current = SliderTrajectoryPlanner.perlinNoise1d(1.21D, 123D);

        assertTrue(Math.abs(current - previous) < 0.08D);
    }
}
