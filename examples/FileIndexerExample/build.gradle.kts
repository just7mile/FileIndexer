plugins {
  kotlin("jvm") version "1.9.0"
  application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  implementation(files("libs/file-indexer-1.0-SNAPSHOT.jar"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
}

tasks.test {
  useJUnitPlatform()
}

application {
  mainClass.set("MainKt")
}