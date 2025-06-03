package com.example

import kotlinx.serialization.Serializable


@Serializable
data class NotificationRequest(
    val token: String,
    val title: String,
    val body: String
)

@Serializable
data class FCMMessage(
    val message: Message
)

@Serializable
data class Message(
    val token: String,
    val notification: Notification
)

@Serializable
data class Notification(
    val title: String,
    val body: String
)



// Модель для чтения (GET)
@Serializable
data class Patient(
    val id: Int,
    val medicalRecordNumber: String,
    val firstName: String,
    val lastName: String,
    val birthDate: String,
    val admissionDate: String,
    val status: String,
    val nurseId: Int?
)

// Модель для создания (POST)
@Serializable
data class CreatePatient(
    val medicalRecordNumber: String,
    val firstName: String,
    val lastName: String,
    val birthDate: String,
    val admissionDate: String,
    val status: String,
    val nurseId: Int?
)

// Модель для чтения (GET)
@Serializable
data class Observation(
    val id: Long,
    val patientId: Int,
    val deviceId: Int,
    val metricCode: String,
    val value: Double,
    val unit: String?,
    val effectiveTimestamp: String
)

// Модель для создания (POST)
@Serializable
data class CreateObservation(
    val patientId: Int,
    val deviceId: Int,
    val metricCode: String,
    val value: Double,
    val unit: String?,
    val effectiveTimestamp: String
)

// Модель для чтения (GET)
@Serializable
data class Practitioner(
    val id: Int,
    val login: String,
    val password: String,
    val fullName: String,
    val role: String
)



// Модель для создания (POST /register)
@Serializable
data class CreatePractitioner(
    val login: String,
    val password: String,
    val fullName: String,
    val role: String
)

// Остальные модели
@Serializable
data class Device(
    val id: Int,
    val serialNumber: String,
    val type: String,
    val status: String
)

@Serializable
data class PatientDevice(
    val id: Int? = null,
    val patientId: Int,
    val deviceId: Int,
    val assignedAt: String? = null,
    val settingsText: String? = null
)

@Serializable
data class AlertThreshold(
    val id: Int,
    val patientId: Int,
    val metricCode: String,
    val minValue: Double?,
    val maxValue: Double?
)

@Serializable
data class Alert(
    val id: Long,
    val patientId: Int,
    val metricCode: String,
    val value: Double,
    val message: String,
    val severity: String,
    val triggeredAt: String
)

@Serializable
data class Encounter(
    val id: Int,
    val patientId: Int,
    val encounterType: String,
    val description: String,
    val recordedAt: String,
    val recordedBy: Int
)

@Serializable
data class CurrentObservation(
    val patientId: Int,
    val firstName: String,
    val lastName: String,
    val metricCode: String,
    val value: Double,
    val unit: String?,
    val effectiveTimestamp: String
)