package com.vms.controller;

import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;
import com.vms.dao.DemandeListeDAO;
import com.vms.model.Demande;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

public class DemandesListeController {

    @FXML private Label     lblTotalDemandes;
    @FXML private HBox      btnNouvelleDemandeZone;
    @FXML private Button    btnTous;
    @FXML private Button    btnAttentePaiement;
    @FXML private Button    btnAttenteAppro;
    @FXML private Button    btnEmis;
    @FXML private Button    btnAnnules;
    @FXML private TextField txtRecherche;

    @FXML private TableView<Demande>            tableView;
    @FXML private TableColumn<Demande, String>  colReference;
    @FXML private TableColumn<Demande, String>  colClient;
    @FXML private TableColumn<Demande, Integer> colNbBons;
    @FXML private TableColumn<Demande, String>  colMontant;
    @FXML private TableColumn<Demande, String>  colDate;
    @FXML private TableColumn<Demande, String>  colStatut;
    @FXML private TableColumn<Demande, Void>    colActions;

    private final DemandeListeDAO dao = new DemandeListeDAO();
    private String filtreActif = "TOUS";
    private final ObservableList<Demande> donnees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurerColonnes();
        tableView.setItems(donnees);

        String role = SessionManager.getInstance().getRoleCourant();
        boolean peutCreer = "ADMIN".equals(role) || "COMPTABLE".equals(role);
        btnNouvelleDemandeZone.setVisible(peutCreer);
        btnNouvelleDemandeZone.setManaged(peutCreer);

