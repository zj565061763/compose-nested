plugins {
   id("com.android.library")
   id("org.jetbrains.kotlin.android")
   `maven-publish`
}

val libGroupId = "com.sd.lib.android"
val libArtifactId = "compose-nested"
val libVersion = "1.0.0-alpha08"

android {
   namespace = "com.sd.lib.compose.nested"
   compileSdk = libs.versions.androidCompileSdk.get().toInt()
   defaultConfig {
      minSdk = 21
   }

   buildFeatures {
      compose = true
   }

   kotlinOptions {
      freeCompilerArgs += "-module-name=$libGroupId.$libArtifactId"
   }
   composeOptions {
      kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
   }

   publishing {
      singleVariant("release") {
         withSourcesJar()
      }
   }
}

kotlin {
   jvmToolchain(8)
}

dependencies {
   implementation(libs.androidx.compose.foundation)
}

publishing {
   publications {
      create<MavenPublication>("release") {
         groupId = libGroupId
         artifactId = libArtifactId
         version = libVersion
         afterEvaluate {
            from(components["release"])
         }
      }
   }
}