package com.vms.controller;

import com.vms.config.DatabaseConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;

public class ParametresController {

    @FXML private TextField txtNomOrg;
    @FXML private TextField txtEmailExp;
    @FXML private TextField txtDureeValidite;
    @FXML private TextField txtDelaiAlerte;
    @FXML private Label     lblStatut;

    @FXML
    public void initialize() {
        charger();
    }

    private void charger() {
        new Thread(() -> {
            try (Connection conn = DatabaseConfig.getConnexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT cle, valeur FROM parametres")) {
                String nomOrg = "", emailExp = "", duree = "365", alerte = "30";
                while (rs.next()) {
                    switch (rs.getString("cle")) {
                        case "org_nom"            -> nomOrg  = rs.getString("valeur");
                        case "org_email"          -> emailExp = rs.getString("valeur");
                        case "bon_duree_validite" -> duree   = rs.getString("valeur");
                        case "alerte_expiration"  -> alerte  = rs.getString("valeur");
                    }
                }
                final String n = nomOrg, e = emailExp, d = duree, a = alerte;
                Platform.runLater(() -> {
                    txtNomOrg.setText(n);
                    txtEmailExp.setText(e);
                    txtDureeValidite.setText(d);
                    txtDelaiAlerte.setText(a);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> afficherStatut("Erreur chargement : " + e.getMessage(), false));
            }
        }).start();
    }

    @FXML
    private void onEnregistrer() {
        // Validation
        if (txtNomOrg.getText().isBlank() || txtEmailExp.getText().isBlank()) {
            afficherStatut("Nom organisation et email sont obligatoires.", false); return;
        }
        try {
            int duree  = Integer.parseInt(txtDureeValidite.getText().trim());
            int alerte = Integer.parseInt(txtDelaiAlerte.getText().trim());
            if (duree <= 0 || alerte <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            afficherStatut("Duree et delai doivent etre des nombres positifs.", false); return;
        }

        new Thread(() -> {
            try (Connection conn = DatabaseConfig.getConnexion();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO parametres(cle, valeur, modifie_le) VALUES(?, ?, NOW()) " +
                         "ON CONFLICT(cle) DO UPDATE SET valeur=EXCLUDED.valeur, modifie_le=NOW()")) {

                String[][] params = {
                    {"org_nom",            txtNomOrg.getText().trim()},
                    {"org_email",          txtEmailExp.getText().trim()},
                    {"bon_duree_validite", txtDureeValidite.getText().trim()},
                    {"alerte_expiration",  txtDelaiAlerte.getText().trim()}
                };
                for (String[] p : params) {
                    ps.setString(1, p[0]);
                    ps.setString(2, p[1]);
                    ps.executeUpdate();
                }
                Platform.runLater(() ->
                        afficherStatut("Parametres enregistres avec succes.", true));
            } catch (SQLException e) {
                Platform.runLater(() ->
                        afficherStatut("Erreur : " + e.getMessage(), false));
            }
        }).start();
    }

    @FXML
    private void onReinitialiser() {
        charger();
        afficherStatut("Valeurs rechargees depuis la base.", true);
    }

    private void afficherStatut(String msg, boolean ok) {
        lblStatut.setText(msg);
        lblStatut.setStyle(ok
                ? "-fx-text-fill:#065f46;-fx-background-color:#d1fae5;" +
                  "-fx-background-radius:8;-fx-padding:8 14;"
                : "-fx-text-fill:#991b1b;-fx-background-color:#fee2e2;" +
                  "-fx-background-radius:8;-fx-padding:8 14;");
        lblStatut.setVisible(true);
    }

    @FXML private void onRetourDashboard() { naviguer("/fxml/dashboard.fxml"); }
    private void naviguer(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) txtNomOrg.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) { e.printStackTrace(); }
    }
}
