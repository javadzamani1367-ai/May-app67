package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.db.UserDao
import ir.ilam.inspection.data.db.UserEntity
import kotlinx.coroutines.flow.Flow

/** The manager's register of who may use the app, and on which installation. */
class UserRepository(private val dao: UserDao) {

    val users: Flow<List<UserEntity>> = dao.observeAll()

    suspend fun byCode(code: String): UserEntity? = dao.byCode(code.trim())

    /**
     * Registering the same user code twice replaces the record rather than
     * creating a second one: a re-registration after a reinstall is the normal
     * case, and two rows for one person would make the register useless.
     */
    suspend fun save(user: UserEntity) =
        dao.upsert(user.copy(userCode = user.userCode.trim(), updatedAt = System.currentTimeMillis()))

    suspend fun delete(user: UserEntity) = dao.delete(user.id)
}
