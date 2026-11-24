plugins {
    id("gadgetbridge.jvm.common")
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(libs.protobuf.lite)
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                findByName("java")?.option("lite")
            }
        }
    }
}
