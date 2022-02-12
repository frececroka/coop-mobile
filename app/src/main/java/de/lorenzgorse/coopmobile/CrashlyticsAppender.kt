package de.lorenzgorse.coopmobile

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

class CrashlyticsAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        Firebase.crashlytics.log("${event.loggerName}/${event.level}: ${event.message}")
    }
}
