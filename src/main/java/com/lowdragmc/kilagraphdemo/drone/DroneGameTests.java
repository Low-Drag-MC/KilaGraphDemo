package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.WhileNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.NotNode;
import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.drone.node.HarvestNode;
import com.lowdragmc.kilagraphdemo.drone.node.MoveNode;
import com.lowdragmc.kilagraphdemo.drone.node.MoveToCoordNode;
import com.lowdragmc.kilagraphdemo.drone.node.PlantNode;
import com.lowdragmc.kilagraphdemo.drone.node.ScanCellNode;
import com.lowdragmc.kilagraphdemo.drone.node.WaitNode;
import com.lowdragmc.kilagraphdemo.server.DroneLeaderboard;
import net.minecraft.core.Direction;
import com.lowdragmc.kilagraphdemo.farm.FarmConfig;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.kilagraphdemo.farm.Stage;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Vector2f;

import java.util.function.Consumer;

/**
 * GameTests for the drone mini-game: they build a {@link DroneGraph} programmatically, run a
 * {@link DroneRuntime} against a {@link FarmSimulation} and assert the resulting score, drive a real
 * {@link DroneStationBlockEntity} on its fixed field, and check official scoring + submission.
 *
 * <p>Lives in main (not test) sources so the GameTestServer, which loads the mod, can register them.
 * Reuses LDLib2's {@code ldlib2:empty} structure so no structure resource is needed here.</p>
 */
