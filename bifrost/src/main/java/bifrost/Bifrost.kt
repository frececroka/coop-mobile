package bifrost

import bifrost.BifrostGrpc.BifrostBlockingStub
import io.grpc.ManagedChannelBuilder

object Bifrost {
    private val channel = ManagedChannelBuilder.forTarget("dns:///bifrost.milang.ch").build()
    val bifrost: BifrostBlockingStub = BifrostGrpc.newBlockingStub(channel)
}
