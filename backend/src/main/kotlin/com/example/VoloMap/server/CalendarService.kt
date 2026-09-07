package com.example.VoloMap.server

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.util.Date

@Service
class CalendarService {

    /** Builds an ICS feed of the given activities, skipping any without a dateTime (nothing to put on a calendar). */
    fun buildIcs(activities: List<VolunteerActivity>): String {
        val calendar = ICalendar()

        for (activity in activities) {
            val dateTime = activity.dateTime ?: continue
            val event = VEvent()
            event.setSummary(activity.name)
            activity.description?.let { event.setDescription(it) }
            activity.addressText?.let { event.setLocation(it) }
            event.setUid("benemap-activity-${activity.id}@benemap.org")

            val zone = ZoneId.systemDefault()
            val start = Date.from(dateTime.atZone(zone).toInstant())
            event.setDateStart(start)
            val durationHours = activity.durationHours ?: DEFAULT_ACTIVITY_DURATION_HOURS
            val end = Date.from(dateTime.plusMinutes((durationHours * 60).toLong()).atZone(zone).toInstant())
            event.setDateEnd(end)

            calendar.addEvent(event)
        }

        return Biweekly.write(calendar).go()
    }
}
