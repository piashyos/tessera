package app.tessera.domain.usecase

import app.tessera.domain.model.AccountSwitchDecision
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AccountRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HandleAccountSwitchUseCaseTest {
    @Test
    fun `returns None when no account owns local notes`() =
        runTest {
            val useCase = HandleAccountSwitchUseCase(FakeAccountRepository(owner = null))

            assertEquals(AccountSwitchDecision.None, useCase(UserId("new")))
        }

    @Test
    fun `returns SameAccount when current owner matches the new uid`() =
        runTest {
            val useCase = HandleAccountSwitchUseCase(FakeAccountRepository(owner = UserId("uid-1")))

            assertEquals(AccountSwitchDecision.SameAccount, useCase(UserId("uid-1")))
        }

    @Test
    fun `returns DifferentAccount carrying the existing owner`() =
        runTest {
            val useCase = HandleAccountSwitchUseCase(FakeAccountRepository(owner = UserId("uid-old")))

            assertEquals(
                AccountSwitchDecision.DifferentAccount(UserId("uid-old")),
                useCase(UserId("uid-new")),
            )
        }
}

private class FakeAccountRepository(
    private val owner: UserId?,
) : AccountRepository {
    override suspend fun currentOwnerId(): UserId? = owner

    override suspend fun adoptLocalNotes(uid: UserId) = Unit

    override suspend fun moveAllNotesTo(uid: UserId) = Unit

    override suspend fun deleteNotesOwnedBy(uid: UserId) = Unit
}
