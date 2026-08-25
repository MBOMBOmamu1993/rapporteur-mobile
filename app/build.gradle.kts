import java.util.Properties

plugins {
    id("com.android.application")
}

/* La clé d'envoi vers Play vit HORS du dépôt (keystore.properties, cle-envoi.jks,
   tous deux ignorés par git). Sans elle, la construction release échoue plutôt
   que de produire un paquet que Play refuserait. */
val cles = Properties().apply {
    val fichier = rootProject.file("keystore.properties")
    if (fichier.exists()) fichier.inputStream().use { load(it) }
}

android {
    namespace = "com.lerapporteur.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lerapporteur.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.1"
    }

    signingConfigs {
        create("envoi") {
            if (cles.isNotEmpty()) {
                storeFile = rootProject.file(cles.getProperty("storeFile"))
                storePassword = cles.getProperty("storePassword")
                keyAlias = cles.getProperty("keyAlias")
                keyPassword = cles.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            /* Rien à réduire : trois classes, aucune dépendance. La coquille
               reste lisible telle quelle dans le paquet. */
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("envoi")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}
