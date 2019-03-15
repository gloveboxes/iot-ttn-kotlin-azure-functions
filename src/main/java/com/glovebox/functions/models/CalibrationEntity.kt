package com.glovebox.functions.models

import com.microsoft.azure.storage.table.TableServiceEntity

//https://docs.microsoft.com/en-us/azure/cosmos-db/table-storage-how-to-use-java

class CalibrationEntity: TableServiceEntity()  {
    var TemperatureSlope: Double? = null
    var TemperatureYIntercept: Double? = null
    var HumiditySlope: Double? = null
    var HumidityYIntercept: Double? = null
    var PressureSlope: Double? = null
    var PressureYIntercept: Double? = null
}