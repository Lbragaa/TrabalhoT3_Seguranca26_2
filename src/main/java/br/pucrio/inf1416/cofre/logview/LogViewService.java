package br.pucrio.inf1416.cofre.logview;

import br.pucrio.inf1416.cofre.audit.AuditEvent;
import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.regex.*;

/** Independent read-only viewer. Only authenticated Access instances can query audit records. */
public final class LogViewService {
    private final Database database;
    public LogViewService(Path database) throws IOException { this.database = Database.existingReadOnly(database); }
    public static final class Access implements AutoCloseable {
        private final LogViewService issuer;
        private final long uid;
        private final String email, certificate;
        private boolean active = true;
        private Access(LogViewService issuer, long uid, String email, String certificate) { this.issuer=issuer;this.uid=uid;this.email=email;this.certificate=certificate; }
        public String email() { return email; }
        @Override public synchronized void close() { active=false; }
        @Override public String toString() { return "LogViewAccess[redacted]"; }
    }
    public record Line(long rid, String timestamp, Integer code, String message, String publication) {
        public String display() { return timestamp+" | "+(code==null?"????":code)+" | "+message+(publication==null?"":" | publicação="+publication); }
    }
    public Access authenticate(Path encryptedKey, char[] phrase) throws IOException, GeneralSecurityException, SQLException {
        if (phrase == null || phrase.length==0 || phrase.length>255 || !Files.isRegularFile(encryptedKey) || !Files.isReadable(encryptedKey)) {
            throw new GeneralSecurityException("Autenticação do administrador recusada.");
        }
        PrivateKey key = new PrivateKeyService().restore(Files.readAllBytes(encryptedKey), phrase);
        Access matched=null;
        try (var c=database.open(); var s=c.prepareStatement("SELECT u.UID,u.email,k.certificate_pem FROM Usuarios u JOIN Chaveiro k ON k.KID=u.KID AND k.UID=u.UID WHERE u.GID=? ORDER BY u.UID")) {
            s.setInt(1,Group.ADMINISTRATOR.id());
            try(var rows=s.executeQuery()) {
                while(rows.next()) {
                    String pem=rows.getString(3), email=rows.getString(2);
                    try {
                        var cert=new CertificateService().parse(pem.getBytes(StandardCharsets.US_ASCII));
                        if(!new CertificateService().identity(cert).email().equals(email)){continue;}
                        // Exactly the assignment's fresh 2,048-byte possession challenge, per candidate.
                        new SignatureService().validateKeyPair(key,cert.getPublicKey(),2_048);
                        if(matched!=null){throw new GeneralSecurityException("Identidade administrativa ambígua.");}
                        matched=new Access(this,rows.getLong(1),email,pem);
                    }catch(SignatureException e){ /* Different registered administrator key. */ }
                }
            }
        }
        if(matched==null){throw new GeneralSecurityException("Autenticação do administrador recusada.");}
        return matched;
    }
    public void read(Access access, Consumer<Line> output) throws SQLException {
        if(access==null){throw new SecurityException("Autenticação administrativa necessária.");}
        synchronized(access){
            if(access.issuer!=this || !access.active){throw new SecurityException("Autenticação administrativa necessária.");}
            try(var c=database.open()){
                c.setAutoCommit(false);
                try(var s=c.prepareStatement("SELECT u.email,u.GID,k.certificate_pem FROM Usuarios u JOIN Chaveiro k ON k.UID=u.UID AND k.KID=u.KID WHERE u.UID=?")){
                    s.setLong(1,access.uid);try(var r=s.executeQuery()){
                        if(!r.next() || r.getInt(2)!=Group.ADMINISTRATOR.id() || !access.email.equals(r.getString(1)) || !access.certificate.equals(r.getString(3))){access.close();throw new SecurityException("Autorização administrativa revogada.");}
                    }
                }
                // Phase 4 databases have no publication_context; read them without modifying them.
                boolean publication=false;
                try(var s=c.createStatement();var r=s.executeQuery("PRAGMA table_info(Registros)")){while(r.next()){publication|="publication_context".equals(r.getString("name"));}}
                String sql="SELECT r.RID,r.timestamp,r.MID,m.texto,r.login_context,r.file_context,"+(publication?"r.publication_context":"NULL")+" FROM Registros r LEFT JOIN Mensagens m ON m.MID=r.MID ORDER BY r.timestamp ASC,r.RID ASC";
                try(var s=c.prepareStatement(sql);var r=s.executeQuery()){
                    while(r.next()){output.accept(line(r));}
                }
                c.commit();
            }
        }
    }
    private Line line(ResultSet r)throws SQLException{
        long rid=r.getLong(1);
        try{
            Object rawTime=r.getObject(2),rawCode=r.getObject(3);
            if(!(rawTime instanceof Integer || rawTime instanceof Long) || !(rawCode instanceof Integer || rawCode instanceof Long)){throw new IllegalArgumentException();}
            long time=((Number)rawTime).longValue();int code=((Number)rawCode).intValue();
            if(time<0 || ((Number)rawCode).longValue()!=code){throw new IllegalArgumentException();}
            var event=AuditEvent.fromCode(code);String template=r.getString(4);
            if(!event.template().equals(template)){throw new IllegalArgumentException();}
            String timestamp=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS XXX").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time));
            String publication=r.getString(7);
            if(publication!=null && (code!=7019 || !publication.matches("pending|published|failed"))){throw new IllegalArgumentException();}
            return new Line(rid,timestamp,code,resolve(template,r.getString(5),r.getString(6)),publication);
        }catch(IllegalArgumentException|DateTimeException e){return new Line(rid,"[data inválida]",null,"[registro inválido: RID "+rid+"]",null);}
    }
    static String resolve(String template,String login,String file){
        Matcher matcher=Pattern.compile("<login_name>|<arq_name>").matcher(template);StringBuffer text=new StringBuffer();
        while(matcher.find()){
            String value=matcher.group().equals("<login_name>")?login:file;
            matcher.appendReplacement(text,Matcher.quoteReplacement(value==null?matcher.group():safe(value)));
        }
        return matcher.appendTail(text).toString();
    }
    private static String safe(String value){
        if(value.length()>255){throw new IllegalArgumentException();}
        StringBuilder result=new StringBuilder();
        for(char ch:value.toCharArray()){
            if(Character.isISOControl(ch) || Character.getType(ch)==Character.FORMAT || ch=='\u2028' || ch=='\u2029'){result.append(String.format("\\u%04x",(int)ch));}else{result.append(ch);}
        }
        return result.toString();
    }
}
