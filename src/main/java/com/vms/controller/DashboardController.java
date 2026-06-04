package com.vms.controller;

import com.vms.config.SessionManager;
import com.vms.model.Utilisateur;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import com.vms.config.DatabaseConfig;

public class DashboardController {

    // Sidebar
    @FXML private Label lblUserNom;
    @FXML private Label lblUserRole;
    @FXML private Label lblUserInitiales;

    // Menus sidebar — visibilité selon rôle
    @FXML private VBox menuNouvelleDemandeBtn; // bouton topbar
    @FXML private VBox menuClients;
    @FXML private VBox menuMagasins;
    @FXML private VBox menuUtilisateurs;
    @FXML private VBox menuParametres;
    @FXML private VBox menuPaiements;
    @FXML private VBox menuApprobations;
    @FXML private VBox menuAudit;
    @FXML private VBox menuRapports;
    @FXML private VBox menuBons;

    // Topbar
    @FXML private Label lblRoleBanner;
    @FXML private Label lblPageSubtitle;
    @FXML private VBox  btnNouvelleDemandeZone; // zone bouton topbar

    // Stats
    @FXML private Label statVal1; @FXML private Label statLbl1; @FXML private Label statMeta1;
    @FXML private Label statVal2; @FXML private Label statLbl2; @FXML private Label statMeta2;
    @FXML private Label statVal3; @FXML private Label statLbl3; @FXML private Label statMeta3;
    @FXML private Label statVal4; @FXML private Label statLbl4; @FXML private Label statMeta4;

    @FXML private Label badgeDemandes;

    @FXML
    public void initialize() {
        Utilisateur u = SessionManager.getInstance().getUtilisateurConnecte();
        if (u == null) return;

        lblUserNom.setText(u.getNomComplet());
        lblUserRole.setText(u.getRoleLibelle());
        lblUserInitiales.setText(initiales(u));

        configurerParRole(u.getRole());
        new Thread(this::chargerStatistiques).start();
    }

