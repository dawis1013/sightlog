plugins {
    java
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "net.dawis.sightlog"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("net.dawis.sightlog.Main")
}

repositories {
    mavenCentral()
}

javafx {
    version = "25.0.3"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    //Database
    implementation("org.hibernate.orm:hibernate-core:7.4.0.Final")
    implementation("org.postgresql:postgresql:42.7.11")

    //GUI
    implementation("org.openjfx:javafx-controls:25.0.3")
    implementation("org.openjfx:javafx-fxml:25.0.3")

    //logs
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-core:2.26.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.0")

    //password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    //Tests
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    archiveClassifier.set("fat")

    mergeServiceFiles()

    manifest {
        attributes(mapOf("Main-Class" to "net.dawis.sightlog.Main"))
    }
}

tasks.test {
    useJUnitPlatform()
}