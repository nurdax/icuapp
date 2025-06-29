package com.example

import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.format.DateTimeFormatter // Для форматирования времени
import java.time.Duration
import java.util.* // Для генерации случайных чисел
import kotlin.math.max
import kotlin.math.min

object MonitoringGenerator {
    private val random = Random()
    private val formatter = DateTimeFormatter.ISO_INSTANT
    private val normalRanges = mapOf( // Нормальные диапазоны для медицинских метрик
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

    private val anomalyRanges = mapOf( // Диапазоны для аномальных значений
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
    // Максимальные изменения значений за интервал
    private val maxChangesPerInterval = mapOf(
        "spo2" to 1f, // SpO2: максимальное изменение 1%
        "pulse" to 5f, // Пульс: максимальное изменение 5 уд/мин
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
    // Вероятность генерации аномалии (10%)
    private const val ANOMALY_PROBABILITY = 0.1f
    private val lastValues = mutableMapOf<Triple<Int, Int, String>, Float>() // Кэш последних значений для каждой метрики
    private val lastGeneratedFormattedValues = mutableMapOf<Triple<Int, Int, String>, Double>()
    // Кэш последних отформатированных значений
    private val lastGenerationTime = mutableMapOf<Triple<Int, Int, String>, Instant>()
    
    private fun getMetricCodes(deviceType: String): List<String> { // Возвращает список кодов метрик для типа устройства
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

    private fun generateObservation( // Генерирует данные для метрики
        patientId: Int,
        deviceId: Int,
        metricCode: String,
        patientStatus: String,
        timestamp: Instant = Instant.now()
    ): Observation? {
        // Если пациент выписан, не генерируем данные
        if (patientStatus == "выписан") return null
        // Уникальный ключ для пациента, устройства и метрики
        val key = Triple(patientId, deviceId, metricCode)
        // Интервал генерации: 5 сек для ЧСС, 15 сек для остальных
        val requiredIntervalMillis = if (metricCode == "heart_rate") 5_000L else 15_000L
        // Проверяем, прошло ли достаточно времени с последней генерации
        val lastTime = lastGenerationTime[key]
        if (lastTime != null) {
            val elapsed = Duration.between(lastTime, timestamp).toMillis()
            if (elapsed < requiredIntervalMillis) {
                return null
            }
        }
        lastGenerationTime[key] = timestamp
        // Определяем, критическое ли состояние пациента
        val isCritical = patientStatus == "критическое"
        // Выбираем диапазон: аномальный или нормальный
        val range = if (isCritical && random.nextFloat() < ANOMALY_PROBABILITY) {
            anomalyRanges[metricCode] ?: normalRanges.getOrDefault(metricCode, 0f..0f)
        } else {
            normalRanges.getOrDefault(metricCode, 0f..100f)
        }
        // Получаем последнее значение или среднее диапазона
        val lastCalculatedValue = lastValues[key] ?: ((range.start + range.endInclusive) / 2f)
        val maxChange = maxChangesPerInterval[metricCode] ?: 5f
        val adjustedMaxChange = if (metricCode == "heart_rate") maxChange else maxChange / 5f
        // Генерируем случайное изменение
        val change = (random.nextFloat() * 2 - 1) * adjustedMaxChange
        var newCalculatedValue = lastCalculatedValue + change

        newCalculatedValue = max(range.start, min(range.endInclusive, newCalculatedValue))
        lastValues[key] = newCalculatedValue

        val formattedFloatValue = if (metricCode == "temperature") {
            String.format(Locale.US, "%.1f", newCalculatedValue).toFloat()
        } else {
            newCalculatedValue.toInt().toFloat()
        }
        val formattedDoubleValue = formattedFloatValue.toDouble()

        if (lastGeneratedFormattedValues[key] == formattedDoubleValue) {
            return null
        }

        lastGeneratedFormattedValues[key] = formattedDoubleValue
        // Определяем единицу измерения
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
        // Возвращаем объект наблюдения 
        return Observation(
            id = 0,
            patientId = patientId,
            deviceId = deviceId,
            metricCode = metricCode,
            value = formattedDoubleValue,
            unit = unit,
            effectiveTimestamp = formatter.format(timestamp)
        )
    }
    // Запускает процесс мониторинга
    fun startMonitoring(
        application: Application,
        observationFlow: MutableSharedFlow<Observation>,
        sendAlertNotification: suspend (patientId: Int, metricCode: String, alertValue: Double, alertMessage: String, severity: String) -> Unit
    ) {
        application.launch(Dispatchers.IO) { // Запускаем корутину в фоновом потоке
            while (isActive) { // Бесконечный цикл, пока активен
                try {
                    val patients = transaction { // Получаем список пациентов из бд
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
                    // Обрабатываем каждого пациента
                    patients.forEach { patient ->
                        // Пропускаем выписанных
                        if (patient.status == "выписан") return@forEach
                        // Получаем устройства пациента
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
                        // Обрабатываем каждое устройство пациента
                        patientDevices.forEach { patientDevice ->
                            val device = devices.find { it.id == patientDevice.deviceId } ?: return@forEach
                            val metricCodes = getMetricCodes(device.type)
                            // Генерируем данные для каждой метрики
                            metricCodes.forEach { metricCodee ->
                                val observation = generateObservation(
                                    patientId = patient.id,
                                    deviceId = patientDevice.deviceId,
                                    metricCode = metricCodee,
                                    patientStatus = patient.status,
                                    timestamp = Instant.now()
                                )
                                if (observation != null) {
                                    // Сохраняем наблюдение в базу
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
                                    application.log.info("Generated observation for patient ${patient.id}, metric ${metricCodee}, timestamp ${observation.effectiveTimestamp}, value ${observation.value}")
                                    // Отправляем в поток
                                    observationFlow.emit(observation)

                                    // Проверяем пороги оповещений
                                    val alertThreshold: AlertThreshold? = transaction {
                                        AlertThresholds.select {
                                            (AlertThresholds.patientId eq observation.patientId) and
                                                    (AlertThresholds.metricCode eq observation.metricCode)
                                        }.singleOrNull()?.let { row ->
                                            // Здесь мы явно сопоставляем ResultRow с экземпляром класса данных AlertThreshold
                                            AlertThreshold(
                                                id = row[AlertThresholds.id],
                                                patientId = row[AlertThresholds.patientId],
                                                metricCode = row[AlertThresholds.metricCode],
                                                minValue = row[AlertThresholds.minValue],
                                                maxValue = row[AlertThresholds.maxValue]  
                                            )
                                        }
                                    }
                                    // Проверяем, превышает ли значение пороги
                                    if (alertThreshold != null) {
                                        var alertMessage: String? = null
                                        var severity: String = "INFO"

                                        val value = observation.value 
                                        // Проверка минимального порога
                                        alertThreshold.minValue?.let { minVal ->
                                            if (minVal > value) {
                                                alertMessage = when (observation.metricCode) {
                                                    "heart_rate" -> "Брадикардия: ${value} ${observation.unit} (ниже порога ${minVal})"
                                                    else -> "${observation.metricCode.replace("_", " ").capitalize()} значение ${value} ${observation.unit} ниже критического порога ${minVal}"
                                                }
                                                severity = "HIGH"
                                            }
                                        }
                                        // Проверка максимального порога
                                        alertThreshold.maxValue?.let { maxVal ->
                                            if (maxVal < value) {
                                                if (alertMessage == null) {
                                                    alertMessage = when (observation.metricCode) {
                                                        "heart_rate" -> "Тахикардия: ${value} ${observation.unit} (выше порога ${maxVal})"
                                                        else -> "${observation.metricCode.replace("_", " ").capitalize()} значение ${value} ${observation.unit} выше критического порога ${maxVal}"
                                                    }
                                                    severity = "HIGH"
                                                } else {
                                                    alertMessage += ". Также выше критического порога ${maxVal}"
                                                    severity = "CRITICAL"
                                                }
                                            }
                                        }
                                        // Если есть alert, создаем оповещение
                                        if (alertMessage != null) {
                                            val newAlertId = transaction {
                                                Alerts.insert {
                                                    it[patientId] = observation.patientId
                                                    it[metricCode] = observation.metricCode
                                                    it[Alerts.value] = observation.value
                                                    it[message] = alertMessage!!
                                                    it[Alerts.severity] = severity
                                                    it[triggeredAt] = Instant.now()
                                                } get Alerts.id
                                            }
                                            application.log.warn("Оповещение, сгенерированное для пациента ${observation.patientId}, метрикак ${observation.metricCode}: $alertMessage (ID: $newAlertId)")
                                            
                                            // Отправляем уведомление
                                            sendAlertNotification(
                                                observation.patientId,
                                                observation.metricCode,
                                                observation.value,
                                                alertMessage!!,
                                                severity
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    application.log.error("Error generating observations or alerts: ${e.message}", e)
                }
                // Задержка 1 сек перед следующей итерацией
                delay(1_000)
            }
        }
    }
}