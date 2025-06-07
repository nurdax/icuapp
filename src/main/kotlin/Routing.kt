package com.example

import com.google.auth.oauth2.GoogleCredentials
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException



import kotlinx.serialization.json.Json



fun Application.configureRouting() {
    val client = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    val projectId = "diplom-e6c8f" // Replace with your Firebase project ID
    val fcmEndpoint = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
    routing {
        // Пациенты
        get("/patients") {
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
            call.respond(patients)
        }

        get("/patients/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                "Invalid patient ID",
                status = HttpStatusCode.BadRequest
            )
            val patient = transaction {
                Patients.selectAll().where { Patients.id eq id }
                    .map {
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
                    .singleOrNull()
            }
            if (patient != null) {
                call.respond(patient)
            } else {
                call.respondText("Patient not found", status = HttpStatusCode.NotFound)
            }
        }

        get("/nurse_patients/{nurseId}") {
            val nurseId = call.parameters["nurseId"]?.toIntOrNull() ?: return@get call.respondText(
                "Invalid nurse ID",
                status = HttpStatusCode.BadRequest
            )
            val patients = transaction {
                Patients.select { Patients.nurseId eq nurseId }.map {
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
            call.respond(patients)
        }

        post("/patients") {
            try {
                val createPatient = call.receive<CreatePatient>()
                val birthDatee = try {
                    LocalDate.parse(createPatient.birthDate)
                } catch (e: DateTimeParseException) {
                    call.respondText("Invalid birthDate format: ${e.message}", status = HttpStatusCode.BadRequest)
                    return@post
                }
                val admissionDatee = try {
                    Instant.parse(createPatient.admissionDate)
                } catch (e: DateTimeParseException) {
                    call.respondText("Invalid admissionDate format: ${e.message}", status = HttpStatusCode.BadRequest)
                    return@post
                }
                val id = transaction {
                    Patients.insert {
                        it[medicalRecordNumber] = createPatient.medicalRecordNumber
                        it[firstName] = createPatient.firstName
                        it[lastName] = createPatient.lastName
                        it[birthDate] = birthDatee
                        it[admissionDate] = admissionDatee
                        it[status] = createPatient.status
                        it[nurseId] = createPatient.nurseId
                    } get Patients.id
                }
                call.respondText("Patient created with ID $id", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.application.log.error("Error creating patient: ${e.message}", e)
                call.respondText("Failed to create patient: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        // Новый маршрут: Обновление пациента
        put("/patients/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respondText(
                    "Invalid patient ID",
                    status = HttpStatusCode.BadRequest
                )
                val createPatient = call.receive<CreatePatient>()
                val birthDatee = try {
                    LocalDate.parse(createPatient.birthDate)
                } catch (e: DateTimeParseException) {
                    call.respondText("Invalid birthDate format: ${e.message}", status = HttpStatusCode.BadRequest)
                    return@put
                }
                val admissionDatee = try {
                    Instant.parse(createPatient.admissionDate)
                } catch (e: DateTimeParseException) {
                    call.respondText("Invalid admissionDate format: ${e.message}", status = HttpStatusCode.BadRequest)
                    return@put
                }
                val updatedRows = transaction {
                    Patients.update({ Patients.id eq id }) {
                        it[medicalRecordNumber] = createPatient.medicalRecordNumber
                        it[firstName] = createPatient.firstName
                        it[lastName] = createPatient.lastName
                        it[birthDate] = birthDatee
                        it[admissionDate] = admissionDatee
                        it[status] = createPatient.status
                        it[nurseId] = createPatient.nurseId
                    }
                }
                if (updatedRows > 0) {
                    call.respondText("Patient with ID $id updated", status = HttpStatusCode.OK)
                } else {
                    call.respondText("Patient with ID $id not found", status = HttpStatusCode.NotFound)
                }
            } catch (e: Exception) {
                call.application.log.error("Error updating patient: ${e.message}", e)
                call.respondText("Failed to update patient: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }


        patch("/patients/{id}/status") {
            try {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@patch call.respondText(
                    "Invalid patient ID",
                    status = HttpStatusCode.BadRequest
                )
                val statusUpdate = call.receive<StatusUpdate>()

                // Валидация статуса
                if (statusUpdate.status !in listOf("критическое", "стабильное", "выписан")) {
                    return@patch call.respondText(
                        "Invalid status: must be one of [критическое, стабильное, выписан]",
                        status = HttpStatusCode.BadRequest
                    )
                }

                val updatedRows = transaction {
                    Patients.update({ Patients.id eq id }) {
                        it[status] = statusUpdate.status
                    }
                }

                if (updatedRows > 0) {
                    call.respondText("Patient status updated to ${statusUpdate.status}", status = HttpStatusCode.OK)
                } else {
                    call.respondText("Patient with ID $id not found", status = HttpStatusCode.NotFound)
                }
            } catch (e: Exception) {
                call.application.log.error("Error updating patient status: ${e.message}", e)
                call.respondText("Failed to update patient status: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        // Новый маршрут: Назначение устройства пациенту
        post("/patient_device") {
            try {
                val patientDevice = call.receive<PatientDevice>()
                // Проверка существования пациента и устройства
                val patientExists = transaction {
                    Patients.select { Patients.id eq patientDevice.patientId }.count() > 0
                }
                val deviceExists = transaction {
                    Devices.select { Devices.id eq patientDevice.deviceId }.count() > 0
                }
                if (!patientExists) {
                    call.respondText("Patient not found", status = HttpStatusCode.BadRequest)
                    return@post
                }
                if (!deviceExists) {
                    call.respondText("Device not found", status = HttpStatusCode.BadRequest)
                    return@post
                }
                val id = transaction {
                    PatientDevices.insert {
                        it[patientId] = patientDevice.patientId
                        it[deviceId] = patientDevice.deviceId
                        it[settingsText] = patientDevice.settingsText
                    } get PatientDevices.id
                }
                call.respondText("Patient device assigned with ID $id", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.application.log.error("Error assigning patient device: ${e.message}", e)
                call.respondText("Failed to assign patient device: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        // Новый маршрут: Получение назначенных устройств
        get("/patient_device") {
            val patientId = call.request.queryParameters["patient_id"]?.toIntOrNull()
            val deviceId = call.request.queryParameters["device_id"]?.toIntOrNull()
            val patientDevices = transaction {
                PatientDevices.selectAll().where {
                    buildList {
                        if (patientId != null) add(PatientDevices.patientId eq patientId)
                        if (deviceId != null) add(PatientDevices.deviceId eq deviceId)
                    }.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
                }.map {
                    PatientDevice(
                        id = it[PatientDevices.id],
                        patientId = it[PatientDevices.patientId],
                        deviceId = it[PatientDevices.deviceId],
                        assignedAt = it[PatientDevices.assignedAt].toString(),
                        settingsText = it[PatientDevices.settingsText]
                    )
                }
            }
            call.respond(patientDevices)
        }

        // Устройства
        get("/devices") {
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
            call.respond(devices)
        }

        post("/devices") {
            try {
                val device = call.receive<Device>()
                val id = transaction {
                    Devices.insert {
                        it[serialNumber] = device.serialNumber
                        it[type] = device.type
                        it[status] = device.status
                    } get Devices.id
                }
                call.respondText("Device created with ID $id", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.application.log.error("Error creating device: ${e.message}", e)
                call.respondText("Failed to create device: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        // Мониторинг
        get("/observations") {
            val patientId = call.request.queryParameters["patient_id"]?.toIntOrNull()
            val metricCode = call.request.queryParameters["metric_code"]
            val observations = transaction {
                Observations.selectAll().where {
                    buildList {
                        if (patientId != null) add(Observations.patientId eq patientId)
                        if (metricCode != null) add(Observations.metricCode eq metricCode)
                    }.reduce { acc, op -> acc and op }
                }.map {
                    Observation(
                        id = it[Observations.id],
                        patientId = it[Observations.patientId],
                        deviceId = it[Observations.deviceId],
                        metricCode = it[Observations.metricCode],
                        value = it[Observations.value],
                        unit = it[Observations.unit],
                        effectiveTimestamp = it[Observations.recordedAt].toString()
                    )
                }
            }
            call.respond(observations)
        }

        post("/observations") {
            try {
                val createObservation = call.receive<CreateObservation>()
                val effectiveTimestamp = try {
                    Instant.parse(createObservation.effectiveTimestamp)
                } catch (e: DateTimeParseException) {
                    call.respondText(
                        "Invalid effectiveTimestamp format: ${e.message}",
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }
                val id = transaction {
                    Observations.insert {
                        it[patientId] = createObservation.patientId
                        it[deviceId] = createObservation.deviceId
                        it[metricCode] = createObservation.metricCode
                        it[value] = createObservation.value
                        it[unit] = createObservation.unit
                        it[recordedAt] = effectiveTimestamp
                    } get Observations.id
                }
                call.respondText("Observation created with ID $id", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.application.log.error("Error creating observation: ${e.message}", e)
                call.respondText("Failed to create observation: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        // Уведомления
        get("/alerts") {
            val patientId = call.request.queryParameters["patient_id"]?.toIntOrNull()
            val alerts = transaction {
                Alerts.selectAll().where { patientId?.let { Alerts.patientId eq it } ?: Op.TRUE }
                    .map {
                        Alert(
                            id = it[Alerts.id],
                            patientId = it[Alerts.patientId],
                            metricCode = it[Alerts.metricCode],
                            value = it[Alerts.value],
                            message = it[Alerts.message],
                            severity = it[Alerts.severity],
                            triggeredAt = it[Alerts.triggeredAt].toString()
                        )
                    }
            }
            call.respond(alerts)
        }

        post("/alerts") {
            try {
                val alert = call.receive<Alert>()
                val id = transaction {
                    Alerts.insert {
                        it[patientId] = alert.patientId
                        it[metricCode] = alert.metricCode
                        it[value] = alert.value
                        it[message] = alert.message
                        it[severity] = alert.severity
                        it[triggeredAt] = Instant.parse(alert.triggeredAt)
                    } get Alerts.id
                }
                call.respondText("Alert created with ID $id", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.application.log.error("Error creating alert: ${e.message}", e)
                call.respondText("Failed to create alert: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        // Пороговые значения
        get("/alert_thresholds") {
            val patientId = call.request.queryParameters["patient_id"]?.toIntOrNull()
            val thresholds = transaction {
                AlertThresholds.selectAll().where { patientId?.let { AlertThresholds.patientId eq it } ?: Op.TRUE }
                    .map {
                        AlertThreshold(
                            id = it[AlertThresholds.id],
                            patientId = it[AlertThresholds.patientId],
                            metricCode = it[AlertThresholds.metricCode],
                            minValue = it[AlertThresholds.minValue],
                            maxValue = it[AlertThresholds.maxValue]
                        )
                    }
            }
            call.respond(thresholds)
        }

        post("/alert_thresholds") {
            try {
                val threshold = call.receive<AlertThreshold>()
                val id = transaction {
                    AlertThresholds.insert {
                        it[patientId] = threshold.patientId
                        it[metricCode] = threshold.metricCode
                        it[minValue] = threshold.minValue
                        it[maxValue] = threshold.maxValue
                    } get AlertThresholds.id
                }
                call.respondText("Alert threshold created with ID $id", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.application.log.error("Error creating alert threshold: ${e.message}", e)
                call.respondText("Failed to create alert threshold: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        // Медицинские записи
        get("/encounters") {
            val patientId = call.request.queryParameters["patient_id"]?.toIntOrNull()
            val encounters = transaction {
                Encounters.selectAll().where { patientId?.let { Encounters.patientId eq it } ?: Op.TRUE }
                    .map {
                        Encounter(
                            id = it[Encounters.id],
                            patientId = it[Encounters.patientId],
                            encounterType = it[Encounters.encounterType],
                            description = it[Encounters.description],
                            recordedAt = it[Encounters.recordedAt].toString(),
                            recordedBy = it[Encounters.recordedBy]
                        )
                    }
            }
            call.respond(encounters)
        }

        post("/encounters") {
            try {
                val encounter = call.receive<Encounter>()
                val id = transaction {
                    Encounters.insert {
                        it[patientId] = encounter.patientId
                        it[encounterType] = encounter.encounterType
                        it[description] = encounter.description
                        it[recordedAt] = Instant.parse(encounter.recordedAt)
                        it[recordedBy] = encounter.recordedBy
                    } get Encounters.id
                }
                call.respondText("Encounter created with ID $id", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.application.log.error("Error creating encounter: ${e.message}", e)
                call.respondText("Failed to create encounter: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        post("/login") {
            val practitioner = call.receive<Practitioner>()
            val user = transaction {
                Practitioners.select { Practitioners.login eq practitioner.login }
                    .map {
                        Practitioner(
                            id = it[Practitioners.id],
                            login = it[Practitioners.login],
                            password = it[Practitioners.passwordHash],
                            fullName = it[Practitioners.fullName],
                            role = it[Practitioners.role]
                        )
                    }
                    .singleOrNull()
            }
            if (user != null && user.password == practitioner.password) { // В реальном проекте используйте bcrypt
                call.respond(user)
            } else {
                call.respondText("Invalid login or password", status = HttpStatusCode.Unauthorized)
            }
        }
        // Новый маршрут: Регистрация пользователя
        post("/register") {
            try {
                val createPractitioner = call.receive<CreatePractitioner>()
                if (createPractitioner.role !in listOf("doctor", "nurse")) {
                    call.respondText("Invalid role: must be 'doctor' or 'nurse'", status = HttpStatusCode.BadRequest)
                    return@post
                }
                val id = transaction {
                    Practitioners.insert {
                        it[login] = createPractitioner.login
                        it[passwordHash] = createPractitioner.password
                        it[fullName] = createPractitioner.fullName
                        it[role] = createPractitioner.role
                    } get Practitioners.id
                }
                call.respond(
                    Practitioner(
                        id = id,
                        login = createPractitioner.login,
                        password = "",
                        fullName = createPractitioner.fullName,
                        role = createPractitioner.role
                    )
                )
            } catch (e: Exception) {
                call.application.log.error("Error creating practitioner: ${e.message}", e)
                call.respondText("Failed to create practitioner: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        post("/send-nurse-notification") {
            try {
                val request = call.receive<NotificationRequest>()
                if (request.token.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Токен устройства не может быть пустым")
                    )
                    return@post
                }
                if (request.token.length < 50) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Токен устройства выглядит недействительным (слишком короткий)")
                    )
                    return@post
                }
                val accessToken = getAccessToken()
                val fcmMessage = FCMMessage(
                    message = Message(
                        token = request.token,
                        notification = Notification(
                            title = request.title,
                            body = request.body
                        )
                    )
                )
                val response = client.post(fcmEndpoint) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    setBody(Json.encodeToString(FCMMessage.serializer(), fcmMessage))
                }
                if (response.status == HttpStatusCode.OK) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Уведомление успешно отправлено"))
                } else {
                    val errorBody = response.bodyAsText()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Не удалось отправить уведомление: ${response.status}, детали: $errorBody")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Ошибка при отправке уведомления: ${e.message}")
                )
            }
        }
    }


}





suspend fun getAccessToken(): String {
    val credentials = GoogleCredentials
        .fromStream(FileInputStream("service_account_key.json"))
        .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
    credentials.refreshIfExpired()
    return credentials.accessToken.tokenValue
}
