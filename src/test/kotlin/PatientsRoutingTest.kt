package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate


class PatientsRoutingTest : FunSpec({
    // Настройка тестовой среды
    fun ApplicationTestBuilder.setupTestApplication() {
        application {
            configureDatabases()
            configureRouting()
        }
    }

    // Инициализация базы данных перед каждым тестом
    beforeTest {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Patients)
        }
    }

    // Очистка базы данных после каждого теста
    afterTest {
        transaction {
            SchemaUtils.drop(Patients)
        }
    }

    test("GET /patients возвращает пустой список, если пациентов нет") {
        testApplication {
            setupTestApplication()
            val response = client.get("/patients")
            response.status shouldBe HttpStatusCode.OK
            val patients = Json.decodeFromString<List<Patient>>(response.bodyAsText())
            patients shouldBe emptyList()
        }
    }

    test("GET /patients возвращает список пациентов") {
        testApplication {
            setupTestApplication()
            // Добавляем тестового пациента
            transaction {
                Patients.insert {
                    it[medicalRecordNumber] = "MRN123"
                    it[firstName] = "Иван"
                    it[lastName] = "Иванов"
                    it[birthDate] = LocalDate.of(1990, 1, 1)
                    it[admissionDate] = Instant.now()
                    it[status] = "стабильное"
                    it[nurseId] = null
                }
            }

            val response = client.get("/patients")
            response.status shouldBe HttpStatusCode.OK
            val patients = Json.decodeFromString<List<Patient>>(response.bodyAsText())
            patients.size shouldBe 1
            with(patients[0]) {
                medicalRecordNumber shouldBe "MRN123"
                firstName shouldBe "Иван"
                lastName shouldBe "Иванов"
                status shouldBe "стабильное"
                nurseId shouldBe null
            }
        }
    }

    test("GET /patients/{id} возвращает пациента по ID") {
        testApplication {
            setupTestApplication()
            // Добавляем тестового пациента
            val patientId = transaction {
                Patients.insert {
                    it[medicalRecordNumber] = "MRN456"
                    it[firstName] = "Петр"
                    it[lastName] = "Петров"
                    it[birthDate] = LocalDate.of(1985, 5, 15)
                    it[admissionDate] = Instant.now()
                    it[status] = "критическое"
                    it[nurseId] = 1
                } get Patients.id
            }

            val response = client.get("/patients/$patientId")
            response.status shouldBe HttpStatusCode.OK
            val patient = Json.decodeFromString<Patient>(response.bodyAsText())
            with(patient) {
                id shouldBe patientId
                medicalRecordNumber shouldBe "MRN456"
                firstName shouldBe "Петр"
                lastName shouldBe "Петров"
                status shouldBe "критическое"
                nurseId shouldBe 1
            }
        }
    }

    test("GET /patients/{id} возвращает 404 для несуществующего пациента") {
        testApplication {
            setupTestApplication()
            val response = client.get("/patients/999")
            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText() shouldBe "Patient not found"
        }
    }

    test("GET /patients/{id} возвращает 400 для некорректного ID") {
        testApplication {
            setupTestApplication()
            val response = client.get("/patients/invalid")
            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldBe "Invalid patient ID"
        }
    }
})