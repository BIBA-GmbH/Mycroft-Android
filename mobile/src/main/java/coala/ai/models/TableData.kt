package coala.ai.models

import com.google.gson.annotations.SerializedName

data class TableData(
    @SerializedName("Defect ID")
    var Defect_ID: String,
    @SerializedName("Involved component")
    var Involved_component: String,
    @SerializedName("Defect Characteristic")
    var Defect_Characteristic: String,
    @SerializedName("Defect Severity")
    var Defect_Severity: String,
    @SerializedName("Occurrences")
    var Occurrences: String
)
