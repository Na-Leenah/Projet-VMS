package com.vms.dao;

import com.vms.config.DatabaseConfig;
import com.vms.model.Utilisateur;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.UUID;

/**
 * DAO authentification avec log connexions (succes et echecs).
 * Le trigger trg_log_connexion logue les succes automatiquement.
 * Les echecs sont logues via fn_log_connexion_echec().
 */
public class UtilisateurDAO {

    public Utilisateur authentifier(String email, String motDePasse) {
        String sql = """
                SELECT id, societe_id, nom, prenom, email, role,
                       est_actif, mot_de_passe, derniere_connexion
                FROM utilisateurs
                WHERE email = ?
                """;
        // Note : on ne filtre PAS sur est_actif ici pour pouvoir loger les tentatives
        // sur comptes inactifs

        try (Connection conn = DatabaseConfig.getConnexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email.trim().toLowerCase());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                // Email inexistant -> log echec
                loguerEchec(email, "COMPTE_INEXISTANT", null);
                return null;
            }

            boolean estActif = rs.getBoolean("est_actif");
            if (!estActif) {
                // Compte desactive -> log echec
                UUID uid = UUID.fromString(rs.getString("id"));
                loguerEchec(email, "COMPTE_INACTIF", uid);
                return null;
            }

            String hashStocke = rs.getString("mot_de_passe");
            boolean mdpOk = BCrypt.checkpw(motDePasse, hashStocke);

            if (!mdpOk) {
                // Mauvais mot de passe -> log echec
                UUID uid = UUID.fromString(rs.getString("id"));
                loguerEchec(email, "MOT_DE_PASSE_INCORRECT", uid);
                return null;
            }

            // Succes : mettre a jour derniere_connexion
            // -> le trigger trg_log_connexion va automatiquement loger le succes
            Utilisateur u = mapper(rs);
            mettreAJourConnexion(u.getId(), conn);
            return u;

        } catch (SQLException e) {
            System.err.println("Erreur authentification : " + e.getMessage());
        }
        return null;
    }

    /**
     * Log un echec de connexion via la fonction PostgreSQL.
     * Appelee depuis Java car le trigger ne couvre que les succes.
     */
    private void loguerEchec(String email, String motif, UUID userId) {
        String sql = "SELECT fn_log_connexion_echec(?, ?, ?::uuid)";
        try (Connection conn = DatabaseConfig.getConnexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, motif);
            ps.setString(3, userId != null ? userId.toString() : null);
            ps.execute();
        } catch (SQLException e) {
            System.err.println("Erreur log echec : " + e.getMessage());
        }
    }

    private void mettreAJourConnexion(UUID userId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE utilisateurs SET derniere_connexion = NOW() WHERE id = ?::uuid")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur MAJ connexion : " + e.getMessage());
        }
    }

    private Utilisateur mapper(ResultSet rs) throws SQLException {
        Utilisateur u = new Utilisateur();
        u.setId(UUID.fromString(rs.getString("id")));
        u.setSocieteId(UUID.fromString(rs.getString("societe_id")));
        u.setNom(rs.getString("nom"));
        u.setPrenom(rs.getString("prenom"));
        u.setEmail(rs.getString("email"));
        u.setRole(rs.getString("role"));
        u.setEstActif(rs.getBoolean("est_actif"));
        Timestamp ts = rs.getTimestamp("derniere_connexion");
        if (ts != null) u.setDerniereConnexion(ts.toLocalDateTime());
        return u;
    }
}
