package redsmods

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object SoundPhysicsPerfected : ModInitializer {
    private val logger = LoggerFactory.getLogger("soundphysicsperfected")
	override fun onInitialize() {
		logger.info("Hello Fabric world!")
	}
}