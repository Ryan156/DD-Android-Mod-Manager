package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.parser.ModMetadataReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testModMetadataFromFolder() {
        val modFolder = tempFolder.newFolder("falconer_class_mod")
        val xmlFile = File(modFolder, "project.xml")
        xmlFile.writeText(
            """
            <project>
                <ModData>
                    <Id>219381001</Id>
                    <Title>The Falconer Class Mod</Title>
                    <Author>Marney &amp; Team</Author>
                    <ModVersion>2.4</ModVersion>
                    <ItemDescriptionHtml>Adds the Falconer hero class to Darkest Dungeon</ItemDescriptionHtml>
                    <Tags>Hero Class, Trinkets</Tags>
                </ModData>
            </project>
            """.trimIndent()
        )

        val metadata = ModMetadataReader.readFromFolder(modFolder)

        assertEquals("219381001", metadata.id)
        assertEquals("The Falconer Class Mod", metadata.title)
        assertEquals("Marney & Team", metadata.author)
        assertEquals("2.4", metadata.version)
        assertTrue(metadata.tags.contains("Hero Class"))
    }

    @Test
    fun testDisabledMarkerFileDetection() {
        val modFolder = tempFolder.newFolder("sample_mod")
        val disabledMarker = File(modFolder, ".disabled")
        disabledMarker.createNewFile()

        assertTrue(disabledMarker.exists())
        disabledMarker.delete()
        assertFalse(disabledMarker.exists())
    }
}
