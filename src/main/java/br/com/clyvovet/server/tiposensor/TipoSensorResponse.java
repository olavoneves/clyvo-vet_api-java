package br.com.clyvovet.server.tiposensor;

public record TipoSensorResponse(Long id, String nome, String unidade, Double valorMin, Double valorMax) {

    public static TipoSensorResponse from(TipoSensor t) {
        return new TipoSensorResponse(t.getId(), t.getNome(), t.getUnidade(), t.getValorMin(), t.getValorMax());
    }
}
