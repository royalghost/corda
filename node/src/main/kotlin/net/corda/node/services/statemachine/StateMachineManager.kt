package net.corda.node.services.statemachine

import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.messaging.DataFeed
import net.corda.core.utilities.Try
import rx.Observable

/**
 * A StateMachineManager is responsible for coordination and persistence of multiple [FlowStateMachine] objects.
 * Each such object represents an instantiation of a (two-party) flow that has reached a particular point.
 *
 * An implementation of this interface will persist state machines to long term storage so they can survive process
 * restarts and, if run with a single-threaded executor, will ensure no two state machines run concurrently with each
 * other (bad for performance, good for programmer mental health!).
 *
 * A flow is a class with a single call method. The call method and any others it invokes are rewritten by a bytecode
 * rewriting engine called Quasar, to ensure the code can be suspended and resumed at any point.
 *
 * TODO: Consider the issue of continuation identity more deeply: is it a safe assumption that a serialised
 *       continuation is always unique?
 * TODO: Think about how to bring the system to a clean stop so it can be upgraded without any serialised stacks on disk
 * TODO: Timeouts
 * TODO: Surfacing of exceptions via an API and/or management UI
 * TODO: Ability to control checkpointing explicitly, for cases where you know replaying a message can't hurt
 * TODO: Don't store all active flows in memory, load from the database on demand.
 */
interface StateMachineManager {
    /**
     * Starts the state machine manager, loading and starting the state machines in storage.
     */
    fun start()
    /**
     * Stops the state machine manager gracefully, waiting until all but [allowedUnsuspendedFiberCount] flows reach the
     * next checkpoint.
     */
    fun stop(allowedUnsuspendedFiberCount: Int)

    /**
     * Starts a new flow.
     *
     * @param flowLogic The flow's code.
     * @param flowInitiator The initiator of the flow.
     */
    fun <A> startFlow(flowLogic: FlowLogic<A>, flowInitiator: FlowInitiator, ourIdentity: Party? = null): CordaFuture<FlowStateMachine<A>>

    /**
     * Represents an addition/removal of a state machine.
     */
    sealed class Change {
        abstract val logic: FlowLogic<*>
        data class Add(override val logic: FlowLogic<*>) : Change()
        data class Removed(override val logic: FlowLogic<*>, val result: Try<*>) : Change()
    }

    /**
     * Returns the list of live state machines and a stream of subsequent additions/removals of them.
     */
    fun track(): DataFeed<List<FlowLogic<*>>, Change>

    /**
     * The stream of additions/removals of flows.
     */
    val changes: Observable<Change>

    /**
     * Returns the currently live flows of type [flowClass], and their corresponding result future.
     */
    fun <A : FlowLogic<*>> findStateMachines(flowClass: Class<A>): List<Pair<A, CordaFuture<*>>>

    /**
     * Returns all currently live flows.
     */
    val allStateMachines: List<FlowLogic<*>>
}