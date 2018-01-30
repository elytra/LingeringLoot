package lingerloot

import com.elytradev.concrete.common.Either
import dimensionalforcefield.ForcefieldRules
import lingerloot.ruleengine.*
import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.config.Configuration
import java.io.File

var cfg: LingeringLootConfig? = null

class LingeringLootConfig(file: File) {
    val antilag: Boolean
    val legacyRules: LegacyRules

    init {
        val config = Configuration(file.resolve("lingeringloot.cfg"))

        config.setCategoryComment(bonusCategory,
                "Despawn times are in seconds.  Minecraft's default is 300.  Use -1 to defer to less granular settings\n" +
                        "eg: player drops and player-killed mob drops are both types of mob drops, and player-caused drops.\n" +
                        "The order of precedence is: player drops, player-killed mob drops or player-mined items or player-thrown\n" +
                        "items, player-caused drops, mob drops, and finally other.")
        antilag = !config.getBoolean("destroyer of worlds", bonusCategory, false, "disable antilag features in despawn handlers.  world may be eaten by infinite chickens.")
        config.getString("WHERE HAVE MY SETTINGS GONE?", bonusCategory, "just a heads up!",
                "Lingering Loot 4.0 is configured using a new rules-based system, in lingeringloot.rules.\n" +
                        "If you have settings here they have been imported, but other than the destroyer of worlds\n" +
                        "setting, this file is now defunct (unless you delete lingeringloot.rules to have it recreated)")
        if (config.hasChanged()) config.save()

        cfg = this

        legacyRules = LegacyRules(config)
        // rules parsing last so we can avoid saving in default values for defunct options when
        // attempting to migrate config
        LingerRulesEngine.loadRulesFile(file.resolve("lingeringloot.rules"), logger)
        ForcefieldRules.loadRulesFile(file.resolve("dimensionalforcefield.rules"), logger)
    }
}

val bonusCategory = "bonus"
val timeCategory = "despawn times"
val shitTierCategory = "shit tier"
class LegacyRules(val config: Configuration) {
    val hardcore = config.getBoolean("hardcore mode", bonusCategory, false, "additional challenge features when items despawn (best used with dramatically reduced despawn times!)")
    val minedPickupDelay = config.getInt("pickup delay", bonusCategory, 5, 0, 10, "pickup delay for player-mined items in ticks (0-10, 0 is instant and 10 is no change)")
    val shitTier = config.get(shitTierCategory, "shit tier items", "cobblestone,andesite,diorite,granite,snowball").string.split(",").
            map{b -> Item.REGISTRY.getObject(ResourceLocation(b))}.filterNotNull().
            toSet()
    val shitTierMods = config.get(shitTierCategory, "shit tier mods", "").string.split(",").
            toSet()
    val despawns = DespawnTimes(
        configOptionRaw(timeCategory, "player drops", 3600),
        configOptionRaw(timeCategory, "player-killed mob drops", -1),
        configOptionRaw(timeCategory, "player-mined items", -1),
        configOptionRaw(timeCategory, "mob drops", -1),
        configOptionRaw(timeCategory, "player-thrown items", -1),
        configOptionRaw(timeCategory, "player-caused drops", 1800),
        configOptionRaw(timeCategory, "other", 900),
        configOptionRaw(timeCategory, "creative drops and /give", 60),
        configOptionRaw(shitTierCategory, "shit despawn time", 300)
    )

    private fun configOptionRaw(category: String, name: String, default: Int) =
        config.get(category, name, default.toDouble()).getDouble(default.toDouble())
}

class DespawnTimes(val playerDrop: Double, val playerKill: Double, val playerMine: Double, val mobDrop: Double,
                   val playerToss: Double, val playerCaused: Double, val other: Double, val creative: Double,
                   val shitTier: Double)
