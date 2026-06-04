package com.vms.dao;

import com.vms.config.DatabaseConfig;
import com.vms.model.Client;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientDAO {

    /** Tous les clients actifs pour la liste déroulante */
    public List<Client> listerTous() {
        List<Client> liste = new ArrayList<>();
        String sql = "SELECT id, code_client, nom, prenom, nom_societe, email, telephone, adresse, ville " +
                     "FROM clients WHERE est_actif = TRUE ORDER BY nom, prenom";
        try (Connection conn = DatabaseConfig.getConnexion();
             Statement st   = conn.createStatement();
             ResultSet rs   = st.executeQuery(sql)) {
            while (rs.next()) liste.add(mapper(rs));
        } catch (SQLException e) {
            System.err.println("Erreur chargement clients : " + e.getMessage());
        }
        return liste;
    }

    /** Enregistrer un nouveau client saisi manuellement */
    public Client enregistrer(Client c) throws SQLException {
        // Générer un code client automatique
        String code = genererCode();
        String sql = """
                INSERT INTO clients(code_client, nom, prenom, nom_societe, email, telephone, adresse, ville)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, code_client
                """;
        try (Connection conn = DatabaseConfig.getConnexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, c.getNom());
            ps.setString(3, c.getPrenom());
            ps.setString(4, c.getNomSociete());
            ps.setString(5, c.getEmail());
            ps.setString(6, c.getTelephone());
            ps.setString(7, c.getAdresse());
            ps.setString(8, c.getVille());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                c.setId(UUID.fromString(rs.getString("id")));
                c.setCodeClient(rs.getString("code_client"));
            }
        }
        return c;
    }

    private String genererCode() throws SQLException {
        String sql = "SELECT 'CLI-' || LPAD((COUNT(*)+1)::TEXT, 4, '0') FROM clients";
        try (Connection conn = DatabaseConfig.getConnexion();
             Statement st   = conn.createStatement();
             ResultSet rs   = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "CLI-0001";
        }
    }

    private Client mapper(ResultSet rs) throws SQLException {
        Client c = new Client();
        c.setId(UUID.fromString(rs.getString("id")));
        c.setCodeClient(rs.getString("code_client"));
        c.setNom(rs.getString("nom"));
        c.setPrenom(rs.getString("prenom"));
        c.setNomSociete(rs.getString("nom_societe"));
        c.setEmail(rs.getString("email"));
        c.setTelephone(rs.getString("telephone"));
        c.setAdresse(rs.getString("adresse"));
        c.setVille(rs.getString("ville"));
        return c;
    }
}
