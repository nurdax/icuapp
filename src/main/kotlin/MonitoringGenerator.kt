package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object MonitoringGenerator {
    private val random = Random()
    private val formatter = DateTimeFormatter.ISO_INSTANT
    private val client = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // Нормальные и аномальные диапазоны, maxChangesPerInterval как в ObservationGenerator.kt
    private val normalRanges = mapOf(
        "spo2" to 95f..100f,
        "pulse" to 60f..100f,
        "heart_rate" to 60f..100f,
        "systolic" to 90f..120f,
        "diastolic" to 60f..80f,
        "mean_pressure" to 70f..95f,
        "etco2" to 35f..45f,
        "respiratory_rate" to 12f..20f,
        "tidal_volume" to 40f..60f,
        "pressure" to 10f..20f,
        "temperature" to 36.5f..37.5f,
        "infusion_rate" to 20f..100f
    )

    private val anomalyRanges = mapOf(
        "spo2" to 70f..90f,
        "pulse" to 40f..120f,
        "heart_rate" to 40f..130f,
        "systolic" to 80f..180f,
        "diastolic" to 50f..110f,
        "mean_pressure" to 60f..120f,
        "etco2" to 20f..60f,
        "respiratory_rate" to 8f..30f,
        "tidal_volume" to 20f..80f,
        "pressure" to 5f..35f,
        "temperature" to 35f..39f,
        "infusion_rate" to 5f..150f
    )

    private val maxChangesPerInterval = mapOf(
        "spo2" to 1f,
        "pulse" to 5f,
        "heart_rate" to 5f,
        "systolic" to 10f,
        "diastolic" to 8f,
        "mean_pressure" to 8f,
        "etco2" to 2f,
        "respiratory_rate" to 2f,
        "tidal_volume" to 50f,
        "pressure" to 2f,
        "temperature" to 0.2f,
        "infusion_rate" to 10f
    )

    private const val ANOMALY_PROBABILITY = 0.1f
    private val lastValues = mutableMapOf<Triple<Int, Int, String>, Float>()

    private fun getMetricCodes(deviceType: String): List<String> {
        return when (deviceType) {
            "Пульсоксиметр" -> listOf("spo2", "pulse")
            "ЭКГ-монитор" -> listOf("heart_rate")
            "Монитор АД" -> listOf("systolic", "diastolic", "mean_pressure")
            "Капнограф" -> listOf("etco2")
            "Аппарат ИВЛ" -> listOf("respiratory_rate", "tidal_volume", "pressure")
            "Термометр" -> listOf("temperature")
            "Инфузомат" -> listOf("infusion_rate")
            else -> emptyList()
        }
    }

    private fun generateObservation(
        patientId: Int,
        deviceId: Int,
        metricCode: String,
        patientStatus: String,
        timestamp: Instant = Instant.now()
    ): Observation? {
        if (patientStatus == "выписан") return null

        val key = Triple(patientId, deviceId, metricCode)
        val isCritical = patientStatus == "критическое"
        val range = if (isCritical && random.nextFloat() < ANOMALY_PROBABILITY) {
            anomalyRanges[metricCode] ?: normalRanges.getOrDefault(metricCode, 0f..0f)
        } else {
            normalRanges.getOrDefault(metricCode, 0f..100f)
        }

        val lastValue = lastValues[key] ?: ((range.start + range.endInclusive) / 2f)
        val maxChange = maxChangesPerInterval[metricCode] ?: 5f
        val adjustedMaxChange = if (metricCode == "heart_rate") maxChange else maxChange / 10f

        val change = (random.nextFloat() * 2 - 1) * adjustedMaxChange
        var newValue = lastValue + change
        newValue = max(range.start, min(range.endInclusive, newValue))
        lastValues[key] = newValue

        val formattedValue = if (metricCode == "temperature") {
            String.format(Locale.US, "%.1f", newValue).toFloat()
        } else {
            newValue.toInt().toFloat()
        }

        val unit = when (metricCode) {
            "spo2" -> "%"
            "pulse", "heart_rate" -> "уд/мин"
            "systolic", "diastolic", "mean_pressure", "etco2" -> "мм рт."
            "respiratory_rate" -> "вдохов/мин"
            "tidal_volume" -> "сл"
            "pressure" -> "см H₂O"
            "temperature" -> "°C"
            "infusion_rate" -> "ml/h"
            else -> ""
        }

        return Observation(
            id = 0,
            patientId = patientId,
            deviceId = deviceId,
            metricCode = metricCode,
            value = formattedValue.toDouble(),
            unit = unit,
            effectiveTimestamp = formatter.format(timestamp)
        )
    }

    fun startMonitoring(application: Application) {
        application.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val patients = transaction {
                        Patients.selectAll().map {
                            Patient(
                                id = it[Patients.id],
                                medicalRecordNumber = it[Patients.medicalRecordNumber],
                                firstName = it[Patients.firstName],
                                lastName = it[Patients.lastName],
                                birthDate = it[Patients.birthDate].toString(),
                                admissionDate = it[Patients.admissionDate].toString(),
                                status = it[Patients.status],
                                nurseId = it[Patients.nurseId]
                            )
                        }
                    }

                    patients.forEach { patient ->
                        if (patient.status == "выписан") return@forEach

                        val patientDevices = transaction {
                            PatientDevices.select { PatientDevices.patientId eq patient.id }.map {
                                PatientDevice(
                                    id = it[PatientDevices.id],
                                    patientId = it[PatientDevices.patientId],
                                    deviceId = it[PatientDevices.deviceId],
                                    assignedAt = it[PatientDevices.assignedAt].toString(),
                                    settingsText = it[PatientDevices.settingsText]
                                )
                            }
                        }

                        val devices = transaction {
                            Devices.selectAll().map {
                                Device(
                                    id = it[Devices.id],
                                    serialNumber = it[Devices.serialNumber],
                                    type = it[Devices.type],
                                    status = it[Devices.status]
                                )
                            }
                        }

                        patientDevices.forEach { patientDevice ->
                            val device = devices.find { it.id == patientDevice.deviceId } ?: return@forEach
                            val metricCodes = getMetricCodes(device.type)
                            metricCodes.forEach { metricCodee ->
                                val observation = generateObservation(
                                    patientId = patient.id,
                                    deviceId = patientDevice.deviceId,
                                    metricCode = metricCodee,
                                    patientStatus = patient.status
                                )
                                if (observation != null) {
                                    transaction {
                                        Observations.insert {
                                            it[patientId] = observation.patientId
                                            it[deviceId] = observation.deviceId
                                            it[metricCode] = observation.metricCode
                                            it[value] = observation.value
                                            it[unit] = observation.unit
                                            it[recordedAt] = Instant.parse(observation.effectiveTimestamp)
                                        }
                                    }
                                    application.log.info("Generated observation for patient ${patient.id}, metric $metricCodee")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    application.log.error("Error generating observations: ${e.message}", e)
                }

                // Задержка: heart_rate каждую секунду, остальные каждые 10 секунд
                delay(1_000)
            }
        }

        // Генерация начальных данных для последних 60 секунд (heart_rate) или 12 точек (остальные)
        application.launch(Dispatchers.IO) {
            try {
                val patients = transaction {
                    Patients.selectAll().map {
                        Patient(
                            id = it[Patients.id],
                            medicalRecordNumber = it[Patients.medicalRecordNumber],
                            firstName = it[Patients.firstName],
                            lastName = it[Patients.lastName],
                            birthDate = it[Patients.birthDate].toString(),
                            admissionDate = it[Patients.admissionDate].toString(),
                            status = it[Patients.status],
                            nurseId = it[Patients.nurseId]
                        )
                    }
                }

                patients.forEach { patient ->
                    if (patient.status == "выписан") return@forEach

                    val patientDevices = transaction {
                        PatientDevices.select { PatientDevices.patientId eq patient.id }.map {
                            PatientDevice(
                                id = it[PatientDevices.id],
                                patientId = it[PatientDevices.patientId],
                                deviceId = it[PatientDevices.deviceId],
                                assignedAt = it[PatientDevices.assignedAt].toString(),
                                settingsText = it[PatientDevices.settingsText]
                            )
                        }
                    }

                    val devices = transaction {
                        Devices.selectAll().map {
                            Device(
                                id = it[Devices.id],
                                serialNumber = it[Devices.serialNumber],
                                type = it[Devices.type],
                                status = it[Devices.status]
                            )
                        }
                    }

                    patientDevices.forEach { patientDevice ->
                        val device = devices.find { it.id == patientDevice.deviceId } ?: return@forEach
                        val metricCodes = getMetricCodes(device.type)
                        val limit = if (metricCodes.contains("heart_rate")) 60 else 12

                        (0 until limit).forEach { i ->
                            val timestamp = Instant.now().minusSeconds(((limit - 1 - i) * (if (metricCodes.contains("heart_rate")) 10 else 300)).toLong())
                            metricCodes.forEach { metricCodee ->
                                val observation = generateObservation(
                                    patientId = patient.id,
                                    deviceId = patientDevice.deviceId,
                                    metricCode = metricCodee,
                                    patientStatus = patient.status,
                                    timestamp = timestamp
                                )
                                if (observation != null) {
                                    transaction {
                                        Observations.insert {
                                            it[patientId] = observation.patientId
                                            it[deviceId] = observation.deviceId
                                            it[metricCode] = observation.metricCode
                                            it[value] = observation.value
                                            it[unit] = observation.unit
                                            it[recordedAt] = Instant.parse(observation.effectiveTimestamp)
                                        }
                                    }
                                    application.log.info("Generated initial observation for patient ${patient.id}, metric $metricCodee")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                application.log.error("Error generating initial observations: ${e.message}", e)
            }
        }
    }
}