public final class DroneGameTests {

    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, Kilagraphdemo.MODID);
    private static boolean initialized = false;

    private static final String PLANT_GROW_HARVEST = "drone_plant_grow_harvest";
    private static final String DETERMINISTIC = "drone_deterministic_score";
    private static final String STATION_RUN = "drone_station_run";
    private static final String CHAINED_MOVES = "drone_chained_moves";
    private static final String MOVE_TO_COORD = "drone_move_to_coord";
    private static final String CELLS_REUSED = "drone_cells_array_reused";
    private static final String SCORING_STANDARD = "drone_scoring_standard";
    private static final String SUBMIT_REMOVE = "drone_submit_remove";
    private static final String SCAN_PLANT_COLUMN = "drone_scan_plant_column";
    private static final String GROWTH_RANDOM = "drone_growth_randomness";

    private DroneGameTests() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) return;
        initialized = true;
        registerFunction(PLANT_GROW_HARVEST, DroneGameTests::plantGrowHarvest);
        registerFunction(DETERMINISTIC, DroneGameTests::deterministicScore);
        registerFunction(STATION_RUN, DroneGameTests::stationRun);
        registerFunction(CHAINED_MOVES, DroneGameTests::chainedMoves);
        registerFunction(MOVE_TO_COORD, DroneGameTests::moveToCoord);
        registerFunction(CELLS_REUSED, DroneGameTests::cellsArrayReused);
        registerFunction(SCORING_STANDARD, DroneGameTests::scoringStandard);
        registerFunction(SUBMIT_REMOVE, DroneGameTests::submitRemove);
        registerFunction(SCAN_PLANT_COLUMN, DroneGameTests::scanPlantColumn);
        registerFunction(GROWTH_RANDOM, DroneGameTests::growthRandomness);
        TEST_FUNCTIONS.register(eventBus);
        eventBus.addListener(DroneGameTests::registerGameTests);
    }

    // ---- test bodies --------------------------------------------------------------------------

    /** A drone that plants, waits for the pumpkin to ripen, then harvests should bank exactly 1 point. */
    private static void plantGrowHarvest(GameTestHelper helper) {
        int score = runPlantGrowHarvest();
        if (score != 1) {
            helper.fail("expected score 1 from plant->grow->harvest, got " + score);
            return;
        }
        helper.succeed();
    }

    /** The same program must produce the same score every run (fixed-seed determinism). */
    private static void deterministicScore(GameTestHelper helper) {
        int a = runPlantGrowHarvest();
        int b = runPlantGrowHarvest();
        if (a != b) {
            helper.fail("non-deterministic score: " + a + " != " + b);
            return;
        }
        helper.succeed();
    }

    private static int runPlantGrowHarvest() {
        // Small, fast-growing, slow-rotting config so the timing is unambiguous.
        FarmSimulation sim = FarmSimulation.allFertile(1, 1, new FarmConfig(5, 1000, 4));
        DroneApi api = new DroneApi(sim, 0, 0);
        DroneGraph graph = buildPlantWaitHarvest(60);

        DroneRuntime runtime = new DroneRuntime(graph, api, 1234L);
        for (int t = 0; t < 4000 && !runtime.isFinished() && !runtime.isHalted(); t++) {
            runtime.tick(t);
        }
        return runtime.getScore();
    }

    /** Build an {@code Entry -> Plant -> Wait(waitTicks) -> Harvest} program for the cell under the drone. */
    private static DroneGraph buildPlantWaitHarvest(int waitTicks) {
        DroneGraph graph = new DroneGraph();
        CustomGraphModelImpl gm = graph.graphModel;
        NodeModel entry = gm.createNodeModel(new EntryNode(), new Vector2f(0, 0));
        NodeModel plant = gm.createNodeModel(new PlantNode(), new Vector2f(100, 0));
        NodeModel wait = gm.createNodeModel(new WaitNode(), new Vector2f(200, 0));
        NodeModel harvest = gm.createNodeModel(new HarvestNode(), new Vector2f(300, 0));
        setConstant(wait, "ticks", waitTicks);
        wireExec(gm, entry, "next", plant, "trigger");
        wireExec(gm, plant, "next", wait, "trigger");
        wireExec(gm, wait, "next", harvest, "trigger");
        return graph;
    }

    /**
     * M4 end-to-end: serialize a program, drive it through a real {@link DroneStationBlockEntity}
     * placed over fertile soil, and assert the run finishes with the expected score. Exercises
     * field detection, {@link DroneGraphCodec} round-trip, {@code startRun} and the {@code tickRun}
     * loop together. Drives {@code tickRun()} directly (bypassing the owner-online policy that
     * {@code serverTick} adds, which a headless test can't satisfy).
     */
    private static void stationRun(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistries.DRONE_STATION_BLOCK.get());
        BlockPos stationWorld = helper.absolutePos(new BlockPos(0, 2, 0));
        if (!(helper.getLevel().getBlockEntity(stationWorld) instanceof DroneStationBlockEntity be)) {
            helper.fail("drone station block entity missing");
            return;
        }
        be.setOwner(java.util.UUID.randomUUID());

        // Fixed field; drone starts on the station OUTSIDE the field, so the program must fly in (south)
        // before it can farm. Default config growTicks=600, so wait long enough for the pumpkin to ripen.
        var provider = helper.getLevel().registryAccess();
        CompoundTag programTag = DroneGraphCodec.toTag(buildMoveInGrowHarvest(), provider);

        if (!be.startRun(programTag)) {
            helper.fail("startRun returned false");
            return;
        }
        if (be.getRunState() != RunState.RUNNING) {
            helper.fail("expected RUNNING after startRun, got " + be.getRunState());
            return;
        }

        // Grow time is randomized (up to ~grow mean + 2σ); poll generously until the run completes.
        for (int t = 0; t < 6000 && be.getRunState() == RunState.RUNNING; t++) {
            be.tickRun();
        }
        if (be.getRunState() != RunState.FINISHED) {
            helper.fail("expected FINISHED, got " + be.getRunState() + " at tick " + be.getRunTick());
            return;
        }
        if (be.getScore() != 1) {
            helper.fail("expected score 1 from station run, got " + be.getScore());
            return;
        }
        helper.succeed();
    }

    /**
     * Pumpkin grow times are per-pumpkin random (Gaussian), but seeded: assert (a) the same seed reproduces
     * identical ripen ticks (fair leaderboard), (b) they actually vary cell-to-cell (not a fixed timer), and
     * (c) every draw lands within the configured mean ± 2σ range.
     */
    private static void growthRandomness(GameTestHelper helper) {
        int[] a = ripenTicks(123L);
        int[] b = ripenTicks(123L);
        if (!java.util.Arrays.equals(a, b)) {
            helper.fail("grow times not reproducible for a fixed seed: " + java.util.Arrays.toString(a)
                    + " vs " + java.util.Arrays.toString(b));
            return;
        }
        boolean varied = false;
        for (int i = 1; i < a.length; i++) {
            if (a[i] != a[0]) { varied = true; break; }
        }
        if (!varied) {
            helper.fail("grow times are not randomized — all identical (" + a[0] + ")");
            return;
        }
        int mean = FarmConfig.DEFAULT.growTicks();
        int jit = FarmConfig.DEFAULT.growJitter();
        int lo = Math.max(1, mean - 2 * jit), hi = mean + 2 * jit;
        for (int t : a) {
            if (t < 0) { helper.fail("a pumpkin never ripened within the expected window"); return; }
            if (t < lo || t > hi) {
                helper.fail("grow time " + t + " outside expected range [" + lo + ", " + hi + "]");
                return;
            }
        }
        helper.succeed();
    }

    /** Plant a 9x1 row (no merges possible) under one seed and record the tick each cell first ripens. */
    private static int[] ripenTicks(long seed) {
        FarmSimulation sim = FarmSimulation.allFertile(9, 1, FarmConfig.DEFAULT, seed);
        for (int x = 0; x < 9; x++) sim.plant(x, 0);
        int[] ripenAt = new int[9];
        java.util.Arrays.fill(ripenAt, -1);
        int budget = FarmConfig.DEFAULT.growTicks() + 2 * FarmConfig.DEFAULT.growJitter() + 5;
        for (int t = 1; t <= budget; t++) {
            sim.tick();
            for (int x = 0; x < 9; x++) {
                if (ripenAt[x] < 0 && sim.getStage(x, 0) == Stage.RIPE) ripenAt[x] = t;
            }
        }
        return ripenAt;
    }

    /**
     * Regression for the invisible-board bug: the {@code @DescSynced int[] cells} must keep the <em>same</em>
     * array instance across same-size ticks. LDLib2's array delta only marks the field dirty on in-place
     * element changes; a fresh array each tick is silently never synced, leaving the client board empty.
     * {@code captureCells} therefore reuses the array — assert that here via reference identity.
     */
    private static void cellsArrayReused(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistries.DRONE_STATION_BLOCK.get());
        BlockPos stationWorld = helper.absolutePos(new BlockPos(0, 2, 0));
        if (!(helper.getLevel().getBlockEntity(stationWorld) instanceof DroneStationBlockEntity be)) {
            helper.fail("drone station block entity missing");
            return;
        }
        be.setOwner(java.util.UUID.randomUUID());
        var provider = helper.getLevel().registryAccess();
        if (!be.startRun(DroneGraphCodec.toTag(buildPlantWaitHarvest(700), provider))) {
            helper.fail("startRun returned false");
            return;
        }
        int[] first = be.getCells();
        if (first.length == 0) {
            helper.fail("cells empty after startRun — field not captured");
            return;
        }
        be.tickRun();
        int[] second = be.getCells();
        if (first != second) {
            helper.fail("cells array was reallocated across ticks — @DescSynced delta will never fire");
            return;
        }
        helper.succeed();
    }

    /**
     * Official scoring runs on the standardized field ({@link DroneScoring}) for the fixed tick budget,
     * not on the blocks under the station. The drone starts on the station <em>outside</em> the field at
     * {@code (0,-1)}, so the program must first fly south into the field before it can farm — proving the
     * off-field start works. The same program must score the same value every time (fair leaderboard),
     * and one fly-in + plant->grow->harvest banks exactly 1 point.
     */
    private static void scoringStandard(GameTestHelper helper) {
        int a = DroneScoring.scoreSync(buildMoveInGrowHarvest());
        int b = DroneScoring.scoreSync(buildMoveInGrowHarvest());
        if (a != b) {
            helper.fail("non-deterministic standardized score: " + a + " != " + b);
            return;
        }
        if (a != 1) {
            helper.fail("expected score 1 from fly-in + plant->grow->harvest on the standard field, got " + a);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code Entry -> Move(SOUTH) -> Plant -> While(NOT ripe){ Wait } -> Harvest}: fly in from the off-field
     * station, plant, then poll until the (now randomized) pumpkin ripens before harvesting. Robust to the
     * Gaussian grow time — it harvests whenever the pumpkin actually ripens, not after a fixed wait.
     */
    private static DroneGraph buildMoveInGrowHarvest() {
        DroneGraph graph = new DroneGraph();
        CustomGraphModelImpl gm = graph.graphModel;
        NodeModel entry = gm.createNodeModel(new EntryNode(), new Vector2f(0, 0));
        NodeModel move = gm.createNodeModel(new MoveNode(), new Vector2f(100, 0));
        NodeModel plant = gm.createNodeModel(new PlantNode(), new Vector2f(200, 0));
        NodeModel whileNode = gm.createNodeModel(new WhileNode(), new Vector2f(300, 0));
        NodeModel wait = gm.createNodeModel(new WaitNode(), new Vector2f(300, 140));
        NodeModel scan = gm.createNodeModel(new ScanCellNode(), new Vector2f(100, 220));
        NodeModel not = gm.createNodeModel(new NotNode(), new Vector2f(200, 220));
        NodeModel harvest = gm.createNodeModel(new HarvestNode(), new Vector2f(420, 0));
        setConstant(move, "direction", Direction.SOUTH);
        setConstant(wait, "ticks", 20);
        wireExec(gm, entry, "next", move, "trigger");
        wireExec(gm, move, "next", plant, "trigger");
        wireExec(gm, plant, "next", whileNode, "in");
        wireExec(gm, whileNode, "body", wait, "trigger");        // loop while not ripe: wait
        wireExec(gm, whileNode, "completed", harvest, "trigger"); // ripe: harvest
        wireExec(gm, scan, "ripe", not, "in");                    // data: cond = NOT scan(current).ripe
        wireExec(gm, not, "out", whileNode, "cond");
        return graph;
    }

    /**
     * A redstone rising edge submits the owner's solution: the program is stored in the leaderboard (for
     * preload + async scoring) and the station block removes itself to free the field for the next player.
     */
    private static void submitRemove(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistries.DRONE_STATION_BLOCK.get());
        BlockPos stationWorld = helper.absolutePos(new BlockPos(0, 2, 0));
        if (!(helper.getLevel().getBlockEntity(stationWorld) instanceof DroneStationBlockEntity be)) {
            helper.fail("drone station block entity missing");
            return;
        }
        java.util.UUID owner = java.util.UUID.randomUUID();
        be.setOwner(owner);
        var provider = helper.getLevel().registryAccess();
        be.setProgram(DroneGraphCodec.toTag(buildPlantWaitHarvest(700), provider));

        be.onRedstone(true); // rising edge -> submit + remove

        if (helper.getLevel().getBlockEntity(stationWorld) instanceof DroneStationBlockEntity) {
            helper.fail("station was not removed after submission");
            return;
        }
        CompoundTag stored = DroneLeaderboard.get(helper.getLevel()).getProgram(owner);
        if (stored == null || stored.isEmpty()) {
            helper.fail("submitted program was not stored in the leaderboard");
            return;
        }
        helper.succeed();
    }

    /**
     * Reproduces the user's "scan each cell, plant if empty else move on" loop over a 1x9 column and
     * asserts every cell ends up planted. A regression both for the planting logic and for sensor
     * freshness: {@link ScanCellNode} reads live drone position, and the executor would otherwise serve a
     * stale memoised reading after the drone moves ({@link DroneRuntime} clears the pull cache each tick).
     */
    private static void scanPlantColumn(GameTestHelper helper) {
        FarmSimulation sim = FarmSimulation.allFertile(1, 9, FarmConfig.DEFAULT);
        DroneApi api = new DroneApi(sim, 0, 0);
        DroneGraph graph = buildScanPlantColumn(40);

        DroneRuntime rt = new DroneRuntime(graph, api, 1L);
        for (int t = 0; t < 4000 && !rt.isFinished() && !rt.isHalted(); t++) {
            rt.tick(t);
        }
        for (int z = 0; z < 9; z++) {
            if (sim.getStage(0, z) == Stage.EMPTY) {
                helper.fail("cell (0," + z + ") not planted (got EMPTY)"
                        + " — drone z=" + api.z() + ", finished=" + rt.isFinished() + " halted=" + rt.isHalted());
                return;
            }
        }
        helper.succeed();
    }

    /**
     * {@code Entry -> For(count){ Scan(current); Branch(plantable){ true: Plant ; false: Move(SOUTH) } }}.
     * Plants when the current cell is empty, otherwise steps south — exactly the user's loop pattern.
     */
    private static DroneGraph buildScanPlantColumn(int count) {
        DroneGraph graph = new DroneGraph();
        CustomGraphModelImpl gm = graph.graphModel;
        NodeModel entry = gm.createNodeModel(new EntryNode(), new Vector2f(0, 0));
        NodeModel forNode = gm.createNodeModel(new ForNode(), new Vector2f(120, 0));
        NodeModel scan = gm.createNodeModel(new ScanCellNode(), new Vector2f(120, 180));
        NodeModel branch = gm.createNodeModel(new BranchNode(), new Vector2f(280, 0));
        NodeModel plant = gm.createNodeModel(new PlantNode(), new Vector2f(440, 0));
        NodeModel move = gm.createNodeModel(new MoveNode(), new Vector2f(440, 140));
        setConstant(forNode, "count", count);
        setConstant(move, "direction", Direction.SOUTH);
        wireExec(gm, entry, "next", forNode, "in");
        wireExec(gm, forNode, "body", branch, "in");
        wireExec(gm, scan, "plantable", branch, "cond");   // data: branch condition = current cell plantable
        wireExec(gm, branch, "trueExec", plant, "trigger");
        wireExec(gm, branch, "falseExec", move, "trigger");
        return graph;
    }

    /**
     * Isolates the runtime's move mechanics from any client sync: over a known 1x5 fertile strip with
     * the drone starting at z=0, an {@code Entry -> Move(SOUTH) x4} program must walk it to z=4. Proves
     * whether chained action nodes each advance the drone (vs. stalling after a couple of cells).
     */
    private static void chainedMoves(GameTestHelper helper) {
        FarmSimulation sim = FarmSimulation.allFertile(1, 5, FarmConfig.DEFAULT);
        DroneApi api = new DroneApi(sim, 0, 0);
        DroneGraph graph = buildChainedMoves(Direction.SOUTH, 4);

        DroneRuntime runtime = new DroneRuntime(graph, api, 1L);
        for (int t = 0; t < 4000 && !runtime.isFinished() && !runtime.isHalted(); t++) {
            runtime.tick(t);
        }
        if (api.z() != 4) {
            helper.fail("expected drone at z=4 after 4 SOUTH moves, got z=" + api.z()
                    + " (finished=" + runtime.isFinished() + " halted=" + runtime.isHalted() + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * {@code MoveToCoord} reaches the target cell directly, and its cost is distance-based: over a 1x5 strip
     * a single {@code move_to (0,4)} must put the drone at z=4 and occupy ~4 ticks (a flat cost would finish
     * almost immediately).
     */
    private static void moveToCoord(GameTestHelper helper) {
        FarmSimulation sim = FarmSimulation.allFertile(1, 5, FarmConfig.DEFAULT);
        DroneApi api = new DroneApi(sim, 0, 0);
        DroneGraph graph = buildMoveTo(0, 4);

        DroneRuntime runtime = new DroneRuntime(graph, api, 1L);
        int finishedTick = -1;
        for (int t = 0; t < 4000 && !runtime.isFinished() && !runtime.isHalted(); t++) {
            runtime.tick(t);
            if (runtime.isFinished() && finishedTick < 0) finishedTick = t;
        }
        if (api.x() != 0 || api.z() != 4) {
            helper.fail("expected drone at (0,4) after move_to, got (" + api.x() + "," + api.z() + ")");
            return;
        }
        if (finishedTick < 4) {
            helper.fail("move_to to a 4-cell-away target finished at tick " + finishedTick
                    + " — cost is not distance-based");
            return;
        }
        helper.succeed();
    }

    /** Build {@code Entry -> MoveToCoord(x, z)}. */
    private static DroneGraph buildMoveTo(int x, int z) {
        DroneGraph graph = new DroneGraph();
        CustomGraphModelImpl gm = graph.graphModel;
        NodeModel entry = gm.createNodeModel(new EntryNode(), new Vector2f(0, 0));
        NodeModel move = gm.createNodeModel(new MoveToCoordNode(), new Vector2f(100, 0));
        setConstant(move, "x", x);
        setConstant(move, "z", z);
        wireExec(gm, entry, "next", move, "trigger");
        return graph;
    }

    /** Build {@code Entry -> Move(dir) -> Move(dir) -> ...} with {@code count} chained move nodes. */
    private static DroneGraph buildChainedMoves(Direction dir, int count) {
        DroneGraph graph = new DroneGraph();
        CustomGraphModelImpl gm = graph.graphModel;
        NodeModel prev = gm.createNodeModel(new EntryNode(), new Vector2f(0, 0));
        String prevPort = "next";
        for (int i = 0; i < count; i++) {
            NodeModel move = gm.createNodeModel(new MoveNode(), new Vector2f(100 * (i + 1), 0));
            setConstant(move, "direction", dir);
            wireExec(gm, prev, prevPort, move, "trigger");
            prev = move;
            prevPort = "next";
        }
        return graph;
    }

    // ---- graph-building helpers ---------------------------------------------------------------

    private static void wireExec(CustomGraphModelImpl gm, NodeModel from, String fromId,
                                 NodeModel to, String toId) {
        var fromPort = from.getOutputsById().get(fromId);
        var toPort = to.getInputsById().get(toId);
        gm.createWire(toPort, fromPort);
    }

    private static void setConstant(NodeModel node, String inputId, Object value) {
        var c = node.getInputConstantsById().get(inputId);
        if (c != null) c.setValue(value);
    }

    // ---- registration boilerplate (mirrors LDLib2's NodeGraphGameTests) -----------------------

    private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> registerFunction(
            String path, Consumer<GameTestHelper> function) {
        return TEST_FUNCTIONS.register(path, () -> function);
    }

    private static ResourceKey<Consumer<GameTestHelper>> functionKey(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION,
                Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, path));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> defaultTestData(
            Holder<TestEnvironmentDefinition<?>> environment) {
        return new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath("ldlib2", "empty"),
                100, 0, true,
                Rotation.NONE,
                false, 1, 1, false, 0);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "drone"),
                new TestEnvironmentDefinition.AllOf());
        TestData<Holder<TestEnvironmentDefinition<?>>> data = defaultTestData(environment);
        registerTest(event, PLANT_GROW_HARVEST, data);
        registerTest(event, DETERMINISTIC, data);
        registerTest(event, STATION_RUN, data);
        registerTest(event, CHAINED_MOVES, data);
        registerTest(event, MOVE_TO_COORD, data);
        registerTest(event, CELLS_REUSED, data);
        registerTest(event, SCORING_STANDARD, data);
        registerTest(event, SUBMIT_REMOVE, data);
        registerTest(event, SCAN_PLANT_COLUMN, data);
        registerTest(event, GROWTH_RANDOM, data);
    }

    private static void registerTest(RegisterGameTestsEvent event, String path,
                                     TestData<Holder<TestEnvironmentDefinition<?>>> data) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, path),
                new FunctionGameTestInstance(functionKey(path), data));
    }
}
