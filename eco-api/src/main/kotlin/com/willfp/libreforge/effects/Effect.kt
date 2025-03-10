package com.willfp.libreforge.effects

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.eco.core.integrations.economy.EconomyManager
import com.willfp.eco.util.NumberUtils
import com.willfp.eco.util.PlayerUtils
import com.willfp.eco.util.StringUtils
import com.willfp.libreforge.ConfigurableProperty
import com.willfp.libreforge.LibReforgePlugin
import com.willfp.libreforge.conditions.ConfiguredCondition
import com.willfp.libreforge.events.EffectActivateEvent
import com.willfp.libreforge.events.EffectPreActivateEvent
import com.willfp.libreforge.filters.Filter
import com.willfp.libreforge.triggers.ConfiguredDataMutator
import com.willfp.libreforge.triggers.InvocationData
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.mutate
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.*
import kotlin.math.ceil

@Suppress("UNUSED_PARAMETER")
abstract class Effect(
    id: String,
    val applicableTriggers: Collection<Trigger> = emptyList(),
    supportsFilters: Boolean = true,
    noDelay: Boolean = false
) : ConfigurableProperty(id), Listener {
    private val cooldownTracker = mutableMapOf<UUID, MutableMap<UUID, Long>>()
    val supportsFilters = applicableTriggers.isNotEmpty()
    val noDelay: Boolean

    init {
        if (applicableTriggers.isEmpty()) {
            this.noDelay = true
        } else {
            this.noDelay = noDelay
        }

        postInit()
    }

    private fun postInit() {
        Effects.addNewEffect(this)
    }

    fun getCooldown(player: Player, uuid: UUID): Int {
        val endTime = (cooldownTracker[player.uniqueId] ?: return 0)[uuid] ?: return 0
        val msLeft = endTime - System.currentTimeMillis()
        val secondsLeft = ceil(msLeft.toDouble() / 1000).toLong()
        return secondsLeft.toInt()
    }

    fun sendCooldownMessage(player: Player, uuid: UUID) {
        val cooldown = getCooldown(player, uuid)

        val message = plugin.langYml.getMessage("on-cooldown").replace("%seconds%", cooldown.toString())
        if (plugin.configYml.getBool("cooldown.in-actionbar")) {
            PlayerUtils.getAudience(player).sendActionBar(StringUtils.toComponent(message))
        } else {
            player.sendMessage(message)
        }

        if (plugin.configYml.getBool("cooldown.sound.enabled")) {
            player.playSound(
                player.location,
                Sound.valueOf(plugin.configYml.getString("cooldown.sound.sound").uppercase()),
                1.0f,
                plugin.configYml.getDouble("cooldown.sound.pitch").toFloat()
            )
        }
    }

    fun sendCannotAffordMessage(player: Player, cost: Double) {
        val message = plugin.langYml.getMessage("cannot-afford").replace("%cost%", cost.toString())
        if (plugin.configYml.getBool("cannot-afford.in-actionbar")) {
            PlayerUtils.getAudience(player).sendActionBar(StringUtils.toComponent(message))
        } else {
            player.sendMessage(message)
        }

        if (plugin.configYml.getBool("cannot-afford.sound.enabled")) {
            player.playSound(
                player.location,
                Sound.valueOf(plugin.configYml.getString("cannot-afford.sound.sound").uppercase()),
                1.0f,
                plugin.configYml.getDouble("cannot-afford.sound.pitch").toFloat()
            )
        }
    }

    fun sendCannotAffordTypeMessage(player: Player, cost: Double, type: String) {
        val message = plugin.langYml.getMessage("cannot-afford-type").replace("%cost%", cost.toString())
            .replace("%type%", type)
        if (plugin.configYml.getBool("cannot-afford-type.in-actionbar")) {
            PlayerUtils.getAudience(player).sendActionBar(StringUtils.toComponent(message))
        } else {
            player.sendMessage(message)
        }

        if (plugin.configYml.getBool("cannot-afford-type.sound.enabled")) {
            player.playSound(
                player.location,
                Sound.valueOf(plugin.configYml.getString("cannot-afford-type.sound.sound").uppercase()),
                1.0f,
                plugin.configYml.getDouble("cannot-afford-type.sound.pitch").toFloat()
            )
        }
    }

    fun resetCooldown(player: Player, config: Config, uuid: UUID) {
        if (!config.has("cooldown")) {
            return
        }
        val current = cooldownTracker[player.uniqueId] ?: mutableMapOf()
        current[uuid] =
            System.currentTimeMillis() + (config.getDoubleFromExpression("cooldown", player) * 1000L).toLong()
        cooldownTracker[player.uniqueId] = current
    }

    /**
     * Generate a UUID with a specified offset.
     *
     * @param offset The offset.
     * @return The UUID.
     */
    fun getUUID(
        offset: Int
    ): UUID {
        return UUID.nameUUIDFromBytes("${plugin.name.lowercase()}$id$offset".toByteArray())
    }

    /**
     * Generate a NamespacedKey with a specified offset.
     *
     * @param offset The offset.
     * @return The NamespacedKey.
     */
    fun getNamespacedKey(
        offset: Int
    ): NamespacedKey {
        return this.plugin.namespacedKeyFactory.create("${id}_$offset")
    }

    /**
     * Handle application of this effect.
     *
     * @param player The player.
     * @param config The config.
     */
    fun enableForPlayer(
        player: Player,
        config: Config
    ) {
        player.pushEffect(this)
        handleEnable(player, config)
    }

    protected open fun handleEnable(
        player: Player,
        config: Config
    ) {
        // Override when needed.
    }

    /**
     * Handle removal of this effect.
     *
     * @param player The player.
     */
    fun disableForPlayer(player: Player) {
        handleDisable(player)
        player.popEffect(this)
    }

    protected open fun handleDisable(player: Player) {
        // Override when needed.
    }

    open fun handle(data: TriggerData, config: Config) {
        // Override when needed
    }

    open fun handle(invocation: InvocationData, config: Config) {
        // Override when needed
    }

    open fun makeCompileData(config: Config, context: String): CompileData? {
        return null
    }
}

