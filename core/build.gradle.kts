plugins {
    kotlin("jvm") version "1.9.22"
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Evaluation outputs (scene exports, reports) live under build/ and are not committed.
    systemProperty("gg.repoRoot", rootDir.parentFile.absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Runnable tools: ./gradlew -p core run<Name>
fun tool(name: String, main: String) = tasks.register<JavaExec>(name) {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(main)
    workingDir = rootDir.parentFile
    args = (project.findProperty("args") as String?)?.split(" ") ?: emptyList()
}
tool("runDemo", "org.guideglass.core.sim.DemoKt")
tool("runBaselineEval", "org.guideglass.core.sim.BaselineEvalKt")
