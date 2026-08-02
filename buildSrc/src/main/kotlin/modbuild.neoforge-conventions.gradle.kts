import modbuild.McData
import modbuild.ModBuildExtension

val mc = McData(project)
val deps = SppDependencyVersions(project)
val modbuild = project.extensions.getByType(ModBuildExtension::class.java)

dependencies {
    "neoForge"("net.neoforged:neoforge:${deps.neoforgeVersion}")
    // isTransitive = false also drops YACL's org.quiltmc.parsers:json/gson runtime dep
    // (GsonReader) -- re-added explicitly below via embed(), non-transitively on purpose.
    "modImplementation"("dev.isxander:yet-another-config-lib:${deps.yaclVersion}+${mc.version}-neoforge") {
        isTransitive = false
    }
    if (mc.version == "1.21.1") {
        val sableCompanionVersion = "1.4.2"
        val sableDep = "modApi"("dev.ryanhcode.sable-companion:sable-companion-common-${mc.version}:[$sableCompanionVersion,)")
        "include"(sableDep!!)
    }
}

modbuild.embed("quiltParsers", "org.quiltmc.parsers:json:0.2.1", "org.quiltmc.parsers:gson:0.2.1")
