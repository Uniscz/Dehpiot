pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google\\.testing.*"); includeGroup("com.google.android.gms"); includeGroup("com.google.mlkit"); includeGroup("com.google.firebase"); includeGroupByRegex("com\\.google\\.android\\.datatransport.*"); includeGroupByRegex("com\\.google\\.android\\.odml.*") } }
        mavenCentral()
    }
}
rootProject.name = "Dehpilot"
include(":core", ":app")
