package org.i2peer.network

class EndpointMatcher(val endpoint: String) : CommunicationTaskMatcher {
    override fun match(task: CommunicationTask): Boolean = endpoint.equals(endpoint)
}

