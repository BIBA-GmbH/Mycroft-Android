package coala.ai.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import coala.ai.BuildConfig
import coala.ai.storage.Constants.MycroftMobileConstants.VERSION_CODE_PREFERENCE_KEY
import coala.ai.storage.Constants.MycroftMobileConstants.VERSION_NAME_PREFERENCE_KEY
import coala.ai.R

private const val TITLE_TAG = "settingsActivityTitle"

class AppSettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment() , "SettingFragment")
                .commit()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val c = supportFragmentManager.backStackEntryCount
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.action_settings)
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }




    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }


    /**
     * The root preference fragment that displays preferences that link to the other preference
     * fragments below.
     */

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root, rootKey)
        }
    }

    class GeneralPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.general_preferences, rootKey)
        }
    }

    class UploadsPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.uploads, rootKey)
        }
    }

    class AboutPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.about, rootKey)
            findPreference<Preference>(VERSION_NAME_PREFERENCE_KEY)?.summary = BuildConfig.VERSION_NAME
            findPreference<Preference>(VERSION_CODE_PREFERENCE_KEY)?.summary = BuildConfig.VERSION_CODE.toString()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                val currentFragment = supportFragmentManager.fragments.last()
                if(currentFragment.tag.contentEquals("SettingFragment") ){
                    finish()
                }else{
                    supportFragmentManager.popBackStackImmediate()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment , "${pref.title}")
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

}