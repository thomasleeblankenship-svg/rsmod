plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.worldList)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.playerOutput)
    integrationImplementation(projects.api.serverConfig)
    integrationImplementation(projects.api.worldList)
    integrationImplementation(libs.rsprot.api)
}
