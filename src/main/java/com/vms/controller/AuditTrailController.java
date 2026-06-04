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
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuditTrailController {

    @FXML private TableView<AuditRow>            tableView;
    @FXML private TableColumn<AuditRow, String>  colDate;
    @FXML private TableColumn<AuditRow, String>  colType;
    @FXML private TableColumn<AuditRow, String>  colDescription;
    @FXML private TableColumn<AuditRow, String>  colUtilisateur;
    @FXML private Label     lblTotal;
    @FXML private TextField txtRecherche;
    @FXML private ComboBox<String> cbTypeFiltre;

    private final ObservableList<AuditRow> donnees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurerColonnes();
        tableView.setItems(donnees);

        cbTypeFiltre.setItems(FXCollections.observableArrayList(
                "Toutes les actions", "DEMANDE", "PAIEMENT", "APPROBATION",
                "GENERATION", "ENVOI", "REDEMPTION", "SYSTEME"));
        cbTypeFiltre.setValue("Toutes les actions");
        cbTypeFiltre.setOnAction(e -> charger());
        txtRecherche.textProperty().addListener((o, a, b) -> charger());

        charger();
    }

    private void configurerColonnes() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
                if (!empty) setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;-fx-font-family:monospace;");
            }
        });

        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v);
                String style = switch (v) {
                    case "DEMANDE"     -> "-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;";
                    case "PAIEMENT"    -> "-fx-background-color:#fef3c7;-fx-text-fill:#92400e;";
                    case "APPROBATION" -> "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
                    case "GENERATION"  -> "-fx-background-color:#f3e8ff;-fx-text-fill:#6b21a8;";
                    case "REDEMPTION"  -> "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
                    case "ENVOI"       -> "-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;";
                    default            -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                badge.setStyle(style + "-fx-background-radius:8;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });

        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colDescription.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
                if (!empty) setStyle("-fx-font-size:12px;");
                setWrapText(true);
            }
        });

        colUtilisateur.setCellValueFactory(new PropertyValueFactory<>("utilisateur"));
        colUtilisateur.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
                if (!empty) setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
            }
        });
    }

    private void charger() {
        new Thread(() -> {
            List<AuditRow> liste = new ArrayList<>();
            String filtre = cbTypeFiltre.getValue();
            String recherche = txtRecherche.getText();

            StringBuilder sql = new StringBuilder("""
                    SELECT a.type_action, a.description, a.cree_le,
                           u.prenom||' '||u.nom AS utilisateur
                    FROM audit_trail a
                    LEFT JOIN utilisateurs u ON u.id = a.utilisateur_id
                    WHERE 1=1
                    """);

            if (filtre != null && !filtre.equals("Toutes les actions")) {
                sql.append(" AND a.type_action = '").append(filtre).append("'::type_audit");
            }
            if (recherche != null && !recherche.isBlank()) {
                sql.append(" AND a.description ILIKE '%").append(recherche).append("%'");
            }
            sql.append(" ORDER BY a.cree_le DESC LIMIT 200");

            try (Connection conn = DatabaseConfig.getConnexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql.toString())) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("cree_le");
                    String date = ts != null ? ts.toLocalDateTime()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")) : "-";
                    liste.add(new AuditRow(
                            date,
                            rs.getString("type_action"),
                            rs.getString("description"),
                            rs.getString("utilisateur") != null ? rs.getString("utilisateur") : "Systeme"
                    ));
                }
            } catch (SQLException e) { System.err.println("Erreur audit : " + e.getMessage()); }

            Platform.runLater(() -> {
                donnees.setAll(liste);
                lblTotal.setText(liste.size() + " entree(s)");
            });
        }).start();
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

    public static class AuditRow {
        private final String date, type, description, utilisateur;
        public AuditRow(String date, String type, String description, String utilisateur) {
            this.date=date; this.type=type; this.description=description; this.utilisateur=utilisateur;
        }
        public String getDate()         { return date; }
        public String getType()         { return type; }
        public String getDescription()  { return description; }
        public String getUtilisateur()  { return utilisateur; }
    }
}
