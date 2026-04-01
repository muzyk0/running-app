package com.vladislav.runningapp.core.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    @Test
    fun baseAndRussianStringCatalogsStayAligned() {
        val baseStrings = parseStrings("src/main/res/values/strings.xml")
        val russianStrings = parseStrings("src/main/res/values-ru/strings.xml")

        assertEquals(
            "Base and Russian string catalogs must define the same resource keys.",
            baseStrings.keys,
            russianStrings.keys,
        )
    }

    @Test
    fun baseStringCatalogStaysEnglishFirst() {
        val baseStrings = parseStrings("src/main/res/values/strings.xml")
        val cyrillicKeys = baseStrings
            .filterValues { value -> CyrillicRegex.containsMatchIn(value) }
            .keys
            .sorted()

        assertTrue(
            "Base values/strings.xml must stay English-first. Found Cyrillic text in: $cyrillicKeys",
            cyrillicKeys.isEmpty(),
        )
    }

    private fun parseStrings(relativePath: String): Map<String, String> {
        val file = locateProjectFile(relativePath)
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)

        return buildMap {
            val nodes = document.getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? Element ?: continue
                put(node.getAttribute("name"), node.textContent.trim())
            }
        }
    }

    private fun locateProjectFile(relativePath: String): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir")) {
            "user.dir is not available for test resource lookup."
        }
        val startingDirectory = File(workingDirectory)
        return generateSequence(startingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::exists)
            ?: error("Could not locate $relativePath from ${startingDirectory.absolutePath}")
    }

    private companion object {
        val CyrillicRegex = Regex("\\p{IsCyrillic}")
    }
}
