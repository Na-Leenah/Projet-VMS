package com.vms.controller;

import com.vms.dao.ClientDAO;
import com.vms.dao.DemandeDAO;
import com.vms.model.Client;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class DemandeFormController {

    // ── Étape client ─────────────────────────────────────────
    @FXML private ComboBox<Client> cbClient;
    @FXML private TextField        txtNomClient;
    @FXML private TextField        txtPrenomClient;
    @FXML private TextField        txtSocieteClient;
    @FXML private TextField        txtEmailClient;
    @FXML private TextField        txtTelClient;
    @FXML private VBox             panneauNouveauClient;
    @FXML private CheckBox         cbNouveauClient;

    // ── Paramètres bons ──────────────────────────────────────
    @FXML private TextField        txtNombreBons;
    @FXML private TextField        txtValeurUnitaire;
    @FXML private DatePicker       dpDateExpiration;
    @FXML private TextField        txtCcInternes;

    // ── Référence & notes ────────────────────────────────────
    @FXML private TextField        txtReference;     // readonly — auto-généré
    @FXML private TextField        txtMotif;
    @FXML private ComboBox<String> cbPriorite;

    // ── Total ────────────────────────────────────────────────
    @FXML private Label            lblMontantTotal;

    // ── Messages ─────────────────────────────────────────────
    @FXML private Label            lblErreur;
    @FXML private Label            lblSucces;

    private final ClientDAO  clientDAO  = new ClientDAO();
    private final DemandeDAO demandeDAO = new DemandeDAO();

    @FXML
    public void initialize() {
        // Charger les clients en arrière-plan
        new Thread(() -> {
            List<Client> clients = clientDAO.listerTous();
            Platform.runLater(() -> {
                cbClient.setItems(FXCollections.observableArrayList(clients));
                cbClient.setPromptText("-- Sélectionner ou saisir un client --");
            });
        }).start();

        // Priorités
        cbPriorite.setItems(FXCollections.observableArrayList("Normale", "Urgente"));
        cbPriorite.setValue("Normale");

        // Date expiration par défaut : 1 an
        dpDateExpiration.setValue(LocalDate.now().plusYears(1));

        // Calcul total automatique
        txtNombreBons.textProperty().addListener((o, a, b) -> calculerTotal());
        txtValeurUnitaire.textProperty().addListener((o, a, b) -> calculerTotal());

        // Remplir les champs quand un client est sélectionné
        cbClient.setOnAction(e -> {
            Client c = cbClient.getValue();
            if (c != null) remplirChampsClient(c);
        });

        // Panneau nouveau client masqué par défaut
        panneauNouveauClient.setVisible(false);
        panneauNouveauClient.setManaged(false);

        txtReference.setText("Auto-généré à l'enregistrement");
    }

    @FXML
    private void onNouveauClientToggle() {
        boolean nouveau = cbNouveauClient.isSelected();
        panneauNouveauClient.setVisible(nouveau);
        panneauNouveauClient.setManaged(nouveau);
        cbClient.setDisable(nouveau);
        if (nouveau) {
            viderChampsClient();
            cbClient.setValue(null);
        }
    }

    private void remplirChampsClient(Client c) {
        txtNomClient.setText(c.getNom()        != null ? c.getNom()        : "");
        txtPrenomClient.setText(c.getPrenom()  != null ? c.getPrenom()     : "");
        txtSocieteClient.setText(c.getNomSociete() != null ? c.getNomSociete() : "");
        txtEmailClient.setText(c.getEmail()    != null ? c.getEmail()      : "");
        txtTelClient.setText(c.getTelephone()  != null ? c.getTelephone()  : "");
    }

    private void viderChampsClient() {
        txtNomClient.clear(); txtPrenomClient.clear();
        txtSocieteClient.clear(); txtEmailClient.clear(); txtTelClient.clear();
    }

    private void calculerTotal() {
        try {
            int    nb  = Integer.parseInt(txtNombreBons.getText().trim());
            double val = Double.parseDouble(txtValeurUnitaire.getText().trim());
            lblMontantTotal.setText(String.format("Rs %,.0f", nb * val));
        } catch (NumberFormatException e) {
            lblMontantTotal.setText("Rs 0");
        }
    }

    @FXML
    private void onEnregistrer() {
        cacherMessages();

        // ── Validation ───────────────────────────────────────
        if (!cbNouveauClient.isSelected() && cbClient.getValue() == null) {
            afficherErreur("Veuillez sélectionner un client ou cocher « Nouveau client ».");
            return;
        }
        if (cbNouveauClient.isSelected()) {
            if (txtNomClient.getText().isBlank()) {
                afficherErreur("Le nom du client est obligatoire.");
                return;
            }
            if (txtEmailClient.getText().isBlank()) {
                afficherErreur("L'email du client est obligatoire pour l'envoi des bons.");
                return;
            }
        }
        if (txtNombreBons.getText().isBlank() || txtValeurUnitaire.getText().isBlank()) {
            afficherErreur("Le nombre de bons et la valeur unitaire sont obligatoires.");
            return;
        }

        int    nb;
        double valeur;
        try {
            nb     = Integer.parseInt(txtNombreBons.getText().trim());
            valeur = Double.parseDouble(txtValeurUnitaire.getText().trim());
            if (nb <= 0 || valeur <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            afficherErreur("Nombre de bons et valeur unitaire doivent être des nombres positifs.");
            return;
        }

        // ── Enregistrement en thread séparé ──────────────────
        new Thread(() -> {
            try {
                // Si nouveau client → créer d'abord
                Client client = cbClient.getValue();
                if (cbNouveauClient.isSelected()) {
                    Client nouveau = new Client();
                    nouveau.setNom(txtNomClient.getText().trim());
                    nouveau.setPrenom(txtPrenomClient.getText().trim());
                    nouveau.setNomSociete(txtSocieteClient.getText().trim());
                    nouveau.setEmail(txtEmailClient.getText().trim());
                    nouveau.setTelephone(txtTelClient.getText().trim());
                    client = clientDAO.enregistrer(nouveau);
                }

                // Créer la demande
                String ref = demandeDAO.enregistrer(
                        client.getId(),
                        demandeDAO.getSiegeId(),
                        nb, valeur,
                        dpDateExpiration.getValue(),
                        txtCcInternes.getText(),
                        txtMotif.getText(),
                        cbPriorite.getValue()
                );

                final String refFinal = ref;
                Platform.runLater(() -> {
                    txtReference.setText(refFinal);
                    afficherSucces("✅ Demande " + refFinal + " enregistrée avec succès !");
                    // Recharger les clients si nouveau client créé
                    if (cbNouveauClient.isSelected()) rechargerClients();
                });

            } catch (Exception e) {
                Platform.runLater(() ->
                        afficherErreur("Erreur : " + e.getMessage()));
            }
        }).start();
    }

    private void rechargerClients() {
        new Thread(() -> {
            List<Client> clients = clientDAO.listerTous();
            Platform.runLater(() ->
                    cbClient.setItems(FXCollections.observableArrayList(clients)));
        }).start();
    }

    @FXML
    private void onAnnuler() { retourDashboard(); }

    @FXML
    private void onRetourDashboard() { retourDashboard(); }

    private void retourDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) cbClient.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void afficherErreur(String msg) {
        lblErreur.setText(msg); lblErreur.setVisible(true); lblErreur.setManaged(true);
        lblSucces.setVisible(false); lblSucces.setManaged(false);
    }

    private void afficherSucces(String msg) {
        lblSucces.setText(msg); lblSucces.setVisible(true); lblSucces.setManaged(true);
        lblErreur.setVisible(false); lblErreur.setManaged(false);
    }

    private void cacherMessages() {
        lblErreur.setVisible(false); lblErreur.setManaged(false);
        lblSucces.setVisible(false); lblSucces.setManaged(false);
    }
}
