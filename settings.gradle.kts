rootProject.name = "Tessera"

pluginManagement {
  includeBuild("build-logic")
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app:androidApp")
include(":app:desktopApp")
include(":app:shared")
include(":protocol")
include(":core-api")
include(":core")
include(":plugin-sdk")
include(":server")
include(":plugins:habits")
include(":plugins:planner")
include(":plugins:books")
include(":plugins:watching")
include(":plugins:finance")
include(":plugins:recipes")
include(":plugins:languages")
include(":plugins:study")
include(":plugins:projects")
include(":plugins:workouts")
include(":plugins:nutrition")
include(":external-plugins:counters")
