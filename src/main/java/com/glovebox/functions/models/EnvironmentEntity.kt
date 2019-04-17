package com.glovebox.functions.models

import com.google.gson.annotations.SerializedName
import com.microsoft.azure.storage.table.TableServiceEntity

class EnvironmentEntity : TableServiceEntity() {
    @SerializedName(value = "deviceId", alternate = ["DeviceId", "deviceid"])
    var deviceId: String? = null

    @SerializedName(value = "battery", alternate = ["Battery"])
    var battery: Double? = null

    @SerializedName(value = "temperature", alternate = ["Temperature", "Celsius", "temp", "Temp"])
    var temperature: Double? = null

    @SerializedName(value = "humidity", alternate = ["Humidity"])
    var humidity: Double? = null

    @SerializedName(value = "pressure", alternate = ["Pressure", "hPa"])
    var pressure: Double? = null

    @SerializedName(value = "geo", alternate = ["Geo"])
    var geo: String? = null

    @SerializedName(value = "schema", alternate = ["Schema"])
    var schema: Int = 1

    @SerializedName(value = "Id", alternate = ["messageId"])
    var id: Int = 0

//    @SerializedName(value = "count", alternate = ["Count"])
    var count: Int = 0

}