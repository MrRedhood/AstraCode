package com.astracode.app

import com.astracode.app.filesystem.FileManagementSystemAndroidTest
import com.astracode.app.terminal.TerminalCommandRunnerAndroidTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    FileManagementSystemAndroidTest::class,
    TerminalCommandRunnerAndroidTest::class,
)
class AstraCodeInstrumentationTestSuite
