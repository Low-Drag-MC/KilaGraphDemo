package com.lowdragmc.kilagraphdemo.farm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FarmSimulationTest {

    @Test
    void plantingOnEmptyFertileCellMakesItGrowing() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, FarmConfig.DEFAULT);

        assertTrue(sim.plant(1, 1), "planting on empty fertile cell should succeed");
        assertEquals(Stage.GROWING, sim.getStage(1, 1));
    }

    @Test
    void cannotPlantOnAnAlreadyOccupiedCell() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, FarmConfig.DEFAULT);
        sim.plant(1, 1);

        assertFalse(sim.plant(1, 1), "planting on an occupied cell should fail");
    }

    @Test
    void pumpkinRipensAfterGrowTicks() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, new FarmConfig(5, 10, 4));
        sim.plant(1, 1);

        for (int i = 0; i < 4; i++) sim.tick();
        assertEquals(Stage.GROWING, sim.getStage(1, 1), "still growing before growTicks");

        sim.tick();
        assertEquals(Stage.RIPE, sim.getStage(1, 1), "ripe at growTicks");
    }

    @Test
    void ripePumpkinRotsAfterFreshTicks() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, new FarmConfig(5, 10, 4));
        sim.plant(1, 1);
        for (int i = 0; i < 5; i++) sim.tick(); // now RIPE
        assertEquals(Stage.RIPE, sim.getStage(1, 1));

        for (int i = 0; i < 9; i++) sim.tick();
        assertEquals(Stage.RIPE, sim.getStage(1, 1), "still fresh before freshTicks");

        sim.tick();
        assertEquals(Stage.ROTTEN, sim.getStage(1, 1), "rots at freshTicks");
    }

    @Test
    void harvestingRipeBasePumpkinScoresOneAndClearsCell() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, new FarmConfig(5, 10, 4));
        sim.plant(1, 1);
        for (int i = 0; i < 5; i++) sim.tick();

        assertEquals(1, sim.harvest(1, 1), "ripe base pumpkin worth 1");
        assertEquals(1, sim.getScore());
        assertEquals(Stage.EMPTY, sim.getStage(1, 1), "harvested cell is cleared");
    }

    @Test
    void harvestingImmatureOrEmptyScoresNothing() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, new FarmConfig(5, 10, 4));
        assertEquals(0, sim.harvest(1, 1), "empty cell yields nothing");

        sim.plant(1, 1);
        assertEquals(0, sim.harvest(1, 1), "growing cell yields nothing");
        assertEquals(0, sim.getScore());
        assertEquals(Stage.GROWING, sim.getStage(1, 1));
    }

    @Test
    void clearingRemovesRottenPumpkinAndScoresNothing() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, new FarmConfig(5, 10, 4));
        sim.plant(1, 1);
        for (int i = 0; i < 15; i++) sim.tick(); // grow then rot
        assertEquals(Stage.ROTTEN, sim.getStage(1, 1));

        assertTrue(sim.clear(1, 1), "clearing a rotten pumpkin succeeds");
        assertEquals(Stage.EMPTY, sim.getStage(1, 1));
        assertEquals(0, sim.getScore());
    }

    @Test
    void clearingAnEmptyCellFails() {
        FarmSimulation sim = FarmSimulation.allFertile(3, 3, new FarmConfig(5, 10, 4));
        assertFalse(sim.clear(1, 1));
    }

    /** Plant a w x h block of pumpkins with its corner at (x0,z0) and ripen them. */
    private static FarmSimulation ripeBlock(int fieldW, int fieldH, int x0, int z0, int w, int h, FarmConfig cfg) {
        FarmSimulation sim = FarmSimulation.allFertile(fieldW, fieldH, cfg);
        for (int dz = 0; dz < h; dz++)
            for (int dx = 0; dx < w; dx++)
                sim.plant(x0 + dx, z0 + dz);
        for (int i = 0; i < cfg.growTicks(); i++) sim.tick();
        return sim;
    }

    @Test
    void fourRipePumpkinsInASquareMergeIntoOneBigPumpkin() {
        FarmSimulation sim = ripeBlock(4, 4, 0, 0, 2, 2, new FarmConfig(5, 100, 4));

        assertEquals(2, sim.getMergeSize(0, 0), "core holds the merge size");
        assertEquals(Stage.RIPE, sim.getStage(0, 0), "core is a ripe big pumpkin");
        assertEquals(Stage.MERGED_MEMBER, sim.getStage(1, 0));
        assertEquals(Stage.MERGED_MEMBER, sim.getStage(0, 1));
        assertEquals(Stage.MERGED_MEMBER, sim.getStage(1, 1));
    }

    @Test
    void greedyMergePrefersTheLargestSquare() {
        FarmSimulation sim = ripeBlock(3, 3, 0, 0, 3, 3, new FarmConfig(5, 100, 4));

        assertEquals(3, sim.getMergeSize(0, 0), "merges as a single 3x3");
        int cores = 0;
        for (int z = 0; z < 3; z++)
            for (int x = 0; x < 3; x++)
                if (sim.getMergeSize(x, z) > 1) cores++;
        assertEquals(1, cores, "exactly one big pumpkin, not several small ones");
    }

    @Test
    void harvestingMergedPumpkinScoresNCubedAndClearsWholeBlock() {
        FarmSimulation sim = ripeBlock(4, 4, 0, 0, 2, 2, new FarmConfig(5, 100, 4));

        assertEquals(8, sim.harvest(0, 0), "2x2 merged worth N^3 = 8");
        assertEquals(8, sim.getScore());
        for (int z = 0; z < 2; z++)
            for (int x = 0; x < 2; x++)
                assertEquals(Stage.EMPTY, sim.getStage(x, z), "whole block cleared");
    }

    @Test
    void harvestingMergedPumpkinWorksFromAnyMemberCell() {
        FarmSimulation sim = ripeBlock(4, 4, 0, 0, 2, 2, new FarmConfig(5, 100, 4));

        assertEquals(8, sim.harvest(1, 1), "harvest from a member cell still works");
        for (int z = 0; z < 2; z++)
            for (int x = 0; x < 2; x++)
                assertEquals(Stage.EMPTY, sim.getStage(x, z));
    }

    @Test
    void mergedPumpkinRotsAfterFreshTicksAndScoresNothing() {
        FarmSimulation sim = ripeBlock(4, 4, 0, 0, 2, 2, new FarmConfig(5, 10, 4));

        // A 2x2 big pumpkin stays fresh N=2x longer than a base one (freshTicks * n), so its window is 20.
        for (int i = 0; i < 20; i++) sim.tick();
        assertEquals(Stage.ROTTEN, sim.getStage(0, 0), "big pumpkin rots");
        assertEquals(0, sim.harvest(0, 0), "rotten big pumpkin scores nothing");
    }

    @Test
    void clearingMergedBlockFromAnyCellClearsWholeBlock() {
        FarmSimulation sim = ripeBlock(4, 4, 0, 0, 2, 2, new FarmConfig(5, 100, 4));

        assertTrue(sim.clear(1, 0), "clear from a member cell succeeds");
        for (int z = 0; z < 2; z++)
            for (int x = 0; x < 2; x++)
                assertEquals(Stage.EMPTY, sim.getStage(x, z), "whole block cleared");
    }

    @Test
    void exposesGridDimensions() {
        FarmSimulation sim = FarmSimulation.allFertile(7, 5, FarmConfig.DEFAULT);
        assertEquals(7, sim.getWidth());
        assertEquals(5, sim.getHeight());
    }

    @Test
    void cannotPlantOnNonFertileCell() {
        boolean[] fertile = {true, false, true, true}; // 2x2 grid; (1,0) is not fertile
        FarmSimulation sim = FarmSimulation.of(2, 2, fertile, FarmConfig.DEFAULT);

        assertFalse(sim.isFertile(1, 0));
        assertFalse(sim.plant(1, 0), "cannot plant off the field");
        assertTrue(sim.plant(0, 0));
    }

    @Test
    void sameScriptProducesSameScoreEveryRun() {
        assertEquals(runScript(), runScript(), "deterministic score for fair leaderboard");
    }

    private static int runScript() {
        FarmSimulation sim = FarmSimulation.allFertile(5, 5, new FarmConfig(5, 50, 4));
        for (int z = 0; z < 2; z++)
            for (int x = 0; x < 2; x++)
                sim.plant(x, z);
        sim.plant(4, 4);
        for (int i = 0; i < 6; i++) sim.tick();
        sim.harvest(0, 0); // merged 2x2 -> 8
        sim.harvest(4, 4); // base -> 1
        return sim.getScore();
    }
}
