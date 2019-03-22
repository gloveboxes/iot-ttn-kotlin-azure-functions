package com.glovebox.functions


import com.glovebox.functions.models.*
import com.google.gson.GsonBuilder
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.Cardinality
import com.microsoft.azure.functions.annotation.EventHubTrigger
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.StorageException
import com.microsoft.azure.storage.table.CloudTable
import com.microsoft.azure.storage.table.CloudTableClient
import com.microsoft.azure.storage.table.TableOperation
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URISyntaxException
import java.net.URL
import java.security.InvalidKeyException
import java.util.*

// https://aspnetmonsters.com/2016/08/2016-08-27-httpclientwrong/
// https://dzone.com/articles/running-kotlin-in-azure-functions
// https://azure-samples.github.io/signalr-service-quickstart-serverless-chat/demo/chat-v2/

class TelemetryProcessing {

    private val _partitionKey: String = System.getenv("PartitionKey")

    private val storageConnectionString = System.getenv("StorageConnectionString")
    private val storageAccount: CloudStorageAccount = CloudStorageAccount.parse(storageConnectionString)
    private val tableClient: CloudTableClient = storageAccount.createCloudTableClient()
    private val deviceStateTable: CloudTable = getTableReference(tableClient, "DeviceState")
    private val calibrationTable: CloudTable = getTableReference(tableClient, "Calibration")
    private var top: TableOperation? = null

    private val signalRUrl: String? = System.getenv("AzureSignalRUrl")


    /**
     * This function will be invoked when an event is received from Event Hub.
     */
    @FunctionName("TelemetryProcessing")
    fun run(
//            @EventHubTrigger(name = "devicesEventHub", eventHubName = "devices", connection = "EventHubListenerCS", consumerGroup = "telemetry-processor", cardinality = Cardinality.MANY) message: List<EnvironmentEntity>,
            @EventHubTrigger(name = "devicesEventHub", eventHubName = "messages/events", connection = "IotHubConnectionString", consumerGroup = "telemetry-processor", cardinality = Cardinality.MANY) message: List<EnvironmentEntity>,
            context: ExecutionContext
    ) {

        context.logger.info("Java Event Hub trigger function executed.")
        context.logger.info("Message Count:" + message.size)

        message.forEach { environment ->

            try {

                if (!validateTelemetry(environment)) {
                    context.logger.info("Data failed validation.")
                    return
                }

                // https://docs.microsoft.com/en-us/azure/cosmos-db/table-storage-how-to-use-java
                top = TableOperation.retrieve(_partitionKey, environment.deviceId, CalibrationEntity::class.java)
                val calibrationData = calibrationTable.execute(top).getResultAsType<CalibrationEntity>()

                with(environment) {
                    calibrationData?.let {
                        celsius = scale(celsius, it.TemperatureSlope, it.TemperatureYIntercept)
                        humidity = scale(humidity, it.HumiditySlope, it.HumidityYIntercept)
                        hPa = scale(hPa, it.PressureSlope, it.PressureYIntercept)
                    }

                    partitionKey = _partitionKey
                    rowKey = environment.deviceId
                    timestamp = Date()
                }


                // https://docs.microsoft.com/en-us/azure/cosmos-db/table-storage-how-to-use-java
                top = TableOperation.insertOrReplace(environment)
                deviceStateTable.execute(top)


                val gson = GsonBuilder().create()
                val json = gson.toJson(environment)

                if (postRequest(json) != HttpURLConnection.HTTP_OK) {
                    context.logger.info("POST to SignalR failed")
                }

            } catch (e: Exception) {
                context.logger.info(e.message)
            } catch (e: URISyntaxException) {
                context.logger.info(e.message)
            } catch (e: StorageException) {
                context.logger.info(e.message)
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

        telemetry.celsius?.let {
            if (it < -10 || it > 70) {
                return@validateTelemetry false
            }
        }

        telemetry.humidity?.let {
            if (it < 0 || it > 100) {
                return@validateTelemetry false
            }
        }

        telemetry.hPa?.let {
            if (it < 0 || it > 1500) {
                return@validateTelemetry false
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
        if (result && tableName.equals("Calibration")) {
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