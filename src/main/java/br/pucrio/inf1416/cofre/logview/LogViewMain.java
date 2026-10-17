package br.pucrio.inf1416.cofre.logview;

/** Separate executable shell. Must not display records before Phase 5 authentication exists. */
public final class LogViewMain {
    private LogViewMain() { }

    public static void main(String[] args) {
        if (args.length == 1 && ("--help".equals(args[0]) || "--check".equals(args[0]))) {
            System.out.println("logView — Phase 0 foundation. Usage: java -jar logView.jar [--help|--check]");
            System.out.println("Administrator authentication and audit viewing are not implemented yet.");
            return;
        }
        System.err.println("logView is unavailable until Phase 5; no records have been read. Use --help.");
        System.exit(2);
    }
}
