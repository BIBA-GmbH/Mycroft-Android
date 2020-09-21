/*
 *  Copyright (c) 2017. Mycroft AI, Inc.
 *
 *  This file is part of Mycroft-Android a client for Mycroft Core.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package mycroft.ai

import android.Manifest.permission
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.speech.RecognizerIntent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.crashlytics.android.Crashlytics
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import mycroft.ai.Constants.MycroftMobileConstants.VERSION_CODE_PREFERENCE_KEY
import mycroft.ai.Constants.MycroftMobileConstants.VERSION_NAME_PREFERENCE_KEY
import mycroft.ai.adapters.MycroftAdapter
import mycroft.ai.receivers.NetworkChangeReceiver
import mycroft.ai.shared.utilities.GuiUtilities
import mycroft.ai.shared.wear.Constants.MycroftSharedConstants.MYCROFT_WEAR_REQUEST
import mycroft.ai.shared.wear.Constants.MycroftSharedConstants.MYCROFT_WEAR_REQUEST_KEY_NAME
import mycroft.ai.shared.wear.Constants.MycroftSharedConstants.MYCROFT_WEAR_REQUEST_MESSAGE
import mycroft.ai.utils.NetworkUtil
import org.java_websocket.client.WebSocketClient
import org.java_websocket.exceptions.WebsocketNotConnectedException
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException
import java.util.*

class MainActivity : AppCompatActivity() {
    private val logTag = "Mycroft"
    private val utterances = mutableListOf<Utterance>()
    private val reqCodeSpeechInput = 100
    private val requestScan = 101
    private var maximumRetries = 1
    private var currentItemPosition = -1
    private val cameraPermissionRequestCode = 1

    private var isNetworkChangeReceiverRegistered = false
    private var isWearBroadcastRevieverRegistered = false
    private var launchedFromWidget = false
    private var autoPromptForSpeech = false

    private lateinit var ttsManager: TTSManager
    private lateinit var mycroftAdapter: MycroftAdapter
    private lateinit var wsct: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    private lateinit var wearBroadcastReceiver: BroadcastReceiver

    var webSocketClient: WebSocketClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Fabric.with(this, Crashlytics())
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar as Toolbar?)


        loadPreferences()

        ttsManager = TTSManager(this)
        mycroftAdapter = MycroftAdapter(utterances, applicationContext, menuInflater)
        mycroftAdapter.setOnLongItemClickListener(object: MycroftAdapter.OnLongItemClickListener {
            override fun itemLongClicked(v: View, position: Int) {
                currentItemPosition = position
                v.showContextMenu()
            }
        })

        kbMicSwitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPref.edit()
            editor.putBoolean("kbMicSwitch", isChecked)
            editor.apply()

            if (isChecked) {
                // Switch to mic
                micButton.visibility = View.VISIBLE
                utteranceInput.visibility = View.INVISIBLE
                sendUtterance.visibility = View.INVISIBLE
                scanButton.visibility = View.VISIBLE
            } else {
                // Switch to keyboard
                micButton.visibility = View.INVISIBLE
                utteranceInput.visibility = View.VISIBLE
                sendUtterance.visibility = View.VISIBLE
                scanButton.visibility = View.INVISIBLE
            }
        }

        utteranceInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if(actionId == EditorInfo.IME_ACTION_DONE){
                sendUtterance()
                true
            } else {
                false
            }
        })
        micButton.setOnClickListener { promptSpeechInput() }
        sendUtterance.setOnClickListener { sendUtterance() }
        scanButton.setOnClickListener { scanBarcode() }

        registerForContextMenu(cardList)

        //attach a listener to check for changes in state
        voxswitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPref.edit()
            editor.putBoolean("appReaderSwitch", isChecked)
            editor.apply()

            // stop tts from speaking if app reader disabled
            if (!isChecked) ttsManager.initQueue("")
        }

        val llm = LinearLayoutManager(this)
        llm.stackFromEnd = true
        llm.orientation = LinearLayoutManager.VERTICAL
        with (cardList) {
            setHasFixedSize(true)
            layoutManager = llm
            adapter = mycroftAdapter
        }

        registerReceivers()

        // start the discovery activity (testing only)
        // startActivity(new Intent(this, DiscoveryActivity.class));
    }

    /**
     * This method starts scanner after camera permission check
     * If permission is already granted, scanner starts
     * If permission is not granted yet, asks for permission
     */
    private fun scanBarcode() {
        // Check camera permission to be able to use scan feature
        val permissionCheck = ContextCompat.checkSelfPermission(this, permission.CAMERA)
        // If camera permission is granted continue to open camera
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            // Open camera to scan barcode or QR code
            openCameraToScan()
        } else {
            // If permission is not granted yet, ask for the permission.
            ActivityCompat.requestPermissions(this,
                    arrayOf(permission.CAMERA),
                    cameraPermissionRequestCode)

        }
    }

    /**
     * This method handles permission request result given by user
     * If permission is given, continues to scan operation by opening camera
     * If permission is not given, explains why the permission is necessary
     */
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            cameraPermissionRequestCode -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission is granted. Continue the action or workflow
                    // in the app.
                    openCameraToScan()
                } else {
                    // Explain to the user that the feature is unavailable because
                    // the features requires a permission that the user has denied.
                    showToast("Scan feature is only available with camera. So camera permission should be granted to enable scan feature")
                }
                return
            }

        }
    }

    /**
     * This method opens the camera to start scan operation
     */
    private fun openCameraToScan() {
        // Create a new IntentIntegrator instance
        val scanIntentIntegrator = IntentIntegrator(this@MainActivity)
        // Set barcode formats to improve performance
        scanIntentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.CODE_128, IntentIntegrator.QR_CODE)
        // Set captureActivity property to new class to use portrait mode orientation
        scanIntentIntegrator.captureActivity = CaptureActivityPortrait::class.java
        // Create the scan intent to start scan
        val scanIntent = scanIntentIntegrator.createScanIntent()
        startActivityForResult(scanIntent, requestScan)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_setup, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        var consumed = false
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                consumed = true
            }
            R.id.action_home_mycroft_ai -> {
                val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.mycroft_website_url)))
                startActivity(intent)
            }
        }

        return consumed && super.onOptionsItemSelected(item)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        super.onContextItemSelected(item)
        if (item.itemId == R.id.user_resend) {
            // Resend user utterance
            sendMessage(utterances[currentItemPosition].utterance)
        } else if (item.itemId == R.id.user_copy || item.itemId == R.id.mycroft_copy) {
            // Copy utterance to clipboard
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val data = ClipData.newPlainText("text", utterances[currentItemPosition].utterance)
            clipboardManager.setPrimaryClip(data)
            showToast("Copied to clipboard")
        } else if (item.itemId == R.id.mycroft_share) {
            // Share utterance
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, utterances[currentItemPosition].utterance)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.action_share)))
        } else {
            return super.onContextItemSelected(item)
        }

        return true
    }

    fun sendUtterance() {
        val utterance = utteranceInput.text.toString()
        if (utterance != "") {
            val barcode = sharedPref.getString("barcode", null)
            if (barcode != null) {
                sendMessage(utterance + " " + barcode)
                // Clear the barcode memory not to include the same barcode in the next utterances
                sharedPref.edit().remove("barcode").apply()
            }else{
                sendMessage(utterance)
            }
            utteranceInput.text.clear()
        }
    }

    fun connectWebSocket() {
        val uri = deriveURI()

        if (uri != null) {
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(serverHandshake: ServerHandshake) {
                    Log.i("Websocket", "Opened")
                }

                override fun onMessage(s: String) {
                    // Log.i(TAG, s);
                    runOnUiThread(MessageParser(s, object : SafeCallback<Utterance> {
                        override fun call(param: Utterance) {
                            addData(param)
                        }
                    }))
                }

                override fun onClose(i: Int, s: String, b: Boolean) {
                    Log.i("Websocket", "Closed $s")

                }

                override fun onError(e: Exception) {
                    Log.i("Websocket", "Error " + e.message)
                }
            }
            webSocketClient!!.connect()
        }
    }

    private fun addData(mycroftUtterance: Utterance) {
        utterances.add(mycroftUtterance)
        defaultMessageTextView.visibility = View.GONE
        mycroftAdapter.notifyItemInserted(utterances.size - 1)

        Log.d(logTag, "Utterance: [$mycroftUtterance]")

        if (voxswitch.isChecked && !mycroftUtterance.silent && (mycroftUtterance.from.id == 1) ) {
            ttsManager.addQueue(mycroftUtterance.utterance)
        }
        cardList.smoothScrollToPosition(mycroftAdapter.itemCount - 1)
    }

    private fun registerReceivers() {
        registerNetworkReceiver()
        registerWearBroadcastReceiver()
    }

    private fun registerNetworkReceiver() {
        if (!isNetworkChangeReceiverRegistered) {
            // set up the dynamic broadcast receiver for maintaining the socket
            networkChangeReceiver = NetworkChangeReceiver()
            networkChangeReceiver.setMainActivityHandler(this)

            // set up the intent filters
            val connChange = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
            val wifiChange = IntentFilter("android.net.wifi.WIFI_STATE_CHANGED")
            registerReceiver(networkChangeReceiver, connChange)
            registerReceiver(networkChangeReceiver, wifiChange)

            isNetworkChangeReceiverRegistered = true
        }
    }

    private fun registerWearBroadcastReceiver() {
        if (!isWearBroadcastRevieverRegistered) {
            wearBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val message = intent.getStringExtra(MYCROFT_WEAR_REQUEST_MESSAGE)
                    // send to mycroft
                    if (message != null) {
                        Log.d(logTag, "Wear message received: [$message] sending to Mycroft")
                        sendMessage(message)
                    }
                }
            }

            LocalBroadcastManager.getInstance(this).registerReceiver(wearBroadcastReceiver, IntentFilter(MYCROFT_WEAR_REQUEST))
            isWearBroadcastRevieverRegistered = true
        }
    }

    private fun unregisterReceivers() {
        unregisterBroadcastReceiver(networkChangeReceiver)
        unregisterBroadcastReceiver(wearBroadcastReceiver)

        isNetworkChangeReceiverRegistered = false
        isWearBroadcastRevieverRegistered = false
    }

    private fun unregisterBroadcastReceiver(broadcastReceiver: BroadcastReceiver) {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    /**
     * This method will use a client-specific token
     * [.wsct] as path for communication
     * with a Mycroft instance behind a reverse proxy.
     *
     *
     * If [.wsct] cannot be used as a hostname
     * in a [URI] (e.g. because it's null), then
     * this method will return null.
     *
     *
     * @return a valid uri, or null
     */
    private fun deriveURI(): URI? {
        return if (wsct.isNotEmpty()) {
            try {
                URI("ws://diamond-dev.ikap.biba.uni-bremen.de/mycroft/$wsct")
            } catch (e: URISyntaxException) {
                Log.e(logTag, "Unable to connect to websocket with this token", e)
                null
            }
        } else {
            null
        }
    }

    fun sendMessage(msg: String) {
        // let's keep it simple eh?
        //final String json = "{\"message_type\":\"recognizer_loop:utterance\", \"context\": null, \"metadata\": {\"utterances\": [\"" + msg + "\"]}}";
        val json = "{\"data\": {\"utterances\": [\"$msg\"]}, \"type\": \"recognizer_loop:utterance\", \"context\": null}"

        try {
            if (webSocketClient == null || webSocketClient!!.connection.isClosed) {
                // try and reconnect
                if (NetworkUtil.getConnectivityStatus(this) == NetworkUtil.NETWORK_STATUS_WIFI) { //TODO: add config to specify wifi only.
                    connectWebSocket()
                }
            }

            val handler = Handler()
            handler.postDelayed({
                // Actions to do after 1 seconds
                try {
                    webSocketClient!!.send(json)
                    addData(Utterance(msg, UtteranceFrom.USER))
                } catch (exception: WebsocketNotConnectedException) {
                    showToast(resources.getString(R.string.websocket_closed))
                }
            }, 1000)

        } catch (exception: WebsocketNotConnectedException) {
            showToast(resources.getString(R.string.websocket_closed))
        }

    }

    /**
     * Showing google speech input dialog
     */
    private fun promptSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt))
        try {
            startActivityForResult(intent, reqCodeSpeechInput)
        } catch (a: ActivityNotFoundException) {
            showToast(getString(R.string.speech_not_supported))
        }

    }

    /**
     * Receiving speech input
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            reqCodeSpeechInput -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

                    val barcode = sharedPref.getString("barcode", null)
                    if (barcode != null) {
                        sendMessage(result[0] + " " + barcode)
                    }
                    sendMessage(result[0])
                }

            }
            requestScan -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Parse the result of the scan intent to retrieve scan info
                    val result: IntentResult = IntentIntegrator.parseActivityResult(resultCode, data)
                    // If scan content is not null, store barcode to attach in the utterance
                    if (result.contents != null) {
                        // Get the result content
                        val rawValue = result.contents
                        // Save the scan info in shared preference
                        sharedPref.edit().putString("barcode", rawValue).apply()
                        showToast("Barcode is saved successfully !")

                    }else{
                        // If scan content is null, show a message
                       showToast("Nothing is scanned !")

                    }

                }
            }

        }
    }


    public override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutDown()
        isNetworkChangeReceiverRegistered = false
        isWearBroadcastRevieverRegistered = false
    }

    public override fun onStart() {
        super.onStart()
        recordVersionInfo()
        registerReceivers()
        checkIfLaunchedFromWidget(intent)
    }

    public override fun onStop() {
        super.onStop()

        unregisterReceivers()

        if (launchedFromWidget) {
            autoPromptForSpeech = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun loadPreferences() {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        // get mycroft-core ip address
        wsct = sharedPref.getString("ip", "")!!
        if (wsct!!.isEmpty()) {
            // eep, show the settings intent!
            startActivity(Intent(this, SettingsActivity::class.java))
        } else if (webSocketClient == null || webSocketClient!!.connection.isClosed) {
            connectWebSocket()
        }

        kbMicSwitch.isChecked = sharedPref.getBoolean("kbMicSwitch", true)
        if (kbMicSwitch.isChecked) {
            // Switch to mic
            micButton.visibility = View.VISIBLE
            utteranceInput.visibility = View.INVISIBLE
            sendUtterance.visibility = View.INVISIBLE
            // Make scan button visible when mic is activated
            scanButton.visibility = View.VISIBLE
        } else {
            // Switch to keyboard
            micButton.visibility = View.INVISIBLE
            utteranceInput.visibility = View.VISIBLE
            sendUtterance.visibility = View.VISIBLE
            // Make scan button invisible when keyboard is activated
            scanButton.visibility = View.INVISIBLE
        }

        // set app reader setting
        voxswitch.isChecked = sharedPref.getBoolean("appReaderSwitch", true)

        maximumRetries = Integer.parseInt(sharedPref.getString("maximumRetries", "1")!!)
    }

    private fun checkIfLaunchedFromWidget(intent: Intent) {
        val extras = getIntent().extras
        if (extras != null) {
            if (extras.containsKey("launchedFromWidget")) {
                launchedFromWidget = extras.getBoolean("launchedFromWidget")
                autoPromptForSpeech = extras.getBoolean("autoPromptForSpeech")
            }

            if (extras.containsKey(MYCROFT_WEAR_REQUEST_KEY_NAME)) {
                Log.d(logTag, "checkIfLaunchedFromWidget - extras contain key:$MYCROFT_WEAR_REQUEST_KEY_NAME")
                extras.getString(MYCROFT_WEAR_REQUEST_KEY_NAME)?.let { sendMessage(it) }
                getIntent().removeExtra(MYCROFT_WEAR_REQUEST_KEY_NAME)
            }
        }

        if (autoPromptForSpeech) {
            promptSpeechInput()
            intent.putExtra("autoPromptForSpeech", false)
        }
    }

    private fun recordVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val editor = sharedPref.edit()
            editor.putInt(VERSION_CODE_PREFERENCE_KEY, packageInfo.versionCode)
            editor.putString(VERSION_NAME_PREFERENCE_KEY, packageInfo.versionName)
            editor.apply()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(logTag, "Couldn't find package info", e)
        }
    }

    private fun showToast(message: String) {
        GuiUtilities.showToast(applicationContext, message)
    }
}
