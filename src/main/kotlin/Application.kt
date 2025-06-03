package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun Application.module() {
    val dbUrl = environment.config.property("database.url").getString()
    val dbDriver = environment.config.property("database.driver").getString()
    val dbUser = environment.config.propertyOrNull("database.user")?.getString() ?: ""
    val dbPassword = environment.config.propertyOrNull("database.password")?.getString() ?: ""

    /*
    Database.connect(
        url = dbUrl,
        driver = dbDriver,
        user = dbUser,
        password = dbPassword
    )*/

    Database.connect(
        url= "jdbc:postgresql://yamabiko.proxy.rlwy.net:42587/railway",
        driver= "org.postgresql.Driver",
        user= "postgres",
        password= "bJnkfcNmxKKnsuLByGqwiAbfzJmKWXYa"
    )

    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureRouting()
}