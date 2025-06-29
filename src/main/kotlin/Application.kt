package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.net.http.HttpClient

fun Application.module() {

    Database.connect(
        url = "jdbc:postgresql://localhost:5432/icu_db",
        driver = "org.postgresql.Driver",
        user = "postgres",
        password = "2025"
    )
    // Инициализируем HttpClient и FCM параметры ВЫШЕ лямбды alertSender
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    val projectId = "diplom-e6c8f"
    val fcmEndpoint = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"

    // Установка плагина WebSockets
    install(WebSockets) {
        pingPeriodMillis = 15_000 // Опционально: Настройка интервала пингов для WebSocket соединений
        timeoutMillis = 15_000 // Опционально: Настройка таймаута для WebSocket соединений
        maxFrameSize = Long.MAX_VALUE // Опционально: Максимальный размер фрейма
        masking = false // Опционально: Отключение маскирования для простоты
    }

    // Определяем лямбда-функцию для отправки оповещений, захватывая нужные зависимости
    val alertSender: suspend (patientId: Int, metricCode: String, alertValue: Double, alertMessage: String, severity: String) -> Unit =
        { patientId, metricCode, alertValue, alertMessage, severity ->
            // Вызываем ранее определенную топ-уровневую функцию sendAlertNotificationToPractitioners,
            // передавая ей 'client' и 'fcmEndpoint' из текущей области видимости
            sendAlertNotificationToPractitioners(
                this@module, // Передаем Application instance
                client,      // Теперь 'client' разрешен, так как определен выше
                fcmEndpoint, // Теперь 'fcmEndpoint' разрешен, так как определен выше
                patientId,
                metricCode,
                alertValue,
                alertMessage,
                severity
            )
        }

    configureSerialization()
    // Предполагается, что configureDatabases() обрабатывает подключение к БД,
    // поэтому прямое подключение Database.connect() здесь не нужно, если оно уже внутри configureDatabases().
    // Если configureDatabases() не подключается к БД, то оставьте Database.connect() здесь.
    // Database.connect(...)
    configureDatabases()
    configureSecurity()
    configureRouting()
    configureWebSocketRouting(alertSender)
}

/*
    Database.connect(
        url= "jdbc:postgresql://yamabiko.proxy.rlwy.net:42587/railway",
        driver= "org.postgresql.Driver",
        user= "postgres",
        password= "bJnkfcNmxKKnsuLByGqwiAbfzJmKWXYa"
    )
 */