package hai913i.tp1.spoon;

import java.nio.file.Path;
import java.nio.file.Paths;

import hai913i.tp1.parse.ProjectSources;
import spoon.Launcher;
import spoon.SpoonException;
import spoon.reflect.CtModel;

/**
 * Construction du modèle Spoon d'un projet (partie C1).
 *
 * Réglages repris de l'annexe « Eclipse JDT et Spoon », § 2.1.
 */
public final class SpoonModel {

    private SpoonModel() {
    }

    /**
     * @throws IllegalArgumentException si Spoon ne parvient pas à construire le modèle
     */
    public static CtModel build(ProjectSources sources) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sources.sourceRoot().toString());
        // Les bibliothèques du projet analysé ne sont pas fournies : Spoon construit quand même le modèle
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setCommentEnabled(false);
        // Analyse seule : ni compilation, ni fichiers produits
        launcher.getEnvironment().setShouldCompile(false);
        Path out = Paths.get(System.getProperty("java.io.tmpdir"), "spooned");
        launcher.setSourceOutputDirectory(out.toString());
        launcher.getEnvironment().setOutputType(spoon.OutputType.NO_OUTPUT);

        try {
            return launcher.buildModel();
        } catch (SpoonException e) {
            throw new IllegalArgumentException("Spoon n'a pas pu construire le modèle de "
                    + sources.sourceRoot() + " : " + e.getMessage());
        }
    }
}
