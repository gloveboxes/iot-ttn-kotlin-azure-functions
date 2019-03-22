package com.glovebox.functions

import com.glovebox.functions.models.*
import java.util.*
import com.microsoft.azure.functions.annotation.*
import com.microsoft.azure.functions.*
import com.google.gson.*


/**
 * Azure Functions with HTTP Trigger.
 */
class TheThingsNetwork {
    private val ttnAppId: String = System.getenv("TheThingsNetworkAppId")

    @FunctionName("TheThingsNetwork")
    fun run(
            @HttpTrigger(name = "req", methods = arrayOf(HttpMethod.POST), authLevel = AuthorizationLevel.FUNCTION) request: HttpRequestMessage<Optional<String>>,
            @EventHubOutput(name = "devicesEventHub", eventHubName = "devices", connection = "EventHubSenderCS") ttnOutData: OutputBinding<EnvironmentEntity>,
            context: ExecutionContext): HttpResponseMessage {

        val gson = GsonBuilder().create()
        val body = request.body.get()
        val ttn = gson.fromJson<TtnEntity>(body, TtnEntity::class.java)

        context.logger.info("Message received from Things Network Application ID ${ttn.app_id} for device ${ttn.dev_id}")

        if (ttn.app_id == null || !ttn.app_id.equals(ttnAppId)){
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build()
        }

        val environment = EnvironmentEntity()
        environment.deviceId = ttn.dev_id
        environment.geo = ttn.dev_id
        environment.id = ttn.counter

        environment.celsius = ttn.payload_fields!!.getOrDefault(key="temperature_1", defaultValue = null)
        environment.hPa = ttn.payload_fields!!.getOrDefault(key="barometric_pressure_2", defaultValue = null)
        environment.humidity = ttn.payload_fields!!.getOrDefault(key="relative_humidity_3", defaultValue = null)
        environment.battery = ttn.payload_fields!!.getOrDefault(key="analog_in_4", defaultValue = null)

        ttnOutData.value = environment

        return request.createResponseBuilder(HttpStatus.OK).build()
    }
}