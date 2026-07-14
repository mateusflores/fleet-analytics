package com.unesp.fleetanalyticsapi.model.enums;

public enum StateCode {
    AC("Acre"),
    AL("Alagoas"),
    AP("Amapá"),
    AM("Amazonas"),
    BA("Bahia"),
    CE("Ceará"),
    DF("Distrito Federal"),
    ES("Espírito Santo"),
    GO("Goiás"),
    MA("Maranhão"),
    MT("Mato Grosso"),
    MS("Mato Grosso do Sul"),
    MG("Minas Gerais"),
    PA("Pará"),
    PB("Paraíba"),
    PR("Paraná"),
    PE("Pernambuco"),
    PI("Piauí"),
    RJ("Rio de Janeiro"),
    RN("Rio Grande do Norte"),
    RS("Rio Grande do Sul"),
    RO("Rondônia"),
    RR("Roraima"),
    SC("Santa Catarina"),
    SP("São Paulo"),
    SE("Sergipe"),
    TO("Tocantins");

    private final String stateName;

    StateCode(String nome) {
        this.stateName = nome;
    }

    public String getStateName() {
        return stateName;
    }

    public String getStateCode() {
        return this.name();
    }

    public static StateCode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("A sigla não pode ser nula ou vazia.");
        }
        for (StateCode uf : StateCode.values()) {
            if (uf.name().equalsIgnoreCase(code.trim())) {
                return uf;
            }
        }
        throw new IllegalArgumentException("Nenhuma Unidade Federativa encontrada para a sigla: " + code);
    }
}