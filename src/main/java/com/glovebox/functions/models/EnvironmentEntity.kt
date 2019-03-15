package com.glovebox.functions.models

import com.microsoft.azure.storage.table.TableServiceEntity

class EnvironmentEntity : TableServiceEntity() {
    var DeviceId: String? = null
    var Battery: Double? = null
    var Celsius: Double? = null
    var Humidity: Double? = null
    var hPa: Double? = null
    var Light: Double? = null
    var Geo: String? = null
    var Schema: Int = 0
    var Id: Int = 0
}