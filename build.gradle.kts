// Note: No repositories block allowed here because repositoriesMode is FAIL_ON_PROJECT_REPOS.
// The buildscript block only declares classpath dependencies without repositories.

buildscript {
    repositories {
        google()

        mavenCentral()
    }
}

allprojects {
    // No repositories block here.
}
