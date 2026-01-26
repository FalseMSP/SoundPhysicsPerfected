import dev.kikugie.stonecutter.data.tree.struct.ProjectNode

plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.publishing)
}

stonecutter active "1.20.1-forge" /* [SC] DO NOT EDIT */

stonecutter tasks {
    val ordering = Comparator
        .comparing<ProjectNode, _> { stonecutter.parse(it.metadata.version) }
        .thenComparingInt { if (it.metadata.project.endsWith("fabric")) 1 else 0 }

    order("publishMods", ordering)
}

tasks.named("publishMods") {
    group = "build"
}
