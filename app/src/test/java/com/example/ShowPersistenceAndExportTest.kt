package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShowMarkdownExporter
import com.example.data.ShowTextureStore
import com.example.data.StarterShowPresets
import com.example.engine.snapToPixelGrid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShowPersistenceAndExportTest {

    @Test
    fun `texture store persists reloads and deletes PNG inside show_textures`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ShowTextureStore(context)
        val showId = 42L

        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.CYAN)

        val savedFile = store.saveTexture(showId, bitmap)
        val expectedDir = File(context.filesDir, ShowTextureStore.TEXTURES_SUBDIR).canonicalFile
        assertTrue(savedFile.exists())
        assertTrue(savedFile.canonicalPath.startsWith(expectedDir.path + File.separator))
        assertEquals("42.png", savedFile.name)
        assertTrue(store.hasTexture(showId))

        val reloaded = store.loadTexture(showId)
        assertNotNull(reloaded)
        assertEquals(16, reloaded!!.width)
        assertEquals(16, reloaded.height)

        val deleted = store.deleteTexture(showId)
        assertTrue(deleted)
        assertFalse(savedFile.exists())
        assertNull(store.loadTexture(showId))
    }

    @Test
    fun `texture store rejects invalid show IDs and path traversal names`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ShowTextureStore(context)

        val invalidIds = listOf(0L, -1L, -99L)
        invalidIds.forEach { badId ->
            val failed = runCatching { store.resolveSafeTextureFile(badId) }.isFailure
            assertTrue("Expected failure for showId=$badId", failed)
        }

        val traversalNames = listOf(
            "../evil.png",
            "..\\evil.png",
            "sub/42.png",
            "42.jpg",
            "abc.png",
            ""
        )
        traversalNames.forEach { badName ->
            val failed = runCatching { store.resolveSafeFileByName(badName) }.isFailure
            assertTrue("Expected failure for filename=$badName", failed)
        }
    }

    @Test
    fun `markdown exporter formats clean H1 H2 H3 storyboard for Docs and Notebook`() {
        val show = StarterShowPresets.defaultShowProjects.first()
        val md = ShowMarkdownExporter.exportShowToMarkdown(show)

        assertTrue(md.startsWith("# ${show.title}"))
        assertTrue(md.contains("## Source Documentation"))
        assertTrue(md.contains("## Storyboard & Scene Breakdown"))
        assertTrue(md.contains("### Scene 1: ${show.scenes.first().title}"))
        assertTrue(md.contains("### Scene ${show.scenes.size}: ${show.scenes.last().title}"))
        assertTrue(md.contains("**Console / Handheld:**"))
        assertTrue(md.contains("**3D Model & Animation:**"))
        assertTrue(md.contains("**2D Sprite Actors:**"))
        assertTrue(md.contains("> \"${show.scenes.first().narratorScript}\""))
    }

    @Test
    fun `pixelGridScale snapping keeps HD-2D sharp and steps retro consoles`() {
        // Modern HD-2D (pixelGridScale = 1, vertexSnapStrength = 0) stays unquantized
        assertEquals(123.45f, snapToPixelGrid(123.45f, pixelGridScale = 1, vertexSnapStrength = 0f), 0.0001f)

        // Game Boy DMG (pixelGridScale = 5) snaps cleanly to multiples of 5
        assertEquals(125f, snapToPixelGrid(123.45f, pixelGridScale = 5, vertexSnapStrength = 0f), 0.0001f)

        // PS1 vertex snap overrides smaller grid scale
        assertEquals(121f, snapToPixelGrid(123.45f, pixelGridScale = 2, vertexSnapStrength = 5.5f), 0.001f)
    }
}
