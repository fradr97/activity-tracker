package activitytracker.locAlgorithm.utils

import com.opencsv.CSVReader
import java.io.IOException
import com.opencsv.exceptions.CsvException
import java.io.FileWriter
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileReader

class FileParser {
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
}