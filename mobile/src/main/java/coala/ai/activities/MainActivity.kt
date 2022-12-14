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

package coala.ai.activities

//keycloak integration

import ai.picovoice.porcupine.*
import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import coala.ai.R
import coala.ai.storage.Constants.MycroftMobileConstants.VERSION_CODE_PREFERENCE_KEY
import coala.ai.storage.Constants.MycroftMobileConstants.VERSION_NAME_PREFERENCE_KEY
import coala.ai.adapters.MycroftAdapter
import coala.ai.configuration.Config
import coala.ai.di.IKeycloakRest
import coala.ai.helper.Helper
import coala.ai.helper.SharedPreferenceManager
import coala.ai.helper.TTSManager
import coala.ai.interfaces.SafeCallback
import coala.ai.interfaces.TextToSpeechCallback
import coala.ai.models.Utterance
import coala.ai.models.UtteranceFrom
import coala.ai.parsers.MessageParser
import coala.ai.receivers.NetworkChangeReceiver
import coala.ai.secure.Crypto
import coala.ai.shared.utilities.GuiUtilities
import coala.ai.storage.IOAuth2AccessTokenStorage
import coala.ai.utils.NetworkUtil
import com.google.android.material.snackbar.Snackbar
import com.nightonke.boommenu.BoomButtons.ButtonPlaceEnum
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton
import com.nightonke.boommenu.BoomMenuButton
import com.nightonke.boommenu.ButtonEnum
import com.nightonke.boommenu.Piece.PiecePlaceEnum
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.exceptions.WebsocketNotConnectedException
import org.java_websocket.handshake.ServerHandshake
import org.koin.android.ext.android.inject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule


//, OnDataTransferProgressListener, OnRemoteOperationListener
class MainActivity : AppCompatActivity() {
    private val logTag = "Mycroft"
    private val utterances = mutableListOf<Utterance>()
    private val reqCodeSpeechInput = 100
    //private val requestScan = 101
    private val requestImageCapture = 102
    private var maximumRetries = 1
    private var currentItemPosition = -1
    //private val cameraPermissionRequestCode = 1
    private var isRecognizerActivated = false
    private var isCameraOpen = false
    private var isContinues = false
    private var isListening = false

    private var isNetworkChangeReceiverRegistered = false
   // private var isWearBroadcastRevieverRegistered = false
    private var launchedFromWidget = false
    private var autoPromptForSpeech = false

    private lateinit var ttsManager: TTSManager
    private lateinit var mycroftAdapter: MycroftAdapter


    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    private lateinit var currentPhotoPath: String

    //private val mHandler = Handler()
    private lateinit var cloudUsername: String
    private lateinit var cloudPassword: String
    //private lateinit var cloudService: String
    private lateinit var cloudHostName: String


    private lateinit var speechRecognizerIntent: Intent

    var webSocketClient: WebSocketClient? = null
   // private var mFirebaseAnalytics: FirebaseAnalytics? = null

    //keycloak integration
    private val api by inject<IKeycloakRest>()
    private val storage by inject<IOAuth2AccessTokenStorage>()
    private val AUTHORIZATION_REQUEST_CODE = 1
    private var speechRecognizer: SpeechRecognizer? = null
    private val audioRequestCode = 12
    private val wakeWordRequestCode = 13
    private lateinit var progressBar :LinearLayout


    // wakeword
    private var porcupineManager: PorcupineManager? = null
    private val ACCESS_KEY = ""
    private var isWakeWordActive = false
    private lateinit var intentLauncher : ActivityResultLauncher<Intent>

    // hiveMind
    private var hiveMindName :String? = ""
    private var hiveMindAccessKey :String? = ""
    private var hiveMindPort :String? = ""
    private var hiveMindIp :String? = ""
    private var hiveMindPath :String? = ""
    private var hiveMindCryptoKey :String? = ""

