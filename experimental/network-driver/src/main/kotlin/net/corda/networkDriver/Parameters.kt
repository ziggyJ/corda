package net.corda.networkDriver

import picocli.CommandLine
import picocli.CommandLine.Parameters

class Parameters {
    @Parameters(index = "0")
    var hostPort: String = ""

    @Parameters(index = "1")
    var username: String = ""

    @Parameters(index = "2")
    var password: String = ""

    @CommandLine.Option(names = ["-f", "--filter"], description = ["Filter pattern in name (default: not active)."])
    var filterPattern: String = ""

    @CommandLine.Option(names = ["-n", "--include-notaries"], description=["Transact with notaries (default: false)."])
    var includeNotaries: Boolean = false

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["display this help message"])
    var usageHelpRequested: Boolean = false
}
