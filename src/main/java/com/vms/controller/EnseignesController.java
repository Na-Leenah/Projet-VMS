package com.vms.controller;

import com.vms.config.DatabaseConfig;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EnseignesController {

    @FXML private TableView<EnseigneRow>            tableView;
    @FXML private TableColumn<EnseigneRow, String>  colCode;
    @FXML private TableColumn<EnseigneRow, String>  colNom;
    @FXML private TableColumn<EnseigneRow, String>  colAdresse;
    @FXML private TableColumn<EnseigneRow, String>  colEmail;
    @FXML private TableColumn<EnseigneRow, String>  colType;
    @FXML private TableColumn<EnseigneRow, String>  colStatut;
    @FXML private TableColumn<EnseigneRow, Void>    colActions;

    @FXML private Label     lblTotal;
    @FXML private TextField txtRecherche;

    private final ObservableList<EnseigneRow> donnees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurerColonnes();
        tableView.setItems(donnees);
        txtRecherche.textProperty().addListener((o, a, b) -> charger());
        charger();
    }

    private void configurerColonnes() {
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colCode.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
                if (!empty) setStyle("-fx-font-family:monospace;-fx-font-weight:600;-fx-text-fill:#1e40af;");
            }
        });

        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colNom.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
                if (!empty) setStyle("-fx-font-weight:600;");
            }
        });

        colAdresse.setCellValueFactory(new PropertyValueFactory<>("adresse"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));

        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v);
                String style = "Siege".equals(v)
                        ? "-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;"
                        : "-fx-background-color:#f3e8ff;-fx-text-fill:#6b21a8;";
                badge.setStyle(style + "-fx-background-radius:10;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });

        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v);
                String style = "Actif".equals(v)
                        ? "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;"
                        : "-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;";
                badge.setStyle(style + "-fx-background-radius:10;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit   = creerBtn("E", "#dbeafe", "#1e40af", "Modifier");
            private final Button btnToggle = creerBtn("D", "#fef3c7", "#92400e", "Activer/Desactiver");
            private final HBox   box       = new HBox(6, btnEdit, btnToggle);
            {
                btnEdit.setOnAction(e   -> onModifier(getTableView().getItems().get(getIndex())));
                btnToggle.setOnAction(e -> onToggler(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                EnseigneRow row = getTableView().getItems().get(getIndex());
                // Ne pas desactiver le siege
                boolean estSiege = "Siege".equals(row.getType());
                btnToggle.setDisable(estSiege);
                btnToggle.setOpacity(estSiege ? 0.3 : 1.0);
                btnToggle.setText("Actif".equals(row.getStatut()) ? "D" : "A");
                setGraphic(box);
            }
        });
    }

    private Button creerBtn(String t, String bg, String fg, String tooltip) {
        Button btn = new Button(t);
        btn.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";" +
                "-fx-background-radius:7;-fx-font-size:12px;-fx-font-weight:700;" +
                "-fx-min-width:32;-fx-min-height:28;-fx-cursor:hand;");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e  -> btn.setOpacity(1.0));
        return btn;
    }

    @FXML private void onNouvelleEnseigne() { afficherDialogue(null); }

    private void onModifier(EnseigneRow row)  { afficherDialogue(row); }

    private void onToggler(EnseigneRow row) {
        boolean estActif = "Actif".equals(row.getStatut());
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmer");
        confirm.setHeaderText((estActif ? "Desactiver" : "Activer") + " - " + row.getNom());
        confirm.setContentText("Etes-vous sur ?");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Oui");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Non");

        if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE societes SET est_actif=?, modifie_le=NOW() WHERE id=?::uuid")) {
                    ps.setBoolean(1, !estActif);
                    ps.setString(2, row.getId());
                    ps.executeUpdate();
                    Platform.runLater(this::charger);
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    private void afficherDialogue(EnseigneRow existant) {
        boolean isNouvel = (existant == null);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isNouvel ? "Nouvelle enseigne" : "Modifier - " + existant.getNom());

        TextField txtCode    = new TextField(isNouvel ? "" : nvl(existant.getCode()));
        TextField txtNom     = new TextField(isNouvel ? "" : nvl(existant.getNom()));
        TextField txtAdresse = new TextField(isNouvel ? "" : nvl(existant.getAdresse()));
        TextField txtTel     = new TextField(isNouvel ? "" : nvl(existant.getTelephone()));
        TextField txtEmail   = new TextField(isNouvel ? "" : nvl(existant.getEmail()));

        if (!isNouvel && "Siege".equals(existant.getType())) {
            txtCode.setDisable(true); // Code siege non modifiable
        }

        VBox contenu = new VBox(10,
                new HBox(10,
                        ligne("Code *",    txtCode,    true),
                        ligne("Nom *",     txtNom,     true)),
                ligne("Adresse",  txtAdresse, false),
                new HBox(10,
                        ligne("Telephone", txtTel,   true),
                        ligne("Email",     txtEmail, true))
        );
        contenu.setPrefWidth(460);
        contenu.setStyle("-fx-padding:10 0 0 0;");
        dialog.getDialogPane().setContent(contenu);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK))
                .setText(isNouvel ? "Creer" : "Enregistrer");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Annuler");

        if (dialog.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            if (txtCode.getText().isBlank() || txtNom.getText().isBlank()) {
                afficherErreur("Champ manquant", "Code et nom sont obligatoires."); return;
            }
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion()) {
                    if (isNouvel) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO societes(code,nom,adresse,telephone,email,est_siege) " +
                                "VALUES(?,?,?,?,?,FALSE)")) {
                            ps.setString(1, txtCode.getText().trim().toUpperCase());
                            ps.setString(2, txtNom.getText().trim());
                            ps.setString(3, txtAdresse.getText().trim());
                            ps.setString(4, txtTel.getText().trim());
                            ps.setString(5, txtEmail.getText().trim());
                            ps.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE societes SET code=?,nom=?,adresse=?,telephone=?,email=?," +
                                "modifie_le=NOW() WHERE id=?::uuid")) {
                            ps.setString(1, txtCode.getText().trim().toUpperCase());
                            ps.setString(2, txtNom.getText().trim());
                            ps.setString(3, txtAdresse.getText().trim());
                            ps.setString(4, txtTel.getText().trim());
                            ps.setString(5, txtEmail.getText().trim());
                            ps.setString(6, existant.getId());
                            ps.executeUpdate();
                        }
                    }
                    Platform.runLater(this::charger);
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    private void charger() {
        new Thread(() -> {
            List<EnseigneRow> liste = new ArrayList<>();
            String r = txtRecherche.getText();
            String sql = "SELECT id, code, nom, adresse, telephone, email, est_siege, est_actif " +
                         "FROM societes WHERE 1=1" +
                         (r != null && !r.isBlank() ? " AND (nom ILIKE '%" + r + "%' OR code ILIKE '%" + r + "%')" : "") +
                         " ORDER BY est_siege DESC, nom";
            try (Connection conn = DatabaseConfig.getConnexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    liste.add(new EnseigneRow(
                            rs.getString("id"), rs.getString("code"), rs.getString("nom"),
                            nvl(rs.getString("adresse")), nvl(rs.getString("telephone")),
                            nvl(rs.getString("email")),
                            rs.getBoolean("est_siege") ? "Siege" : "Magasin",
                            rs.getBoolean("est_actif") ? "Actif" : "Inactif"));
                }
            } catch (SQLException e) { System.err.println("Erreur enseignes : " + e.getMessage()); }
            Platform.runLater(() -> {
                donnees.setAll(liste);
                lblTotal.setText(liste.size() + " enseigne(s)");
            });
        }).start();
    }

    private VBox ligne(String label, TextField field, boolean hgrow) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#374151;");
        field.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, lbl, field);
        if (hgrow) HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private void afficherErreur(String t, String m) {
        Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }

    @FXML private void onRetourDashboard() { naviguer("/fxml/dashboard.fxml"); }
    private void naviguer(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) tableView.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static class EnseigneRow {
        private final String id, code, nom, adresse, telephone, email, type, statut;
        public EnseigneRow(String id, String code, String nom, String adresse,
                String telephone, String email, String type, String statut) {
            this.id=id; this.code=code; this.nom=nom; this.adresse=adresse;
            this.telephone=telephone; this.email=email; this.type=type; this.statut=statut;
        }
        public String getId()         { return id; }
        public String getCode()       { return code; }
        public String getNom()        { return nom; }
        public String getAdresse()    { return adresse; }
        public String getTelephone()  { return telephone; }
        public String getEmail()      { return email; }
        public String getType()       { return type; }
        public String getStatut()     { return statut; }
    }
}