        txtRecherche.textProperty().addListener((o, a, b) -> chargerDonnees());
        chargerDonnees();
        chargerCompteurs();
    }

    private void configurerColonnes() {
        colReference.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colReference.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) setStyle("-fx-font-family:monospace;-fx-text-fill:#1e40af;-fx-font-weight:600;");
            }
        });

        colClient.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getClientAffichage()));
        colNbBons.setCellValueFactory(new PropertyValueFactory<>("nombreBons"));
        colMontant.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getMontantFormate()));
        colDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getDateDemande() != null
                                ? data.getValue().getDateDemande()
                                       .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"))
                                : ""));

        colStatut.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getStatutLibelle()));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.setStyle(getTableView().getItems().get(getIndex()).getStatutBadgeStyle()
                        + "-fx-background-radius:10;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });

        // ── Actions : icônes modernes identiques à Clients ───
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnModif = creerBtn("✎", "#dbeafe", "#1e40af", "Modifier la demande");
            private final Button btnAnnul = creerBtn("✕", "#fee2e2", "#991b1b", "Annuler la demande");
            private final HBox   box      = new HBox(6, btnModif, btnAnnul);
            {
                btnModif.setOnAction(e -> onModifier(getTableView().getItems().get(getIndex())));
                btnAnnul.setOnAction(e -> onAnnulerDemande(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Demande d = getTableView().getItems().get(getIndex());
                // Modifier : EN_ATTENTE ou FACTUREE seulement (avant paiement)
                boolean modifiable = "EN_ATTENTE".equals(d.getStatut())
                                  || "FACTUREE".equals(d.getStatut());
                // Annuler : tout sauf EMISE et ANNULEE
                boolean annulable  = !"ANNULEE".equals(d.getStatut())
                                  && !"EMISE".equals(d.getStatut());
                btnModif.setDisable(!modifiable);
                btnModif.setOpacity(modifiable ? 1.0 : 0.35);
                btnAnnul.setDisable(!annulable);
                btnAnnul.setOpacity(annulable ? 1.0 : 0.35);
                setGraphic(box);
            }
        });
    }

    private Button creerBtn(String icone, String bg, String fg, String tooltip) {
        Button btn = new Button(icone);
        btn.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";" +
                "-fx-background-radius:7;-fx-font-size:14px;-fx-font-weight:700;" +
                "-fx-min-width:32;-fx-min-height:28;-fx-cursor:hand;");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e  -> btn.setOpacity(1.0));
        return btn;
    }

    private void onModifier(Demande d) {
        if (!"EN_ATTENTE".equals(d.getStatut()) && !"FACTUREE".equals(d.getStatut())) {
            afficherInfo("Modification impossible",
                    "Le paiement a déjà été traité.\nSi une correction est nécessaire, créez une nouvelle demande.");
            return;
        }
        TextField txtNb  = new TextField(String.valueOf(d.getNombreBons()));
        TextField txtVal = new TextField(String.valueOf((int) d.getValeurUnitaire()));
        Label     lblTot = new Label(d.getMontantFormate());
        lblTot.setStyle("-fx-font-size:14px;-fx-font-weight:700;-fx-text-fill:#1e40af;");
        Runnable calc = () -> {
            try { lblTot.setText(String.format("Rs %,.0f",
                    Integer.parseInt(txtNb.getText().trim())
                    * Double.parseDouble(txtVal.getText().trim())));
            } catch (NumberFormatException ex) { lblTot.setText("—"); }
        };
        txtNb.textProperty().addListener((o,a,b) -> calc.run());
        txtVal.textProperty().addListener((o,a,b) -> calc.run());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier — " + d.getReference());
        dialog.setHeaderText(d.getClientAffichage() + " · " + d.getStatutLibelle());
        var contenu = new javafx.scene.layout.VBox(10,
                label("Nombre de bons :"), txtNb,
                label("Valeur unitaire (Rs) :"), txtVal,
                new HBox(10, label("Nouveau total :"), lblTot),
                new Label("⚠️  Modification loguée dans l'audit trail.") {{
                    setStyle("-fx-font-size:11px;-fx-text-fill:#92400e;" +
                             "-fx-background-color:#fef3c7;-fx-padding:6 10;-fx-background-radius:6;");
                }});
        contenu.setPrefWidth(360);
        dialog.getDialogPane().setContent(contenu);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("💾 Enregistrer");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Annuler");

        if (dialog.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            try {
                int nb = Integer.parseInt(txtNb.getText().trim());
                double val = Double.parseDouble(txtVal.getText().trim());
                if (nb <= 0 || val <= 0) throw new NumberFormatException();
                new Thread(() -> {
                    try (Connection conn = DatabaseConfig.getConnexion()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO audit_trail(type_action,entite,entite_id,utilisateur_id,description,details) " +
                                "VALUES('DEMANDE'::type_audit,'demandes',?::uuid,?::uuid,?,?::jsonb)")) {
                            ps.setString(1, d.getId().toString());
                            ps.setString(2, SessionManager.getInstance().getUtilisateurConnecte().getId().toString());
                            ps.setString(3, "Modification " + d.getReference() + " : " + d.getNombreBons() + "→" + nb + " bons, " + (int)d.getValeurUnitaire() + "→" + (int)val + " Rs");
                            ps.setString(4, String.format("{\"ancien_nb\":%d,\"nouveau_nb\":%d,\"ancienne_val\":%.0f,\"nouvelle_val\":%.0f}", d.getNombreBons(), nb, d.getValeurUnitaire(), val));
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE demandes SET nombre_bons=?,valeur_unitaire=?,modifie_le=NOW() WHERE id=?::uuid")) {
                            ps.setInt(1, nb); ps.setDouble(2, val); ps.setString(3, d.getId().toString());
                            ps.executeUpdate();
                        }
                        Platform.runLater(() -> { chargerDonnees(); chargerCompteurs(); });
                    } catch (Exception ex) {
                        Platform.runLater(() -> afficherErreur("Erreur", ex.getMessage()));
                    }
                }).start();
            } catch (NumberFormatException ex) {
                afficherErreur("Valeurs invalides", "Veuillez saisir des nombres positifs.");
            }
        }
    }

    private void onAnnulerDemande(Demande d) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmer l'annulation");
        confirm.setHeaderText("Annuler la demande " + d.getReference() + " ?");
        confirm.setContentText("Client : " + d.getClientAffichage() + "\nMontant : " + d.getMontantFormate() +
                "\n\nLa demande sera marquée ANNULÉE et loguée dans l'audit trail.");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Oui, annuler");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Non");

        if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO audit_trail(type_action,entite,entite_id,utilisateur_id,description) " +
                            "VALUES('DEMANDE'::type_audit,'demandes',?::uuid,?::uuid,?)")) {
                        ps.setString(1, d.getId().toString());
                        ps.setString(2, SessionManager.getInstance().getUtilisateurConnecte().getId().toString());
                        ps.setString(3, "Annulation demande " + d.getReference() + " — " + d.getClientAffichage());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE demandes SET statut='ANNULEE',modifie_le=NOW() WHERE id=?::uuid")) {
                        ps.setString(1, d.getId().toString()); ps.executeUpdate();
                    }
                    Platform.runLater(() -> { chargerDonnees(); chargerCompteurs(); });
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    private void chargerDonnees() {
        new Thread(() -> {
            List<Demande> liste = dao.lister(filtreActif, txtRecherche.getText());
            Platform.runLater(() -> { donnees.setAll(liste); lblTotalDemandes.setText(liste.size() + " demande(s)"); });
        }).start();
    }

    private void chargerCompteurs() {
        new Thread(() -> {
            int[] c = dao.compterParStatut();
            Platform.runLater(() -> {
                btnTous.setText("Tous (" + c[0] + ")");
                btnAttentePaiement.setText("Att. paiement (" + c[1] + ")");
                btnAttenteAppro.setText("Att. approbation (" + c[2] + ")");
                btnEmis.setText("Émis (" + c[3] + ")");
                btnAnnules.setText("Annulés (" + c[4] + ")");
            });
        }).start();
    }

    @FXML private void onFiltreToutes()          { setFiltre("TOUS"); }
    @FXML private void onFiltreAttentePaiement() { setFiltre("EN_ATTENTE"); }
    @FXML private void onFiltreAttenteAppro()    { setFiltre("PAIEMENT_VALIDE"); }
    @FXML private void onFiltreEmis()            { setFiltre("EMISE"); }
    @FXML private void onFiltreAnnules()         { setFiltre("ANNULEE"); }

    private void setFiltre(String f) {
        this.filtreActif = f; surlignerFiltreActif(); chargerDonnees();
    }

    private void surlignerFiltreActif() {
        String on  = "-fx-background-color:#1e40af;-fx-text-fill:white;-fx-background-radius:16;-fx-padding:4 14;-fx-font-size:11px;-fx-cursor:hand;";
        String off = "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;-fx-background-radius:16;-fx-padding:4 14;-fx-font-size:11px;-fx-cursor:hand;";
        btnTous.setStyle("TOUS".equals(filtreActif) ? on : off);
        btnAttentePaiement.setStyle("EN_ATTENTE".equals(filtreActif) ? on : off);
        btnAttenteAppro.setStyle("PAIEMENT_VALIDE".equals(filtreActif) ? on : off);
        btnEmis.setStyle("EMISE".equals(filtreActif) ? on : off);
        btnAnnules.setStyle("ANNULEE".equals(filtreActif) ? on : off);
    }

    @FXML private void onNouvelleDemandeAction() { naviguer("/fxml/demande_form.fxml"); }

    private void naviguer(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) tableView.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) { System.err.println("Erreur navigation : " + e.getMessage()); }
    }

    private Label label(String txt) {
        Label l = new Label(txt);
        l.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#374151;");
        return l;
    }
    private void afficherInfo(String t, String m)   { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait(); }
    private void afficherErreur(String t, String m) { Alert a = new Alert(Alert.AlertType.ERROR);       a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait(); }
}
