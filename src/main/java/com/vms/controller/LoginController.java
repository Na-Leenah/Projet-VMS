package com.vms.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;
import com.vms.dao.UtilisateurDAO;
import com.vms.model.Utilisateur;

import java.io.IOException;

public class LoginController {

    @FXML private TextField     champEmail;
    @FXML private PasswordField champMotDePasse;
    @FXML private TextField     champMotDePasseVisible;
    @FXML private Button        btnConnexion;
    @FXML private Label         lblErreur;
    @FXML private Label         lblStatutBD;
    @FXML private CheckBox      cbAfficherMDP;

    private final UtilisateurDAO utilisateurDAO = new UtilisateurDAO();

    @FXML
    public void initialize() {
        if (champMotDePasseVisible != null) {
            champMotDePasseVisible.setVisible(false);
            champMotDePasseVisible.setManaged(false);
            champMotDePasseVisible.textProperty()
                    .bindBidirectional(champMotDePasse.textProperty());
        }
        verifierConnexionBD();
        Platform.runLater(() -> champEmail.requestFocus());
    }

    private void verifierConnexionBD() {
        new Thread(() -> {
            boolean ok = DatabaseConfig.testerConnexion();
            Platform.runLater(() -> {
                if (lblStatutBD != null) {
                    lblStatutBD.setText(ok ? "● Base de données connectée" : "● Base de données inaccessible");
                    lblStatutBD.setStyle(ok
                            ? "-fx-text-fill:#059669;-fx-font-size:11px;"
                            : "-fx-text-fill:#dc2626;-fx-font-size:11px;");
                }
            });
        }).start();
    }

    @FXML
    private void onConnexion() {
        String email = champEmail.getText().trim();
        String mdp   = champMotDePasse.getText();

        if (email.isEmpty() || mdp.isEmpty()) {
            afficherErreur("Veuillez renseigner l'email et le mot de passe.");
            return;
        }

        btnConnexion.setDisable(true);
        btnConnexion.setText("Connexion...");
        cacherErreur();

        new Thread(() -> {
            Utilisateur u = utilisateurDAO.authentifier(email, mdp);
            Platform.runLater(() -> {
                btnConnexion.setDisable(false);
                btnConnexion.setText("Se connecter");
                if (u != null) {
                    SessionManager.getInstance().setUtilisateurConnecte(u);
                    ouvrirDashboard();
                } else {
                    afficherErreur("Email ou mot de passe incorrect.");
                    champMotDePasse.clear();
                    champEmail.requestFocus();
                }
            });
        }).start();
    }

    @FXML
    private void onToucheEntre(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) onConnexion();
    }

    @FXML
    private void onAfficherMDP() {
        if (champMotDePasseVisible == null) return;
        boolean visible = cbAfficherMDP.isSelected();
        champMotDePasse.setVisible(!visible);
        champMotDePasse.setManaged(!visible);
        champMotDePasseVisible.setVisible(visible);
        champMotDePasseVisible.setManaged(visible);
    }

    private void ouvrirDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) btnConnexion.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("VMS – " + SessionManager.getInstance()
                    .getUtilisateurConnecte().getNomComplet());
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            afficherErreur("Erreur chargement : " + e.getMessage());
        }
    }

    private void afficherErreur(String msg) { lblErreur.setText(msg); lblErreur.setVisible(true); }
    private void cacherErreur()             { lblErreur.setVisible(false); }
}
