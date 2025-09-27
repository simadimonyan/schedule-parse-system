plugins {
    id("java")
    id("org.springframework.boot") version("3.5.6")
    id("io.spring.dependency-management") version("1.1.7")
}

group = "app"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // Spring (Boot, Web, Data, Security)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Excel (Apache POI)
    implementation("org.apache.poi:poi:5.4.1")
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    // PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // S3
    implementation("io.minio:minio:8.5.17")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.14.0")
    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.14.0-alpha")

//    // OTLP BOM
//    implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.20.1"))
//    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.20.1")
//
//    // OTLP API
//    implementation("io.opentelemetry:opentelemetry-api")
//    implementation("io.opentelemetry:opentelemetry-sdk")
//    implementation("io.opentelemetry:opentelemetry-common")
//    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
//    implementation("io.opentelemetry.semconv:opentelemetry-semconv")
//    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
//
//    // SLF4J / Logback (мост для логов)
//    implementation("org.slf4j:slf4j-api:2.0.17")
//    implementation("ch.qos.logback:logback-core:1.5.18")
//    implementation("ch.qos.logback:logback-classic:1.5.18")
//    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.20.1-alpha")

}

tasks.test {
    useJUnitPlatform()
}