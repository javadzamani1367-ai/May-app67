plugins {
    alias(libs.plugins.roozban.jvm.library)
    application
}

application {
    mainClass.set("ir.roozban.ai.eval.EvalKt")
}

dependencies {
    implementation(projects.ai.core)
    implementation(projects.core.documents)
    implementation(libs.pdfbox)
    implementation(projects.ai.tools)
    implementation(projects.core.testing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

/** Long-form dictation eval (see LongForm.kt): ./gradlew :ai:eval:longform --args="bench model list config". */
tasks.register<JavaExec>("longform") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ir.roozban.ai.eval.LongFormKt")
    workingDir = rootProject.projectDir
}

/** PDF → Word eval (see PdfEval.kt): ./gradlew :ai:eval:pdf --args="in.pdf out.docx". */
tasks.register<JavaExec>("pdf") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ir.roozban.ai.eval.PdfEvalKt")
    workingDir = rootProject.projectDir
    maxHeapSize = "256m"
}
