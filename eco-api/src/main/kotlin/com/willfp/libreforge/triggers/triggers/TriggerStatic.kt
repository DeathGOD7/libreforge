package com.willfp.libreforge.triggers.triggers

import com.willfp.eco.core.Prerequisite
import com.willfp.libreforge.LibReforgePlugin
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class TriggerStatic(interval: Int) : Trigger(
    "static_$interval", listOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.VICTIM,
        TriggerParameter.BLOCK
    )
) {
    private fun invoke(player: Player) {
        val block = if (Prerequisite.HAS_PAPER.isMet) {
            player.getTargetBlock(plugin.configYml.getDoubleFromExpression("raytrace-distance").toInt())
        } else null

        this.processTrigger(
            player,
            TriggerData(
                player = player,
                location = player.location,
                victim = player,
                block = block
            )
        )
    }

    companion object {
        private val intervals = mutableMapOf<Int, TriggerStatic>()

        fun getWithInterval(interval: Int): Trigger {
            if (intervals.containsKey(interval)) {
                return intervals[interval]!!
            }

            val trigger = TriggerStatic(interval)
            intervals[interval] = trigger

            return trigger
        }

        fun beginTiming(plugin: LibReforgePlugin) {
            var tick = 0

            plugin.scheduler.runTimer(1, 1) {
                tick++
                for ((interval, trigger) in intervals) {
                    if (tick % interval == 0) {
                        for (player in Bukkit.getOnlinePlayers()) {
                            trigger.invoke(player)
                        }
                    }
                }
            }
        }
    }
}
