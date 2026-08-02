import modbuild.McData
import modbuild.ModBuildExtension

val mc = McData(project)
val deps = SppDependencyVersions(project)
val modbuild = project.extensions.getByType(ModBuildExtension::class.java)

dependencies {
    "forge"("net.minecraftforge:forge:${mc.version}-${deps.forgeVersion}")

    // Kotlin for Forge (required by YACL on Forge 1.20.1)
//    "modImplementation"("thedarkcolour:kotlinforforge:${deps.kotlinForgeVersion}")

    // Same GsonReader gap as the NeoForge convention plugin -- see that file's comment.
    "modImplementation"("dev.isxander:yet-another-config-lib:${deps.yaclVersion}+${mc.version}-forge") {
        isTransitive = false
    }
}

modbuild.embed("quiltParsers", "org.quiltmc.parsers:json:0.2.1", "org.quiltmc.parsers:gson:0.2.1")
