import tasks.BaseGeneratorTask

plugins {
    id("gadgetbridge.jvm.common")
    application
}

application {
    mainClass = "org.gadgetbridge.kaitai_aux.main.KaitaiAuxGen"
}

dependencies {
    implementation(libs.snakeyaml)
}

val genKaitaiAux = tasks.register<BaseGeneratorTask>("genKaitaiAux") {
    description = "Generate auxiliary source files from Kaitai structs"

    inputDirectory.convention(layout.projectDirectory.dir("../app/src/main/ksy"))
    generatedSourceDirectory.set(layout.buildDirectory.dir("generated/sources/kaitaiaux"))

    mainClass = application.mainClass
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            inputDirectory.get().asFile.absolutePath,
            generatedSourceDirectory.get().asFile.absolutePath
        )
    })
    classpath(sourceSets.named("main").map { it.runtimeClasspath })
}

val generatedKaitaiAux = configurations.create("generatedKaitaiAux") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("generatedKaitaiAux", layout.buildDirectory.dir("generated/sources/kaitaiaux")) {
        builtBy(genKaitaiAux)
    }
}
