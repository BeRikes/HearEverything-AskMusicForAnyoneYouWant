package com.example.aimusicplayer.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ViewModel scope is initialized`() {
        val vm = TestViewModel()
        assertNotNull(vm.scope)
    }

    @Test
    fun `ViewModel launches coroutine`() = runTest {
        val vm = TestViewModel()
        val result = MutableStateFlow("")

        vm.scope.launch {
            result.value = "done"
        }

        this.testScheduler.advanceUntilIdle()
        assertEquals("done", result.value)
    }
}

private class TestViewModel : ViewModel()
