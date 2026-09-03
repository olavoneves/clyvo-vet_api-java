package br.com.clyvovet.server.protocolo;

import java.util.List;

public record ProtocoloDetalheResponse(
        ProtocoloResponse protocolo,
        List<VersaoProtocoloResponse> versoes
) {}
