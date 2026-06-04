package com.vms.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utilitaire de génération de hash BCrypt avec sel.
 * Exécuter via le bouton Run ▷ au-dessus de main dans VS Code.
 */
public class GenererHash {

    public static void main(String[] args) {

        // ── Définition des comptes avec leurs mots de passe ──
        // Tu peux changer les mots de passe ici librement
        String[][] comptes = {
            // { email,                      motDePasse          }
            { "nazrababdallah+admin@vms.mu",      "AliceAdmin@2026"   },
            { "nazrababdallah+comptable@vms.mu",     "BrunoCompta@2026"  },
            { "nazrababdallah+approbateur@vms.mu",     "ClaireAppro@2026"  },
            { "david.sup@vms.mu",        "DavidSup@2026"     },
        };

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║         VMS – Génération des hash BCrypt (sel=12)        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        StringBuilder sql = new StringBuilder();
        sql.append("-- =====================================================\n");
        sql.append("-- SQL à exécuter dans pgAdmin sur vms_db\n");
        sql.append("-- =====================================================\n\n");

        for (String[] compte : comptes) {
            String email  = compte[0];
            String mdp    = compte[1];

            // Génération du hash BCrypt avec sel (coût 12)
            String hash = BCrypt.hashpw(mdp, BCrypt.gensalt(12));

            // Vérification immédiate
            boolean ok = BCrypt.checkpw(mdp, hash);

            System.out.println("Email      : " + email);
            System.out.println("Mot passe  : " + mdp);
            System.out.println("Hash       : " + hash);
            System.out.println("Vérifié    : " + (ok ? "✅ OK" : "❌ ECHEC"));
            System.out.println("─────────────────────────────────────────────────────");

            sql.append("UPDATE utilisateurs\n");
            sql.append("SET    mot_de_passe = '").append(hash).append("'\n");
            sql.append("WHERE  email = '").append(email).append("';\n\n");
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║              SQL prêt à copier dans pgAdmin              ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println(sql);

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                 Récapitulatif des accès                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.printf("%-35s %-25s %-20s%n", "Email", "Mot de passe", "Rôle");
        System.out.println("─────────────────────────────────────────────────────────────────────────────");
        System.out.printf("%-35s %-25s %-20s%n", "nazrababdallah+admin@vms.mu,  "AliceAdmin@2026",  "ADMIN");
        System.out.printf("%-35s %-25s %-20s%n", "nazrababdallah+comptable@vms.mu", "BrunoCompta@2026", "COMPTABLE");
        System.out.printf("%-35s %-25s %-20s%n", "nazrababdallah+approbateur@vms.mu", "ClaireAppro@2026", "APPROBATEUR");
        System.out.printf("%-35s %-25s %-20s%n", "david.sup@vms.mu",    "DavidSup@2026",    "SUPERVISEUR_MAGASIN");
        System.out.println();
        System.out.println("⚠️  Conserve ce tableau précieusement – les hash ne sont pas réversibles !");
    }
}