    //private lateinit var sessionID:String
    private var sharedPreferenceManager: SharedPreferenceManager? = null



    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* Context:
         * SessionID only exists after login.
         *
         */
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        progressBar = findViewById<View>(R.id.progress_view) as LinearLayout
        sharedPreferenceManager = SharedPreferenceManager.getInstance(this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        ttsManager = TTSManager(this)

        //conversation menu as boom menu declaration
        val conversation_menu = findViewById<View>(R.id.conversation_menu) as BoomMenuButton

        //type of buttons in boom menu
        conversation_menu.buttonEnum = ButtonEnum.TextOutsideCircle
        //number of items in boom menu
        conversation_menu.piecePlaceEnum = PiecePlaceEnum.DOT_3_1
        //placements of buttons
        conversation_menu.buttonPlaceEnum = ButtonPlaceEnum.SC_3_3
        //conversation_menu.setButtonPlaceAlignmentEnum(ButtonPlaceAlignmentEnum.BL);

        conversation_menu.addBuilder(
            TextOutsideCircleButton.Builder().normalImageRes(R.drawable.ic_conversation)
            .normalText("Conversation")
            .shadowEffect(false)
            .normalColor(Color.LTGRAY)
                .highlightedColor(Color.rgb(255, 228, 105))
                .imagePadding( Rect(15, 15, 15, 15))
            .textSize(12)
                .listener {
                    stopService()
                    ttsManager.stopSpeaking()
                    if (defaultMessageTextView.isShown)
                        defaultMessageTextView.visibility = View.GONE
                    if (!hasRecordPermission()) checkPermission(audioRequestCode) else {
                        isContinues = true
                        speechRecognizer!!.startListening(speechRecognizerIntent)
                    }
                }
        )


        conversation_menu.addBuilder(
            TextOutsideCircleButton.Builder().normalImageRes(R.drawable.ic_mic_button)
            .normalText("Walkie-Talkie")
            .shadowEffect(false)
            .normalColor(Color.LTGRAY)
                .highlightedColor(Color.rgb(255, 228, 105))
                .imagePadding( Rect(15, 15, 15, 15))
            .textSize(12)
                .listener {
                    promptSpeechInput()
                })

        conversation_menu.addBuilder(
            TextOutsideCircleButton.Builder().normalImageRes(R.drawable.ic_hey)
            .normalText("Wakeup Word")
            .shadowEffect(false)
            //.normalColor(Color.rgb(255,237,139))
              .normalColor(Color.LTGRAY)
                .highlightedColor(Color.rgb(255, 228, 105))
                .imagePadding( Rect(15, 15, 15, 15))
            .textSize(12)
                    .listener {
                        if(isWakeWordActive){
                            stopService()
                            isWakeWordActive = false
                        }else{

                            if (!hasRecordPermission()) checkPermission(wakeWordRequestCode) else{
                                playback() // call funtion for listening wakeword
                            }
                        }
                    })


        uploadButton.visibility = View.VISIBLE

        //check for refresh token expiration, if expired transfer user to login activity
        if (Helper.isRefreshTokenExpired(storage.getStoredAccessToken())){
            stopService()
            startActivityForResult(Intent(this, LoginActivity::class.java), AUTHORIZATION_REQUEST_CODE)

        }

        loadPreferences()

        mycroftAdapter = MycroftAdapter(utterances, this, menuInflater)
        mycroftAdapter.setOnLongItemClickListener(object : MycroftAdapter.OnLongItemClickListener {
            override fun itemLongClicked(v: View, position: Int) {
                currentItemPosition = position
                v.showContextMenu()
            }
        })


        kbMicSwitch.setOnCheckedChangeListener { _, isChecked ->

            sharedPreferenceManager?.putValue("kbMicSwitch", isChecked)
            ttsManager.stopSpeaking()
            if (isChecked) {
                // Switch to mic
                scanButton.visibility = View.VISIBLE
                uploadButton.visibility = View.VISIBLE
                utteranceInput.visibility = View.INVISIBLE
                sendUtterance.visibility = View.INVISIBLE

            } else {
                // Switch to keyboard
                utteranceInput.visibility = View.VISIBLE
                sendUtterance.visibility = View.VISIBLE
            }
        }

        utteranceInput.setOnEditorActionListener { _, actionId, _ ->
            if(actionId == EditorInfo.IME_ACTION_DONE){
                sendUtterance()
                true
            } else {
                false
            }
        }

        // get results of QR/Bar code from scanner activity
        intentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            isCameraOpen = false
            if (result.resultCode == Activity.RESULT_OK) {
                val value = result.data?.getStringExtra("scannedValue")
                if (value != null) {
                    sendMessage(value)
                }

            }
        }
        sendUtterance.setOnClickListener { sendUtterance() }
        scanButton.setOnClickListener { scanBarcode() }


        registerForContextMenu(cardList)

