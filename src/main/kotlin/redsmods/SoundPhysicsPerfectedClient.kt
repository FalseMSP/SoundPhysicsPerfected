package redsmods

import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

object SoundPhysicsPerfectedClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("soundphysicsperfectedClient")
    override fun onInitializeClient() {
        logger.info("Client Test!")
    }
}