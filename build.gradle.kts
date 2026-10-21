import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml.Load

plugins {
    java
    `maven-publish`

    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run)
    alias(libs.plugins.resource.paper)
    alias(libs.plugins.shadow)
}

val developmentVersion = "2.3.2-1.20.1-SNAPSHOT"

version = providers.gradleProperty("releaseVersion").orElse(developmentVersion).get()
group = "live.minehub"

repositories {
    mavenCentral()
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.jspecify:jspecify:1.0.0")
    paperweight.paperDevBundle("${libs.versions.minecraft.get()}-R0.1-SNAPSHOT")

    implementation(project(":core"))
    implementation(project(":paper_1_20_1"))

    compileOnly(libs.zstd)
    compileOnly(libs.worldedit)
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
    }

    shadowJar {
        archiveClassifier.set("")
    }

    jar {
        enabled = false
    }
}

allprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }
    }
}

java {
    // Generate sources JAR
    withSourcesJar()
}

tasks.named("build") {
    dependsOn(tasks.named("reobfJar"))
}

publishing {
    repositories {
        // Publish to Maven Local only if not running in an action environment
        if (!isAction()) {
            mavenLocal()
        } else {
            maven {
                name = if (version.toString().endsWith("-SNAPSHOT")) "Snapshots" else "Releases"
                url = uri("https://repo.minehub.live/" + if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases")
                credentials {
                    username = System.getenv("REPO_ACTOR")
                    password = System.getenv("REPO_TOKEN")
                }
            }
        }
    }

    publications {
        create<MavenPublication>("shadow") {
            artifact(layout.buildDirectory.file("libs/${project.name}-${project.version}-reobf.jar")) {
                builtBy("reobfJar")
            }
            artifact(tasks.named("sourcesJar"))
        }
    }
}

fun isAction(): Boolean {
    return System.getenv("CI") != null
}

paperPluginYaml {
    name = project.name
    version = project.version.toString()
    authors = listOf("emortaldev")
    website = "https://github.com/TavernSMP/PolarPaper/tree/1_20_1"
    description = "Polar world format for Paper"
    apiVersion = "1.20"

    foliaSupported = false

    main = "live.minehub.polarpaper.PolarPaper"
    loader = "live.minehub.polarpaper.PolarPaperLoader"

    dependencies {
        server("WorldEdit", load = Load.BEFORE, required = false)
        server("FastAsyncWorldEdit", load = Load.BEFORE, required = false)
    }
}


configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "net.fabricmc" && requested.name == "tiny-remapper") {
            useVersion("0.14.0")
        }
    }
}

val acceptance by sourceSets.creating {
    java.srcDir("integration-tests/src")
    resources.srcDir("integration-tests/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

tasks.register<Jar>("acceptanceJar") {
    dependsOn(acceptance.classesTaskName)
    from(acceptance.output)
    archiveFileName.set("polarpaper-acceptance.jar")
    destinationDirectory.set(layout.buildDirectory.dir("acceptance"))
}

tasks.register<Sync>("stagePlugin") {
    dependsOn("reobfJar")
    from(layout.buildDirectory.file("libs/${project.name}-${project.version}-reobf.jar"))
    into(layout.projectDirectory.dir("dist"))
}

tasks.named("build") {
    dependsOn("stagePlugin")
}
