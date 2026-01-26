pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net")
		maven("https://maven.neoforged.net/releases")
		maven("https://maven.architectury.dev")
		maven("https://maven.kikugie.dev/snapshots")
		maven("https://maven.kikugie.dev/releases")
		maven("https://repo.polyfrost.cc/releases")
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.7.10"
}

stonecutter {
	kotlinController = true
	centralScript = "build.gradle.kts"

	create(rootProject) {
		fun mc(mcVersion: String, loaders: Iterable<String>) {
			for (loader in loaders) {
				vers("$mcVersion-$loader", mcVersion)
			}
		}

		mc("1.20.1", listOf("fabric", "forge"))
		mc("1.21.1", listOf("fabric", "neoforge"))
		mc("1.21.3", listOf("fabric", "neoforge"))
		mc("1.21.6", listOf("fabric", "neoforge"))
		mc("1.21.9", listOf("fabric", "neoforge"))
//		mc("1.21.11", listOf("neoforge"))

		vcsVersion = "1.21.1-fabric"
	}
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "SoundPhysicsPerfected"