        //attach a listener to check for changes in state
        voxswitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferenceManager?.putValue("appReaderSwitch", isChecked)
            // stop tts from speaking if app reader disabled
            if (!isChecked) ttsManager.initQueue("")
        }
        //attach a listener to check for changes in state for user utterance
        voxswitch2.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferenceManager?.putValue("appUserReaderSwitch", isChecked)
            // stop tts from speaking if app user reader disabled
            if (!isChecked) ttsManager.initQueue("")
        }

        val llm = LinearLayoutManager(this)
        llm.stackFromEnd = true
        llm.orientation = LinearLayoutManager.VERTICAL
        with(cardList) {
            setHasFixedSize(true)
            layoutManager = llm
            adapter = mycroftAdapter
        }


        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())


        try {
            porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(ACCESS_KEY)
                    .setKeywordPath("custom_keyword_file.ppn")
                    .setSensitivity(0.7f)
                    .build(applicationContext, porcupineManagerCallback)
        } catch (e: PorcupineInvalidArgumentException) {
            showToast(String.format("%s\nEnsure your accessKey '%s' is a valid access key.", e.message, ACCESS_KEY))
        } catch (e: PorcupineActivationException) {
            showToast("AccessKey activation error")
        } catch (e: PorcupineActivationLimitException) {
            showToast("AccessKey reached its device limit")
        } catch (e: PorcupineActivationRefusedException) {
            showToast("AccessKey refused")
        } catch (e: PorcupineActivationThrottledException) {
            showToast("AccessKey has been throttled")
        } catch (e: PorcupineException) {
            showToast("Failed to initialize Porcupine " + e.message)
        }


        speechRecognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(bundle: Bundle) {
                isListening = true
                progressBar.visibility = View.VISIBLE
                isRecognizerActivated = true
                Log.i("", "start")
            }

            override fun onBeginningOfSpeech() {
                Log.i("", "start")
            }

            override fun onRmsChanged(v: Float) {
                Log.i("", "start")
            }

            override fun onBufferReceived(bytes: ByteArray) {
                Log.i("", "start")
            }

            override fun onEndOfSpeech() {
                isListening = false
                if (progressBar.isShown)
                    progressBar.visibility = View.INVISIBLE
                Log.i("", "start")
            }

            override fun onError(error: Int) {
                isListening = false
                if (progressBar.isShown)
                    progressBar.visibility = View.INVISIBLE
                Log.i("", "start")
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        if (isWakeWordActive) {
                            porcupineManager!!.start()
                        }
                        return
                    }
                    SpeechRecognizer.ERROR_CLIENT -> return

                }

            }

            override fun onResults(bundle: Bundle) {

                isRecognizerActivated = false
                val data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (data != null)
                    sendMessage(data[0])
            }

            override fun onPartialResults(bundle: Bundle) {}
            override fun onEvent(i: Int, bundle: Bundle) {}
        })


        registerReceivers()

    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("dd.MM.yyyy 'at' HH:mm:ss").format(Date())
        // Get the storage directory to store (this directory is only visible to this app)
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
                "Assistant-${timeStamp}*", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }


    @SuppressLint("SimpleDateFormat")
