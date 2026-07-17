pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://jitpack.io")
            // Adiciona credenciais para autenticar no JitPack
            credentials {
                username = providers.gradleProperty("JITPACK_USER").getOrElse("defaultUser")
                password = providers.gradleProperty("JITPACK_TOKEN").getOrElse("defaultToken")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
//dependencyResolutionManagement {
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
//    repositories {
//        google()
//        mavenCentral()
//        maven {
//            url = uri("https://jitpack.io")
//            // Adiciona as mesmas credenciais aqui também
//            credentials {
//                username = providers.gradleProperty("JITPACK_USER").getOrElse("defaultUser")
//                password = providers.gradleProperty("JITPACK_TOKEN").getOrElse("defaultToken")
//            }
//        }
//    }
//}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Lume"
include(":app")
