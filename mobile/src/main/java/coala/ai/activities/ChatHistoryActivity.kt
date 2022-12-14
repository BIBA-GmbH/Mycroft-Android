package coala.ai.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import coala.ai.R
import coala.ai.models.UtteranceFrom
import coala.ai.adapters.MycroftAdapter
import coala.ai.helper.SharedPreferenceManager
import coala.ai.models.Utterance
import kotlinx.android.synthetic.main.activity_chat_history.*
import java.io.File

class ChatHistoryActivity : AppCompatActivity() {

    private var sharedPreferenceManager: SharedPreferenceManager? = null
    private lateinit var listForChatHistory: ListView
    private lateinit var items: MutableList<String>
    private lateinit var itemsAdapter: ArrayAdapter<String>
    private lateinit var mycroftAdapter: MycroftAdapter
    private val utterances = mutableListOf<Utterance>()
    private val itemsRemovedTxt = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferenceManager = SharedPreferenceManager.getInstance(this)

        setupActionBar()
        setContentView(R.layout.activity_chat_history)

        listForChatHistory = findViewById(R.id.chatHistory)
        //Get all chat names from a txt file
        items = ArrayList(File(applicationContext.filesDir, "namesOfChats.txt").readLines())

        //Delete .txt extension
        items.forEach {
            val s = it.replace("\\.\\w+$".toRegex(), "")
            itemsRemovedTxt.add(s)
        }

        //Show chat names as list
        itemsAdapter = ArrayAdapter(this, R.layout.item_chat_button, itemsRemovedTxt)
        listForChatHistory.adapter = itemsAdapter
        itemsAdapter.notifyDataSetChanged()

        //Check if there are chats to show, if yes delete the default message
        if (items.isNotEmpty())
            defaultMessageChats.visibility = View.GONE

        //When a specific chat is clicked
        listForChatHistory.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val itemValue = listForChatHistory.getItemAtPosition(position) as String
                val folderName = "chats"
                val context = applicationContext
                val folder = context.filesDir.absolutePath.toString() + File.separator + folderName


        //First delete .txt extension then compare item from list to name of chat
        File(folder).listFiles()?.forEach { it ->
            val sRem = it.name.replace("\\.\\w+$".toRegex(), "")
            if(itemValue == sRem){
                items = ArrayList(File(folder, "$itemValue.txt").readLines())
                items.forEach {
                    //Depending on the label assign adapter view
                    when {
                        it.contains("USER") -> {
                            val s = it.replace("USER","")
                            utterances.add(Utterance(s, UtteranceFrom.USER))
                        }
                        it.contains("MYCROFT") -> {
                            val s = it.replace("MYCROFT","")
                            utterances.add(Utterance(s, UtteranceFrom.MYCROFT))
                        }
                        it.contains("BUTTON") -> {
                            val s = it.replace("BUTTON","")
                            utterances.add(Utterance(s, UtteranceFrom.BUTTONS_CHAT))
                        }
                        it.contains("IMAGE") -> {
                            val s = it.replace("IMAGE","")
                            utterances.add(Utterance(s, UtteranceFrom.MYCROFT_IMG))
                        }
                    }
                }
                listForChatHistory.visibility = View.INVISIBLE
                recyclerViewChat.visibility=View.VISIBLE

                mycroftAdapter = MycroftAdapter(utterances, this, menuInflater)

                val llm = LinearLayoutManager(this)
                llm.stackFromEnd = true
                llm.orientation = LinearLayoutManager.VERTICAL
                with(recyclerViewChat) {
                    setHasFixedSize(true)
                    layoutManager = llm
                    adapter = mycroftAdapter
                }
            }
        }
            }
    }

    private fun setupActionBar() {
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}