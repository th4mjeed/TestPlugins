package com.thamjeed.thamstream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class ThamStreamPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(ThamStreamProvider())
    }
}
