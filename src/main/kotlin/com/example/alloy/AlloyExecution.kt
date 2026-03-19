package com.example.alloy

import edu.mit.csail.sdg.alloy4.A4Reporter
import edu.mit.csail.sdg.ast.Command
import edu.mit.csail.sdg.ast.Module
import edu.mit.csail.sdg.parser.CompUtil
import edu.mit.csail.sdg.translator.A4Options
import edu.mit.csail.sdg.translator.A4Solution
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod
import java.nio.file.Files
import java.nio.file.Path
import kodkod.engine.satlab.SATFactory

fun parseAlloyModule(path: Path): Module =
        CompUtil.parseEverything_fromFile(A4Reporter.NOP, null, path.toString())

sealed class SolveOutcome {
    data class Sat(
            val solution: A4Solution,
            val xmlPath: Path,
            val command: Command,
    ) : SolveOutcome()

    data class Unsat(
            val command: Command,
    ) : SolveOutcome()
}

private val REPORTER: A4Reporter = A4Reporter()
private val SOLVER: SATFactory = SATFactory.DEFAULT

// Runs the command and writes the satisfying instance (if any) to a fresh temp XML file
// The temp file is marked `deleteOnExit`; callers that want to keep it should copy it
fun solveCommand(
        module: Module,
        command: Command,
): SolveOutcome {
    val solution =
            TranslateAlloyToKodkod.execute_command(
                    REPORTER,
                    module.allReachableSigs,
                    command,
                    A4Options().apply { solver = SOLVER },
            )

    if (!solution.satisfiable()) return SolveOutcome.Unsat(command)

    val xml = Files.createTempFile("alloy-instance-", ".xml")
    xml.toFile().deleteOnExit()
    solution.writeXML(REPORTER, xml.toString(), emptyList(), emptyMap())
    return SolveOutcome.Sat(solution, xml, command)
}

// Advances [solution]. Overwrites [xmlPath] so `VizGUI.loadXML` can reload the next instance from
// disk
fun nextInstance(
        solution: A4Solution,
        xmlPath: Path,
): A4Solution? {
    val next = solution.next() ?: return null
    next.writeXML(REPORTER, xmlPath.toString(), emptyList(), emptyMap())
    return next
}
