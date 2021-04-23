package coala.ai

import android.annotation.SuppressLint
import android.os.Bundle

class ConfigureImageUpload : AppCompatPreferenceActivity() {
    @SuppressLint("ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.pref_upload_config)


    }
}