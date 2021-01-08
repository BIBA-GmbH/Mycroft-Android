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
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.owncloud.android.lib.common.OwnCloudClientFactory
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory
import com.owncloud.android.lib.common.network.OnDatatransferProgressListener
import com.owncloud.android.lib.common.operations.OnRemoteOperationListener
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.files.UploadFileRemoteOperation
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
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnDatatransferProgressListener, OnRemoteOperationListener {
    private val logTag = "Mycroft"
    private val utterances = mutableListOf<Utterance>()
    private val reqCodeSpeechInput = 100
    private val requestScan = 101
    private val requestImageCapture = 102
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
    private lateinit var currentPhotoPath: String

    private val mHandler = Handler()
    private lateinit var cloudUsername: String
    private lateinit var cloudPassword: String
    private lateinit var cloudService: String
    private lateinit var cloudHostName: String


    private lateinit var auth: FirebaseAuth

    var webSocketClient: WebSocketClient? = null
    private var mFirebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)


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
                uploadButton.visibility = View.VISIBLE

            } else {
                // Switch to keyboard
                micButton.visibility = View.INVISIBLE
                utteranceInput.visibility = View.VISIBLE
                sendUtterance.visibility = View.VISIBLE
                scanButton.visibility = View.INVISIBLE
                uploadButton.visibility = View.INVISIBLE

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
        //attach a listener to check for changes in state for user utterance
        voxswitch2.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPref.edit()
            editor.putBoolean("appUserReaderSwitch", isChecked)
            editor.apply()

            // stop tts from speaking if app user reader disabled
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

        // Set a click listener to a new button to upload images to cloud
        uploadButton.setOnClickListener { dispatchTakePictureIntent() }

        // start the discovery activity (testing only)
        // startActivity(new Intent(this, DiscoveryActivity.class));
    }

    /**
     * Starts the camera to take a picture and upload it to storage
     */
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    // Get and put the photo URI to intent to upload it in storage in onActivityResult
                    val photoURI: Uri = FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, requestImageCapture)
                }
            }
        }
    }


    /**
     * Creates a full size image file and saves it by storing its path to be used to upload
     */
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


    private fun signInToFirebase() {
        // Initialize Firebase Auth
        auth = Firebase.auth

        auth.signInWithEmailAndPassword(cloudUsername, cloudPassword)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information

                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(baseContext, "Authentication failed. Check username and password",
                                Toast.LENGTH_SHORT).show()
                    }

                }

    }


    /**
     * Uploads input stream to cloud when a picture taken from the app
     */
    @SuppressLint("SimpleDateFormat")
    private fun uploadToCloud(path: String, fileName: String) {

        // get next cloud username and password from settings
        cloudUsername = sharedPref.getString("cloud_username", "")!!
        cloudPassword = sharedPref.getString("cloud_password", "")!!

        cloudService = sharedPref.getString("cloud_preference", "")!!

        // Cloud host name for Nextcloud
        cloudHostName = sharedPref.getString("cloud_host_name", "")!!

        if (cloudUsername == "") {
            showToast("Please give a valid username in settings !")
        } else {
            if (cloudService == "Nextcloud") {

                // Parse URI to the base URL of the Nextcloud server
                val serverUri = Uri.parse(cloudHostName)

                // Create client object to perform remote operations
                val mClient = OwnCloudClientFactory.createOwnCloudClient(serverUri, this, true)
                // Set credentials for authorization
                mClient.credentials = OwnCloudCredentialsFactory.newBasicCredentials(cloudUsername, cloudPassword)
                // Set file properties to upload
                val uploadOperation = UploadFileRemoteOperation(path, "assistant/$fileName.jpg", "image/jpg", SimpleDateFormat("dd.MM.yyyy").format(Date()))
                // Upload the file
                uploadOperation.execute(mClient, this, mHandler)

            }
            if (cloudService == "Firebase") {

                //TODO: Sign in only once and sign out when needed.
                signInToFirebase()
                // Get a non-default Storage bucket
                val storage = Firebase.storage("gs://quickstart-1595243332893.appspot.com")

                // Create a storage reference from our app
                val storageRef = storage.reference

                // Give the name to file
                val assistantRef = storageRef.child("assistant/$fileName")
                val stream = FileInputStream(File(path))

                // Upload the image to storage
                val uploadTask = assistantRef.putStream(stream)
                uploadTask.addOnFailureListener { e ->
                    // Handle unsuccessful uploads
                    showToast(e.message.toString())
                }.addOnSuccessListener {
                    // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
                    // ...
                    showToast("Image is uploaded successfully")
                }

            }

        }
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

    private fun sendUtterance() {
        val utterance = utteranceInput.text.toString()
        if (utterance != "") {
            // Send utterance and clear input frame for next time
            sendMessage(utterance)
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

        // Check voxswitch2 to speak user utterance loudly
        if (voxswitch2.isChecked){
            ttsManager.addQueue(msg)
        }

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
            requestScan -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Parse the result of the scan intent to retrieve scan info
                    val result: IntentResult = IntentIntegrator.parseActivityResult(resultCode, data)
                    // If scan content is not null, store barcode to attach in the utterance
                    if (result.contents != null) {
                        // Get the result content
                        val rawValue = result.contents
                        // send barcode value
                        sendMessage(rawValue)

                    }else{
                        // If scan content is null, show a message
                       showToast("Nothing is scanned !")

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
                    uploadToCloud(path, picture.name.substringBefore("*"))
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
        if (wsct.isEmpty()) {
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
            // Make upload button visible when mic is activated
            uploadButton.visibility = View.VISIBLE

        } else {
            // Switch to keyboard
            micButton.visibility = View.INVISIBLE
            utteranceInput.visibility = View.VISIBLE
            sendUtterance.visibility = View.VISIBLE
            // Make scan button invisible when keyboard is activated
            scanButton.visibility = View.INVISIBLE
            // Make upload button invisible when keyboard is activated
            uploadButton.visibility = View.INVISIBLE

        }

        // set app reader setting
        voxswitch.isChecked = sharedPref.getBoolean("appReaderSwitch", true)
        // set app user utterance reader setting
        voxswitch2.isChecked = sharedPref.getBoolean("appUserReaderSwitch", true)

        maximumRetries = Integer.parseInt(sharedPref.getString("maximumRetries", "1")!!)

        // get next cloud username and password
        cloudUsername = sharedPref.getString("cloud_username", "")!!
        cloudPassword = sharedPref.getString("cloud_password", "")!!

        cloudHostName = sharedPref.getString("cloud_host_name", "")!!


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

    override fun onTransferProgress(progressRate: Long, totalTransferredSoFar: Long, totalToTransfer: Long, fileAbsoluteName: String?) {
        mHandler.post {
            // do your UI updates about progress here
        }
    }

    override fun onRemoteOperationFinish(operation: RemoteOperation?, result: RemoteOperationResult?) {
        if (operation is UploadFileRemoteOperation) {
            if (result != null) {
                if (result.isSuccess) {
                    // do your stuff here
                    showToast("Image is uploaded successfully")
                } else {
                    // TODO: Fix 500:Internal server error
                    showToast("Something went wrong with upload ! Check username and password or internet connection")

                }
            }
        }
    }
}
