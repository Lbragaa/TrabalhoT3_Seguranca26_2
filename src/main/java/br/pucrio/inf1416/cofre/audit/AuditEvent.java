/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.audit;

/** Exact assignment event catalog (Trabalho3.pdf, pages 8–9). Not a logging service. */
public enum AuditEvent {
    E1001(1001, "Sistema iniciado."),
    E1002(1002, "Sistema encerrado."),
    E1003(1003, "Sessão iniciada para <login_name>."),
    E1004(1004, "Sessão encerrada para <login_name>."),
    E1005(1005, "Partida do sistema iniciada para cadastro do administrador."),
    E1006(1006, "Partida do sistema iniciada para operação normal pelos usuários."),
    E2001(2001, "Autenticação etapa 1 iniciada."),
    E2002(2002, "Autenticação etapa 1 encerrada."),
    E2003(2003, "Login name <login_name> identificado com acesso liberado."),
    E2004(2004, "Login name <login_name> identificado com acesso bloqueado."),
    E2005(2005, "Login name <login_name> não identificado."),
    E3001(3001, "Autenticação etapa 2 iniciada para <login_name>."),
    E3002(3002, "Autenticação etapa 2 encerrada para <login_name>."),
    E3003(3003, "Senha pessoal verificada positivamente para <login_name>."),
    E3004(3004, "Primeiro erro da senha pessoal contabilizado para <login_name>."),
    E3005(3005, "Segundo erro da senha pessoal contabilizado para <login_name>."),
    E3006(3006, "Terceiro erro da senha pessoal contabilizado para <login_name>."),
    E3007(3007, "Acesso do usuario <login_name> bloqueado pela autenticação etapa 2."),
    E4001(4001, "Autenticação etapa 3 iniciada para <login_name>."),
    E4002(4002, "Autenticação etapa 3 encerrada para <login_name>."),
    E4003(4003, "Token verificado positivamente para <login_name>."),
    E4004(4004, "Primeiro erro de token contabilizado para <login_name>."),
    E4005(4005, "Segundo erro de token contabilizado para <login_name>."),
    E4006(4006, "Terceiro erro de token contabilizado para <login_name>."),
    E4007(4007, "Acesso do usuario <login_name> bloqueado pela autenticação etapa 3."),
    E5001(5001, "Tela principal apresentada para <login_name>."),
    E5002(5002, "Opção 1 do menu principal selecionada por <login_name>."),
    E5003(5003, "Opção 2 do menu principal selecionada por <login_name>."),
    E5004(5004, "Opção 3 do menu principal selecionada por <login_name>."),
    E6001(6001, "Tela de cadastro apresentada para <login_name>."),
    E6002(6002, "Botão cadastrar pressionado por <login_name>."),
    E6003(6003, "Senha pessoal inválida fornecida por <login_name>."),
    E6004(6004, "Caminho do certificado digital inválido fornecido por <login_name>."),
    E6005(6005, "Chave privada verificada negativamente para <login_name> (caminho inválido)."),
    E6006(6006, "Chave privada verificada negativamente para <login_name> (frase secreta inválida)."),
    E6007(6007, "Chave privada verificada negativamente para <login_name> (assinatura digital inválida)."),
    E6008(6008, "Confirmação de dados aceita por <login_name>."),
    E6009(6009, "Confirmação de dados rejeitada por <login_name>."),
    E6010(6010, "Botão voltar de cadastro para o menu principal pressionado por <login_name>."),
    E7001(7001, "Tela de consulta de arquivos secretos apresentada para <login_name>."),
    E7002(7002, "Botão voltar de consulta para o menu principal pressionado por <login_name>."),
    E7003(7003, "Botão Listar de consulta pressionado por <login_name>."),
    E7004(7004, "Caminho de pasta inválido fornecido por <login_name>."),
    E7005(7005, "Arquivo de índice decriptado com sucesso para <login_name>."),
    E7006(7006, "Arquivo de índice verificado (integridade e autenticidade) com sucesso para <login_name>."),
    E7007(7007, "Falha na decriptação do arquivo de índice para <login_name>."),
    E7008(7008, "Falha na verificação (integridade e autenticidade) do arquivo de índice para <login_name>."),
    E7009(7009, "Lista de arquivos presentes no índice apresentada para <login_name>."),
    E7010(7010, "Arquivo de share decriptado com sucesso para <login_name>."),
    E7011(7011, "Arquivo de share verificado (integridade e autenticidade) com sucesso para <login_name>."),
    E7012(7012, "Falha na decriptação do arquivo de share para <login_name>."),
    E7013(7013, "Falha na verificação (integridade e autenticidade) do arquivo de share para <login_name>."),
    E7014(7014, "Lista de arquivos presentes no share apresentada para <login_name>."),
    E7015(7015, "Arquivo <arq_name> selecionado por <login_name> para decriptação."),
    E7016(7016, "Acesso permitido ao arquivo <arq_name> para <login_name>."),
    E7017(7017, "Acesso negado ao arquivo <arq_name> para <login_name>."),
    E7018(7018, "Arquivo <arq_name> decriptado com sucesso para <login_name>."),
    E7019(7019, "Arquivo <arq_name> verificado (integridade e autenticidade) com sucesso para <login_name>."),
    E7020(7020, "Falha na decriptação do arquivo <arq_name> para <login_name>."),
    E7021(7021, "Falha na verificação (integridade e autenticidade) do arquivo <arq_name> para <login_name>."),
    E8001(8001, "Tela de saída apresentada para <login_name>."),
    E8002(8002, "Botão encerrar sessão pressionado por <login_name>."),
    E8003(8003, "Botão encerrar sistema pressionado por <login_name>."),
    E8004(8004, "Botão voltar de sair para o menu principal pressionado por <login_name>.");

    private final int code;
    private final String template;

    AuditEvent(int code, String template) {
        this.code = code;
        this.template = template;
    }

    public int code() { return code; }
    public String template() { return template; }

    public static AuditEvent fromCode(int code) {
        for (AuditEvent event : values()) {
            if (event.code == code) { return event; }
        }
        throw new IllegalArgumentException("Unknown audit event code: " + code);
    }
}
