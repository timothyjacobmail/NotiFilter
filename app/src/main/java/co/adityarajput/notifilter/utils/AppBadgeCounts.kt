package co.adityarajput.notifilter.utils

import android.app.Notification.FLAG_GROUP_SUMMARY
import android.service.notification.StatusBarNotification
import co.adityarajput.notifilter.data.models.Any
import co.adityarajput.notifilter.data.models.Filter
import co.adityarajput.notifilter.data.models.Notification

object AppBadgeCounts {
    fun count(active: List<StatusBarNotification>, filters: List<Filter>, filteredOnly: Boolean, ownPackage: String): Map<String, Int> =
        active.distinctBy { it.key }.filter { sbn ->
            sbn.packageName != ownPackage && sbn.notification.flags and FLAG_GROUP_SUMMARY == 0 &&
                (!filteredOnly || filters.any { filter ->
                    filter.enabled && (filter.app.packageName == sbn.packageName || filter.app == Any) &&
                        filter.schedule.includesNow() && filter.matchesTextOf(Notification(sbn))
                })
        }.groupingBy { it.packageName }.eachCount()
}
