package com.vms.dao;

import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

public class DemandeDAO {

    /**
     * Enregistre une nouvelle demande.
     * La référence est auto-générée par le trigger PostgreSQL.
     * Retourne la référence générée (ex. VR0049-200).
     */
    public String enregistrer(UUID clientId,
                              UUID societeId,
                              int  nombreBons,
                              double valeurUnitaire,
                              LocalDate dateExpiration,
                              String ccInternes,
                              String motif,
                              String priorite) throws SQLException {

        String sql = """
                INSERT INTO demandes
                    (client_id, societe_id, nombre_bons, valeur_unitaire,
                     date_expiration_bons, remarques, cree_par)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING reference
                """;

        UUID userId = SessionManager.getInstance().getUtilisateurConnecte().getId();
        String remarques = buildRemarques(ccInternes, motif, priorite);

        try (Connection conn = DatabaseConfig.getConnexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, clientId);
            ps.setObject(2, societeId);
            ps.setInt   (3, nombreBons);
            ps.setDouble(4, valeurUnitaire);
            ps.setDate  (5, dateExpiration != null ? Date.valueOf(dateExpiration) : null);
            ps.setString(6, remarques);
            ps.setObject(7, userId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("reference");
        }
        return null;
    }

    private String buildRemarques(String cc, String motif, String priorite) {
        StringBuilder sb = new StringBuilder();
        if (motif    != null && !motif.isBlank())    sb.append("Motif: ").append(motif).append(" | ");
        if (priorite != null && !priorite.isBlank()) sb.append("Priorité: ").append(priorite).append(" | ");
        if (cc       != null && !cc.isBlank())       sb.append("CC: ").append(cc);
        return sb.toString().replaceAll(" \\| $", "");
    }

    /** Récupère la societe_id du siège (est_siege = TRUE) */
    public UUID getSiegeId() {
        try (Connection conn = DatabaseConfig.getConnexion();
             Statement st    = conn.createStatement();
             ResultSet rs    = st.executeQuery(
                     "SELECT id FROM societes WHERE est_siege = TRUE LIMIT 1")) {
            if (rs.next()) return UUID.fromString(rs.getString("id"));
        } catch (SQLException e) {
            System.err.println("Erreur getSiegeId : " + e.getMessage());
        }
        return null;
    }
}
