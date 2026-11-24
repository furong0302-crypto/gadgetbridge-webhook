import tasks.BaseGeneratorTask

plugins {
    id("gadgetbridge.jvm.common")
    application
}

application {
    mainClass = "nodomain.freeyourgadget.gadgetbridge.daogen.GBDaoGenerator"
}

dependencies {
    // https://codeberg.org/Freeyourgadget/greenDao
    // As of 2025-06-19, this is bundled directly in the repository due to jitpack build issues.
    //implementation 'com.github.Freeyourgadget:greendao:1998d7cd2d21f662c6044f6ccf3b3a251bbad341'

    implementation(libs.freemarker)
}

sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("src-template")
        }
    }
}

val genSources = tasks.register<BaseGeneratorTask>("genSources") {
    description = "Generate DAO entity and session source files"

    generatedSourceDirectory.set(layout.buildDirectory.dir("generated/sources/gbdao"))

    inputs.dir(file("src"))
    inputs.dir(file("src-template"))

    mainClass = application.mainClass
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(generatedSourceDirectory.get().asFile.absolutePath)
    })
    classpath(sourceSets.named("main").map { it.runtimeClasspath })
}

val generatedGbDao = configurations.create("generatedGbDao") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("generatedGbDao", layout.buildDirectory.dir("generated/sources/gbdao")) {
        builtBy(genSources)
    }
}
