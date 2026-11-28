package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.BaseUrl

internal fun addressOf(baseUrl: String, target: String): String =
  "${BaseUrl(baseUrl.trim()).withoutTrailingSlash()}/$target"

fun ApiFormat.requestAddress(baseUrl: String): String = addressOf(baseUrl, adapter.path)
