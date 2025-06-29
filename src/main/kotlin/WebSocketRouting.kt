package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

fun Application.configureWebSocketRouting(
    // Новый параметр: лямбда для отправки оповещений
    sendAlertNotification: suspend (patientId: Int, metricCode: String, alertValue: Double, alertMessage: String, severity: String) -> Unit
) {
    val observationFlow = MutableSharedFlow<Observation>(replay = 0, extraBufferCapacity = 0)
    val connections = ConcurrentHashMap<Int, MutableSet<DefaultWebSocketSession>>()

    // Запуск генерации наблюдений и отправка в SharedFlow
    launch {
        // Передаем новый параметр sendAlertNotification
        MonitoringGenerator.startMonitoring(this@configureWebSocketRouting, observationFlow, sendAlertNotification)
    }

    routing {
        webSocket("/observations/{patientId}") {
            val patientId = call.parameters["patientId"]?.toIntOrNull()
            if (patientId == null) {
                close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Invalid patient ID"))
                return@webSocket
            }
            // Добавьте логирование здесь:
            application.log.info("WebSocket connection opened for patient $patientId")
            connections.computeIfAbsent(patientId) { ConcurrentHashMap.newKeySet() }.add(this)
            try {
                observationFlow.collect { observation ->
                    if (observation.patientId == patientId) {
                        val message = Json.encodeToString(observation)
                        send(Frame.Text(message))
                    }
                }
            } catch (e: Exception) {
                application.log.error("WebSocket error for patient $patientId: ${e.message}", e)
            } finally {
                connections[patientId]?.remove(this)
                if (connections[patientId]?.isEmpty() == true) {
                    connections.remove(patientId)
                }
                // Добавьте логирование здесь:
                application.log.info("WebSocket connection closed for patient $patientId")
            }
        }
    }
}