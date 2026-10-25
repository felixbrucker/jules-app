package com.felixbrucker.jules.network.api.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowDispatchParserTest {

    @Test
    fun testNoWorkflowDispatch() {
        val yaml = """
            name: CI
            on:
              push:
                branches: [ main ]
        """.trimIndent()

        val result = WorkflowDispatchParser.parseWorkflowDispatch(yaml)
        assertFalse(result.hasWorkflowDispatch)
        assertTrue(result.inputs.isEmpty())
    }

    @Test
    fun testInlineWorkflowDispatch() {
        val yaml = """
            name: Manual Run
            on: workflow_dispatch
        """.trimIndent()

        val result = WorkflowDispatchParser.parseWorkflowDispatch(yaml)
        assertTrue(result.hasWorkflowDispatch)
        assertTrue(result.inputs.isEmpty())
    }

    @Test
    fun testListWorkflowDispatch() {
        val yaml = """
            name: Manual Run
            on: [push, workflow_dispatch]
        """.trimIndent()

        val result = WorkflowDispatchParser.parseWorkflowDispatch(yaml)
        assertTrue(result.hasWorkflowDispatch)
        assertTrue(result.inputs.isEmpty())
    }

    @Test
    fun testWorkflowDispatchWithInputs() {
        val yaml = """
            name: Deploy App
            on:
              workflow_dispatch:
                inputs:
                  logLevel:
                    description: 'Log level'
                    required: true
                    default: 'warning'
                    type: choice
                    options:
                      - info
                      - warning
                      - debug
                  tags:
                    description: 'Test tags'
                    required: false
                    type: boolean
                    default: 'false'
        """.trimIndent()

        val result = WorkflowDispatchParser.parseWorkflowDispatch(yaml)
        assertTrue(result.hasWorkflowDispatch)
        assertEquals(2, result.inputs.size)

        val input1 = result.inputs[0]
        assertEquals("logLevel", input1.name)
        assertEquals("Log level", input1.description)
        assertTrue(input1.required)
        assertEquals("warning", input1.default)
        assertEquals("choice", input1.type)
        assertEquals(listOf("info", "warning", "debug"), input1.options)

        val input2 = result.inputs[1]
        assertEquals("tags", input2.name)
        assertEquals("Test tags", input2.description)
        assertFalse(input2.required)
        assertEquals("false", input2.default)
        assertEquals("boolean", input2.type)
        assertNull(input2.options)
    }

    @Test
    fun testWorkflowDispatchWithPrimitiveDefaults() {
        val yaml = """
            name: Primitive Defaults
            on:
              workflow_dispatch:
                inputs:
                  port:
                    description: 'Port number'
                    required: true
                    default: 8080
                    type: number
                  enabled:
                    description: 'Flag'
                    required: false
                    default: true
                    type: boolean
        """.trimIndent()

        val result = WorkflowDispatchParser.parseWorkflowDispatch(yaml)
        assertTrue(result.hasWorkflowDispatch)
        assertEquals(2, result.inputs.size)

        val portInput = result.inputs[0]
        assertEquals("port", portInput.name)
        assertEquals("8080", portInput.default)

        val enabledInput = result.inputs[1]
        assertEquals("enabled", enabledInput.name)
        assertEquals("true", enabledInput.default)
    }
}
