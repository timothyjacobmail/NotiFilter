package co.adityarajput.notifilter.viewmodels

import co.adityarajput.notifilter.data.models.Action
import co.adityarajput.notifilter.data.models.App
import co.adityarajput.notifilter.data.models.Notification
import co.adityarajput.notifilter.data.models.RegexTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpsertFilterValuesTest {
    @Test
    fun allNotificationsMatchesEmptyAndMultilineTextAndSurvivesEditing() {
        val app = App("Example", "example.app")
        val values = UpsertFilterViewModel.Values(
            app = app,
            regexTarget = RegexTarget.AND,
            queryPattern = "[",
            secondaryQueryPattern = "restricted",
            action = Action.MUTE,
            allNotifications = true,
        )
        val filter = values.toFilter()

        assertEquals(app, filter.app)
        assertEquals(Action.MUTE, filter.action)
        assertNull(filter.secondaryRegexPattern)
        for ((title, content) in listOf("" to "", "Unrelated" to "Text", "\n" to "First\nSecond")) {
            assertTrue(filter.matchesTextOf(Notification(title, content, app.packageName, 0)))
        }
        val restored = UpsertFilterViewModel.Values(filter)
        assertTrue(restored.allNotifications)
        assertEquals(filter, restored.toFilter())
        assertEquals("[", values.copy(allNotifications = false).toFilter().regexPattern)
    }

    @Test
    fun ordinaryPatternsRemainRestricted() {
        val values = UpsertFilterViewModel.Values(queryPattern = "wanted")
        val filter = values.toFilter()
        assertFalse(UpsertFilterViewModel.Values(filter).allNotifications)
        assertFalse(filter.matchesTextOf(Notification("unrelated", "", "example.app", 0)))
    }
}
