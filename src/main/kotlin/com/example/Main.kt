package com.example

import com.example.app.App

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            System.err.println("Uncaught exception on thread '${thread.name}': ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
        App.run(args)
    }
}
