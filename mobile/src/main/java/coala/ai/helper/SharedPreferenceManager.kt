package coala.ai.helper

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import coala.ai.Coala
import com.google.gson.Gson


object SharedPreferenceManager {
    private var pref: SharedPreferences? = null
    private var factory: SharedPreferenceManager? = null

    @JvmStatic
    fun getInstance(context: Context): SharedPreferenceManager? {

        if (pref == null) pref = PreferenceManager.getDefaultSharedPreferences(Coala.applicationContext())
        if (factory == null) factory =
            SharedPreferenceManager
        return factory
    }


    fun clearKey(key: String?) {
        pref!!.edit().remove(key).apply()
    }


    fun putValue(key: String?, value: Any?) {
        when (value) {
            is Boolean -> pref!!.edit().putBoolean(key, (value as Boolean?)!!)
                .apply()
            is String -> pref!!.edit()
                .putString(key, value.trim() as String?).apply()
            is Int -> pref!!.edit()
                .putInt(key, value).apply()
            is Long -> pref!!.edit()
                .putLong(key, value).apply()
        }

    }

    fun putObject(key: String?, `object`: Any?) {
        if (`object` == null || `object` == "") {
            pref!!.edit().putString(key, `object` as String?).apply()
            return
        }
        pref!!.edit().putString(key, Gson().toJson(`object`)).apply()
    }

    fun getInt(key: String?): Int {
        return pref!!.getInt(key, -1)
    }

    fun getInt(key: String?, value: Int): Int {
        return pref!!.getInt(key, value)
    }

    fun getLong(key: String?): Long {
        return pref!!.getLong(key, 0)
    }

    fun getString(key: String?): String? {
        val value = pref!!.getString(key, "")
        return value?.trim()
    }
    fun getString(key: String?, value: String): String? {
        val value =pref!!.getString(key, value)
        val v= value?.trim()
        return v
    }

    fun getBoolean(key: String? , value: Boolean): Boolean {
        return pref!!.getBoolean(key, value)
    }


    fun removeObject(key: String?) {
        pref!!.edit().remove(key).commit()
    }

    fun <T> getObject(key: String, a: Class<T>?): T? {
        val json = pref!!.getString(key, null)
        return if (json == null) {
            null
        } else {
            try {
                Gson().fromJson(json, a)
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "Object stored with key "
                            + key + " is instance of other class"
                )
            }
        }
    }

    fun hasValue(key: String?): Boolean {
        return pref!!.contains(key)
    }

    fun removePreference(
        context: Context, prefsName: String?,
        key: String?
    ) {
        val preferences: SharedPreferences = context.getSharedPreferences(
            prefsName, Activity.MODE_PRIVATE
        )
        val editor = preferences.edit()
        editor.remove(key)
        editor.apply()
    }


}
