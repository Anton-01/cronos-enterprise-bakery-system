package com.ninsky.cronos.calendar.infrastructure;

import com.ninsky.cronos.calendar.application.CalendarReminders;
import com.ninsky.cronos.eventing.infrastructure.ClusterJobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every minute: due reminders and production alerts become {@code calendar.*-due} events (§5.6, §8.7). */
@Component
public class CalendarReminderJob {

    static final int BATCH = 200;

    private final ClusterJobRunner jobs;
    private final CalendarReminders reminders;

    public CalendarReminderJob(ClusterJobRunner jobs, CalendarReminders reminders) {
        this.jobs = jobs;
        this.reminders = reminders;
    }

    @Scheduled(cron = "${app.calendar.reminder-cron:0 * * * * *}", zone = "America/Mexico_City")
    public void run() {
        jobs.run("CalendarReminderJob", () -> reminders.publishDue(BATCH));
    }
}
