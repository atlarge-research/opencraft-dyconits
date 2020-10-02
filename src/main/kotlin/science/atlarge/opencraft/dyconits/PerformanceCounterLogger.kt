package science.atlarge.opencraft.dyconits

import java.io.File
import java.time.Instant
import kotlin.reflect.full.memberProperties

class PerformanceCounterLogger(val logFilePath: String = "dyconits.log") {

    private val file = File(logFilePath)

    fun log(counters: Counters) {
        val now = Instant.now()
        val builder = StringBuilder()
        for (prop in Counters::class.memberProperties) {
            builder.appendLine("$now ${prop.name} ${prop.call(counters)}")
        }
        file.appendText(builder.toString())
    }
}
