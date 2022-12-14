package coala.ai

import android.app.Application
import android.content.Context
import android.util.Log
import coala.ai.di.apiModule
import coala.ai.di.sharedPrefsModule
import org.koin.android.ext.android.startKoin
import java.io.*
import java.util.*

/**
 * The Coala class starts the Android koin.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
class Coala : Application() {

    init {
        instance = this
    }

    companion object {
        private var instance: Coala? = null

        fun applicationContext() : Context {
            return instance!!.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        startKoin(this@Coala,
                listOf(apiModule, sharedPrefsModule))



            //Variables for chat history. In this class because we need to check the subdirectory with chats files
            //every time the application starts.
            val folderName = "chats"
            val fileName="namesOfChats.txt"
            val context = applicationContext
            val folder = context.filesDir.absolutePath.toString() + File.separator + folderName
            val txtFileName = context.filesDir.absolutePath.toString()+File.separator + fileName
            val items: MutableList<String> = mutableListOf()
            val items2: MutableList<String> = mutableListOf()
            val current = Calendar.getInstance().time


            val chatFile = File(txtFileName)

            //Creates a txt file with names of all chats that are stored in chat folder
            val oFile = FileOutputStream(chatFile, true)
            if (!chatFile.exists()) {
                chatFile.createNewFile()
            }


            // delete chat after 30 days
            File(folder).listFiles()?.forEach {
                val lastModDate = Date(it.lastModified())
                val diff =  current.time-lastModDate.time
                //Needed to format time to days
                val secondsInMilli: Long = 1000
                val minutesInMilli = secondsInMilli * 60
                val hoursInMilli = minutesInMilli * 60
                val daysInMilli: Long = hoursInMilli * 24
                val elapsedDays: Long = diff / daysInMilli
                if(elapsedDays>30){
                    it.delete()
                }
                Log.i("date",elapsedDays.toString())
            }
            //Get chats names from chat folder
            File(folder).listFiles()?.forEach {
                val s = it.name
                items.add(it.name)
                Log.i("check", "$s name in folder")
            }

            //Get names from the txt file with chats names and compare it with chat names from chat folder
            //Needed to prevent duplicates
            File(txtFileName).forEachLine {
                var s = it
                val charsToRemove = "[]"
                charsToRemove.forEach { s = s.replace(it.toString(), "") }
                items2.add(s)
                Log.i("check", "$s name in txt file")
            }

            //Delete duplicates if found
            val distinct: MutableSet<String> = items.toMutableSet()
            val distinct2: MutableSet<String> = items2.toMutableSet()

            //Write chat names to txt file
            distinct2.forEach {
                if(it !in distinct){
                    val br = BufferedReader(FileReader(chatFile))
                    removeLine(br , chatFile, it)
                }
            }
            distinct.forEach{
                if(it !in distinct2) {
                    try {
                        val s = it + "\r\n"
                        oFile.write(s.toByteArray())
                        Log.i("check", "add $s to file")

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    oFile.flush()
                    oFile.close()
                    Log.i("check", "after output stream")
                }
            }
        }
    }


private fun removeLine(br: BufferedReader, f: File, Line: String) {
    val temp = File("temp.txt")
    val bw = BufferedWriter(FileWriter(temp))
    var currentLine: String
    while (br.readLine().also { currentLine = it } != null) {
        val trimmedLine = currentLine.trim { it <= ' ' }
        if (trimmedLine == Line) {
            currentLine = ""
        }
        bw.write(currentLine + System.getProperty("line.separator"))
    }
    bw.close()
    br.close()
    val delete = f.delete()
    val b = temp.renameTo(f)
}

