package me.bardrinks.models;

public enum DrinkType {

    CERVEJA(1001, 20),
    VERDE(1002, 5),
    LARANJA(1003, 10),
    VERMELHO(1004, 15);

    private final int modelData;
    private final int birita;

    DrinkType(int modelData, int birita) {
        this.modelData = modelData;
        this.birita = birita;
    }

    public int getModelData() {
        return modelData;
    }

    public int getBirita() {
        return birita;
    }

}
