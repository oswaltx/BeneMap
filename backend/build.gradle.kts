    plugins {
        kotlin("jvm") version "2.2.21"
        kotlin("plugin.spring") version "2.2.21"
        id("org.springframework.boot") version "4.0.3"
        id("io.spring.dependency-management") version "1.1.7"
        kotlin("plugin.jpa") version "2.2.21"


    }

    group = "com.example"
    version = "0.0.1-SNAPSHOT"
    description = "Demo project for Spring Boot"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.jsoup:jsoup:1.17.2") //Scraping
        implementation("org.json:json:20240303")
        implementation("tools.jackson.module:jackson-module-kotlin:3.0.0")
        runtimeOnly("com.h2database:h2")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
