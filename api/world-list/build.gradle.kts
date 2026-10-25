plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.guice)
    implementation(libs.kotlin.coroutines.core)
    implementation(projects.api.db)
    implementation(projects.api.script)
    implementation(projects.api.serverConfig)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
    implementation(projects.server.services)
}
