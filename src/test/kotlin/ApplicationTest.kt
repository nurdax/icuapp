package com.example


class ApplicationTest {
/*
    private fun ApplicationTestBuilder.setupTestApplication() {
        environment {
            config = MapApplicationConfig(
                "ktor.environment" to "test",
                "jwt.audience" to "jwt-audience",
                "jwt.domain" to "https://jwt-provider-domain/",
                "jwt.realm" to "diplom",
                "jwt.secret" to "test-secret",
                "postgres.url" to "jdbc:postgresql://localhost:5432/govno_db",
                "postgres.driver" to "org.postgresql.Driver",
                "postgres.user" to "postgres",
                "postgres.password" to "2025"
            )
        }
        application {
            configureDatabases()
            configureSerialization()
            configureSecurity()
            configureRouting()
        }
    }

    private fun setupTestDatabase() {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/govno_db",
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "2025"
        )
      /*  transaction {
            // Очистка всех таблиц
            MonitoringDataTable.deleteAll()
            Operations.deleteAll()
            PatientExtracranialInjuries.deleteAll()
            PatientPrimaryInjuries.deleteAll()
            PatientSecondaryInjuries.deleteAll()
            PatientAnamnesis.deleteAll()
            PatientDiseaseComplications.deleteAll()
            PatientDiagnoses.deleteAll()
            PatientMedicalHistory.deleteAll()
            Patients.deleteAll()
            Doctors.deleteAll()
            ExtracranialInjuries.deleteAll()
            SecondaryInjuries.deleteAll()
            PrimaryInjuries.deleteAll()
            DiseaseComplications.deleteAll()
            Diagnoses.deleteAll()
            Anamnesis.deleteAll()
            MonitoringParameters.deleteAll()
            Specializations.deleteAll()
            Users.deleteAll()
            println("Users count after reset: ${Users.selectAll().count()}")
            println("Specializations count after reset: ${Specializations.selectAll().count()}")
            println("Patients count after reset: ${Patients.selectAll().count()}")
        }*/
    }

    private fun generateTestToken(userId: Int, role: String): String {
        return JWT.create()
            .withAudience("jwt-audience")
            .withIssuer("https://jwt-provider-domain/")
            .withClaim("userId", userId)
            .withClaim("role", role)
            .sign(Algorithm.HMAC256("test-secret"))
    }

    @BeforeTest
    fun setupMocks() {
        mockkStatic("com.example.ModelsKt")
        every { hashPassword(any()) } returns "mocked_hash"
        every { verifyPassword(any(), any()) } returns true
    }

    @Test
    fun testRegisterAndLogin() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Register a doctor
        val uniqueLogin = "doctor_${System.currentTimeMillis()}"
        val specializationId = transaction {
            Specializations.insert {
                it[name] = "General Medicine"
                it[createdAt] = "2025-05-20T12:00:00"
            } get Specializations.id
        }
        val registerRequest = RegisterUser(
            login = uniqueLogin,
            password = "pass123",
            role = "DOCTOR",
            fullName = "Dr. John Doe",
            contactInfo = "qotaq@example.com",
            specializationId = specializationId
        )
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterUser.serializer(), registerRequest))
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
            assertEquals("User registered successfully", bodyAsText())
        }

        // Login with the registered doctor
        val loginRequest = LoginRequest(login = uniqueLogin, password = "pass123")
        client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest.serializer(), loginRequest))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = Json.decodeFromString<Map<String, String>>(bodyAsText())
            assertNotNull(response["token"])
        }
    }


    @Test
    fun testRegisterAndLoginAsPatient() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Register a doctor
        val uniqueLogin = "patient_${System.currentTimeMillis()}"
        val registerRequest = RegisterUser(
            login = uniqueLogin,
            password = "pass123",
            role = "PATIENT",
            fullName = "Patient John Doe",
            contactInfo = "qotaqbaspatient@example.com"
        )
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterUser.serializer(), registerRequest))
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
            assertEquals("User registered successfully", bodyAsText())
        }

        // Login with the registered doctor
        val loginRequest = LoginRequest(login = uniqueLogin, password = "pass123")
        client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest.serializer(), loginRequest))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = Json.decodeFromString<Map<String, String>>(bodyAsText())
            assertNotNull(response["token"])
        }
    }


    @Test
    fun testGetUsersAsDoctor() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Register a doctor
        val specializationId = transaction {
            Specializations.insert {
                it[name] = "General Medicine"
                it[createdAt] = "2025-05-20T12:00:00"
            } get Specializations.id
        }
        val userId = transaction {
            Users.insert {
                it[login] = "doctor_${System.currentTimeMillis()}"
                it[passwordHash] = "mocked_hash"
                it[role] = "DOCTOR"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            } get Users.id
        }
        transaction {
            Doctors.insert {
                it[Doctors.userId] = userId
                it[fullName] = "Dr. John Doe"
                it[contactInfo] = "qotaq@example.com"
                it[this.specializationId] = Doctors.specializationId
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            }
        }
        println("Users count before /users: ${Users.selectAll().count()}")

        // Test /users endpoint
        val token = generateTestToken(userId, "DOCTOR")
        client.get("/users") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val users = Json.decodeFromString<List<User>>(bodyAsText())
            assertEquals(1, users.size)
            assertEquals("DOCTOR", users[0].role)
        }
    }

    @Test
    fun testGetUsersAsPatient() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Register a patient
        val userId = transaction {
            Users.insert {
                it[login] = "patient_${System.currentTimeMillis()}"
                it[passwordHash] = "mocked_hash"
                it[role] = "PATIENT"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            } get Users.id
        }
        transaction {
            Patients.insert {
                it[Patients.userId] = userId
                it[fullName] = "Jane Doe"
                it[contactInfo] = "jane2@example.com"
                it[dateOfBirth] = "1990-01-01"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            }
        }

        // Test /users endpoint (should be forbidden)
        val token = generateTestToken(userId, "PATIENT")
        client.get("/users") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.apply {
            assertEquals(HttpStatusCode.Forbidden, status)
            assertEquals("Only doctors can access user list", bodyAsText())
        }
    }

    @Test
    fun testGetSpecializations() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Insert a specialization
        transaction {
            Specializations.insert {
                it[name] = "Cardiology"
                it[createdAt] = "2025-05-20T12:00:00"
            }
            println("Specializations count after insert: ${Specializations.selectAll().count()}")
        }

        // Test /specializations endpoint
        val token = generateTestToken(1, "DOCTOR")
        client.get("/specializations") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val specializations = Json.decodeFromString<List<Specialization>>(bodyAsText())
            assertEquals(1, specializations.size)
            assertEquals("Cardiology", specializations[0].name)
        }
    }

    @Test
    fun testGetPatientsAsDoctor() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Register a patient
        val userId = transaction {
            Users.insert {
                it[login] = "patient_${System.currentTimeMillis()}"
                it[passwordHash] = "mocked_hash"
                it[role] = "PATIENT"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            } get Users.id
        }
        transaction {
            Patients.insert {
                it[Patients.userId] = userId
                it[fullName] = "Jane Doe"
                it[contactInfo] = "jane2@example.com"
                it[dateOfBirth] = "1990-01-01"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            }
            println("Patients count after insert: ${Patients.selectAll().count()}")
        }

        // Test /patients endpoint
        val token = generateTestToken(1, "DOCTOR")
        client.get("/patients") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val patients = Json.decodeFromString<List<Patient>>(bodyAsText())
            assertEquals(1, patients.size)
            assertEquals("Jane Doe", patients[0].fullName)
        }
    }

    @Test
    fun testAddAndGetPatientDiagnosis() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Register a patient
        val userId = transaction {
            Users.insert {
                it[login] = "patient_${System.currentTimeMillis()}"
                it[passwordHash] = "mocked_hash"
                it[role] = "PATIENT"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            } get Users.id
        }
        val patientId = transaction {
            Patients.insert {
                it[Patients.userId] = userId
                it[fullName] = "Jane Doe"
                it[contactInfo] = "jane2@example.com"
                it[dateOfBirth] = "1990-01-01"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            } get Patients.id
            println("Patient ID: $patientId, Patients count: ${Patients.selectAll().count()}")
        }

        // Insert a diagnosis
        transaction {
            Diagnoses.insert {
                it[code] = "D001"
                it[name] = "Hypertension"
                it[createdAt] = "2025-05-20T12:00:00"
            }
            println("Diagnoses count: ${Diagnoses.selectAll().count()}")
        }

        // Add a diagnosis
        val diagnosis = PatientDiagnosis(
            id = 0,
            patientId = 222,
            diagnosisCode = "D001",
            diagnosisDate = "2025-05-20",
            createdAt = "2025-05-20T12:00:00"
        )
        val token = generateTestToken(1, "DOCTOR")
        client.post("/patients/$patientId/diagnoses") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PatientDiagnosis.serializer(), diagnosis))
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }

        // Get diagnoses
        client.get("/patients/$patientId/diagnoses") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val diagnoses = Json.decodeFromString<List<PatientDiagnosis>>(bodyAsText())
            assertEquals(1, diagnoses.size)
            assertEquals("D001", diagnoses[0].diagnosisCode)
        }
    }

    @Test
    fun testAddAndGetMonitoringData() = testApplication {
        setupTestApplication()
        setupTestDatabase()

        // Register a patient
        val userId = transaction {
            Users.insert {
                it[login] = "patient_${System.currentTimeMillis()}"
                it[passwordHash] = "mocked_hash"
                it[role] = "PATIENT"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            } get Users.id
        }
        val patientId = transaction {
            Patients.insert {
                it[Patients.userId] = userId
                it[fullName] = "Jane Doe"
                it[contactInfo] = "jane2@example.com"
                it[dateOfBirth] = "1990-01-01"
                it[createdAt] = "2025-05-20T12:00:00"
                it[updatedAt] = "2025-05-20T12:00:00"
            } get Patients.id
            println("Patient ID: $patientId, Patients count: ${Patients.selectAll().count()}")
        }
        val parameterId = transaction {
            MonitoringParameters.insert {
                it[name] = "Heart Rate"
                it[unit] = "bpm"
                it[createdAt] = "2025-05-20T12:00:00"
            } get MonitoringParameters.id
            println("Parameter ID: $parameterId, MonitoringParameters count: ${MonitoringParameters.selectAll().count()}")
        }

        // Add monitoring data
        val monitoringData = MonitoringData(
            id = 0,
            patientId = 222,
            parameterId = 2222,
            value = 75.0f,
            recordedAt = "2025-05-20T12:00:00",
            createdAt = "2025-05-20T12:00:00"
        )
        val token = generateTestToken(1, "DOCTOR")
        client.post("/patients/$patientId/monitoring-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(MonitoringData.serializer(), monitoringData))
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }

        // Get monitoring data
        client.get("/patients/$patientId/monitoring-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val data = Json.decodeFromString<List<MonitoringData>>(bodyAsText())
            assertEquals(1, data.size)
            assertEquals(75.0f, data[0].value)
        }
    }
 */
}