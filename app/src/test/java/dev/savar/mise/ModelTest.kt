package dev.savar.mise

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelTest {
    @Test fun watchRoundTrips() {
        val w = Fixtures.watchInterstellar
        assertEquals(w, Watch.fromJson(JSONObject(w.toJson().toString())))
    }

    @Test fun titleRoundTrips() {
        val t = Fixtures.arcane.copy(watchlistAt = 42, genres = listOf("Animation"))
        assertEquals(t, Title.fromJson(JSONObject(t.toJson().toString())))
    }

    @Test fun journalRoundTrips() {
        Fixtures.seed()
        val (t, w) = Store.read(Store.json())
        assertEquals(Fixtures.titles.toSet(), t.toSet())
        assertEquals(Fixtures.watches.toSet(), w.toSet())
    }

    @Test fun yearNumberAndRewatch() {
        Fixtures.seed()
        val w = Fixtures.watchInterstellar
        assertTrue(Store.isRewatch(w))
        val expected = Store.diary().count { Store.title(it.titleId)?.kind == Kind.MOVIE && it.date.year == w.date.year && (it.day < w.day || (it.day == w.day && it.createdAt <= w.createdAt)) }
        assertEquals(expected, Store.yearNumber(w))
    }

    @Test fun mergeKeepsNewerAndUnionsMemos() {
        Fixtures.seed()
        val local = Fixtures.watchInterstellar
        val remote = local.copy(thoughts = "edited elsewhere", memos = listOf(Memo("other.m4a", 1000, 9)), updatedAt = local.updatedAt + 10)
        val older = Fixtures.sinners.copy(name = "stale", updatedAt = Fixtures.sinners.updatedAt - 10)
        Store.merge(listOf(older), listOf(remote))
        val merged = Store.watch(local.id)!!
        assertEquals("edited elsewhere", merged.thoughts)
        assertEquals(setOf("other.m4a", "none.m4a"), merged.memos.map { it.file }.toSet())
        assertEquals("Sinners", Store.title(Fixtures.sinners.id)!!.name)
    }

    @Test fun tmdbYear() {
        assertEquals(2014, Tmdb.yearOf("2014-11-05"))
        assertEquals(null, Tmdb.yearOf(""))
    }
}
