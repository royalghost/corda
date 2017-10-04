package net.corda.node.internal.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.StartedNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

@StartableByService
class DummyServiceFlow : FlowLogic<FlowInitiator>() {
    companion object {
        object TEST_STEP : ProgressTracker.Step("Custom progress step")
    }
    override val progressTracker: ProgressTracker = ProgressTracker(TEST_STEP)

    @Suspendable
    override fun call(): FlowInitiator {
        // We call a subFlow, otehrwise there is no chance to subscribe to the ProgressTracker
        subFlow(CashIssueFlow(100.DOLLARS, OpaqueBytes.of(1), serviceHub.networkMapCache.notaryIdentities.first()))
        progressTracker.currentStep = TEST_STEP
        return stateMachine.flowInitiator
    }
}

@CordaService
class TestCordaService(val appServiceHub: AppServiceHub): SingletonSerializeAsToken() {
    fun startServiceFlow() {
        val handle = appServiceHub.startFlowDynamic(DummyServiceFlow::class.java)
        val initiator = handle.returnValue.get()
        assert(initiator is FlowInitiator.Service)
        initiator as FlowInitiator.Service
        assertEquals(this.javaClass.name, initiator.serviceClassName)
    }

    fun startServiceFlowAndTrack() {
        val handle = appServiceHub.startTrackedFlowDynamic(DummyServiceFlow::class.java)
        val count = AtomicInteger(0)
        val subscriber = handle.progress.subscribe { count.incrementAndGet() }
        handle.returnValue.get()
        // Simply prove some progress was made.
        // The actual number is currently 11, but don't want to hard code an implementation detail.
        assert(count.get() > 1)
        subscriber.unsubscribe()
    }

}

@CordaService
class TestCordaService2(val appServiceHub: AppServiceHub): SingletonSerializeAsToken() {
    fun startInvalidRPCFlow() {
        val handle = appServiceHub.startFlowDynamic(DummyRPCFlow::class.java)
        handle.returnValue.get()
    }

}

@CordaService
class LegacyCordaService(val simpleServiceHub: ServiceHub): SingletonSerializeAsToken() {

}

class CordaServiceTest {
    lateinit var mockNet: MockNetwork
    lateinit var notaryNode: StartedNode<MockNetwork.MockNode>
    lateinit var nodeA: StartedNode<MockNetwork.MockNode>

    @Before
    fun start() {
        setCordappPackages("net.corda.node.internal.cordapp","net.corda.finance")
        mockNet = MockNetwork(threadPerNode = true)
        notaryNode = mockNet.createNode(
                legalName = DUMMY_NOTARY.name,
                advertisedServices = *arrayOf(ServiceInfo(ValidatingNotaryService.type)))
        nodeA = mockNet.createNode(notaryNode.network.myAddress)
        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `Can find distinct services on node`() {
        val service = nodeA.services.cordaService(TestCordaService::class.java)
        val service2 = nodeA.services.cordaService(TestCordaService2::class.java)
        val legacyService = nodeA.services.cordaService(LegacyCordaService::class.java)
        assertEquals(TestCordaService::class.java, service.javaClass)
        assertEquals(TestCordaService2::class.java, service2.javaClass)
        assertNotEquals(service.appServiceHub, service2.appServiceHub) // Each gets a customised AppServiceHub
        assertEquals(LegacyCordaService::class.java, legacyService.javaClass)
    }

    @Test
    fun `Can start StartableByService flows`() {
        val service = nodeA.services.cordaService(TestCordaService::class.java)
        service.startServiceFlow()
    }

    @Test
    fun `Can't start StartableByRPC flows`() {
        val service = nodeA.services.cordaService(TestCordaService2::class.java)
        assertFails { service.startInvalidRPCFlow() }
    }


    @Test
    fun `Test flow with progress tracking`() {
        val service = nodeA.services.cordaService(TestCordaService::class.java)
        service.startServiceFlowAndTrack()
    }

}