plugins {
    java
    alias(libs.plugins.paperweight.userdev)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.jspecify:jspecify:1.0.0")
    paperweight.paperDevBundle("${libs.versions.minecraft.get()}-R0.1-SNAPSHOT")
    implementation(project(":core"))
}
