package com.aipromptbridge.core.storage

import java.io.File
import kotlinx.serialization.json.Json

/** Shared JSON configuration for everything persisted by the app. */
object CoreJson {
    val json: Json = Json {
        ignoreUnknownKeys = true   // forward compatible: newer files can be read by older builds
        encodeDefaults = true
        prettyPrint = true
    }
}

/** Writes via a temp file + rename so a crash mid-write cannot leave a half-written JSON file. */
internal fun File.writeTextAtomically(text: String) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (!tmp.renameTo(this)) {
        writeText(text, Charsets.UTF_8)
        tmp.delete()
    }
}
