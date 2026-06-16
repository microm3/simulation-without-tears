package com.example.transformation

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Helper for the OntoUML to Alloy transformation via a bundled CLI (`ontouml2alloy-cli.bundle.js`)
 * Expects Node.js 18+ on PATH
 */
object OntoUml2AlloyTransformer {
  private const val BUNDLE_RESOURCE = "/ontouml2alloy/ontouml2alloy-cli.bundle.js"

  fun transform(
          jsonFile: Path,
          outputDir: Path? = null,
          allowAbstractLeafInstances: Boolean = false,
  ): Path {
    val normalizedJson = jsonFile.normalize()
    val normalizedOutDir = (outputDir ?: normalizedJson.parent).normalize()
    Files.createDirectories(normalizedOutDir)

    require(normalizedJson.toFile().exists()) {
      "Input file not found: ${normalizedJson.toString()}"
    }

    val cli = Files.createTempFile("ontouml2alloy-cli-", ".js")
    try {
      javaClass.getResourceAsStream(BUNDLE_RESOURCE)?.use { input ->
        Files.newOutputStream(cli).use { input.copyTo(it) }
      }
              ?: error("Missing $BUNDLE_RESOURCE on classpath; the JAR is built incorrectly.")

      val cmd =
              mutableListOf(
                      "node",
                      cli.toString(),
                      normalizedJson.toString(),
                      normalizedOutDir.toString()
              )
      if (allowAbstractLeafInstances) cmd.add("--allow-abstract-leaf-instances")

      val process = ProcessBuilder(cmd).redirectErrorStream(false).start()
      val stdout = process.inputStream.bufferedReader().use { it.readText() }
      val stderr = process.errorStream.bufferedReader().use { it.readText() }
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        error(
                "ontouml2alloy-transformation exited with code $exitCode\n" +
                        "stdout: ${stdout.trim()}\nstderr: ${stderr.trim()}",
        )
      }
      return normalizedOutDir
    } finally {
      try {
        Files.deleteIfExists(cli)
      } catch (_: IOException) {
        // Best-effort cleanup
      }
    }
  }
}
