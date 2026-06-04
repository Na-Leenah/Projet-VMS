package com.vms.dao;

import com.vms.config.DatabaseConfig;
import com.vms.model.Demande;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DemandeListeDAO {

    /** Toutes les demandes avec filtre statut optionnel */
    public List<Demande> lister(String filtreStatut, String recherche) {
        List<Demande> liste = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.reference, d.nombre_bons, d.valeur_unitaire,
                       d.montant_total, d.statut, d.date_demande,
                       d.date_expiration_bons, d.remarques, d.cree_le,
                       c.nom AS client_nom, c.nom_societe
                FROM demandes d
                JOIN clients c ON c.id = d.client_id
                WHERE 1=1
                """);

        if (filtreStatut != null && !filtreStatut.isBlank() && !filtreStatut.equals("TOUS")) {
            sql.append(" AND d.statut = '").append(filtreStatut).append("'");
        }
        if (recherche != null && !recherche.isBlank()) {
            sql.append(" AND (d.reference ILIKE '%").append(recherche)
               .append("%' OR c.nom ILIKE '%").append(recherche)
               .append("%' OR c.nom_societe ILIKE '%").append(recherche).append("%')");
        }
        sql.append(" ORDER BY d.cree_le DESC");

        try (Connection conn = DatabaseConfig.getConnexion();
             Statement  st   = conn.createStatement();
             ResultSet  rs   = st.executeQuery(sql.toString())) {
            while (rs.next()) liste.add(mapper(rs));
        } catch (SQLException e) {
            System.err.println("Erreur liste demandes : " + e.getMessage());
        }
        return liste;
    }

    /** Compteurs par statut pour les badges filtres */
    public int[] compterParStatut() {
        // [0]=tous [1]=attente_paiement [2]=attente_appro [3]=emis [4]=annules
        int[] counts = new int[5];
        String sql = """
                SELECT statut, COUNT(*) FROM demandes GROUP BY statut
                """;
        try (Connection conn = DatabaseConfig.getConnexion();
             Statement  st   = conn.createStatement();
             ResultSet  rs   = st.executeQuery(sql)) {
            while (rs.next()) {
                String s = rs.getString("statut");
                int    n = rs.getInt(2);
                counts[0] += n;
                switch (s) {
                    case "EN_ATTENTE", "FACTUREE" -> counts[1] += n;
                    case "PAIEMENT_VALIDE"         -> counts[2] += n;
                    case "EMISE", "APPROUVEE"      -> counts[3] += n;
                    case "ANNULEE"                 -> counts[4] += n;
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur compteurs : " + e.getMessage());
        }
        return counts;
    }

    private Demande mapper(ResultSet rs) throws SQLException {
        Demande d = new Demande();
        d.setId(UUID.fromString(rs.getString("id")));
        d.setReference(rs.getString("reference"));
        d.setNombreBons(rs.getInt("nombre_bons"));
        d.setValeurUnitaire(rs.getDouble("valeur_unitaire"));
        d.setMontantTotal(rs.getDouble("montant_total"));
        d.setStatut(rs.getString("statut"));
        d.setClientNom(rs.getString("client_nom"));
        d.setClientSociete(rs.getString("nom_societe"));
        Date dd = rs.getDate("date_demande");
        if (dd != null) d.setDateDemande(dd.toLocalDate());
        Date de = rs.getDate("date_expiration_bons");
        if (de != null) d.setDateExpiration(de.toLocalDate());
        d.setRemarques(rs.getString("remarques"));
        Timestamp ts = rs.getTimestamp("cree_le");
        if (ts != null) d.setCreeLe(ts.toLocalDateTime());
        return d;
    }
}
