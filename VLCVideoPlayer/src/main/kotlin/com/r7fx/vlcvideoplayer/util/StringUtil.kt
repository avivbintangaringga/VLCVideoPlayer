package com.r7fx.vlcvideoplayer.util

import java.util.concurrent.TimeUnit

class StringUtil {
    companion object {
        fun longMillisToTimeStamp(millis: Long?): String {
            return millis?.let {
                if (it >= 3_600_000) {
                    String.format(
                        "%d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                        TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
                    );
                } else {
                    String.format(
                        "%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
                    );
                }
            } ?: "Unknown"
        }
    }
}