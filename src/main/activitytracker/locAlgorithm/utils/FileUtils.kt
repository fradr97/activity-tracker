package activitytracker.locAlgorithm.utils

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.opencsv.exceptions.CsvException
import org.apache.commons.io.filefilter.WildcardFileFilter
import java.io.*


class FileUtils {
    fun parseCSVFile(filepath: String?): List<Array<String>>? {
        var r: List<Array<String>>
        try {
            CSVReader(FileReader(filepath)).use { reader ->
                r = reader.readAll()
                return r
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } catch (e: CsvException) {
            e.printStackTrace()
            return null
        }
    }

    fun writeFile(filepath: String?, list: MutableList<Array<String>>, append: Boolean) {
        try {
            val myObj = File(filepath)
            if (myObj.createNewFile()) {
                println("File created.")
            }
            val fileWriter = FileWriter(filepath, append)
            val csvWriter = CSVWriter(fileWriter)
            csvWriter.writeAll(list)
            csvWriter.close()
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }

    fun addCSVHeader(filepath: String?, list: MutableList<Array<String>>, headers: MutableList<Array<String>>) {
        writeFile(filepath, headers, false)
        writeFile(filepath, list, true)
    }

    fun getFilesFromFolder(folderPath: String, ext: String): Array<File>? {
        var folder = folderPath
        if (folder == "") return null
        if (!folder.endsWith("\\")) folder += "\\"
        folder = folder.replace("\\", "\\\\").trim { it <= ' ' }
        val dir = File(folder)
        val fileFilter: FileFilter = WildcardFileFilter("*.$ext")
        val files = dir.listFiles(fileFilter) ?: return null
        return if (files.isNotEmpty()) {
            files
        } else null
    }
}