package ir.roozban.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Creates a real version-1 database from the committed schema (`schemas/…/1.json`), fills it,
 * then opens it with the current Room database. Room runs the migrations and validates the
 * resulting schema against the current entities, so a wrong migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val schemaDir = File("schemas/ir.roozban.core.database.RoozbanDatabase")

    private fun createFromSchema(file: File, version: Int) {
        val db = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = db.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = db.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
        sql.version = version
        sql.close()
    }

    @Test
    fun `migrates 1 to 2 keeping tasks, reminders and completions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createFromSchema(file, 1)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { v1 ->
            v1.execSQL(
                """INSERT INTO task (id, title, notes, due_date, due_minute, important, urgent, estimate_min, rrule,
                   rrule_start, reminder_kind, reminder_offset, completed_at, created_at, updated_at, deleted_at)
                   VALUES ('t1', 'جلسه', '', 20720, 600, 1, 0, 30, 'FREQ=DAILY', 20720, 'ALARM', 5, NULL, 1, 2, NULL)""",
            )
            v1.execSQL("INSERT INTO reminder (task_id, trigger_at, kind, state) VALUES ('t1', 1790000000, 'ALARM', 'PENDING')")
            v1.execSQL("INSERT INTO task_completion (task_id, occurrence, completed_at) VALUES ('t1', 20719, 3)")
        }

        val db = Room.databaseBuilder(context, RoozbanDatabase::class.java, file.path)
            .allowMainThreadQueries()
            .build()
        val task = db.taskDao().get("t1")!!
        assertThat(task.task.title).isEqualTo("جلسه")
        assertThat(task.task.estimateMinutes).isEqualTo(30)
        assertThat(task.task.projectId).isNull()
        assertThat(task.task.parentId).isNull()
        assertThat(task.labelIds).isEmpty()
        assertThat(db.reminderDao().get("t1")?.kind).isEqualTo("ALARM")
        assertThat(db.taskDao().completions("t1").map { it.occurrence }).containsExactly(20719L)
        // New tables work after migration.
        db.projectDao().upsert(ProjectEntity("p1", "خانه", 2, false, 0, 1, 1))
        assertThat(db.projectDao().observeAll().first().map { it.name }).containsExactly("خانه")
        db.close()
    }

    @Test
    fun `migrates 2 to 3 keeping projects and labels and adding focus and habits`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migration-test-2.db").apply { parentFile?.mkdirs(); delete() }
        createFromSchema(file, 2)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { v2 ->
            v2.execSQL("INSERT INTO project (id, name, color, archived, sort_order, created_at, updated_at) VALUES ('p', 'کار', 1, 0, 0, 1, 1)")
            v2.execSQL(
                """INSERT INTO task (id, title, notes, due_date, due_minute, important, urgent, estimate_min, rrule,
                   rrule_start, reminder_kind, reminder_offset, completed_at, created_at, updated_at, deleted_at, project_id, parent_id)
                   VALUES ('t', 'گزارش', '', NULL, NULL, 0, 0, NULL, NULL, NULL, NULL, 0, NULL, 1, 1, NULL, 'p', NULL)""",
            )
            v2.execSQL("INSERT INTO label (id, name, color, created_at, updated_at) VALUES ('l', 'مهم', 0, 1, 1)")
            v2.execSQL("INSERT INTO task_label (task_id, label_id) VALUES ('t', 'l')")
        }
        val db = Room.databaseBuilder(context, RoozbanDatabase::class.java, file.path).allowMainThreadQueries().build()
        val task = db.taskDao().get("t")!!
        assertThat(task.task.projectId).isEqualTo("p")
        assertThat(task.labelIds).containsExactly("l")
        db.habitDao().upsert(HabitEntity("h", "ورزش", 0, "D", 1, null, 20_000, false, 0, 1, 1))
        db.focusDao().record(FocusSessionEntity("s", "t", 0, 1, 25, 1, true), TimeEntryEntity("e", "t", 0, 1000, "FOCUS"))
        assertThat(db.habitDao().all().map { it.name }).containsExactly("ورزش")
        assertThat(db.focusDao().observeTracked(0, 2000).first().single().projectId).isEqualTo("p")
        db.close()
    }

    @Test
    fun `migrates 3 to 4 keeping habits and adding personal events`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migration-test-3.db").apply { parentFile?.mkdirs(); delete() }
        createFromSchema(file, 3)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { v3 ->
            v3.execSQL(
                """INSERT INTO habit (id, name, color, schedule, target, reminder_minute, start_date, archived, sort_order, created_at, updated_at)
                   VALUES ('h', 'ورزش', 0, 'D', 1, NULL, 20000, 0, 0, 1, 1)""",
            )
            v3.execSQL("INSERT INTO habit_log (habit_id, date, count, updated_at) VALUES ('h', 20001, 1, 1)")
        }
        val db = Room.databaseBuilder(context, RoozbanDatabase::class.java, file.path).allowMainThreadQueries().build()
        assertThat(db.habitDao().log("h", 20001)?.count).isEqualTo(1)
        db.eventDao().upsert(PersonalEventEntity("e", "تولد", "BIRTHDAY", 0, "JALALI", 7, 15, 1375, true, "0,1", 540, "", 1, 1))
        assertThat(db.eventDao().all().single().title).isEqualTo("تولد")
        db.close()
    }
}
