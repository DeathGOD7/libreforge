package com.willfp.libreforge.triggers.triggers

import com.willfp.eco.core.Prerequisite
import com.willfp.eco.core.integrations.mcmmo.McmmoManager
import com.willfp.libreforge.triggers.GenericCancellableEvent
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent

class TriggerMove : Trigger(
    "move", listOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.VELOCITY,
        TriggerParameter.EVENT
    )
) {
    @EventHandler(ignoreCancelled = true)
    fun handle(event: PlayerMoveEvent) {
        if (McmmoManager.isFake(event)) {
            return
        }
        val player = event.player

        if (plugin.configYml.getBool("use-faster-move-trigger") && Prerequisite.HAS_PAPER.isMet) {
            if (!event.hasChangedBlock()) {
                return
            }
        }

        val distance = if (event.to.world == event.from.world) {
            event.to.distance(event.from)
        } else {
            0.0
        }

        this.processTrigger(
            player,
            TriggerData(
                player = player,
                location = player.location,
                velocity = player.velocity,
                event = GenericCancellableEvent(event)
            ),
            distance
        )
    }
}
