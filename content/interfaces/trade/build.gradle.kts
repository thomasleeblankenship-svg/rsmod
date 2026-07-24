plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    testImplementation(projects.api.testing.testParams)
}
