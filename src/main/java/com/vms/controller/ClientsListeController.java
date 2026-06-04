package com.vms.controller;

import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;
import com.vms.dao.ClientDAO;
import com.vms.model.Client;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

public class ClientsListeController {

    @FXML private Label     lblTotalClients;
    @FXML private TextField txtRecherche;
    @FXML private HBox      btnNouveauClientZone;

    @FXML private TableView<Client>           tableView;
    @FXML private TableColumn<Client, String> colSociete;
    @FXML private TableColumn<Client, String> colContact;
    @FXML private TableColumn<Client, String> colEmail;
    @FXML private TableColumn<Client, String> colTelephone;
    @FXML private TableColumn<Client, String> colVille;
    @FXML private TableColumn<Client, Void>   colActions;

    private final ClientDAO dao = new ClientDAO();
    private final ObservableList<Client> donnees = FXCollections.observableArrayList();
    private boolean estAdmin = false;

    @FXML
    public void initialize() {
        String role = SessionManager.getInstance().getRoleCourant();
        estAdmin = "ADMIN".equals(role);

        // Bouton nouveau client — admin seulement
        btnNouveauClientZone.setVisible(estAdmin);
        btnNouveauClientZone.setManaged(estAdmin);

        // Colonne actions — masquée pour comptable (lecture seule)
        colActions.setVisible(estAdmin);

        configurerColonnes();
        tableView.setItems(donnees);
        txtRecherche.textProperty().addListener((o, a, b) -> filtrer(b));
        chargerClients();
    }

