package net.corda.core.node

import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import rx.Observable

/**
 * A [CordaService] annotated class requires
 */
interface AppServiceHub : ServiceHub {

    /**
     * Start the given flow with the given arguments. [logicType] must be annotated
     * with [net.corda.core.flows.StartableByService].
     */
    fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T>

    /**
     * Start the given flow with the given arguments, returning an [Observable] with a single observation of the
     * result of running the flow. [logicType] must be annotated with [net.corda.core.flows.StartableByService].
     */
    fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T>

}