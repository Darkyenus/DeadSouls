@file:Suppress("unused")
import wemi.*
import wemi.boot.WemiBuildFolder
import wemi.boot.WemiRootFolder
import wemi.dependency.Jitpack
import wemi.dependency.sonatypeOss
import wemi.util.*
import java.nio.file.StandardCopyOption

val liveTesting by configuration("Add live testing capability") {
	sources modify { it + FileSet(WemiRootFolder / "src/live-test/java") }

	libraryDependencies add { JUnitAPI }
}

val DeadSouls by project(Archetypes.JavaProject) {

	projectGroup set { "com.darkyen.minecraft" }
	projectName set { "DeadSouls" }
	projectVersion set { "1.1" }

	repositories add { Repository("spigot-repo", "https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
	repositories add { Jitpack }
	repositories add { sonatypeOss("snapshots") }

	extend(compiling) {
		libraryDependencies add { dependency("org.jetbrains", "annotations", "16.0.2") }
		libraryDependencies add { dependency("org.spigotmc", "spigot-api", "1.14.2-R0.1-SNAPSHOT") }
	}
	
	extend(testing) {
		libraryDependencies add { JUnitAPI }
		libraryDependencies add { JUnitEngine }
		libraryDependencies add { dependency("org.spigotmc", "spigot-api", "1.14.4-R0.1-SNAPSHOT") }
	}

	assemblyOutputFile set { WemiBuildFolder / "DeadSouls-${projectVersion.get()}.jar" }
	
	assembly modify { assembled ->
		val testServerPlugins = WemiRootFolder / "../TEST SERVER/plugins"
		if (testServerPlugins.exists()) {
			assembled.copyRecursively(testServerPlugins / (projectName.get() + ".jar"), StandardCopyOption.REPLACE_EXISTING)
			println("Copied to test server")
		}

		assembled
	}
}
