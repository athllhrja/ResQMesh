package com.resqmesh.experiment

import com.resqmesh.domain.model.NodeId
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExperimentLoggerTest {

    @Test
    fun `experiment_event_enum_values_valid`() {
        assertNotNull(ExperimentEvent.SEND)
        assertNotNull(ExperimentEvent.TX)
        assertNotNull(ExperimentEvent.RX)
        assertNotNull(ExperimentEvent.RELAY)
        assertNotNull(ExperimentEvent.ACK_TX)
        assertNotNull(ExperimentEvent.ACK_RX)
        assertNotNull(ExperimentEvent.DELIVERED)
    }

    @Test
    fun `node_id_hex_formatting_valid`() {
        val node = NodeId(0x112233)
        assertNotNull(node.hex)
    }
}