//    private fun uploadToCloud(path: String, fileName: String) {
//
//        // get next cloud username and password from settings
//        cloudUsername = sharedPref.getString("cloud_username", "")!!
//        cloudPassword = sharedPref.getString("cloud_password", "")!!
//
//        cloudService = sharedPref.getString("cloud_preference", "")!!
//
//        // Cloud host name for Nextcloud
//        cloudHostName = sharedPref.getString("cloud_host_name", "")!!
//
//        if (cloudUsername == "") {
//            showToast("Please give a valid username in settings !")
//        } else {
//            if (cloudService == "Nextcloud") {
//
//                // Parse URI to the base URL of the Nextcloud server
//                val serverUri = Uri.parse(cloudHostName)
//
//                // Create client object to perform remote operations
//                val mClient = OwnCloudClientFactory.createOwnCloudClient(serverUri, this, true)
//                // Set credentials for authorization
//                mClient.credentials = OwnCloudCredentialsFactory.newBasicCredentials(cloudUsername, cloudPassword)
//                // Set file properties to upload
//                val uploadOperation = UploadFileRemoteOperation(path, "assistant/$fileName.jpg", "image/jpg", SimpleDateFormat("dd.MM.yyyy").format(Date()))
//                // Upload the file
//                uploadOperation.execute(mClient, this, mHandler)
//
//            }
//
//        }
//    }


    /**
     * This method starts scanner after camera permission check
     * If permission is already granted, scanner starts
     * If permission is not granted yet, asks for permission
     */
    private fun scanBarcode() {

        // Check camera permission to be able to use scan feature
        ttsManager.stopSpeaking()
        progressBar.visibility = View.INVISIBLE
        isCameraOpen = true
        intentLauncher.launch(Intent(this, ScannerActivity::class.java))
    }

    /**
     * This method handles permission request result given by user
     * If permission is given, continues to scan operation by opening camera
     * If permission is not given, explains why the permission is necessary
     */
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            audioRequestCode -> {
                if ((grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    showToast("Permission Granted")
                    isContinues = true
                    speechRecognizer!!.startListening(speechRecognizerIntent)

                }
            }wakeWordRequestCode -> {
                if ((grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    showToast("Permission Granted")
                   playback()

                }
            }
        }
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
        ttsManager.stopSpeaking()
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, AppSettingsActivity::class.java))
                consumed = true
            }

            R.id.action_chat_history -> {
                startActivity(Intent(this, ChatHistoryActivity::class.java))
                consumed = true
            }

            R.id.action_home_mycroft_ai -> {
                val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.mycroft_website_url)))
                startActivity(intent)
            }
            // logout menu presentation
            R.id.logout -> handleLogout()
        }

        return consumed && super.onOptionsItemSelected(item)
    }

    @SuppressLint("CheckResult")
    private fun handleLogout() {

        utterances.clear()
        mycroftAdapter.notifyDataSetChanged()
        stopService()

        val refreshToken = storage.getStoredAccessToken()!!.refreshToken!!

        // Reset the conversation session information.
        sharedPreferenceManager?.clearKey("session_id")
        sharedPreferenceManager?.clearKey("username")
        Log.d("session", "Session cleared on logout")

        api.logout(Config.clientId, refreshToken)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Toast.makeText(this, "logged out", Toast.LENGTH_LONG).show()
                storage.removeAccessToken()
                this@MainActivity.startActivity(
                    Intent(
                        this@MainActivity,
                        LoginActivity::class.java
                    )
                )
            }, {
                it.printStackTrace()
                Toast.makeText(this@MainActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            })

    }

    private fun getSessionID(): String {
        /*
         * Updates the sessionID and sessionIDView using keycloakToken
         * Returns the sessionID
         */

        // SessionID presentation for users.
        val sessionIDView = findViewById<View>(R.id.sessionID_view) as TextView

        val username = sharedPreferenceManager?.getString("username")
        val keycloakToken = storage.getStoredAccessToken()?.accessToken.toString()
        // Use name and hash (instead of token directly)
        val sessionID: String = "$username - ${keycloakToken.takeLast(20)}"

        sessionIDView.text = "Session: $sessionID"

        return sessionID
    }


    override fun onContextItemSelected(item: MenuItem): Boolean {
        super.onContextItemSelected(item)
        when (item.itemId) {
            R.id.user_resend -> {
                ttsManager.stopSpeaking()
                if(isListening){
                    progressBar.visibility = View.INVISIBLE
                    speechRecognizer!!.stopListening()
                    isListening = false
                }
                // Resend user utterance
                sendMessage(utterances[currentItemPosition].utterance)
            }
            R.id.user_copy, R.id.mycroft_copy -> {
                // Copy utterance to clipboard
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val data = ClipData.newPlainText("text", utterances[currentItemPosition].utterance)
                clipboardManager.setPrimaryClip(data)
                showToast("Copied to clipboard")
            }
            R.id.mycroft_share -> {
                ttsManager.stopSpeaking()
                if(isListening){
                    progressBar.visibility = View.INVISIBLE
                    speechRecognizer!!.stopListening()
                    isListening = false
                }
                // Share utterance
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, utterances[currentItemPosition].utterance)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.action_share)))
            }
            else -> {
                return super.onContextItemSelected(item)
            }
        }

        return true
    }

    private fun sendUtterance() {
        ttsManager.stopSpeaking()
        val utterance = utteranceInput.text.toString()
        writeChatToFile("$utterance USER\r\n")
        if (utterance != "") {
            // Send utterance and clear input frame for next time
            sendMessage(utterance)
            utteranceInput.text?.clear()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(utteranceInput.windowToken, 0)
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
                     Log.i("Msg received", s)
                    runOnUiThread(MessageParser(sharedPreferenceManager, s, object : SafeCallback<Utterance> {
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
        cardList.smoothScrollToPosition(mycroftAdapter.itemCount - 1)


        Log.d(logTag, "Utterance: [$mycroftUtterance]")

        //Check what kind of utterance it is and label it accordingly
        if (mycroftUtterance.from.id == 1){
            writeChatToFile(mycroftUtterance.utterance.replace(Regex("(<{1}\\/?[^>]*>{1})"), "") + " " + "MYCROFT"+"\r\n")
            Log.i("mycroft utterance to file",mycroftUtterance.utterance.replace(Regex("(<{1}\\/?[^>]*>{1})"), "") )
        }
        else if (mycroftUtterance.from.id == 3){
            writeChatToFile(mycroftUtterance.utterance.replace(Regex("(<{1}\\/?[^>]*>{1})"), "") + " " + "BUTTON"+"\r\n")
            Log.i("mycroft utterance to file",mycroftUtterance.utterance.replace(Regex("(<{1}\\/?[^>]*>{1})"), "") )
        }
        else if (mycroftUtterance.from.id == 4){
            writeChatToFile(mycroftUtterance.utterance.replace(Regex("(<{1}\\/?[^>]*>{1})"), "") + " " + "IMAGE"+"\r\n")
            Log.i("mycroft utterance to file",mycroftUtterance.utterance.replace(Regex("(<{1}\\/?[^>]*>{1})"), "") )
        }


        if (voxswitch.isChecked  && (mycroftUtterance.from.id == 1) ) {
            when {
                !isRecognizerActivated && !isCameraOpen -> {
                    ttsManager.addQueue(mycroftUtterance.utterance, object : TextToSpeechCallback {
                        override fun onStart() {
                            Log.i("start callback", "started")
                            speechRecognizer!!.stopListening()
                        }

                        override fun onCompleted() {
                            Log.i("complete callback", "completed")
                            if (isContinues && !ttsManager.isTTSSpeaking()) {
                                Log.i("complete callback", "start listening")
                                speechRecognizer!!.startListening(speechRecognizerIntent)
                            }
                        }

                        override fun onError() {
                            Log.i("error callback", "error")
                        }

                    })
                }
            }
        }else{
            if (isContinues && (mycroftUtterance.from.id == 1)) {
                Log.i("complete callback", "start listening")
                speechRecognizer!!.startListening(speechRecognizerIntent)
            }
        }



    }




    private fun registerReceivers() {
        registerNetworkReceiver()
       // registerWearBroadcastReceiver()
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



    private fun unregisterReceivers() {
        unregisterBroadcastReceiver(networkChangeReceiver)
       // unregisterBroadcastReceiver(wearBroadcastReceiver)

        isNetworkChangeReceiverRegistered = false
      //  isWearBroadcastRevieverRegistered = false
    }

    private fun unregisterBroadcastReceiver(broadcastReceiver: BroadcastReceiver) {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }


    private fun deriveURI(): URI? {

        hiveMindName = sharedPreferenceManager?.getString("hivemind_name", getString(R.string.pref_default_hivemind_name))
        hiveMindAccessKey =sharedPreferenceManager?.getString("hivemind_access_key", getString(R.string.pref_default_hivemind_access_key))
        hiveMindPort = sharedPreferenceManager?.getString("hivemind_port", getString(R.string.pref_default_websocket_hivemind_port)).toString()
        hiveMindIp = sharedPreferenceManager?.getString("hivemind_ip", getString(R.string.pref_default_websocket_hivemind_ip))
        hiveMindPath = sharedPreferenceManager?.getString("hivemind_path", getString(R.string.pref_default_websocket_hivemind_path))


        return if ( hiveMindIp!! !== "") {
            try {
                val authToken = "$hiveMindName:$hiveMindAccessKey"
                val encodedString = Base64.getEncoder().encodeToString(authToken.toByteArray(StandardCharsets.UTF_8))

                var port: String = hiveMindPort!!
                if (port !== "")
                    port = ":$port"
                var wss = "wss://"
                if (!(sharedPreferenceManager?.getBoolean("hivemind_secured_websocket_connection", true))!!)
                    wss = "ws://"
                URI("${wss}${hiveMindIp}${port}${hiveMindPath}?authorization=$encodedString")

            } catch (e: URISyntaxException) {
                Log.e(logTag, "Unable to connect to websocket with this token", e)
                null
            }
        } else {
            return null
        }
    }


    fun sendMessage(msg: String) {
        // Check voxswitch2 to speak user utterance loudly
        if (voxswitch2.isChecked){
            if(!isRecognizerActivated){
                ttsManager.addQueue(msg)
            }else{
                ttsManager.initQueue("")
            }
        }

        hiveMindName =
            sharedPreferenceManager?.getString("hivemind_name", getString(R.string.pref_default_hivemind_name))
        hiveMindAccessKey = sharedPreferenceManager?.getString(
            "hivemind_access_key",
            getString(R.string.pref_default_hivemind_access_key)
        )
        hiveMindPort = sharedPreferenceManager?.getString(
            "hivemind_port",
            getString(R.string.pref_default_websocket_hivemind_port)
        ).toString()
        hiveMindIp = sharedPreferenceManager?.getString(
            "hivemind_ip",
            getString(R.string.pref_default_websocket_hivemind_ip)
        )
        hiveMindPath = sharedPreferenceManager?.getString(
            "hivemind_path",
            getString(R.string.pref_default_websocket_hivemind_path)
        )
        hiveMindCryptoKey = sharedPreferenceManager?.getString(
            "hivemind_crypto_key",
            getString(R.string.pref_default_hivemind_crypto_key)
        )


        // Token to send to Mycroft as context.
        val keycloakToken = storage.getStoredAccessToken()?.accessToken.toString()

        var port: String = hiveMindPort!!
        if (port !== "")
            port = ":$port"
        val nodeInfo = "${hiveMindIp}${port}${hiveMindPath}"
        val hivemindName = "$hiveMindName"
        val sessionID = getSessionID()

        val username = sharedPreferenceManager?.getString("username")



        // Messages must be escaped so that HiveMind can process button responses with entities.
        // Example button response (escaped): /request_risk_assessment{\"defect_id\": \"1002\"}
        val escapedMessage  = msg.replace(Regex("\""), """\\"""")

        // Log.i("Message before sending", escaped_msg)

        // TODO: check ubitech rename keycloakToken to "keycloak_token" to match convention of other key names.
        // TODO: check ubitech do we need the keycloak token in the payload? I can only access the one from context in the Mycroft skill.
        val json =
            "{\"msg_type\": \"bus\", \"payload\": {\"data\": {\"utterances\": [\"$escapedMessage\"], \"lang\": \"en-us\"}, \"type\": \"recognizer_loop:utterance\", \"keycloakToken\": \"$keycloakToken\", \"context\": {\"user_name\": \"$username\", \"keycloak_token\": \"$keycloakToken\",\"session_id\": \"$sessionID\",\"source\": \"tcp4:$nodeInfo\", \"destination\": \"hive_mind\", \"platform\": \"$hivemindName\"}}, \"node\": \"tcp4:$nodeInfo:terminal\", \"source_peer\": \"tcp4:$nodeInfo\" }"
        val encryptGcm = Crypto.encryptGCM(
            json.toByteArray(charset("UTF-8")),
            hiveMindCryptoKey!!.toByteArray(charset("UTF-8"))
        )

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
                    if (webSocketClient != null) {
                        webSocketClient!!.send(encryptGcm.toString())
                        addData(Utterance(msg, UtteranceFrom.USER))
                    } else {
                        showToast(resources.getString(R.string.authentication_code_missing))
                    }

                } catch (exception: WebsocketNotConnectedException) {
                    Log.e(logTag, "Unable to connect to web socket.", exception)
                    showToast(resources.getString(R.string.websocket_closed))
                }
            }, 1000)

        } catch (exception: WebsocketNotConnectedException) {
            showToast(resources.getString(R.string.websocket_closed))
        }

    }

    // override function to show button title in chat
    fun sendMessage(msg: String , title : String) {
        // Check voxswitch2 to speak user utterance loudly
        if (voxswitch2.isChecked){
            if(!isRecognizerActivated){
                ttsManager.addQueue(title)
            }else{
                ttsManager.initQueue("")
            }
        }

        hiveMindName =
            sharedPreferenceManager?.getString("hivemind_name", getString(R.string.pref_default_hivemind_name))
        hiveMindAccessKey = sharedPreferenceManager?.getString(
            "hivemind_access_key",
            getString(R.string.pref_default_hivemind_access_key)
        )
        hiveMindPort = sharedPreferenceManager?.getString(
            "hivemind_port",
            getString(R.string.pref_default_websocket_hivemind_port)
        ).toString()
        hiveMindIp = sharedPreferenceManager?.getString(
            "hivemind_ip",
            getString(R.string.pref_default_websocket_hivemind_ip)
        )
        hiveMindPath = sharedPreferenceManager?.getString(
            "hivemind_path",
            getString(R.string.pref_default_websocket_hivemind_path)
        )
        hiveMindCryptoKey = sharedPreferenceManager?.getString(
            "hivemind_crypto_key",
            getString(R.string.pref_default_hivemind_crypto_key)
        )

        // Token to send to Mycroft as context.
        val keycloakToken = storage.getStoredAccessToken()?.accessToken.toString()

        var port: String = hiveMindPort!!
        if (port !== "")
            port = ":$port"
        val nodeInfo: String = "${hiveMindIp}${port}${hiveMindPath}"
        val hivemindName: String = "$hiveMindName"

        val sessionID = getSessionID()

        val username = sharedPreferenceManager?.getString("username")



        // Messages must be escaped so that HiveMind can process button responses with entities.
        // Example button response (escaped): /request_risk_assessment{\"defect_id\": \"1002\"}
        val escapedMessage  = msg.replace(Regex("\""), """\\"""")

        // Log.i("Message before sending", escaped_msg)

        // TODO: check ubitech rename keycloakToken to "keycloak_token" to match convention of other key names.
        // TODO: check ubitech do we need the keycloak token in the payload? I can only access the one from context in the Mycroft skill.
        val json =  "{\"msg_type\": \"bus\", \"payload\": {\"data\": {\"utterances\": [\"$escapedMessage\"], \"lang\": \"en-us\"}, \"type\": \"recognizer_loop:utterance\", \"keycloakToken\": \"$keycloakToken\", \"context\": {\"user_name\": \"$username\", \"keycloak_token\": \"$keycloakToken\",\"session_id\": \"$sessionID\",\"source\": \"tcp4:$nodeInfo\", \"destination\": \"hive_mind\", \"platform\": \"$hivemindName\"}}, \"node\": \"tcp4:$nodeInfo:terminal\", \"source_peer\": \"tcp4:$nodeInfo\" }"
        val encryptGcm = Crypto.encryptGCM(
            json.toByteArray(charset("UTF-8")),
            hiveMindCryptoKey!!.toByteArray(charset("UTF-8"))
        )

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
                    if (webSocketClient != null) {
                        webSocketClient!!.send(encryptGcm.toString())
                        addData(Utterance(title, UtteranceFrom.USER))
                    } else {
                        showToast(resources.getString(R.string.authentication_code_missing))
                    }

                } catch (exception: WebsocketNotConnectedException) {
                    Log.e(logTag, "Unable to connect to web socket.", exception)
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
        stopService()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
            getString(R.string.speech_prompt))

        // Create an audio manager instance to accept bluetooth mic input
        val mAudioManager = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // Check if there is a bluetooth , if it is enabled and if it is connected to headset
        if(bluetoothAdapter != null
            && bluetoothAdapter.isEnabled
            && bluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED)
        {
            // Start to send and receive audio to/from a bluetooth SCO headset
            mAudioManager.startBluetoothSco()
        }

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
                isRecognizerActivated = false
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

                    if (result != null) {
                        // send audio input
                        sendMessage(result[0])
                        // Stop bluetooth SCO audio connection
                        val mAudioManager = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        mAudioManager.stopBluetoothSco()

                    }

                }

            }

            requestImageCapture -> {
                if (requestCode == requestImageCapture && resultCode == RESULT_OK) {
                    // Get the image file
                    val picture = File(currentPhotoPath)
                    // Convert it into input stream to be able to upload to cloud
                    val path = picture.absolutePath
                    // Upload to cloud with its name
                    //uploadToCloud(path, picture.name.substringBefore("*"))
                }
            }

        }
    }


    public override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutDown()
        stopService()
        isNetworkChangeReceiverRegistered = false
        progressBar.visibility = View.INVISIBLE
    }

    public override fun onStart() {
        super.onStart()
        recordVersionInfo()
        registerReceivers()
        //checkIfLaunchedFromWidget(intent)
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
         if (webSocketClient == null || webSocketClient!!.connection.isClosed) {
            connectWebSocket()
        }

        kbMicSwitch.isChecked = sharedPreferenceManager?.getBoolean("kbMicSwitch", true)!!
        if (kbMicSwitch.isChecked) {

            // Switch to mic
            utteranceInput.visibility = View.INVISIBLE
            sendUtterance.visibility = View.INVISIBLE
            // Make scan button visible when mic is activated
            scanButton.visibility = View.VISIBLE


        } else {
            // Switch to keyboard
            utteranceInput.visibility = View.VISIBLE
            sendUtterance.visibility = View.VISIBLE
        }

        // set app reader setting
        voxswitch.isChecked = sharedPreferenceManager?.getBoolean("appReaderSwitch", true)!!
        // set app user utterance reader setting
        voxswitch2.isChecked = sharedPreferenceManager?.getBoolean("appUserReaderSwitch", true)!!

        maximumRetries = Integer.parseInt(sharedPreferenceManager?.getString("maximumRetries", "1")!!)

        // get next cloud username and password
        cloudUsername = sharedPreferenceManager?.getString("cloud_username")!!
        cloudPassword = sharedPreferenceManager?.getString("cloud_password")!!

        cloudHostName = sharedPreferenceManager?.getString("cloud_host_name")!!


    }



    private fun recordVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            sharedPreferenceManager?.putValue(VERSION_CODE_PREFERENCE_KEY, packageInfo.versionCode)
            sharedPreferenceManager?.putValue(VERSION_NAME_PREFERENCE_KEY, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(logTag, "Couldn't find package info", e)
        }
    }


    private fun showToast(message: String) {
        GuiUtilities.showToast(applicationContext, message)
    }

