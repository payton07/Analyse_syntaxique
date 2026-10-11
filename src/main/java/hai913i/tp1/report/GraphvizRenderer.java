package hai913i.tp1.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Produit l'image d'un graphe en appelant Graphviz (partie C2).
 *
 * Graphviz est un programme externe, facultatif : s'il n'est pas installé, l'outil le dit et le
 * fichier DOT reste utilisable. Le texte DOT est envoyé à la commande « dot » sur son entrée standard.
 */
public final class GraphvizRenderer {

    private GraphvizRenderer() {
    }

    /**
     * @param dot   le graphe au format DOT
     * @param image fichier à écrire ; son extension (svg, png, pdf) donne le format
     * @throws IllegalArgumentException si Graphviz est absent ou refuse le graphe
     */
    public static void render(String dot, Path image) throws IOException {
        String name = image.getFileName().toString();
        String format = name.substring(name.lastIndexOf('.') + 1).toLowerCase();

        Process process;
        try {
            process = new ProcessBuilder("dot", "-T" + format, "-o", image.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new IllegalArgumentException("Graphviz est introuvable (commande « dot ») : installez-le, "
                    + "ou utilisez --dot FICHIER pour obtenir le graphe au format DOT.");
        }

        try (OutputStream input = process.getOutputStream()) {
            input.write(dot.getBytes(StandardCharsets.UTF_8));
        }
        String messages = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
            if (process.waitFor() != 0) {
                throw new IllegalArgumentException("Graphviz n'a pas pu produire " + image + " : " + messages);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Graphviz a été interrompu.");
        }
    }
}
