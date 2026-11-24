import tasks.BaseGeneratorTask

plugins {
    id("gadgetbridge.jvm.common")
    application
}

application {
    mainClass = "nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.codegen.FitCodeGen"
}

dependencies {
    implementation(libs.json)
    implementation(libs.gson)
}

val genFit = tasks.register<BaseGeneratorTask>("genFit") {
    description = "Generate FitMessage source files from JSON"

    val fitProfileJson = file("src/main/resources/fit_profile.json")

    inputDirectory.convention(layout.projectDirectory.dir("../app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/garmin/fit"))
    generatedSourceDirectory.set(layout.buildDirectory.dir("generated/sources/fit"))

    inputs.file(fitProfileJson)

    mainClass = application.mainClass
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            fitProfileJson.absolutePath,
            generatedSourceDirectory.get().dir("nodomain/freeyourgadget/gadgetbridge/service/devices/garmin/fit").asFile.absolutePath,
            inputDirectory.get().asFile.absolutePath
        )
    })
    classpath(sourceSets.named("main").map { it.runtimeClasspath })
}

val generatedFit = configurations.create("generatedFit") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("generatedFit", layout.buildDirectory.dir("generated/sources/fit")) {
        builtBy(genFit)
    }
}

tasks.register<JavaExec>("formatFitProfileJson") {
    description = "Reformat the JSON specification"

    val profileFile = file("src/main/resources/fit_profile.json")

    inputs.file(profileFile)
    outputs.file(profileFile)

    mainClass = "nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.codegen.FormatFitProfileJson"
    args(profileFile.absolutePath)
    classpath(sourceSets.named("main").map { it.runtimeClasspath })

    val verboseP = providers.gradleProperty("verboseLogLevel").map { it.toBoolean() }.orElse(false)
    jvmArguments.addAll(verboseP.map { if (it) listOf("-Dverbose=true") else emptyList() })
}
