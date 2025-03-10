@file:JvmName("LibReforgeUtils")

package com.willfp.libreforge

import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.PluginProps
import com.willfp.eco.core.Prerequisite
import com.willfp.eco.core.integrations.IntegrationLoader
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.conditions.MovementConditionListener
import com.willfp.libreforge.effects.ConfiguredEffect
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.integrations.aureliumskills.AureliumSkillsIntegration
import com.willfp.libreforge.integrations.boosters.BoostersIntegration
import com.willfp.libreforge.integrations.ecoarmor.EcoArmorIntegration
import com.willfp.libreforge.integrations.ecobosses.EcoBossesIntegration
import com.willfp.libreforge.integrations.ecoenchants.EcoEnchantsIntegration
import com.willfp.libreforge.integrations.ecoitems.EcoItemsIntegration
import com.willfp.libreforge.integrations.ecopets.EcoPetsIntegration
import com.willfp.libreforge.integrations.ecoskills.EcoSkillsIntegration
import com.willfp.libreforge.integrations.jobs.JobsIntegration
import com.willfp.libreforge.integrations.mcmmo.McMMOIntegration
import com.willfp.libreforge.integrations.paper.PaperIntegration
import com.willfp.libreforge.integrations.reforges.ReforgesIntegration
import com.willfp.libreforge.integrations.talismans.TalismansIntegration
import com.willfp.libreforge.integrations.tmmobcoins.TMMobcoinsIntegration
import com.willfp.libreforge.integrations.vault.VaultIntegration
import com.willfp.libreforge.triggers.Triggers
import com.willfp.libreforge.triggers.triggers.TriggerStatic
import org.apache.commons.lang.StringUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.TimeUnit

private val holderProviders = mutableSetOf<HolderProvider>()

private val previousStates: MutableMap<UUID, Iterable<ConfiguredEffect>> = WeakHashMap()
private val holderCache = Caffeine.newBuilder()
    .expireAfterWrite(4L, TimeUnit.SECONDS)
    .build<UUID, Iterable<Holder>>()

typealias HolderProvider = (Player) -> Iterable<Holder>

@Suppress("UNUSED")
abstract class LibReforgePlugin : EcoPlugin() {
    private val defaultPackage = StringUtils.join(
        arrayOf("com", "willfp", "libreforge"),
        "."
    )

    init {
        setInstance()

        if (this.javaClass.packageName == defaultPackage) {
            throw IllegalStateException("You must shade and relocate libreforge into your jar!")
        }

        if (Prerequisite.HAS_PAPER.isMet) {
            PaperIntegration.load()
        }
    }

    open fun handleEnableAdditional() {

    }

    open fun handleDisableAdditional() {

    }

    open fun handleReloadAdditional() {

    }

    open fun loadAdditionalIntegrations(): List<IntegrationLoader> {
        return emptyList()
    }

    fun registerHolderProvider(provider: HolderProvider) {
        holderProviders.add(provider)
    }

    fun registerJavaHolderProvider(provider: java.util.function.Function<Player, Iterable<Holder>>) {
        holderProviders.add(provider::apply)
    }

    fun logViolation(id: String, context: String, violation: ConfigViolation) {
        this.logger.warning("")
        this.logger.warning("Invalid configuration for $id in context $context:")
        this.logger.warning("(Cause) Argument '${violation.param}'")
        this.logger.warning("(Reason) ${violation.message}")
        this.logger.warning("")
    }

    final override fun handleEnable() {
        initPointPlaceholders()
        this.eventManager.registerListener(TridentHolderDataAttacher(this))
        this.eventManager.registerListener(MovementConditionListener())
        this.eventManager.registerListener(PointCostHandler())
        for (condition in Conditions.values()) {
            this.eventManager.registerListener(condition)
        }
        for (effect in Effects.values()) {
            this.eventManager.registerListener(effect)
        }
        for (trigger in Triggers.values()) {
            this.eventManager.registerListener(trigger)
        }

        handleEnableAdditional()
    }

    final override fun handleDisable() {
        for (player in Bukkit.getOnlinePlayers()) {
            try {
                for (holder in player.getHolders()) {
                    for ((effect) in holder.effects) {
                        effect.disableForPlayer(player)
                    }
                }
            } catch (e: Exception) {
                Bukkit.getLogger().warning("Error disabling effects, not important - do not report this")
            }
        }

        handleDisableAdditional()
    }

