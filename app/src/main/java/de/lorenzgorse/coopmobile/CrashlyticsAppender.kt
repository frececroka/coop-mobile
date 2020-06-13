package de.lorenzgorse.coopmobile

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import de.lorenzgorse.coopmobile.CoopModule.firebaseCrashlytics

class CrashlyticsAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        firebaseCrashlytics().log("${event.loggerName}/${event.level}: ${event.message}")
    }
}
