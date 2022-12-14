package coala.ai.models

import com.google.gson.annotations.SerializedName


data class ButtonsData(
        @SerializedName("title")
        var title: String,
        @SerializedName("payload")
        var payload: String
)
