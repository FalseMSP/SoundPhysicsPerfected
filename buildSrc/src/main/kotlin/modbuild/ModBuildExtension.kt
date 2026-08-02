package modbuild

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/** Config block for the relocating [ModBuildExtension.embed] overload. */
class EmbedSpec {
    internal val relocations = mutableMapOf<String, String>()

    /** Relocates every class under package [from] to package [to] in the shipped/dev-visible jar. */
    fun relocate(from: String, to: String) {
        relocations[from] = to
    }
}

/**
 * See buildSrc/README.md for the full usage guide and rationale. Short version: `embed(...)` makes
 * a subproject or plain jar dependency both ship in the mod jar and stay visible on
 * NeoForge/Forge's dev-time classpath, which plain `include()` alone does not guarantee.
 */
open class ModBuildExtension(private val project: Project, private val loader: LoaderData) {
    private val modId: String
        get() = project.property("mod.id").toString()

    private val isForgeLike: Boolean
        get() = loader.isForge || loader.isNeoforge

    /** Embeds a subproject: packaged via include(), registered for dev-time visibility. */
    fun embed(subproject: Project) {
        // Sibling subprojects aren't evaluated in dependency order automatically.
        project.evaluationDependsOn(subproject.path)

        project.dependencies.add("implementation", subproject)
        project.dependencies.add("include", subproject)

        val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")
        val subSourceSet = subproject.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")

        // Reflection, not a typed LoomGradleExtensionAPI/ModSettings reference — see README.md.
        val loomExtension = project.extensions.getByName("loom")
        val mods = loomExtension.javaClass.getMethod("getMods").invoke(loomExtension)
        val maybeCreate = mods.javaClass.methods.first {
            it.name == "maybeCreate" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        val modSettings = maybeCreate.invoke(mods, modId)

        // ModSettings.sourceSet has two 2-arg overloads (String, Project) and (SourceSet, Project);
        // match exact parameter types, not just arg count.
        val sourceSetOneArg = modSettings.javaClass.methods.first {
            it.name == "sourceSet" && it.parameterTypes.contentEquals(arrayOf(SourceSet::class.java))
        }
        val sourceSetTwoArg = modSettings.javaClass.methods.first {
            it.name == "sourceSet" && it.parameterTypes.contentEquals(arrayOf(SourceSet::class.java, Project::class.java))
        }
        sourceSetOneArg.invoke(modSettings, mainSourceSet)
        sourceSetTwoArg.invoke(modSettings, subSourceSet, subproject)
    }

    /**
     * Embeds one or more plain jar dependencies (not a subproject), resolved non-transitively.
     * `name` must be a valid identifier fragment — it names the backing Configuration/Sync task.
     */
    fun embed(name: String, vararg notations: String) {
        val config = project.configurations.create("${name}RuntimeForDev") { isTransitive = false }
        notations.forEach { project.dependencies.add(config.name, it) }
        notations.forEach { project.dependencies.add("include", it) }

        if (!isForgeLike) return

        val capitalized = name.replaceFirstChar { it.uppercase() }
        val extractedDir = project.layout.buildDirectory.dir("embedded-$name")
        val extractTask = project.tasks.register<Sync>("extract${capitalized}ForDev") {
            from({ config.map { project.zipTree(it) } })
            into(extractedDir)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        project.afterEvaluate {
            val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")
            (mainSourceSet.output.classesDirs as ConfigurableFileCollection).from(extractedDir).builtBy(extractTask)
        }
    }

    /**
     * Embeds a single plain jar dependency with Shadow-backed package relocation — see
     * buildSrc/README.md's "Relocation" section. `configure` has no default value: a defaulted
     * trailing lambda is ambiguous against [embed] (name, vararg notations) for a single-notation
     * call, since Kotlin prefers a fixed-arity overload over a vararg one even via a default.
     */
    fun embed(name: String, notation: String, configure: EmbedSpec.() -> Unit) {
        val spec = EmbedSpec().apply(configure)
        val config = project.configurations.create("${name}RuntimeForDev") { isTransitive = false }
        project.dependencies.add(config.name, notation)

        if (spec.relocations.isEmpty()) {
            project.dependencies.add("include", notation)
        } else {
            val alreadyApplied = project.pluginManager.hasPlugin("com.gradleup.shadow")
            project.pluginManager.apply(ShadowPlugin::class.java)
            if (!alreadyApplied) {
                // Shadow's own auto-registered default "shadowJar" task bundles the whole project
                // (not just our isolated configuration) and runs as part of build/assemble
                // unconditionally; disable it once, the first time this plugin gets applied.
                project.tasks.named("shadowJar") { enabled = false }
            }

            val capitalized = name.replaceFirstChar { it.uppercase() }
            val shadowJarTask = project.tasks.register<ShadowJar>("relocate${capitalized}") {
                configurations.set(listOf(config))
                spec.relocations.forEach { (from, to) -> relocate(from, to) }
                archiveClassifier.set("relocated-$name")
            }
            // Not include(): Loom's processIncludeJars requires every included artifact to trace
            // back to a real module or project component, which a locally-generated Shadow output
            // file can't. Merge its contents into the jar task directly instead.
            project.tasks.named<Jar>("jar") {
                from({ project.zipTree(shadowJarTask.get().archiveFile) })
            }
        }

        if (!isForgeLike) return

        val capitalized = name.replaceFirstChar { it.uppercase() }
        val extractedDir = project.layout.buildDirectory.dir("embedded-$name")
        val extractTask = project.tasks.register<Sync>("extract${capitalized}ForDev") {
            from({
                if (spec.relocations.isEmpty()) {
                    config.map { project.zipTree(it) }
                } else {
                    val shadowJarTask = project.tasks.named<ShadowJar>("relocate$capitalized")
                    listOf(project.zipTree(shadowJarTask.get().archiveFile))
                }
            })
            into(extractedDir)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        project.afterEvaluate {
            val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")
            (mainSourceSet.output.classesDirs as ConfigurableFileCollection).from(extractedDir).builtBy(extractTask)
        }
    }
}
