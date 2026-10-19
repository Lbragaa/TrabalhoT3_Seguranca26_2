/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.logview;

/** Separate executable. The production input path is exclusively Console.readPassword(). */
public final class LogViewMain {
    private LogViewMain() { }

    public static void main(String[] args) {
        java.io.Console console=System.console();
        int code=run(args,console==null?null:()->console.readPassword("Frase secreta: "),
                new java.io.PrintWriter(System.out,true,java.nio.charset.StandardCharsets.UTF_8),
                new java.io.PrintWriter(System.err,true,java.nio.charset.StandardCharsets.UTF_8));
        if(code!=0){System.exit(code);}
    }
    @FunctionalInterface interface SecretInput { char[] read(); }
    static int run(String[] args,SecretInput input,java.io.PrintWriter out,java.io.PrintWriter err){
        if (args.length == 1 && ("--help".equals(args[0]) || "--check".equals(args[0]))) {
            out.println("logView — uso: java -jar logView.jar <chave-privada-encriptada> [--database <cofre.db>]");
            out.println("Requer administrador cadastrado e terminal com entrada de senha sem eco. --check não lê dados.");
            return 0;
        }
        if(args.length!=1 && !(args.length==3 && "--database".equals(args[1]))){err.println("Argumentos inválidos. Use --help.");return 2;}
        if(args[0].startsWith("--")){err.println("Caminho da chave necessário. Use --help.");return 2;}
        if(input==null){err.println("Terminal com entrada sem eco indisponível. Nenhum registro foi lido.");return 2;}
        char[] phrase=null;
        try{
            var key=java.nio.file.Path.of(args[0]);var db=java.nio.file.Path.of(args.length==3?args[2]:"runtime/cofre.db");
            if(!java.nio.file.Files.isRegularFile(key)){err.println("Caminho da chave privada inválido.");return 1;}
            var service=new LogViewService(db);
            phrase=input.read();
            try(var access=service.authenticate(key,phrase)){
                java.util.Arrays.fill(phrase,'\0');phrase=null;
                int[] count={0};service.read(access,line->{out.println(line.display());count[0]++;});
                if(count[0]==0){out.println("Nenhum registro.");}
                out.flush();if(out.checkError()){err.println("Falha ao exibir os registros.");return 3;}
            }
            return 0;
        }catch(java.security.GeneralSecurityException|SecurityException e){err.println("Autenticação do administrador recusada. Nenhum registro exibido.");return 1;}
        catch(java.io.IOException|java.sql.SQLException|IllegalArgumentException e){err.println("Banco de dados, chave ou registros inválidos/inacessíveis.");return 3;}
        catch(java.io.IOError e){err.println("Falha na entrada segura do terminal. Nenhum registro exibido.");return 2;}
        finally{if(phrase!=null){java.util.Arrays.fill(phrase,'\0');}}
    }
}
