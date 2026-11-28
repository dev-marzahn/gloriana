package dev.marzahn.gloriana.logic.connection.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.Logging

val loggingClient = HttpClient { install(Logging) }
