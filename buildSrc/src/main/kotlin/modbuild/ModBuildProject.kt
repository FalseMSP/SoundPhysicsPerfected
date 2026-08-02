package modbuild

import org.gradle.api.Project

/** Generic mod metadata, by convention read from gradle.properties as mod.id/mod.name/etc. */
class ModData(project: Project) {
    val id = project.property("mod.id").toString()
    val name = project.property("mod.name")
    val version = project.property("mod.version")
    val group = project.property("mod.group").toString()
    val description = project.property("mod.description")
    val source = project.property("mod.source")
    val issues = project.property("mod.issues")
    val license = project.property("mod.license").toString()
    val modrinth = project.property("mod.modrinth")
    val discord = project.property("mod.discord")
}

class LoaderData(val loader: String) {
    val isFabric = loader == "fabric"
    val isNeoforge = loader == "neoforge"
    val isForge = loader == "forge"
}

class McData(project: Project) {
    val version = project.property("mod.mc_version")
    val dep = project.property("mod.mc_dep").toString()
}
