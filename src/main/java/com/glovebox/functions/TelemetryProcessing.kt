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

    private val partitionKey: String = System.getenv("PartitionKey")
    private val signalRUrl: String = System.getenv("AzureSignalRUrl")


    /**
     * This function will be invoked when an event is received from Event Hub.
     */
    @FunctionName("TelemetryProcessing")
    @Throws(URISyntaxException::class, InvalidKeyException::class)
    fun run(
            @EventHubTrigger(name = "devicesEventHub", eventHubName = "devices", connection = "EventHubListenerCS", consumerGroup = "telemetry-processor", cardinality = Cardinality.MANY) message: List<EnvironmentEntity>,
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
                top = TableOperation.retrieve(partitionKey, environment.DeviceId, CalibrationEntity::class.java)
                val calibrationData = calibrationTable.execute(top).getResultAsType<CalibrationEntity>()

                if (calibrationData != null) {
                    environment.Celsius = round(environment.Celsius!! * calibrationData.TemperatureSlope!! + calibrationData.TemperatureYIntercept!!)
                    environment.Humidity = round(environment.Humidity!! * calibrationData.HumiditySlope!! + calibrationData.HumidityYIntercept!!)
                    environment.hPa = round(environment.hPa!! * calibrationData.PressureSlope!! + calibrationData.PressureYIntercept!!)
                }

                environment.partitionKey = partitionKey
                environment.rowKey = environment.DeviceId
                environment.timestamp = Date()


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

        if (telemetry.Celsius != null && (telemetry.Celsius!! < -10 || telemetry.Celsius!! > 70)) {
            return false
        }

        if (telemetry.Humidity != null && (telemetry.Humidity!! < 0 || telemetry.Humidity!! > 100)) {
            return false
        }

        if (telemetry.hPa != null && (telemetry.hPa!! < 0 || telemetry.hPa!! > 1400)) {
            return false
        }

        return true
    }


    private fun round(value: Double): Double {
        return BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toDouble()
    }
}