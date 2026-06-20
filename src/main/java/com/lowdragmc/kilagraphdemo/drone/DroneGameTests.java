package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.drone.node.HarvestNode;
import com.lowdragmc.kilagraphdemo.drone.node.MoveNode;
import com.lowdragmc.kilagraphdemo.drone.node.PlantNode;
import com.lowdragmc.kilagraphdemo.drone.node.WaitNode;
import net.minecraft.core.Direction;
import com.lowdragmc.kilagraphdemo.farm.FarmConfig;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.farm.RunState;
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
 * GameTests that exercise milestone M1 (field detection) and M3 (drone graph + runtime coroutine)
 * end-to-end inside a real server. They build a {@link DroneGraph} programmatically, run a
 * {@link DroneRuntime} against a {@link FarmSimulation} and assert the resulting score, plus place
 * blocks in the world and verify {@link FieldDetector}.
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
    private static final String FIELD_DETECT = "drone_field_detect";
    private static final String STATION_RUN = "drone_station_run";
    private static final String CHAINED_MOVES = "drone_chained_moves";
    private static final String CELLS_REUSED = "drone_cells_array_reused";

    private DroneGameTests() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) return;
        initialized = true;
        registerFunction(PLANT_GROW_HARVEST, DroneGameTests::plantGrowHarvest);
        registerFunction(DETERMINISTIC, DroneGameTests::deterministicScore);
        registerFunction(FIELD_DETECT, DroneGameTests::fieldDetect);
        registerFunction(STATION_RUN, DroneGameTests::stationRun);
        registerFunction(CHAINED_MOVES, DroneGameTests::chainedMoves);
        registerFunction(CELLS_REUSED, DroneGameTests::cellsArrayReused);
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
        helper.setBlock(new BlockPos(0, 1, 0), ModRegistries.FERTILE_SOIL_BLOCK.get());
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistries.DRONE_STATION_BLOCK.get());
        BlockPos stationWorld = helper.absolutePos(new BlockPos(0, 2, 0));
        if (!(helper.getLevel().getBlockEntity(stationWorld) instanceof DroneStationBlockEntity be)) {
            helper.fail("drone station block entity missing");
            return;
        }
        be.setOwner(java.util.UUID.randomUUID());

        // Default config: growTicks=600, so wait long enough for the pumpkin to ripen before harvest.
        var provider = helper.getLevel().registryAccess();
        CompoundTag programTag = DroneGraphCodec.toTag(buildPlantWaitHarvest(700), provider);

        if (!be.startRun(programTag)) {
            helper.fail("startRun returned false (field not detected?)");
            return;
        }
        if (be.getRunState() != RunState.RUNNING) {
            helper.fail("expected RUNNING after startRun, got " + be.getRunState());
            return;
        }

        for (int t = 0; t < DroneStationBlockEntity.TOTAL_TICKS && be.getRunState() == RunState.RUNNING; t++) {
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
     * Regression for the invisible-board bug: the {@code @DescSynced int[] cells} must keep the <em>same</em>
     * array instance across same-size ticks. LDLib2's array delta only marks the field dirty on in-place
     * element changes; a fresh array each tick is silently never synced, leaving the client board empty.
     * {@code captureCells} therefore reuses the array — assert that here via reference identity.
     */
    private static void cellsArrayReused(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0), ModRegistries.FERTILE_SOIL_BLOCK.get());
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistries.DRONE_STATION_BLOCK.get());
        BlockPos stationWorld = helper.absolutePos(new BlockPos(0, 2, 0));
        if (!(helper.getLevel().getBlockEntity(stationWorld) instanceof DroneStationBlockEntity be)) {
            helper.fail("drone station block entity missing");
            return;
        }
        be.setOwner(java.util.UUID.randomUUID());
        var provider = helper.getLevel().registryAccess();
        if (!be.startRun(DroneGraphCodec.toTag(buildPlantWaitHarvest(700), provider))) {
            helper.fail("startRun returned false (field not detected?)");
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

    /** Flood-fill detection: a 3x2 block of fertile soil under a station yields a 3x2, 6-cell field. */
    private static void fieldDetect(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ModRegistries.FERTILE_SOIL_BLOCK.get());
            }
        }
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistries.DRONE_STATION_BLOCK.get());

        BlockPos stationWorld = helper.absolutePos(new BlockPos(0, 2, 0));
        FieldDetector.Field field = FieldDetector.detect(helper.getLevel(), stationWorld);
        if (field == null) {
            helper.fail("field not detected under station");
            return;
        }
        if (field.width() != 3 || field.height() != 2 || field.cellCount() != 6) {
            helper.fail("unexpected field: width=" + field.width() + " height=" + field.height()
                    + " cells=" + field.cellCount());
            return;
        }
        helper.succeed();
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
        registerTest(event, FIELD_DETECT, data);
        registerTest(event, STATION_RUN, data);
        registerTest(event, CHAINED_MOVES, data);
        registerTest(event, CELLS_REUSED, data);
    }

    private static void registerTest(RegisterGameTestsEvent event, String path,
                                     TestData<Holder<TestEnvironmentDefinition<?>>> data) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, path),
                new FunctionGameTestInstance(functionKey(path), data));
    }
}
