package com.example

import com.example.app.App
import javax.swing.SwingUtilities

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread {
            Thread { Thread.sleep(300)
            Runtime.getRuntime().halt(0) }.start()
        })

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            System.err.println("Uncaught exception: ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
        
        SwingUtilities.invokeLater { App.run(args) }
    }
}
