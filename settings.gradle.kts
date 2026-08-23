rootProject.name = "casino-engine"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        // One engine, one client: pam-engine publishes com.nekgambling:pam-grpc-client, which
        // carries the player account, the wallet ledger and the currency registry together.
        // The retired IGaming-User-Engine / IGaming-Wallet-Engine feeds are gone with them.
        maven {
            name = "GitHubPackagesPamEngine"
            url = uri("https://maven.pkg.github.com/nekzabirov/IGaming-Pam-Engine")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
