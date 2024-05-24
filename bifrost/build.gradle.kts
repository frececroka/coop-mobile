import com.google.protobuf.gradle.proto

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("com.google.protobuf")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    target {
        jvmToolchain(11)
    }
}

sourceSets {
    main {
        proto {
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        register("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
        }
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                this["java"].option("lite")
            }
            plugins {
                create("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    implementation("io.grpc:grpc-protobuf-lite:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}
