package com.vms.controller;

import com.vms.config.SessionManager;
import com.vms.model.Utilisateur;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class SidebarController {

    @FXML private Label lblUserNom;
    @FXML private Label lblUserRole;
    @FXML private Label lblUserInitiales;
    @FXML private Label badgeDemandes;

    @FXML private VBox menuBons;
    @FXML private VBox menuClients;
    @FXML private VBox menuMagasins;
    @FXML private VBox menuUtilisateurs;
    @FXML private VBox menuParametres;
    @FXML private VBox menuPaiements;
    @FXML private VBox menuApprobations;
    @FXML private VBox menuAudit;
    @FXML private VBox menuRapports;
    @FXML private VBox menuScan;

    @FXML
    public void initialize() {
        Utilisateur u = SessionManager.getInstance().getUtilisateurConnecte();
        if (u == null) return;
        lblUserNom.setText(u.getNomComplet());
        lblUserRole.setText(u.getRoleLibelle());
        lblUserInitiales.setText(initiales(u));
        configurerMenus(u.getRole());
    }

    private void configurerMenus(String role) {
        setV(menuBons,false); setV(menuClients,false); setV(menuMagasins,false);
        setV(menuUtilisateurs,false); setV(menuParametres,false); setV(menuPaiements,false);
        setV(menuApprobations,false); setV(menuAudit,false); setV(menuRapports,false);
        setV(menuScan,false);

        switch (role) {
            case "ADMIN" -> {
                setV(menuBons,true); setV(menuClients,true); setV(menuMagasins,true);
                setV(menuUtilisateurs,true); setV(menuParametres,true);
                setV(menuPaiements,true); setV(menuApprobations,true);
                setV(menuAudit,true); setV(menuRapports,true);
            }
            case "COMPTABLE" -> {
                setV(menuPaiements,true); setV(menuApprobations,true);
                setV(menuClients,true); setV(menuRapports,true);
            }
            case "APPROBATEUR" -> {
                setV(menuBons,true); setV(menuApprobations,true);
            }
            case "SUPERVISEUR_MAGASIN" -> {
                setV(menuScan,true);
            }
        }
    }

    private void setV(VBox v, boolean b) {
        if (v != null) { v.setVisible(b); v.setManaged(b); }
    }

    // ── Navigation — tous les liens branches ─────────────────
    @FXML public void onNavDashboard()    { naviguer("/fxml/dashboard.fxml"); }
    @FXML public void onNavDemandes()     { naviguer("/fxml/demandes_liste.fxml"); }
    @FXML public void onNavBons()         { naviguer("/fxml/bons_liste.fxml"); }
    @FXML public void onNavClients()      { naviguer("/fxml/clients_liste.fxml"); }
    @FXML public void onNavMagasins()     { naviguer("/fxml/enseignes.fxml"); }
    @FXML public void onNavUtilisateurs() { naviguer("/fxml/utilisateurs.fxml"); }
    @FXML public void onNavPaiements()    { naviguer("/fxml/paiement_liste.fxml"); }
    @FXML public void onNavApprobations() { naviguer("/fxml/approbation_liste.fxml"); }
    @FXML public void onNavRapports()     { naviguer("/fxml/rapports.fxml"); }
    @FXML public void onNavAudit()        { naviguer("/fxml/audit_trail.fxml"); }
    @FXML public void onNavParametres()   { naviguer("/fxml/parametres.fxml"); }
    @FXML public void onNavScan()         { naviguer("/fxml/scan_redemption.fxml"); }

    @FXML
    public void onDeconnexion() {
        SessionManager.getInstance().deconnecter();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = obtenirStage();
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
            stage.setMaximized(false);
            stage.setTitle("VMS - Connexion");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void naviguer(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = obtenirStage();
            if (stage == null) return;
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) {
            System.err.println("Nav impossible : " + fxml + " - " + e.getMessage());
        }
    }

    private Stage obtenirStage() {
        if (lblUserNom != null && lblUserNom.getScene() != null)
            return (Stage) lblUserNom.getScene().getWindow();
        return null;
    }

    private String initiales(Utilisateur u) {
        String p = (u.getPrenom() != null && !u.getPrenom().isEmpty())
                ? String.valueOf(u.getPrenom().charAt(0)) : "";
        String n = (u.getNom() != null && !u.getNom().isEmpty())
                ? String.valueOf(u.getNom().charAt(0)) : "";
        return (p + n).toUpperCase();
    }
}
