@file:Suppress("unused")
import wemi.boot.WemiBuildFolder
import wemi.boot.WemiRootFolder
import wemi.configuration
import wemi.dependency
import wemi.dependency.Jitpack
import wemi.dependency.sonatypeOss
import wemi.util.FileSet
import wemi.util.plus

val liveTesting by configuration("Add live testing capability") {
	sources modify { it + FileSet(WemiRootFolder / "src/live-test/java") }

	libraryDependencies add { JUnitAPI }
}

val DeadSouls by project(Archetypes.JavaProject) {

	projectGroup set { "com.darkyen.minecraft" }
	projectName set { "DeadSouls" }
	projectVersion set { "1.0-mc1.13.2" }

	repositories add { Repository("spigot-repo", "https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
	repositories add { Jitpack }
	repositories add { sonatypeOss("snapshots") }

	extend(compiling) {
		libraryDependencies add { dependency("org.jetbrains", "annotations", "16.0.2") }
		libraryDependencies add { dependency("org.spigotmc", "spigot-api", "1.13.2-R0.1-SNAPSHOT") }
	}
	
	extend(testing) {
		libraryDependencies add { JUnitAPI }
		libraryDependencies add { JUnitEngine }
		libraryDependencies add { dependency("org.spigotmc", "spigot-api", "1.14.2-R0.1-SNAPSHOT") }
	}

	assemblyOutputFile set { WemiBuildFolder / "DeadSouls-${projectVersion.get()}.jar" }
}
