import modbuild.McData

val mc = McData(project)
val deps = SppDependencyVersions(project)

dependencies {
    "modImplementation"("net.fabricmc:fabric-loader:${deps.fabricLoaderVersion}")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:${deps.fabricApiVersion}+${mc.version}")
    "modImplementation"("dev.isxander:yet-another-config-lib:${deps.yaclVersion}+${mc.version}-fabric")
    "modImplementation"("com.terraformersmc:modmenu:${deps.modmenuVersion}")
}
