package com.example.persistdurablecluster.actor

import com.example.persistdurablecluster.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.TimerScheduler
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.state.javadsl.CommandHandler
import org.apache.pekko.persistence.typed.state.javadsl.DurableStateBehavior
import org.apache.pekko.persistence.typed.state.javadsl.Effect
import java.time.Duration
import java.time.LocalDateTime

class ClusteredUserStateActor private constructor(
    private val context: ActorContext<UserCommand>,
    private val persistenceId: PersistenceId,
    private val timers: TimerScheduler<UserCommand>,
    private val mallId: String,
    private val userId: String
) : DurableStateBehavior<UserCommand, UserState>(persistenceId) {

    companion object {
        private const val INACTIVITY_CHECK_KEY = "inactivity-check"
        private val INACTIVITY_TIMEOUT = Duration.ofMinutes(30)
        private val CHECK_INTERVAL = Duration.ofMinutes(5)
        
        fun create(entityContext: EntityContext<UserCommand>): Behavior<UserCommand> {
            val entityId = entityContext.entityId
            val parts = entityId.split("-")
            if (parts.size != 2) {
                throw IllegalArgumentException("Entity ID must be in format 'mallId-userId'")
            }
            val mallId = parts[0]
            val userId = parts[1]
            
            val persistenceId = PersistenceId.of(
                entityContext.entityTypeKey.name(),
                entityId
            )
            
            return Behaviors.setup { context ->
                Behaviors.withTimers { timers ->
                    ClusteredUserStateActor(context, persistenceId, timers, mallId, userId)
                }
            }
        }
        
        fun extractEntityId(mallId: String, userId: String): String {
            return "$mallId-$userId"
        }
    }

    init {
        scheduleInactivityCheck()
        context.log.info("ClusteredUserStateActor initialized for mall: $mallId, user: $userId on node: ${context.system.address()}")
    }

    override fun tag(): String = "user-state"

    override fun emptyState(): UserState = UserState(
        mallId = mallId,
        userId = userId,
        lastLogin = null,
        lastCartUsedTime = null,
        recentProducts = emptyList(),
        marketingOptIn = false,
        lastEventTime = LocalDateTime.now()
    )

    override fun commandHandler(): CommandHandler<UserCommand, UserState> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(UserLogin::class.java) { state, command -> onUserLogin(state, command) }
            .onCommand(UseCart::class.java) { state, command -> onUseCart(state, command) }
            .onCommand(ViewProduct::class.java) { state, command -> onViewProduct(state, command) }
            .onCommand(SetMarketingOptIn::class.java) { state, command -> onSetMarketingOptIn(state, command) }
            .onCommand(GetUserState::class.java) { state, command -> onGetUserState(state, command) }
            .onCommand(CheckInactivity::class.java) { state, _ -> onCheckInactivity(state) }
            .build()
    }

    private fun onUserLogin(state: UserState, command: UserLogin): Effect<UserState> {
        context.log.info("User $userId logged in at mall $mallId on node: ${context.system.address()}")
        val newState = state.withLogin()
        
        return Effect().persist(newState).thenRun {
            command.replyTo?.tell(ActionCompleted("login"))
            scheduleInactivityCheck()
        }
    }

    private fun onUseCart(state: UserState, command: UseCart): Effect<UserState> {
        context.log.info("User $userId used cart at mall $mallId on node: ${context.system.address()}")
        val newState = state.withCartUsed()
        
        return Effect().persist(newState).thenRun {
            command.replyTo?.tell(ActionCompleted("cart_used"))
            scheduleInactivityCheck()
        }
    }

    private fun onViewProduct(state: UserState, command: ViewProduct): Effect<UserState> {
        context.log.info("User $userId viewed product ${command.productId} at mall $mallId on node: ${context.system.address()}")
        val newState = state.withRecentProduct(command.productId)
        
        return Effect().persist(newState).thenRun {
            command.replyTo?.tell(ActionCompleted("product_viewed"))
            scheduleInactivityCheck()
        }
    }

    private fun onSetMarketingOptIn(state: UserState, command: SetMarketingOptIn): Effect<UserState> {
        context.log.info("User $userId set marketing opt-in to ${command.optIn} at mall $mallId on node: ${context.system.address()}")
        val newState = state.withMarketingOptIn(command.optIn)
        
        return Effect().persist(newState).thenRun {
            command.replyTo?.tell(ActionCompleted("marketing_opt_in_set"))
            scheduleInactivityCheck()
        }
    }

    private fun onGetUserState(state: UserState, command: GetUserState): Effect<UserState> {
        context.log.debug("Returning state for user $userId at mall $mallId from node: ${context.system.address()}")
        command.replyTo.tell(UserStateResponse(state))
        return Effect().none()
    }

    private fun onCheckInactivity(state: UserState): Effect<UserState> {
        val now = LocalDateTime.now()
        val timeSinceLastEvent = Duration.between(state.lastEventTime, now)
        
        if (timeSinceLastEvent.compareTo(INACTIVITY_TIMEOUT) > 0) {
            context.log.info("User $userId has been inactive for more than 30 minutes. Passivating actor on node: ${context.system.address()}")
            return Effect().none().thenStop()
        } else {
            return Effect().none()
        }
    }

    private fun scheduleInactivityCheck() {
        timers.cancel(INACTIVITY_CHECK_KEY)
        timers.startTimerWithFixedDelay(INACTIVITY_CHECK_KEY, CheckInactivity(), CHECK_INTERVAL)
    }
}