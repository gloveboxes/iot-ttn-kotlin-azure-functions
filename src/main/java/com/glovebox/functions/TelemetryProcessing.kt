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

    private val storageConnectionString = System.getenv("StorageConnectionString")
    private val storageAccount: CloudStorageAccount = CloudStorageAccount.parse(storageConnectionString)
    private val tableClient: CloudTableClient = storageAccount.createCloudTableClient()
    private val deviceStateTable: CloudTable = tableClient.getTableReference("DeviceState")
    private val calibrationTable: CloudTable = tableClient.getTableReference("Calibration")
    private var top: TableOperation? = null

    private val _partitionKey: String = System.getenv("PartitionKey")
    private val signalRUrl: String = System.getenv("AzureSignalRUrl")


    /**
     * This function will be invoked when an event is received from Event Hub.
     */
    @FunctionName("TelemetryProcessing")
    @Throws(URISyntaxException::class, InvalidKeyException::class)
    fun run(
            @EventHubTrigger(name = "devicesEventHub", eventHubName = "devices", connection = "EventHubListenerCS", consumerGroup = "telemetry-processor", cardinality = Cardinality.MANY) message: List<EnvironmentEntity>,
//            @EventHubTrigger(name = "devicesEventHub", eventHubName = "messages/events", connection = "EventHubListenerCS", consumerGroup = "\$Default", cardinality = Cardinality.MANY) message: List<EnvironmentEntity>,
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
                        celsius = round(celsius!! * it.TemperatureSlope!! + it.TemperatureYIntercept!!)
                        humidity = round(humidity!! * it.HumiditySlope!! + it.HumidityYIntercept!!)
                        hPa = round(hPa!! * it.PressureSlope!! + it.PressureYIntercept!!)
                    }

                    partitionKey = _partitionKey
                    rowKey = environment.deviceId
                    timestamp = Date()
                }


                // https://docs.microsoft.com/en-us/azure/cosmos-db/table-storage-how-to-use-java
                top = TableOperation.insertOrReplace(environment)
                deviceStateTable.execute(top!!)


                val gson = GsonBuilder().create()
                val json = gson.toJson(environment)

                if (postRequest(URL(signalRUrl), json) != HttpURLConnection.HTTP_OK) {
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


    private fun postRequest(url: URL, message: String): Int {
        // https://stackoverflow.com/questions/48387708/azure-java-function-failing-while-making-outbound-rest-call-socketexception-p
        // prefer IPv4 needed on Azure otherwise socket exception thrown
        System.setProperty("java.net.preferIPv4Stack", "true")

        val postConnection = url.openConnection() as HttpURLConnection
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


    private fun round(value: Double): Double {
        return BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toDouble()
    }
}