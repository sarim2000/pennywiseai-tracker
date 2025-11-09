package com.pennywiseai.parser.core.test

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class SMSData(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val readableDate: String,
    val description: String
)

/**
 * Central utility for loading SMS data from XML files for testing.
 * This reduces code duplication across different parser tests.
 */
object XMLTestUtils {

    /**
     * Load SMS data from an XML file in the test resources directory.
     */
    fun loadSMSDataFromXML(fileName: String): List<SMSData> {
        val smsList = mutableListOf<SMSData>()

        try {
            val xmlFile = File("src/test/resources/$fileName")
            if (!xmlFile.exists()) {
                throw RuntimeException("XML file not found: ${xmlFile.absolutePath}")
            }

            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc: Document = dBuilder.parse(xmlFile)
            doc.documentElement.normalize()

            // Parse SMS messages
            val smsNodes: NodeList = doc.getElementsByTagName("sms")
            for (i in 0 until smsNodes.length) {
                val smsNode = smsNodes.item(i)
                if (smsNode.nodeType == Node.ELEMENT_NODE) {
                    val smsElement = smsNode as Element
                    smsList.add(parseSMSElement(smsElement))
                }
            }

        } catch (e: Exception) {
            throw RuntimeException("Failed to load SMS data from XML file: $fileName", e)
        }

        return smsList
    }

    /**
     * Load SMS data from an XML file using classpath resource.
     */
    fun loadSMSDataFromResource(resourcePath: String): List<SMSData> {
        val smsList = mutableListOf<SMSData>()

        try {
            val inputStream: InputStream =
                object {}.javaClass.classLoader.getResourceAsStream(resourcePath)
                    ?: throw RuntimeException("XML resource not found: $resourcePath")

            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc: Document = dBuilder.parse(inputStream)
            doc.documentElement.normalize()

            // Parse SMS messages
            val smsNodes: NodeList = doc.getElementsByTagName("sms")
            for (i in 0 until smsNodes.length) {
                val smsNode = smsNodes.item(i)
                if (smsNode.nodeType == Node.ELEMENT_NODE) {
                    val smsElement = smsNode as Element
                    smsList.add(parseSMSElement(smsElement))
                }
            }

            // Parse MMS messages
            val mmsNodes: NodeList = doc.getElementsByTagName("mms")
            for (i in 0 until mmsNodes.length) {
                val mmsNode = mmsNodes.item(i)
                if (mmsNode.nodeType == Node.ELEMENT_NODE) {
                    val mmsElement = mmsNode as Element
                    smsList.add(parseMMSElement(mmsElement))
                }
            }

        } catch (e: Exception) {
            throw RuntimeException("Failed to load SMS data from XML resource: $resourcePath", e)
        }

        return smsList
    }

    private fun parseSMSElement(smsElement: Element): SMSData {
        val address = smsElement.getAttribute("address")
        val body = smsElement.getAttribute("body")
            .replace("&#10;", "\n")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            // Clean Google Messages proto headers
            .replace("proto:CjoKImNvbS5nb29nbGUuYW5kcm9pZC5hcHBzLm1lc3NhZ2luZy4SFCIAKh", "")
            .replace(Regex("SFCIAKh[^\\s]*"), "")
        val date = smsElement.getAttribute("date").toLongOrNull() ?: System.currentTimeMillis()
        val readableDate = smsElement.getAttribute("readable_date")

        return SMSData(
            sender = address,
            body = body,
            timestamp = date,
            readableDate = readableDate,
            description = readableDate // Use readable date as default description
        )
    }

    private fun parseMMSElement(mmsElement: Element): SMSData {
        val address = mmsElement.getAttribute("address")
        val date = mmsElement.getAttribute("date").toLongOrNull() ?: System.currentTimeMillis()
        val readableDate = mmsElement.getAttribute("readable_date")

        var body = ""
        val parts = mmsElement.getElementsByTagName("part")
        for (i in 0 until parts.length) {
            val part = parts.item(i) as Element
            if (part.getAttribute("ct") == "text/plain") {
                body = part.getAttribute("text")
                    .replace("&#10;", "\n")
                    .replace("&amp;", "&")
                break
            }
        }

        return SMSData(
            sender = address,
            body = body,
            timestamp = date,
            readableDate = readableDate,
            description = "MMS: $readableDate"
        )
    }

}