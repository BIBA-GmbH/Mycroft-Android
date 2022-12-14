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

package coala.ai.parsers

import android.util.Log
import coala.ai.models.UtteranceFrom
import coala.ai.helper.SharedPreferenceManager
import coala.ai.interfaces.SafeCallback
import coala.ai.models.Utterance
import coala.ai.secure.Crypto
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


/**
 * Specialised Runnable that parses the [JSONObject] in [.message]
 * when run. If it contains a [Utterance] object, the callback
 * defined in [the constructor][.MessageParser] will
 * be [called][SafeCallback.call] with that object as a parameter.
 *
 *
 * TODO: Add error-aware callback for cases where the message is malformed.
 *
 *
 * @author Philip Cohn-Cort Version 1
 * @author Stefan Wellsandt COALA extensions
 */
internal class MessageParser(private val sharedPref: SharedPreferenceManager?,
                             private val message: String,
                             private val callback: SafeCallback<Utterance>) : Runnable {
    private val logTag = "MessageParser"
    private val charset: Charset = StandardCharsets.UTF_8



    override fun run() {
        // Messages typically contain an utterance (text message).
        // Some messages with rich media may contain no utterance.
        // Messages can contain an utterance and rich media data.

        try {

            val decryptedMessage = String(Crypto.decryptGcm(message, sharedPref?.getString("hivemind_crypto_key", "resistanceISfuti")!!.toByteArray(charset)), Charsets.UTF_8)
            val obj = JSONObject(decryptedMessage).optJSONObject("payload")

            // To be removed in production
            Log.i("Incoming Mycroft message", decryptedMessage)

            if (obj!!.optString("type") == "speak") {

                // If an utterance key exists, use its value.
                if (obj.getJSONObject("data").has("utterance")) {
                    val text = obj.getJSONObject("data").getString("utterance")
                    val utterance = Utterance(text, UtteranceFrom.MYCROFT)
                    Log.i("data", obj.getJSONObject("data").getString("utterance"))
                    callback.call(utterance)
                }

                // Check if there are rich_media attached. The parser must handle them specifically.
                if (obj.getJSONObject("data").has("rich_media_data")) {

                    val richMedia = obj.getJSONObject("data").getString("rich_media_data")
                    Log.i("media_data", richMedia)
                    val mediaData = JSONObject(richMedia)

                    if (mediaData.has("table")) {
                        val tableData = mediaData.getString("table")
                        val utterance = Utterance(tableData, UtteranceFrom.TABLE)
                        callback.call(utterance)

                    }

                    if (mediaData.has("attachment")) {
                        val type: String? = mediaData.getJSONObject("attachment").getString("type")
                        if (type!!.contentEquals("image")) {
                            val imgLink =
                                mediaData.optJSONObject("attachment")!!.optJSONObject("payload")!!
                                    .getString("src")
                            val utterance = Utterance(imgLink, UtteranceFrom.MYCROFT_IMG)
                            callback.call(utterance)
                        }
                    }

                    if (mediaData.has("quick_replies")) {
                        val buttons = mediaData.getJSONArray("quick_replies").toString()
                        val utterance = Utterance(buttons, UtteranceFrom.BUTTONS)
                        callback.call(utterance)
                    }

                }
            }

        } catch (e: JSONException) {
            Log.e(logTag, "The response received did not conform to our expected JSON format.", e)
        }
    }


}
