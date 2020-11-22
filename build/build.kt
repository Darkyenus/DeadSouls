@file:Suppress("unused")
import wemi.*
import wemi.Keys
import wemi.assembly.MergeStrategy
import wemi.boot.WemiBuildFolder
import wemi.boot.WemiRootFolder
import wemi.dependency.Jitpack
import wemi.dependency.ProjectDependency
import wemi.dependency.sonatypeOss
import wemi.test.JUnitPlatformLauncher
import wemi.util.*
import java.nio.file.StandardCopyOption

val liveTesting by configuration("Add live testing capability") {
	sources modify { it + FileSet(WemiRootFolder / "src/live-test/java") }

	libraryDependencies add { Dependency(JUnitAPI) }

	spigotVersion set { "1.16.1-R0.1-SNAPSHOT" }
}

val spigotVersion by key("Spigot API version to use", "1.14.4-R0.1-SNAPSHOT")

val SpigotPlugin by archetype(Archetypes::JavaProject) {
	projectGroup set { "com.darkyen.minecraft" }
	projectVersion set { "1.6" }

	repositories add { Repository("spigot-repo", "https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
	repositories add { Jitpack }
	repositories add { sonatypeOss("snapshots") }

	libraryDependencies add { wemi.dependency("org.jetbrains", "annotations", "16.0.2", scope = ScopeProvided) }
	libraryDependencies add { wemi.dependency("org.spigotmc", "spigot-api", spigotVersion.get(), scope = ScopeProvided) }

	assemblyOutputFile set { WemiBuildFolder / "${projectName.get()}-${projectVersion.get()}.jar" }

	assembly modify { assembled ->
		val testServerPlugins = WemiRootFolder / "../TEST SERVER/plugins"
		if (testServerPlugins.exists()) {
			assembled.copyRecursively(testServerPlugins / (projectName.get() + ".jar"), StandardCopyOption.REPLACE_EXISTING)
			println("Copied to test server")
		}

		assembled
	}

	assemblyMergeStrategy modify { strategy ->
		{ name ->
			if (name == "module-info.class") {
				MergeStrategy.Discard
			} else {
				strategy(name)
			}
		}
	}
}

val DeadSouls by project(SpigotPlugin, Archetypes.JUnitLayer) {
	projectName set { "DeadSouls" }

	// We have to apply testing dependencies only in the testing
	extend(Archetypes.JUnitLayer) {
		libraryDependencies modify { it.filterNot { it.scope == ScopeTest }.toSet() }
	}
	extend(testing) {
		Keys.libraryDependencies addAll { listOf(
				wemi.dependency.Dependency(wemi.test.JUnitAPI, scope = wemi.dependency.ScopeTest),
				wemi.dependency.Dependency(wemi.test.JUnitEngine, scope = wemi.dependency.ScopeTest),
				wemi.dependency.Dependency(JUnitPlatformLauncher, scope = wemi.dependency.ScopeTest)
		) }
	}
}

val DeadSoulsAPITest by project(path("api-test"), SpigotPlugin) {
	projectDependencies add { ProjectDependency(DeadSouls, scope = ScopeProvided) }
}