//    override fun onTransferProgress(progressRate: Long, totalTransferredSoFar: Long, totalToTransfer: Long, fileAbsoluteName: String?) {
//        mHandler.post {
//            // do your UI updates about progress here
//        }
//    }

//    override fun onRemoteOperationFinish(operation: RemoteOperation<Any>, result: RemoteOperationResult<Any>) {
//        if (operation is UploadFileRemoteOperation) {
//            if (result.isSuccess) {
//                // do your stuff here
//                showToast("Image is uploaded successfully")
//            } else {
//                // TODO: Fix 500:Internal server error
//                showToast("Something went wrong with upload ! Check username and password or internet connection")
//
//            }
//        }
//    }

    /**
     * When view resume, check if token has been expired. If token is expired request
     * user to login again through keycloak by redirecting to the LoginActivity.
     * @author Gina Chatzimarkaki
     *
     * @version  1.0
     * @see LoginActivity
     */
    override fun onResume() {
        super.onResume()
        if (Helper.isRefreshTokenExpired(storage.getStoredAccessToken())){
            stopService()
            startActivityForResult(Intent(this, LoginActivity::class.java), AUTHORIZATION_REQUEST_CODE)
        }
        getSessionID()
    }

    override fun onBackPressed() {
        isCameraOpen = false
        super.onBackPressed()

    }

    private fun hasRecordPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermission(code : Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission.RECORD_AUDIO), code)
    }


    private val porcupineManagerCallback = PorcupineManagerCallback {
        runOnUiThread {
            //intentTextView.setText("")
            try {
                // need to stop porcupine manager before speechRecognizer can start listening.
                porcupineManager!!.stop()
            } catch (e: PorcupineException) {
                showToast("Failed to stop Porcupine.")
                return@runOnUiThread
            }
            speechRecognizer!!.startListening(speechRecognizerIntent)
        }
    }

    private fun playback() {
        isWakeWordActive = true
        isContinues = true
        speechRecognizer!!.stopListening()
        porcupineManager!!.start()
        wakeup_on.visibility=View.VISIBLE
        Snackbar.make(this.findViewById(android.R.id.content), "Listening for hey coala", Snackbar.LENGTH_LONG).show()

        Timer("StopWakeWordService", false).schedule(900000) {
            porcupineManager!!.stop() // stop wake word service after 15 minutes
            val beepSound = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            wakeup_on.visibility=View.INVISIBLE
            beepSound.startTone(ToneGenerator.TONE_PROP_BEEP, 250) // play beeps sound when service stops
            Snackbar.make(findViewById(android.R.id.content), "Wake word service stopped", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun stopService() {
        Timer("StopWakeWordService").purge()
        Timer("StopWakeWordService").cancel()
        if(wakeup_on.isVisible){
            wakeup_on.visibility=View.INVISIBLE
        }

        if (porcupineManager != null) {
            try {
                porcupineManager!!.stop()
            } catch (e: PorcupineException) {
                showToast("Failed to stop porcupine.")
            }
        }
        if(isListening){
            progressBar.visibility = View.INVISIBLE
            speechRecognizer!!.stopListening()
            isListening = false

        }

        isContinues = false
        isWakeWordActive = false
        isRecognizerActivated = true
        ttsManager.stopSpeaking()
    }

    //Writes utterances to a txt file in chat subdirectory
    private fun writeChatToFile(data: String) {
        val folderName = "chats"
        val context = applicationContext
        val folder = context.filesDir.absolutePath.toString() + File.separator + folderName

        val subFolder = File(folder)

        if (!subFolder.exists()) {
            subFolder.mkdirs()
        }

        val sessionID = sharedPreferenceManager?.getString("session_id")

        val filename = "$sessionID.txt"

        val path = File(folder, filename)
        Log.i("sessionID writeChat", filename)

        val fileOutputStream = FileOutputStream(File(subFolder,filename), true)
        try {
            fileOutputStream.write(data.toByteArray())
            Log.i("writing process", "writes to file")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.i("File Path", "write to file $path")
        fileOutputStream.flush()
        fileOutputStream.close()
    }

    override fun onRestart() {
        super.onRestart()
        getSessionID()
    }
  }