    private void configurerColonnes() {
        colSociete.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getNomSociete() != null
                                && !data.getValue().getNomSociete().isBlank()
                                ? data.getValue().getNomSociete() : "—"));
        colSociete.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) setStyle("-fx-font-weight:600;-fx-text-fill:#1a2a4a;");
            }
        });

        colContact.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        nvl(data.getValue().getPrenom()) + " " + nvl(data.getValue().getNom())));

        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));

        colTelephone.setCellValueFactory(new PropertyValueFactory<>("telephone"));

        colVille.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        nvl(data.getValue().getVille())));

        // ── Colonne Actions — admin uniquement ───────────────
        colActions.setCellFactory(col -> new TableCell<>() {
            // Bouton Modifier — icône crayon avec tooltip
            private final Button btnModif = creerBouton(
                    "✎", "#dbeafe", "#1e40af", "Modifier ce client");
            // Bouton Supprimer — icône corbeille avec tooltip
            private final Button btnSuppr = creerBouton(
                    "✕", "#fee2e2", "#991b1b", "Supprimer ce client");
            private final HBox box = new HBox(6, btnModif, btnSuppr);

            {
                btnModif.setOnAction(e ->
                        onModifier(getTableView().getItems().get(getIndex())));
                btnSuppr.setOnAction(e ->
                        onSupprimer(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    /** Crée un bouton icône moderne avec tooltip */
    private Button creerBouton(String icone, String bgColor, String fgColor, String tooltip) {
        Button btn = new Button(icone);
        btn.setStyle(
                "-fx-background-color:" + bgColor + ";" +
                "-fx-text-fill:" + fgColor + ";" +
                "-fx-background-radius:7;" +
                "-fx-font-size:14px;" +
                "-fx-font-weight:700;" +
                "-fx-min-width:32;-fx-min-height:28;" +
                "-fx-cursor:hand;");
        btn.setTooltip(new Tooltip(tooltip));
        // Effet hover
        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e  -> btn.setOpacity(1.0));
        return btn;
    }

    // ── Modifier ─────────────────────────────────────────────
    private void onModifier(Client c) {
        afficherDialogue(c);
    }

    // ── Supprimer (soft delete) ───────────────────────────────
    private void onSupprimer(Client c) {
        // Vérifier si le client a des demandes actives
        long nbDemandes = compterDemandesClient(c);
        String avertissement = nbDemandes > 0
                ? "\n\n⚠️ Ce client a " + nbDemandes + " demande(s) associée(s).\n"
                  + "Les demandes resteront visibles dans le système."
                : "";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmer la suppression");
        confirm.setHeaderText("Supprimer le client " + nvl(c.getNomSociete()) + " ?");
        confirm.setContentText(
                "Contact : " + nvl(c.getPrenom()) + " " + nvl(c.getNom()) + "\n" +
                "Email   : " + nvl(c.getEmail()) +
                avertissement + "\n\n" +
                "Le client sera désactivé (non supprimé définitivement).");

        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK))
                .setText("Oui, supprimer");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL))
                .setText("Annuler");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE clients SET est_actif = FALSE, modifie_le = NOW() WHERE id = ?::uuid")) {
                    ps.setString(1, c.getId().toString());
                    ps.executeUpdate();
                    Platform.runLater(() -> {
                        chargerClients();
                        afficherInfo("✅ Client supprimé",
                                "Le client " + nvl(c.getNomSociete()) + " a été désactivé.");
                    });
                } catch (Exception e) {
                    Platform.runLater(() ->
                            afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    private long compterDemandesClient(Client c) {
        try (Connection conn = DatabaseConfig.getConnexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM demandes WHERE client_id = ?::uuid AND statut != 'ANNULEE'")) {
            ps.setString(1, c.getId().toString());
            var rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) { return 0; }
    }

    // ── Nouveau client ───────────────────────────────────────
    @FXML
    private void onNouveauClient() {
        afficherDialogue(null);
    }

    private void afficherDialogue(Client existant) {
        boolean isNouvel = (existant == null);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isNouvel ? "Nouveau client" : "Modifier — " + nvl(existant.getNomSociete()));
        dialog.setHeaderText(isNouvel
                ? "Remplir les informations du nouveau client"
                : "Modification de la fiche client");

        TextField txtSociete = new TextField(isNouvel ? "" : nvl(existant.getNomSociete()));
        TextField txtNom     = new TextField(isNouvel ? "" : nvl(existant.getNom()));
        TextField txtPrenom  = new TextField(isNouvel ? "" : nvl(existant.getPrenom()));
        TextField txtEmail   = new TextField(isNouvel ? "" : nvl(existant.getEmail()));
        TextField txtTel     = new TextField(isNouvel ? "" : nvl(existant.getTelephone()));
        TextField txtAdresse = new TextField(isNouvel ? "" : nvl(existant.getAdresse()));
        TextField txtVille   = new TextField(isNouvel ? "" : nvl(existant.getVille()));

        VBox contenu = new VBox(12,
                champ("Société / Entreprise", txtSociete, false),
                new HBox(10,
                        champ("Nom *",   txtNom,    true),
                        champ("Prénom",  txtPrenom, true)),
                new HBox(10,
                        champ("Email dispatch *", txtEmail, true),
                        champ("Téléphone",        txtTel,   true)),
                new HBox(10,
                        champ("Adresse", txtAdresse, true),
                        champ("Ville",   txtVille,   true))
        );
        contenu.setPrefWidth(480);
        contenu.setStyle("-fx-padding:10 0 0 0;");
        dialog.getDialogPane().setContent(contenu);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK))
                .setText(isNouvel ? "➕ Créer" : "💾 Enregistrer");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL))
                .setText("Annuler");

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (txtNom.getText().isBlank()) {
                afficherErreur("Champ manquant", "Le nom est obligatoire."); return;
            }
            if (txtEmail.getText().isBlank()) {
                afficherErreur("Champ manquant", "L'email est obligatoire."); return;
            }
            Client c = isNouvel ? new Client() : existant;
            c.setNom(txtNom.getText().trim());
            c.setPrenom(txtPrenom.getText().trim());
            c.setNomSociete(txtSociete.getText().trim());
            c.setEmail(txtEmail.getText().trim());
            c.setTelephone(txtTel.getText().trim());
            c.setAdresse(txtAdresse.getText().trim());
            c.setVille(txtVille.getText().trim());

            new Thread(() -> {
                try {
                    if (isNouvel) {
                        dao.enregistrer(c);
                    } else {
                        try (Connection conn = DatabaseConfig.getConnexion();
                             PreparedStatement ps = conn.prepareStatement("""
                                     UPDATE clients SET nom=?,prenom=?,nom_societe=?,
                                         email=?,telephone=?,adresse=?,ville=?,modifie_le=NOW()
                                     WHERE id=?::uuid""")) {
                            ps.setString(1, c.getNom());
                            ps.setString(2, c.getPrenom());
                            ps.setString(3, c.getNomSociete());
                            ps.setString(4, c.getEmail());
                            ps.setString(5, c.getTelephone());
                            ps.setString(6, c.getAdresse());
                            ps.setString(7, c.getVille());
                            ps.setString(8, c.getId().toString());
                            ps.executeUpdate();
                        }
                    }
                    Platform.runLater(() -> {
                        chargerClients();
                        afficherInfo("✅ " + (isNouvel ? "Client créé" : "Client mis à jour"),
                                nvl(c.getNomSociete()) + " — " + c.getNom());
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    // ── Chargement / Filtre ──────────────────────────────────
    private void chargerClients() {
        new Thread(() -> {
            List<Client> liste = dao.listerTous();
            Platform.runLater(() -> {
                donnees.setAll(liste);
                lblTotalClients.setText(liste.size() + " client(s)");
            });
        }).start();
    }

    private void filtrer(String recherche) {
        if (recherche == null || recherche.isBlank()) { chargerClients(); return; }
        String r = recherche.toLowerCase();
        new Thread(() -> {
            List<Client> filtres = dao.listerTous().stream().filter(c ->
                    contient(c.getNom(), r) || contient(c.getPrenom(), r)
                 || contient(c.getNomSociete(), r) || contient(c.getEmail(), r)
                 || contient(c.getVille(), r)
            ).toList();
            Platform.runLater(() -> {
                donnees.setAll(filtres);
                lblTotalClients.setText(filtres.size() + " client(s) trouvé(s)");
            });
        }).start();
    }

    // ── Helpers ──────────────────────────────────────────────
    private boolean contient(String val, String r) {
        return val != null && val.toLowerCase().contains(r);
    }

    private VBox champ(String label, TextField field, boolean hgrow) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#374151;");
        field.setStyle("-fx-font-size:12px;-fx-padding:8 10;-fx-border-color:#d1d5db;" +
                       "-fx-border-radius:6;-fx-background-radius:6;-fx-background-color:#f9fafb;");
        VBox box = new VBox(4, lbl, field);
        if (hgrow) HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private void afficherInfo(String titre, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(titre); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private void afficherErreur(String titre, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(titre); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
