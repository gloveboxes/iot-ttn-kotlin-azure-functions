package com.glovebox.functions

import com.glovebox.functions.models.*
import com.google.gson.GsonBuilder
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.Cardinality
import com.microsoft.azure.functions.annotation.EventHubTrigger
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.table.CloudTable
import com.microsoft.azure.storage.table.CloudTableClient
import com.microsoft.azure.storage.table.TableOperation
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

// https://aspnetmonsters.com/2016/08/2016-08-27-httpclientwrong/
// https://dzone.com/articles/running-kotlin-in-azure-functions
// https://azure-samples.github.io/signalr-service-quickstart-serverless-chat/demo/chat-v2/
// https://docs.microsoft.com/en-us/azure/cosmos-db/table-storage-how-to-use-java

class TelemetryProcessing {

    private val _partitionKey: String = System.getenv("PartitionKey")
    private val storageConnectionString = System.getenv("StorageConnectionString")
    private val signalRUrl: String? = System.getenv("AzureSignalRUrl")

    private val storageAccount: CloudStorageAccount = CloudStorageAccount.parse(storageConnectionString)
    private val tableClient: CloudTableClient = storageAccount.createCloudTableClient()
    private val deviceStateTable: CloudTable = getTableReference(tableClient, "DeviceState")
    private val calibrationTable: CloudTable = getTableReference(tableClient, "Calibration")
    private var top: TableOperation? = null

    private val calibrationMap = mutableMapOf<String?, CalibrationEntity?>()

    // Optimistic Concurrency Tuning Parameters
    private val occBase:Long = 40 // 40 milliseconds
    private val occCap:Long = 1000 // 1000 milliseconds


    @FunctionName("TelemetryProcessing")
    fun run(
            @EventHubTrigger(name = "AzureIotHub", eventHubName = "glovebox-iothub", connection = "IotHubConnectionString", consumerGroup = "kotlin-enviromon-functions", cardinality = Cardinality.MANY) message: List<EnvironmentEntity>,
            context: ExecutionContext
    ) {
        var maxRetry: Int
        val deviceState = mutableMapOf<String, EnvironmentEntity>()

        message.forEach { environment ->

            maxRetry = 0

            while (maxRetry < 10) {
                maxRetry++

                try {

                    if (environment.deviceId == null){
                        break
                    }

                    calibrate(environment)

                    if (!validateTelemetry(environment)) {
                        context.logger.info("Data failed validation.")
                        break
                    }

                    top = TableOperation.retrieve(_partitionKey, environment.deviceId, EnvironmentEntity::class.java)
                    val existingEntity = deviceStateTable.execute(top).getResultAsType<EnvironmentEntity>()

                    with(environment) {
                        partitionKey = _partitionKey
                        rowKey = environment.deviceId
                        timestamp = Date()
                    }

                    if (existingEntity?.etag != null) {
                        environment.etag = existingEntity.etag
                        environment.count = existingEntity.count
                        environment.count++

                        top = TableOperation.replace(environment)
                        deviceStateTable.execute(top)
                    } else {
                        environment.count = 1
                        top = TableOperation.insert(environment)
                        deviceStateTable.execute(top)
                    }

                    deviceState[environment.deviceId!!] = environment

                    break

                } catch (e: java.lang.Exception) {
                    val interval = calcExponentialBackoff(maxRetry)
                    Thread.sleep(interval)
                    context.logger.info("Optimistic Consistency Backoff interval $interval")
                }
            }

            if (maxRetry >= 10){
                context.logger.info("Failed to commit")
            }
        }

        if (deviceState.count() > 0){
            val gson = GsonBuilder().create()

            deviceState.forEach {
                val json = gson.toJson(it.value)
                if (postRequest(json) != HttpURLConnection.HTTP_OK) {
                    context.logger.info("POST to SignalR failed")
                }
            }
        }
    }

    private  fun calcExponentialBackoff(attempt: Int) : Long{
        val base = occBase * Math.pow(2.0, attempt.toDouble())
        return ThreadLocalRandom.current().nextLong(occBase,min(occCap, base.toLong()))
    }

    private fun calibrate(environment: EnvironmentEntity) {
        val calibrationData:CalibrationEntity?

        if (calibrationMap.containsKey(environment.deviceId)){
            calibrationData = calibrationMap[environment.deviceId]
        }
        else {
            top = TableOperation.retrieve(_partitionKey, environment.deviceId, CalibrationEntity::class.java)
            calibrationData = calibrationTable.execute(top).getResultAsType<CalibrationEntity>()
            calibrationMap[environment.deviceId] = calibrationData
        }

        with(environment) {
            calibrationData?.let {
                temperature = scale(temperature, it.TemperatureSlope, it.TemperatureYIntercept)
                humidity = scale(humidity, it.HumiditySlope, it.HumidityYIntercept)
                pressure = scale(pressure, it.PressureSlope, it.PressureYIntercept)
            }
        }
    }

    private fun postRequest(message: String): Int {
        // https://stackoverflow.com/questions/48387708/azure-java-function-failing-while-making-outbound-rest-call-socketexception-p
        // prefer IPv4 needed on Azure otherwise socket exception thrown

        if (isNullOrEmpty(signalRUrl)) {
            return 200
        }

        System.setProperty("java.net.preferIPv4Stack", "true")

        val postConnection = URL(signalRUrl).openConnection() as HttpURLConnection
        postConnection.requestMethod = "POST"
        postConnection.setRequestProperty("Content-Type", "application/json")
        postConnection.doOutput = true

        val os = postConnection.outputStream
        os.write(message.toByteArray())
        os.flush()
        os.close()

        return postConnection.responseCode
    }


    private fun validateTelemetry(telemetry: EnvironmentEntity): Boolean {
        telemetry.temperature?.let {
            if (it < -10 || it > 70) {
                return false
            }
        }

        telemetry.humidity?.let {
            if (it < 0 || it > 100) {
                return false
            }
        }

        telemetry.pressure?.let {
            if (it < 0 || it > 1500) {
                return false
            }
        }

        return true
    }

    private fun scale(value: Double?, slope: Double?, intercept: Double?): Double? {
        if (value == null || slope == null || intercept == null) {
            return value
        }
        return round(value * slope + intercept)
    }


    private fun round(value: Double): Double {
        return BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toDouble()
    }

    private fun getTableReference(tableClient: CloudTableClient, tableName: String): CloudTable {
        val cloudTable = tableClient.getTableReference(tableName)
        val result = cloudTable.createIfNotExists() // returns true if the table was created

        // for demo purposes only. Adds some dummy calibration data
        if (result && tableName == "Calibration") {
            val calibration = CalibrationEntity()
            with(calibration) {
                partitionKey = _partitionKey
                rowKey = "Raspberry Pi"
                TemperatureSlope = 1.0
                TemperatureYIntercept = 0.0
                PressureSlope = 1.0
                PressureYIntercept = 0.0
                HumiditySlope = 1.0
                HumidityYIntercept = 0.0
            }

            top = TableOperation.insertOrReplace(calibration)
            cloudTable.execute(top)
        }
        return cloudTable
    }

    fun isNullOrEmpty(str: String?): Boolean {
        if (str != null && !str.trim().isEmpty())
            return false
        return true
    }
}