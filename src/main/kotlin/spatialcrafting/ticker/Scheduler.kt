@file:UseSerializers(ForUuid::class)

package spatialcrafting.ticker

import drawer.ForUuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.block.Block
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import spatialcrafting.util.getMinecraftClient
import spatialcrafting.util.isServer
import spatialcrafting.util.sendPacket
import spatialcrafting.util.sendPacketToServer
import java.util.*


interface Scheduleable {
    fun onScheduleEnd(world: World, pos: BlockPos, scheduleId: Int, additionalData: CompoundTag)
}

/**
 * When scheduling from the client it will tick in the server and then send a packet back to the client when its done.
 */
object Scheduler {
    /**
     * @param scheduleId The same value will be available in [Scheduleable.onScheduleEnd] for you to be able
     * to differentiate between different schedules. This is not needed when you only schedule one thing in a block.
     * @param blockPos The same value will be available in [Scheduleable.onScheduleEnd] for you to use.
     * You can opt to not provide a value, but note that you will just get [BlockPos.ORIGIN] in the callback.
     */
    fun <T> schedule(ticksUntilEnd: Int,
                     block: T, world: World,
                     scheduleId: Int = 0,
                     blockPos: BlockPos = BlockPos.ORIGIN,
                     additionalData: CompoundTag = CompoundTag()): CancellationToken
            where T : Scheduleable, T : Block = schedule(
            block, world, scheduleId, blockPos, additionalData, repetition = Repetition.Once(world.time + ticksUntilEnd)
    )


    //TODO: test
    fun <T> repeat(tickInterval: Int,
                   repeatAmount: Int,
                   block: T, world: World,
                   scheduleId: Int = 0,
                   blockPos: BlockPos = BlockPos.ORIGIN,
                   additionalData: CompoundTag = CompoundTag()): CancellationToken
            where T : Scheduleable, T : Block = schedule(
            block, world, scheduleId, blockPos, additionalData,
            repetition = Repetition.RepeatAmount(
                    repeatInterval = tickInterval, amountLeft = repeatAmount, nextTickTime = world.time + tickInterval
            )
    )

    //TODO: use for particles
    fun <T> repeatFor(ticksUntilStop: Int,
                      tickInterval: Int,
                      block: T, world: World,
                      scheduleId: Int = 0,
                      blockPos: BlockPos = BlockPos.ORIGIN,
                      additionalData: CompoundTag = CompoundTag()): CancellationToken
            where T : Scheduleable, T : Block = schedule(
            block, world, scheduleId, blockPos, additionalData,
            repetition = Repetition.RepeatUntil(
                    repeatInterval = tickInterval,
                    nextTickTime = world.time + tickInterval,
                    stopTime = world.time + ticksUntilStop
            )
    )


}

private fun <T> schedule(block: T,
                         world: World,
                         scheduleId: Int,
                         blockPos: BlockPos,
                         additionalData: CompoundTag,
                         repetition: Repetition): CancellationToken
        where T : Scheduleable, T : Block {


    val clientToSendTo = if (world.isServer && world is ServerWorld) {
        null
    }
    else if (world.isClient) {
        getMinecraftClient().player.uuid
    }
    else {
        logWarning("Attempt to schedule in a world that is ClientWorld but with isClient = false. " +
                "You might get a ClassNotFound exception here!")
        getMinecraftClient().player.uuid
    }

    val cancellationUUID = UUID.randomUUID()

    val schedule = Schedule(repetition = repetition,
            context = ScheduleContext(
                    blockId = Registry.BLOCK.getId(block),
                    blockPos = blockPos,
                    scheduleId = scheduleId,
                    additionalData = additionalData
            ),
            clientRequestingSchedule = clientToSendTo,
            cancellationUUID = cancellationUUID
    )

    if (clientToSendTo != null) {
        sendPacketToServer(TickInServerPacket(schedule))
    }
    else {
        scheduleServer(world as ServerWorld, schedule, block)
    }

    return CancellationToken(cancellationUUID)


}

internal fun scheduleServer(world: ServerWorld, schedule: Schedule, schedulingBlock: Scheduleable) {
    val tickState = world.persistentStateManager.getOrCreate(SchedulerId) { TickerState() }
    tickState.add(schedule.apply { scheduleable = schedulingBlock })
    tickState.isDirty = true
}

internal fun cancelScheduleServer(world: ServerWorld, cancellationUUID: UUID) = world.persistentStateManager
        .getOrCreate(SchedulerId) { TickerState() }
        .cancel(cancellationUUID)


@Serializable
data class CancellationToken internal constructor(
        /**
         * To correctly identify the scheduled action
         */
        private val cancellationUUID: UUID
) {
    fun cancel(world: World) {
        if (world.isServer && world is ServerWorld) {
            cancelScheduleServer(world,cancellationUUID)
        }
        else if (world.isClient) {
            sendPacketToServer(CancelTickingInServerPacket(cancellationUUID))
        }
        else {
            logWarning("Attempt to cancel a schedule in a world that is ClientWorld but with isClient = false. ")
        }
    }
}


//TODO: canceling






