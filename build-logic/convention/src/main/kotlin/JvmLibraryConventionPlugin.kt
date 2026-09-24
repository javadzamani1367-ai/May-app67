import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin/JVM module (no Android): fast JUnit 5 tests. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JAVA_VERSION.toString()))
        }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        dependencies {
            add("testImplementation", platform(libs.lib("junit5-bom")))
            add("testImplementation", libs.lib("junit5-jupiter"))
            add("testImplementation", libs.lib("junit5-params"))
            add("testRuntimeOnly", libs.lib("junit5-launcher"))
            add("testImplementation", libs.lib("truth"))
        }
    }
}