    final override fun handleReload() {
        this.scheduler.runTimer({
            for (player in Bukkit.getOnlinePlayers()) {
                player.updateEffects()
            }
        }, 30, 30)
        TriggerStatic.beginTiming(this)

        handleReloadAdditional()
    }

    final override fun loadIntegrationLoaders(): List<IntegrationLoader> {
        val integrations = mutableListOf(
            IntegrationLoader("EcoSkills", EcoSkillsIntegration::load),
            IntegrationLoader("AureliumSkills", AureliumSkillsIntegration::load),
            IntegrationLoader("mcMMO", McMMOIntegration::load),
            IntegrationLoader("Jobs", JobsIntegration::load),
            IntegrationLoader("Vault", VaultIntegration::load),
            IntegrationLoader("TMMobcoins", TMMobcoinsIntegration::load),
            IntegrationLoader("EcoArmor", EcoArmorIntegration::load),
            IntegrationLoader("EcoBosses", EcoBossesIntegration::load),
            IntegrationLoader("Talismans", TalismansIntegration::load),
            IntegrationLoader("EcoItems", EcoItemsIntegration::load),
            IntegrationLoader("Reforges", ReforgesIntegration::load),
            IntegrationLoader("Boosters", BoostersIntegration::load),
            IntegrationLoader("EcoEnchants", EcoEnchantsIntegration::load),
            IntegrationLoader("EcoPets", EcoPetsIntegration::load)
        )

        integrations.addAll(loadAdditionalIntegrations())

        return integrations
    }

    private fun setInstance() {
        instance = this
    }

    override fun mutateProps(props: PluginProps): PluginProps {
        return props.apply {
            isSupportingExtensions = true
        }
    }

    override fun getMinimumEcoVersion(): String {
        return "6.35.7"
    }

    companion object {
        @JvmStatic
        internal lateinit var instance: LibReforgePlugin
    }
}

private fun Player.clearEffectCache() {
    holderCache.invalidate(this.uniqueId)
}

private fun Player.getPureHolders(): Iterable<Holder> {
    return holderCache.get(this.uniqueId) {
        val holders = mutableListOf<Holder>()
        for (provider in holderProviders) {
            holders.addAll(provider(this))
        }
        holders
    }
}

@JvmOverloads
fun Player.getHolders(respectConditions: Boolean = true): Iterable<Holder> {
    val holders = this.getPureHolders().toMutableList()

    if (respectConditions) {
        for (holder in holders.toList()) {
            var isMet = true
            for (condition in holder.conditions) {
                if (!condition.isMet(this)) {
                    isMet = false
                    break
                }
            }

            if (!isMet) {
                holders.remove(holder)
            }
        }
    }

    return holders
}

fun Player.getActiveEffects(): Iterable<ConfiguredEffect> {
    val holders = this.getHolders(respectConditions = true)
    val effects = mutableListOf<ConfiguredEffect>()

    for (holder in holders) {
        if (!holder.conditions.all { it.isMet(this) }) {
            continue
        }

        for (effect in holder.effects) {
            if (!effect.conditions.all { it.isMet(this) }) {
                continue
            }

            effects.add(effect)
        }
    }

    return effects
}

@JvmOverloads
fun Player.updateEffects(noRescan: Boolean = false) {
    val before = mutableListOf<ConfiguredEffect>()
    if (previousStates.containsKey(this.uniqueId)) {
        before.addAll(previousStates[this.uniqueId] ?: emptyList())
    }

    if (!noRescan) {
        this.clearEffectCache()
    }

    val after = this.getActiveEffects()
    previousStates[this.uniqueId] = after

    val added = after.toMutableList().apply { removeAll(before.toSet()) }
    val removed = before.toMutableList().apply { removeAll(after.toSet()) }

    for ((effect, config, _, _, _, conditions) in added) {
        var effectConditions = true
        for (condition in conditions) {
            if (!condition.isMet(this)) {
                effectConditions = false
                break
            }
        }

        if (!effectConditions) {
            continue
        }

        effect.enableForPlayer(this, config)
    }

    for ((effect) in removed) {
        effect.disableForPlayer(this)
    }

    for ((effect, _, _, _, _, conditions) in after) {
        var effectConditions = true
        for (condition in conditions) {
            if (!condition.isMet(this)) {
                effectConditions = false
                break
            }
        }

        if (!effectConditions) {
            effect.disableForPlayer(this)
        }
    }
}