private val everyHandler = mutableMapOf<UUID, Int>()

interface CompileData {
    val data: Any
}

internal data class RepeatData(
    var times: Int,
    var start: Double,
    var increment: Double,
    var count: Double
) {
    fun update(config: Config, player: Player) {
        times = config.getIntFromExpression("times", player)
        start = config.getDoubleFromExpression("start", player)
        increment = config.getDoubleFromExpression("increment", player)
        count = config.getDoubleFromExpression("start", player)

        if (times < 1) times = 1
    }
}

data class ConfiguredEffect internal constructor(
    val effect: Effect,
    val args: Config,
    val filter: Filter,
    val triggers: Collection<Trigger>,
    val uuid: UUID,
    val conditions: Collection<ConfiguredCondition>,
    val mutators: Collection<ConfiguredDataMutator>,
    val compileData: CompileData?,
    private val repeatData: RepeatData
) {
    internal operator fun invoke(
        rawInvocation: InvocationData,
        ignoreTriggerList: Boolean = false,
        namedArguments: Iterable<NamedArgument> = emptyList(),
    ) {
        if (!ignoreTriggerList) {
            if (!triggers.contains(rawInvocation.trigger)) {
                return
            }
        }
        
        var invocation = rawInvocation.copy(compileData = compileData)

        args.addInjectablePlaceholder(namedArguments.map { it.placeholder })
        mutators.forEach { it.config.addInjectablePlaceholder(namedArguments.map { a -> a.placeholder }) }
        conditions.forEach { it.config.addInjectablePlaceholder(namedArguments.map { a -> a.placeholder }) }

        if (args.getBool("self_as_victim")) {
            invocation = invocation.copy(
                data = invocation.data.copy(victim = invocation.data.player)
            )
        }

        repeatData.update(args.getSubsection("repeat"), invocation.player)

        invocation = invocation.copy(
            data = mutators.mutate(invocation.data)
        )

        var effectAreMet = true
        for (condition in conditions) {
            if (!condition.isMet(invocation.player)) {
                effectAreMet = false
            }
        }

        if (!effectAreMet) {
            return
        }

        val (player, data, holder, _) = invocation

        if (args.has("chance")) {
            if (NumberUtils.randFloat(0.0, 100.0) > args.getDoubleFromExpression("chance", player)) {
                return
            }
        }

        if (data.player != null && data.victim != null) {
            if (!args.getBool("disable_antigrief_check")) {
                if (!AntigriefManager.canInjure(data.player, data.victim)) {
                    return
                }
            }
        }

        if (args.getBool("filters_before_mutation")) {
            if (!filter.matches(rawInvocation.data)) {
                return
            }
        } else {
            if (!filter.matches(data)) {
                return
            }
        }

        val every = if (args.has("every")) args.getIntFromExpression("every", player) else 0

        if (every > 0) {
            var current = everyHandler[uuid] ?: 0
            val prev = current

            current++

            if (current >= every) {
                current = 0
            }

            everyHandler[uuid] = current

            if (prev != 0) {
                return
            }
        }

        val preActivateEvent = EffectPreActivateEvent(player, holder, effect, args)
        LibReforgePlugin.instance.server.pluginManager.callEvent(preActivateEvent)

        if (preActivateEvent.isCancelled) {
            return
        }

        if (effect.getCooldown(player, uuid) > 0) {
            if (args.getBoolOrNull("send_cooldown_message") != false) {
                effect.sendCooldownMessage(player, uuid)
            }
            return
        }

        if (args.has("cost")) {
            val cost = args.getDoubleFromExpression("cost", player)
            if (!EconomyManager.hasAmount(player, cost)) {
                effect.sendCannotAffordMessage(player, cost)
                return
            }

            EconomyManager.removeMoney(player, cost)
        }

        val activateEvent = EffectActivateEvent(player, holder, effect, args)
        LibReforgePlugin.instance.server.pluginManager.callEvent(activateEvent)

        if (activateEvent.isCancelled) {
            return
        }

        effect.resetCooldown(player, args, uuid)

        for (i in 0 until repeatData.times) {
            /*
            Can't use the destructured objects as they haven't been affected by subsequent mutations in repeats.
             */
            val delay = if (args.has("delay")) {
                val found = args.getIntFromExpression("delay", invocation.player)

                if (effect.noDelay || found < 0) 0 else found
            } else 0

            if (delay > 0) {
                LibReforgePlugin.instance.scheduler.runLater(delay.toLong()) {
                    effect.handle(invocation.data, args)
                    effect.handle(invocation, args)
                }
            } else {
                effect.handle(invocation.data, args)
                effect.handle(invocation, args)
            }

            repeatData.count += repeatData.increment

            invocation = invocation.copy(data = mutators.mutate(rawInvocation.copy(compileData = compileData).data))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ConfiguredEffect) {
            return false
        }

        return this.uuid == other.uuid
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }
}

data class MultiplierModifier(val uuid: UUID, val multiplier: Double)