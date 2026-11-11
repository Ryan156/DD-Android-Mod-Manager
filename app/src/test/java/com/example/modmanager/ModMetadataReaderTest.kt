package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.parser.ModMetadataReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ModMetadataReaderTest {

    @Test
    fun testParseProjectXml() {
        val xmlContent = """
            <project>
                <ModData>
                    <Id>123456</Id>
                    <Title>The Falconer Class Mod</Title>
                    <Author>Marney</Author>
                    <ModVersion>2.4</ModVersion>
                    <Language>english</Language>
                    <ItemDescription>Plain project description</ItemDescription>
                    <ItemDescriptionHtml>Awesome hero class mod for Darkest Dungeon</ItemDescriptionHtml>
                    <Tags>Hero Class, Trinkets</Tags>
                </ModData>
            </project>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8))
        val metadata = ModMetadataReader.parseProjectXml(inputStream)

        assertNotNull(metadata)
        assertEquals("123456", metadata?.id)
        assertEquals("The Falconer Class Mod", metadata?.title)
        assertEquals("Marney", metadata?.author)
        assertEquals("2.4", metadata?.version)
        assertEquals("English", metadata?.language)
        assertEquals("Plain project description", metadata?.description)
        assertTrue(metadata?.tags?.contains("Hero Class") == true)
        assertTrue(metadata?.tags?.contains("Trinkets") == true)
    }

    @Test
    fun testParseProjectXmlVersionComponents() {
        val xmlContent = """
            <project>
                <Title>Versioned Mod</Title>
                <VersionMajor>1</VersionMajor>
                <VersionMinor>2</VersionMinor>
                <TargetBuild>1</TargetBuild>
            </project>
        """.trimIndent()

        val metadata = ModMetadataReader.parseProjectXml(
            ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8))
        )

        assertEquals("1.2.1", metadata?.version)
    }

    @Test
    fun testParseNestedProjectTags() {
        val xmlContent = """
            <project>
                <Title>Tagged Mod</Title>
                <Language>french</Language>
                <Tags>
                    <Tags>Gameplay</Tags>
                    <Tags>Overhaul</Tags>
                </Tags>
            </project>
        """.trimIndent()

        val metadata = ModMetadataReader.parseProjectXml(
            ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8))
        )

        assertEquals("French", metadata?.language)
        assertEquals(listOf("Gameplay", "Overhaul"), metadata?.tags)
    }

    @Test
    fun testDisplayLanguageForSimplifiedChinese() {
        val xmlContent = """
            <project>
                <Title>Chinese Mod</Title>
                <Language>schinese</Language>
                <ItemDescription/>
            </project>
        """.trimIndent()

        val metadata = ModMetadataReader.parseProjectXml(
            ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8))
        )

        assertEquals("Chinese", metadata?.language)
        assertEquals(null, metadata?.description)
    }

    @Test
    fun testParseModInfoJson() {
        val jsonContent = """
            {
                "id": "mariner_mod",
                "title": "The Mariner Hero",
                "author": "ActionJack",
                "version": "1.8",
                "description": "Seafaring front-line warrior",
                "tags": ["Hero Class", "Skins"]
            }
        """.trimIndent()

        val metadata = ModMetadataReader.parseModInfoJson(jsonContent)

        assertNotNull(metadata)
        assertEquals("mariner_mod", metadata?.id)
        assertEquals("The Mariner Hero", metadata?.title)
        assertEquals("ActionJack", metadata?.author)
        assertEquals("1.8", metadata?.version)
        assertEquals("Seafaring front-line warrior", metadata?.description)
        assertEquals(2, metadata?.tags?.size)
    }

    @Test
    fun testParseModTxt() {
        val txtContent = """
            id: qol_walk_speed
            title: Faster Walking Speed
            author: ModderX
            version: 3.0
            description: Walk faster in dungeons
            tags: QoL, Speed
        """.trimIndent()

        val metadata = ModMetadataReader.parseModTxt(txtContent)

        assertNotNull(metadata)
        assertEquals("qol_walk_speed", metadata?.id)
        assertEquals("Faster Walking Speed", metadata?.title)
        assertEquals("ModderX", metadata?.author)
        assertEquals("3.0", metadata?.version)
        assertEquals("Walk faster in dungeons", metadata?.description)
    }

    @Test
    fun testFallbackMetadata() {
        val metadata = ModMetadataReader.fallbackMetadata("219381928_leper_rework_mod")

        assertEquals("219381928_leper_rework_mod", metadata.id)
        assertEquals("Leper Rework Mod", metadata.title)
        assertEquals("Unknown Modder", metadata.author)
        assertEquals("1.0", metadata.version)
    }
}
