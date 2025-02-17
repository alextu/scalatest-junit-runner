plugins {
    java
    scala
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.12.8")

    testImplementation("org.scalatest:scalatest_2.12:3.2.0-M3")
    testRuntimeOnly("org.junit.platform:junit-platform-engine:1.6.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.6.0")
    testRuntimeOnly("co.helmethair:scalatest-junit-runner:0.1.11")
}

tasks {
    test{
        useJUnitPlatform {
            includeEngines("scalatest")
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}
