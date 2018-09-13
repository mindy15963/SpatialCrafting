package fudge.spatialcrafting.client.util;

import fudge.spatialcrafting.client.tick.ClientTicker;
import fudge.spatialcrafting.common.MCConstants;
import fudge.spatialcrafting.common.tile.TileCrafter;
import fudge.spatialcrafting.common.tile.TileHologram;
import fudge.spatialcrafting.common.util.ArrayUtil;
import fudge.spatialcrafting.common.util.MathUtil;
import fudge.spatialcrafting.common.util.Util;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static fudge.spatialcrafting.client.particle.ParticleCraft.*;
import static fudge.spatialcrafting.common.MCConstants.TICKS_PER_SECOND;
import static fudge.spatialcrafting.common.block.BlockCrafter.CRAFT_DURATION_MULTIPLIER;

public class ParticleUtil {

    private static final int TICKS_BETWEEN_PARTICLES = (int) (0.1f * TICKS_PER_SECOND);
    private static final String TICKER_ID = "ticker_particle_item_dust";

    public static void playCraftParticles(World world, BlockPos crafterPos) {


        TileCrafter crafter = Util.getTileEntity(world, crafterPos);

        int size = crafter.size();


        int durationTicks = size * CRAFT_DURATION_MULTIPLIER * MCConstants.TICKS_PER_SECOND;

        // Send these particle every so often using a ticker
        ClientTicker.addTicker(ticksPassed -> {


            // Go through each hologram and send particles from it
            ArrayUtil.innerForEach(crafter.getHolograms(), hologramPos -> {
                TileHologram hologramTile = Util.getTileEntity(world, hologramPos);
                ItemStack itemStack = hologramTile.getStoredItem();

                if (!itemStack.getItem().equals(Items.AIR)) {

                    // Calculate start and end positions
                    Vec3d startPos = new Vec3d(hologramPos.getX() + 0.2, hologramPos.getY() + 0.5, hologramPos.getZ() + 0.2);

                    Vec3d currentEndPos = crafter.centerOfHolograms();

                    BlockPos[][] crafters = crafter.getCrafterBlocks();

                    double craftYEndPos = crafters[0][0].getY() + 1.5;


                    if (durationTicks > getRelativeTicksPassed(ticksPassed, durationTicks, startPos, currentEndPos, craftYEndPos)) {

                        ParticleBuilder particleBuilder = new ParticleBuilder(world,
                                currentEndPos,
                                ticksPassed,
                                durationTicks,
                                craftYEndPos,
                                itemStack);

                        // Shot particles from the 4 corners of the hologram
                        particleBuilder.shoot(startPos);

                        particleBuilder.shoot(new Vec3d(hologramPos.getX() + 0.2, hologramPos.getY() + 0.5, hologramPos.getZ() + 0.8));

                        particleBuilder.shoot(new Vec3d(hologramPos.getX() + 0.8, hologramPos.getY() + 0.5, hologramPos.getZ() + 0.2));

                        particleBuilder.shoot(new Vec3d(hologramPos.getX() + 0.8, hologramPos.getY() + 0.5, hologramPos.getZ() + 0.8));
                    }

                }
            });
        }, TICKS_BETWEEN_PARTICLES, durationTicks, TICKER_ID + Util.<TileCrafter>getTileEntity(world, crafterPos).masterPos());

    }

    public static Vec3d calcEndPos(int ticksPassed, int durationTicks, Vec3d origEndPos, double craftEndY) {
        double endY;

        if (ticksPassed >= PHASE_2_START_TICKS) {
            final int phase2TicksPassed = ticksPassed - PHASE_2_START_TICKS;
            endY = origEndPos.y + (PHASE_2_Y_END_POS_INCREASE_PER_TICK * phase2TicksPassed);
        } else {
            endY = origEndPos.y;
        }


        // Calculates phase 3 start time based on how close the crafting is to ending
        final int phase3StartTime = (int) (durationTicks - ((endY - craftEndY) / SLAMDOWN_SPEED_BLOCKS_PER_TICK));

        if (ticksPassed >= phase3StartTime) {
            final int phase3TicksPassed = ticksPassed - phase3StartTime;
            endY = Math.max(craftEndY, endY - (SLAMDOWN_SPEED_BLOCKS_PER_TICK * phase3TicksPassed));
        }

        return new Vec3d(origEndPos.x, endY, origEndPos.z);
    }

    public static double calcSpeed(int ticksPassed) {
        return SPEED_BLOCKS_PER_TICK_BASE + (SPEED_BLOCKS_PER_TICKS_INCREASE_PER_TICK * ticksPassed);
    }

    // This is used to make particles stop appearing slightly before the crafting stops, such that it looks like once ALL particles have stopped the crafting is done.
    private static int getRelativeTicksPassed(int ticksPassed, int durationTicks, Vec3d startPos, Vec3d origEndPos, double endYLoc) {

        Vec3d endPos = calcEndPos(ticksPassed, durationTicks, origEndPos, endYLoc);

        return (int) (ticksPassed + MathUtil.minimalDistanceOf(startPos, endPos) / calcSpeed(ticksPassed));

    }

    public static void stopCraftParticles(TileCrafter tile) {
        ClientTicker.stopTickers(TICKER_ID + tile.masterPos());
    }

    /*class ParticleBuilder{
        private final World world;
        private final Vec3d startPos;
        private Vec3d endPos;
        private final int startTimeDelay;
        private final int craftDuration;
        private final int craftYEndPos;
        private final ItemStack stack;

        public ParticleBuilder(World world, Vec3d startPos, int startTimeDelay, int craftDuration, int craftYEndPos, ItemStack stack) {
            this.world = world;
            this.startPos = startPos;
            this.startTimeDelay = startTimeDelay;
            this.craftDuration = craftDuration;
            this.craftYEndPos = craftYEndPos;
            this.stack = stack;
        }

        public World getWorld() {
            return world;
        }

        public Vec3d getStartPos() {
            return startPos;
        }

        public Vec3d getEndPos() {
            return endPos;
        }

        public void setEndPos(Vec3d endPos) {
            this.endPos = endPos;
        }

        public int getStartTimeDelay() {
            return startTimeDelay;
        }

        public int getCraftDuration() {
            return craftDuration;
        }

        public int getCraftYEndPos() {
            return craftYEndPos;
        }

        public ItemStack getStack() {
            return stack;
        }
    }
*/

}
