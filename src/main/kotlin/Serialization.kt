package com.example

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

/*
    Database.connect(
        url= "jdbc:postgresql://yamabiko.proxy.rlwy.net:42587/railway",
        driver= "org.postgresql.Driver",
        user= "postgres",
        password= "bJnkfcNmxKKnsuLByGqwiAbfzJmKWXYa"
    )
 */

/*
database:
  url: "jdbc:postgresql://yamabiko.proxy.rlwy.net:42587/railway"
  driver: "org.postgresql.Driver"
  user: "postgres"
  password: "bJnkfcNmxKKnsuLByGqwiAbfzJmKWXYa"

jwt:
  audience: "http://yamabiko.proxy.rlwy/"
  domain: "http://yamabiko.proxy.rlwy/"
  realm: "Access to protected routes"
  secret: "secret"

postgres:
  url: "jdbc:postgresql://yamabiko.proxy.rlwy.net/railway"
  user: postgres
  password: bJnkfcNmxKKnsuLByGqwiAbfzJmKWXYa

  */