package com.iceibank.service;

public final class CompararVetores {
    private CompararVetores() {}

    public static String comparar(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("Vetores devem ter o mesmo tamanho.");
        }
        boolean aLeqB = true, bLeqA = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] < 0 || b[i] < 0) throw new IllegalArgumentException("Contadores não podem ser negativos.");
            if (a[i] > b[i]) aLeqB = false;
            if (b[i] > a[i]) bLeqA = false;
        }
        if (aLeqB && bLeqA) return "IGUAIS";
        if (aLeqB) return "ANTES";
        if (bLeqA) return "DEPOIS";
        return "CONCORRENTES";
    }
}
