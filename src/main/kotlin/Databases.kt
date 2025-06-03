package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    transaction {
        SchemaUtils.create(
            Patients,
            Devices,
            Practitioners,
            PatientDevices,
            Observations,
            AlertThresholds,
            Alerts,
            Encounters
        )

    }
}

// Определения таблиц остаются без изменений (как в предыдущем ответе)
object Patients : Table("patient") {
    val id = integer("id").autoIncrement()
    val medicalRecordNumber = varchar("medical_record_number", 20).uniqueIndex()
    val firstName = varchar("first_name", 50)
    val lastName = varchar("last_name", 50)
    val birthDate = date("birth_date")
    val admissionDate = timestamp("admission_date")
    val nurseId = integer("nurse_id").references(Practitioners.id).nullable() // Новое поле
    val status = varchar("status", 20).check { it inList listOf("критическое", "стабильное", "выписан") }.default("stable")
    override val primaryKey = PrimaryKey(id)
}

object Devices : Table("device") {
    val id = integer("id").autoIncrement()
    val serialNumber = varchar("serial_number", 50).uniqueIndex()
    val type = varchar("type", 50)
    val status = varchar("status", 20).check { it inList listOf("active", "inactive") }.default("active")
    override val primaryKey = PrimaryKey(id)
}

object Practitioners : Table("practitioner") {
    val id = integer("id").autoIncrement()
    val login = varchar("login", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val fullName = varchar("full_name", 100)
    val role = varchar("role", 20).check { it inList listOf("doctor", "nurse") }
    override val primaryKey = PrimaryKey(id)
}


object PatientDevices : Table("patient_device") {
    val id = integer("id").autoIncrement()
    val patientId = integer("patient_id").references(Patients.id)
    val deviceId = integer("device_id").references(Devices.id)
    val assignedAt = timestamp("assigned_at").defaultExpression(CurrentTimestamp())
    val settingsText = varchar("settings_text", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Observations : Table("observation") {
    val id = long("id").autoIncrement()
    val patientId = integer("patient_id").references(Patients.id)
    val deviceId = integer("device_id").references(Devices.id)
    val metricCode = varchar("metric_code", 50)
    val value = double("value")
    val unit = varchar("unit", 20).nullable()
    val recordedAt = timestamp("recorded_at").defaultExpression(CurrentTimestamp())
    override val primaryKey = PrimaryKey(id)
}

object AlertThresholds : Table("alert_threshold") {
    val id = integer("id").autoIncrement()
    val patientId = integer("patient_id").references(Patients.id)
    val metricCode = varchar("metric_code", 50)
    val minValue = double("min_value").nullable()
    val maxValue = double("max_value").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Alerts : Table("alert") {
    val id = long("id").autoIncrement()
    val patientId = integer("patient_id").references(Patients.id)
    val metricCode = varchar("metric_code", 50)
    val value = double("value")
    val message = text("message")
    val severity = varchar("severity", 20).check { it inList listOf("low", "high", "critical") }
    val triggeredAt = timestamp("triggered_at").defaultExpression(CurrentTimestamp())
    override val primaryKey = PrimaryKey(id)
}

object Encounters : Table("encounter") {
    val id = integer("id").autoIncrement()
    val patientId = integer("patient_id").references(Patients.id)
    val encounterType = varchar("encounter_type", 50)
    val description = text("description")
    val recordedAt = timestamp("recorded_at").defaultExpression(CurrentTimestamp())
    val recordedBy = integer("recorded_by").references(Practitioners.id)
    override val primaryKey = PrimaryKey(id)
}