    private void configurerParRole(String role) {
        // Masquer tout par défaut
        setVisible(menuClients,            false);
        setVisible(menuMagasins,           false);
        setVisible(menuUtilisateurs,       false);
        setVisible(menuParametres,         false);
        setVisible(menuPaiements,          false);
        setVisible(menuApprobations,       false);
        setVisible(menuAudit,              false);
        setVisible(menuRapports,           false);
        setVisible(menuBons,               false);
        setVisible(btnNouvelleDemandeZone, false);

        switch (role) {
            case "ADMIN" -> {
                lblRoleBanner.setText("🔒 Accès complet — Administrateur");
                lblRoleBanner.setStyle("-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;" +
                        "-fx-background-radius:8;-fx-padding:8 12;-fx-font-size:12px;");
                lblPageSubtitle.setText("Vue d'ensemble du système VMS");
                // Admin voit tout
                setVisible(menuClients,            true);
                setVisible(menuMagasins,           true);
                setVisible(menuUtilisateurs,       true);
                setVisible(menuParametres,         true);
                setVisible(menuPaiements,          true);
                setVisible(menuApprobations,       true);
                setVisible(menuAudit,              true);
                setVisible(menuRapports,           true);
                setVisible(menuBons,               true);
                setVisible(btnNouvelleDemandeZone, true);
            }
            case "COMPTABLE" -> {
                lblRoleBanner.setText("💰 Vue Comptable — Saisie & validation des paiements");
                lblRoleBanner.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#92400e;" +
                        "-fx-background-radius:8;-fx-padding:8 12;-fx-font-size:12px;");
                lblPageSubtitle.setText("Saisie des demandes et suivi des paiements");
                setVisible(menuPaiements,          true);
                setVisible(menuApprobations,       true); // lecture
                setVisible(menuClients,            true); // lecture
                setVisible(menuRapports,           true);
                setVisible(btnNouvelleDemandeZone, true);
            }
            case "APPROBATEUR" -> {
                lblRoleBanner.setText("✅ Vue Approbateur — Validation des demandes payées");
                lblRoleBanner.setStyle("-fx-background-color:#d1fae5;-fx-text-fill:#065f46;" +
                        "-fx-background-radius:8;-fx-padding:8 12;-fx-font-size:12px;");
                lblPageSubtitle.setText("Demandes en attente d'approbation");
                setVisible(menuApprobations,       true);
                setVisible(menuBons,               true); // lecture
                // Pas de bouton Nouvelle demande
            }
            case "SUPERVISEUR_MAGASIN" -> {
                lblRoleBanner.setText("🏪 Vue Superviseur — Rédemption des bons");
                lblRoleBanner.setStyle("-fx-background-color:#f3e8ff;-fx-text-fill:#6b21a8;" +
                        "-fx-background-radius:8;-fx-padding:8 12;-fx-font-size:12px;");
                lblPageSubtitle.setText("Scanner et valider les bons cadeaux");
                // Pas de bouton Nouvelle demande
            }
            default -> {
                lblRoleBanner.setText("Bienvenue dans VMS");
                lblPageSubtitle.setText("");
            }
        }
    }

    private void setVisible(VBox menu, boolean visible) {
        if (menu != null) { menu.setVisible(visible); menu.setManaged(visible); }
    }

    private void chargerStatistiques() {
        try (Connection conn = DatabaseConfig.getConnexion()) {
            String role = SessionManager.getInstance().getRoleCourant();

            long nbDemandes = compter(conn, "SELECT COUNT(*) FROM demandes");
            long nbAttenteP = compter(conn,
                    "SELECT COUNT(*) FROM demandes WHERE statut IN ('EN_ATTENTE','FACTUREE')");
            long nbAttenteA = compter(conn,
                    "SELECT COUNT(*) FROM demandes WHERE statut = 'PAIEMENT_VALIDE'");
            long nbBons     = compter(conn, "SELECT COUNT(*) FROM vouchers");
            long nbRedimes  = compter(conn,
                    "SELECT COUNT(*) FROM vouchers WHERE statut = 'REDIME'");
            long nbExpires  = compter(conn,
                    "SELECT COUNT(*) FROM vouchers WHERE statut = 'EXPIRE'");
            long nbBientot  = compter(conn,
                    "SELECT COUNT(*) FROM vouchers WHERE statut NOT IN ('REDIME','EXPIRE','ANNULE')" +
                    " AND date_expiration BETWEEN CURRENT_DATE AND CURRENT_DATE + 30");

            Platform.runLater(() -> {
                switch (role) {
                    case "ADMIN" -> setStats(
                            "Demandes totales",    nbDemandes, "total système",
                            "Attente paiement",    nbAttenteP, "à traiter",
                            "Bons générés",        nbBons,     "dont " + nbRedimes + " rédimés",
                            "Bons expirés",        nbExpires,  nbBientot + " proches expiration");
                    case "COMPTABLE" -> setStats(
                            "À valider",           nbAttenteP, "paiements en attente",
                            "En approbation",      nbAttenteA, "demandes payées",
                            "Demandes totales",    nbDemandes, "dans le système",
                            "Bons générés",        nbBons,     "total système");
                    case "APPROBATEUR" -> setStats(
                            "À approuver",         nbAttenteA, "paiements vérifiés",
                            "Bons générés",        nbBons,     "total système",
                            "Rédimés",             nbRedimes,  "bons utilisés",
                            "Expirés",             nbExpires,  "bons expirés");
                    default -> setStats(
                            "Bons actifs",         nbBons - nbRedimes - nbExpires, "dans le système",
                            "Rédimés",             nbRedimes,  "bons utilisés",
                            "Expirés",             nbExpires,  "bons expirés",
                            "Proches expiration",  nbBientot,  "dans les 30 jours");
                }
                if (badgeDemandes != null) {
                    long badge = "APPROBATEUR".equals(role) ? nbAttenteA : nbAttenteP;
                    badgeDemandes.setText(String.valueOf(badge));
                    badgeDemandes.setVisible(badge > 0);
                    badgeDemandes.setManaged(badge > 0);
                }
            });
        } catch (SQLException e) {
            System.err.println("Erreur stats : " + e.getMessage());
        }
    }

    private long compter(Connection conn, String sql) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private void setStats(String l1, long v1, String m1, String l2, long v2, String m2,
                          String l3, long v3, String m3, String l4, long v4, String m4) {
        statLbl1.setText(l1); statVal1.setText(String.valueOf(v1)); statMeta1.setText(m1);
        statLbl2.setText(l2); statVal2.setText(String.valueOf(v2)); statMeta2.setText(m2);
        statLbl3.setText(l3); statVal3.setText(String.valueOf(v3)); statMeta3.setText(m3);
        statLbl4.setText(l4); statVal4.setText(String.valueOf(v4)); statMeta4.setText(m4);
    }

    // Navigation
    @FXML private void onNouvelleDemandeAction() { naviguer("/fxml/demande_form.fxml"); }
    @FXML private void onVoirDemandes()          { naviguer("/fxml/demandes_liste.fxml"); }
    @FXML private void onVoirClients()           { naviguer("/fxml/clients_liste.fxml"); }
    @FXML private void onVoirBons()              { naviguer("/fxml/bons_liste.fxml"); }
    @FXML private void onVoirApprobations()      { naviguer("/fxml/approbation_liste.fxml"); }
    @FXML private void onVoirPaiements()         { naviguer("/fxml/paiement_liste.fxml"); }

    @FXML
    private void onDeconnexion() {
        SessionManager.getInstance().deconnecter();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) lblUserNom.getScene().getWindow();
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
            stage.setMaximized(false);
            stage.setTitle("VMS – Connexion");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void naviguer(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) lblUserNom.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) {
            System.err.println("Navigation impossible : " + fxml + " — " + e.getMessage());
        }
    }

    private String initiales(Utilisateur u) {
        String p = (u.getPrenom() != null && !u.getPrenom().isEmpty())
                ? String.valueOf(u.getPrenom().charAt(0)) : "";
        String n = (u.getNom() != null && !u.getNom().isEmpty())
                ? String.valueOf(u.getNom().charAt(0)) : "";
        return (p + n).toUpperCase();
    }
}
