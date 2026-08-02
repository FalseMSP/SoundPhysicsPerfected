import org.gradle.api.Project

// No package, deliberately: unlike modbuild's classes, this enumerates Sound Physics Perfected's
// own third-party dependency versions specifically (YACL, DevAuth, mixinconstraints, ...) -- it
// is not part of the portable modbuild engine and wouldn't apply to another mod's dependency set
// as-is. Lives in buildSrc only because the convention plugins (also buildSrc-compiled) need it.
class SppDependencyVersions(project: Project) {
    val forgeVersion = project.property("deps.forge_version")
    val neoforgeVersion = project.property("deps.neoforge_version")
    val fabricLoaderVersion = project.property("deps.fabric_loader_version")
    val fabricApiVersion = project.property("deps.fabric_api_version")
    val modmenuVersion = project.property("deps.modmenu_version")
    val voicechat_api_version = project.property("deps.voicechat_api_version")
    val yaclVersion = project.property("deps.yacl_version")
    val devauthVersion = project.property("deps.devauth_version")
    val mixinconstraintsVersion = project.property("deps.mixinconstraints_version")
    val mixinsquaredVersion = project.property("deps.mixinsquared_version")
}
