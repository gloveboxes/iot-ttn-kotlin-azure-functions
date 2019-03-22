package com.glovebox.functions.models

import com.google.gson.annotations.SerializedName
import com.microsoft.azure.storage.table.TableServiceEntity

class EnvironmentEntity : TableServiceEntity() {
    @SerializedName(value = "deviceId", alternate = ["DeviceId", "deviceid"])
    var deviceId: String? = null

    @SerializedName(value = "battery", alternate = ["Battery"])
    var battery: Double? = null

    @SerializedName(value = "celsius", alternate = ["Temperature", "temperature", "Celsius", "temp", "Temp"])
    var celsius: Double? = null

    @SerializedName(value = "humidity", alternate = ["Humidity"])
    var humidity: Double? = null

    @SerializedName(value = "hPa", alternate = ["Pressure", "pressure"])
    var hPa: Double? = null

    @SerializedName(value = "light", alternate = ["Light"])
    var light: Double? = null

    @SerializedName(value = "geo", alternate = ["Geo"])
    var geo: String? = null

    @SerializedName(value = "schema", alternate = ["Schema"])
    var schema: Int = 1

    @SerializedName(value = "Id", alternate = ["messageId"])
    var id: Int = 0
}