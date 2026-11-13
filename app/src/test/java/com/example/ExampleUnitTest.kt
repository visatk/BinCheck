package com.example

import com.example.domain.CardScheme
import com.example.domain.CardValidatorEngine
import com.example.domain.FileTextExtractor
import com.example.domain.ResultBucket
import com.example.domain.SupportedFileType
import com.example.domain.ValidationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun wildcardPanSolver_alwaysProducesValidLuhnChecksum() {
        val pattern = "453225005536xxx3"
        repeat(25) {
            val resolved = CardValidatorEngine.resolveWildcardPan(pattern)
            assertEquals(16, resolved.length)
            assertTrue(resolved.startsWith("453225005536"))
            assertTrue(resolved.endsWith("3"))
            assertTrue("Expected valid Luhn for $resolved", CardValidatorEngine.verifyLuhn(resolved))
            assertEquals(CardScheme.VISA, CardValidatorEngine.detectScheme(resolved))
        }
    }

    @Test
    fun expandPatternBatch_generatesRequestedUniqueValidLines() {
        val batch = CardValidatorEngine.expandPatternBatch("453225005536xxx3|08|2028|113", 10)
        assertEquals(10, batch.size)
        batch.forEach { line ->
            val parts = line.split('|')
            assertEquals(4, parts.size)
            assertTrue(CardValidatorEngine.verifyLuhn(parts[0]))
            assertEquals("08", parts[1])
            assertEquals("2028", parts[2])
            assertEquals("113", parts[3])
        }
    }

    @Test
    fun validateLine_classifiesLiveDieAndUnknownCorrectly() = runTest {
        val liveRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536xxx3|08|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = true
        )
        assertEquals(ResultBucket.LIVE, liveRecord.bucket)
        assertEquals(CardScheme.VISA, liveRecord.scheme)

        val validPan = liveRecord.pan
        val brokenLastDigit = if (validPan.last() == '3') '4' else '3'
        val invalidPan = validPan.dropLast(1) + brokenLastDigit
        assertFalse(CardValidatorEngine.verifyLuhn(invalidPan))

        val dieRecord = CardValidatorEngine.validateLine(
            rawLine = "$invalidPan|08|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = false
        )
        assertEquals(ResultBucket.DIE, dieRecord.bucket)

        val unknownRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = false
        )
        assertEquals(ResultBucket.UNKNOWN, unknownRecord.bucket)
    }

    @Test
    fun fileTextExtractor_detectsSupportedTypesAndStripsHtml() {
        assertEquals(SupportedFileType.TXT, FileTextExtractor.detectFileType("batch.txt", "text/plain"))
        assertEquals(SupportedFileType.HTML, FileTextExtractor.detectFileType("export.html", "text/html"))
        assertEquals(SupportedFileType.HTML, FileTextExtractor.detectFileType("report.HTM", null))
        assertNull(FileTextExtractor.detectFileType("archive.zip", "application/zip"))

        val htmlSample = """
            <html>
              <head><title>Export 4532250055360000|01|2029|111</title><!-- 4532250055369999|02|2029|999 --><style>.hidden{display:none}</style><script>console.log('skip');</script></head>
              <body>
                <h1>Exported Batch</h1>
                <!-- ignored comment 5412750012340000|03|2029|000 -->
                <div>453225005536xxx3&#124;08&#124;2028&#124;113</div>
                <p>Entry: 5412750012345678&#x7c;11&#x7c;2029&#x7c;321 &amp; verified</p>
              </body>
            </html>
        """.trimIndent()

        val extractedLines = FileTextExtractor.extractLines(htmlSample, SupportedFileType.HTML)
        assertTrue(extractedLines.any { it.contains("453225005536xxx3|08|2028|113") })
        assertFalse(extractedLines.any { it.contains("console.log") })
        assertFalse(extractedLines.any { it.contains("4532250055360000") })
        assertFalse(extractedLines.any { it.contains("5412750012340000") })

        val delimited = FileTextExtractor.extractDelimitedLines(extractedLines)
        assertEquals(2, delimited.size)
        assertEquals("453225005536xxx3|08|2028|113", delimited[0])
        assertEquals("5412750012345678|11|2029|321", delimited[1])
    }

    @Test
    fun expandPatternBatch_defaultsToSchemeCvvLengthAndPadsMonth() = runTest {
        val amexBatch = CardValidatorEngine.expandPatternBatch("378282246310xxx", 5)
        assertEquals(5, amexBatch.size)
        amexBatch.forEach { line ->
            val parts = line.split('|')
            assertEquals(4, parts.size)
            assertTrue(CardValidatorEngine.verifyLuhn(parts[0]))
            assertEquals(CardScheme.AMEX, CardValidatorEngine.detectScheme(parts[0]))
            assertEquals(4, parts[3].length)
        }

        val paddedRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536xxx3|8|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = true
        )
        assertEquals("08", paddedRecord.month)
        assertTrue(paddedRecord.normalizedLine.contains("|08|2028|113"))
    }
}
