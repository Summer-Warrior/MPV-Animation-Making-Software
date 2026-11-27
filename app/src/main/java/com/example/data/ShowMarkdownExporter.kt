package com.example.data

import com.example.model.ConsoleHardwareCatalog
import com.example.model.ShowProject

/**
 * Formats a ChronoStage AI ShowProject into clean H1/H2/H3 Markdown ready for
 * 1-tap clipboard export into Google Docs, Gemini Notebook, or production wikis.
 */
object ShowMarkdownExporter {

    fun exportShowToMarkdown(project: ShowProject): String {
        val totalSeconds = project.scenes.sumOf { it.durationSeconds.coerceAtLeast(1) }
        val consoleNames = project.scenes
            .map { ConsoleHardwareCatalog.findById(it.consoleId).name }
            .distinct()
            .joinToString(", ")

        val sb = StringBuilder()
        sb.appendLine("# ${project.title}")
        sb.appendLine()
        sb.appendLine("> **Subtitle:** ${project.subtitle}")
        sb.appendLine("> **Scenes:** ${project.scenes.size} | **Estimated Runtime:** ${totalSeconds}s")
        sb.appendLine("> **Hardware Pipelines:** $consoleNames")
        sb.appendLine()

        sb.appendLine("## Source Documentation")
        sb.appendLine()
        sb.appendLine(project.sourceDocumentation.trim().ifBlank { "_No source documentation provided._" })
        sb.appendLine()

        sb.appendLine("## Storyboard & Scene Breakdown")
        sb.appendLine()

        project.scenes.forEachIndexed { idx, scene ->
            val console = ConsoleHardwareCatalog.findById(scene.consoleId)
            sb.appendLine("### Scene ${idx + 1}: ${scene.title}")
            sb.appendLine()
            sb.appendLine("- **Console / Handheld:** ${console.name} (`${console.shortCode}`, ${console.resolutionLabel}, ${console.shadingPipeline.title})")
            sb.appendLine("- **3D Model & Animation:** ${scene.model3D.displayName} — ${scene.modelMotion.displayName} (Scale ${"%.2f".format(scene.modelScale)}x)")
            sb.appendLine("- **2D Sprite Actors:** ${scene.primarySprite.displayName} & ${scene.secondarySprite.displayName}")
            sb.appendLine("- **Surface Texture & FX:** ${scene.texturePreset.displayName} | ${scene.particleFx.displayName}")
            sb.appendLine("- **Camera Rig & Duration:** ${scene.cameraMove.displayName} (${scene.durationSeconds}s)")
            if (scene.documentationExcerpt.isNotBlank()) {
                sb.appendLine("- **Documentation Excerpt:** ${scene.documentationExcerpt}")
            }
            sb.appendLine()
            sb.appendLine("**Narrator Script:**")
            sb.appendLine()
            sb.appendLine("> \"${scene.narratorScript}\"")
            sb.appendLine()
        }

        return sb.toString().trimEnd() + "\n"
    }
}
