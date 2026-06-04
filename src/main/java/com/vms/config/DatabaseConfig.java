package com.vms.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Gestion de la connexion JDBC à PostgreSQL.
 * Modifiez PASSWORD selon votre environnement.
 */
public class DatabaseConfig {

    private static final String URL      = "jdbc:postgresql://localhost:5432/vms_db";
    private static final String USER     = "postgres";
    private static final String PASSWORD = "nazra21"; // ← À CHANGER

    private static Connection connexion;

    public static Connection getConnexion() throws SQLException {
        if (connexion == null || connexion.isClosed()) {
            connexion = DriverManager.getConnection(URL, USER, PASSWORD);
            connexion.setAutoCommit(true);
        }
        return connexion;
    }

    public static void fermer() {
        try {
            if (connexion != null && !connexion.isClosed()) {
                connexion.close();
            }
        } catch (SQLException e) {
            System.err.println("Erreur fermeture connexion : " + e.getMessage());
        }
    }

    public static boolean testerConnexion() {
        try {
            getConnexion();
            return true;
        } catch (SQLException e) {
            System.err.println("Impossible de se connecter à la base : " + e.getMessage());
            return false;
        }
    }
}
