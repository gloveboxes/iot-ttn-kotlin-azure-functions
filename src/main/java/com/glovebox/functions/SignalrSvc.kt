package com.glovebox.functions

import com.glovebox.functions.models.EnvironmentEntity
import com.google.gson.GsonBuilder
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import com.microsoft.azure.functions.signalr.SignalRConnectionInfo
import com.microsoft.azure.functions.signalr.SignalRMessage
import com.microsoft.azure.functions.signalr.annotation.SignalRConnectionInfoInput
import com.microsoft.azure.functions.signalr.annotation.SignalROutput
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.table.CloudTable
import com.microsoft.azure.storage.table.CloudTableClient
import com.microsoft.azure.storage.table.TableQuery
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons
import java.util.*


/**
 * Azure Functions with HTTP Trigger.
 */
class SignalrSvc {

    private val storageConnectionString = System.getenv("StorageConnectionString")
    private val storageAccount: CloudStorageAccount = CloudStorageAccount.parse(storageConnectionString)
    private val tableClient: CloudTableClient = storageAccount.createCloudTableClient()
    private val deviceStateTable: CloudTable = tableClient.getTableReference("DeviceState")
    private val partitionKey: String = System.getenv("PartitionKey")


    @FunctionName("negotiate")
    fun negotiate(
            @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
            req: HttpRequestMessage<Optional<String>>,
            @SignalRConnectionInfoInput(name = "connectionInfo", hubName = "iot")
            connectionInfo: SignalRConnectionInfo): SignalRConnectionInfo {
        return connectionInfo
    }

    // REST call made from Telemetry Processing Function
    @FunctionName("sendmessage")
    @SignalROutput(name = "\$return", hubName = "iot")
    fun sendMessage(
            @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.FUNCTION)
            req: HttpRequestMessage<Any>): SignalRMessage {

        val message = req.body
        return SignalRMessage("newMessage", message)
    }

    @FunctionName("getdevicestate")
    @SignalROutput(name = "\$return", hubName = "iot")
    fun initDeviceState(
            @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
            req: HttpRequestMessage<Any>): SignalRMessage {

        val gson = GsonBuilder().create()
        val json = gson.toJson(deviceState())

        return SignalRMessage("newMessage", json)
    }

    private fun deviceState(): List<EnvironmentEntity> {
        val partitionFilter = TableQuery.generateFilterCondition("PartitionKey", QueryComparisons.EQUAL, partitionKey)
        val partitionQuery = TableQuery.from(EnvironmentEntity::class.java).where(partitionFilter)
        return deviceStateTable.execute(partitionQuery).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.deviceId!! })).toList()
    }
}