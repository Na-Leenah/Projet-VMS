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

public class BonsListeController {

    @FXML private TableView<BonRow>            tableView;
    @FXML private TableColumn<BonRow, String>  colCode;
    @FXML private TableColumn<BonRow, String>  colDemande;
    @FXML private TableColumn<BonRow, String>  colClient;
    @FXML private TableColumn<BonRow, String>  colValeur;
    @FXML private TableColumn<BonRow, String>  colExpiration;
    @FXML private TableColumn<BonRow, String>  colStatut;

    @FXML private Label     lblTotal;
    @FXML private TextField txtRecherche;
    @FXML private Button    btnTous;
    @FXML private Button    btnGeneres;
    @FXML private Button    btnRedimes;
    @FXML private Button    btnExpires;

    private final ObservableList<BonRow> donnees = FXCollections.observableArrayList();
    private String filtreActif = "TOUS";

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
                if (!empty) setStyle("-fx-font-family:monospace;-fx-font-size:11px;-fx-text-fill:#1e40af;-fx-font-weight:600;");
            }
        });

        colDemande.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colClient.setCellValueFactory(new PropertyValueFactory<>("client"));
        colValeur.setCellValueFactory(new PropertyValueFactory<>("valeur"));
        colExpiration.setCellValueFactory(new PropertyValueFactory<>("expiration"));

        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v);
                String style = switch (v) {
                    case "Généré"  -> "-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;";
                    case "Envoyé"  -> "-fx-background-color:#f3e8ff;-fx-text-fill:#6b21a8;";
                    case "Rédimé ✅"-> "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
                    case "Expiré"  -> "-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;";
                    case "Annulé"  -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                    default        -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                badge.setStyle(style + "-fx-background-radius:10;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });
    }

    private void charger() {
        new Thread(() -> {
            List<BonRow> liste = new ArrayList<>();
            StringBuilder sql = new StringBuilder("""
                    SELECT v.code, v.valeur, v.statut, v.date_expiration,
                           d.reference, c.nom, c.nom_societe
                    FROM vouchers v
                    JOIN demandes d ON d.id = v.demande_id
                    JOIN clients  c ON c.id = v.client_id
                    WHERE 1=1
                    """);
            if (!"TOUS".equals(filtreActif)) {
                sql.append(" AND v.statut = '").append(filtreActif).append("'");
            }
            String r = txtRecherche.getText();
            if (r != null && !r.isBlank()) {
                sql.append(" AND (v.code ILIKE '%").append(r)
                   .append("%' OR d.reference ILIKE '%").append(r)
                   .append("%' OR c.nom ILIKE '%").append(r).append("%')");
            }
            sql.append(" ORDER BY v.cree_le DESC LIMIT 500");

            try (Connection conn = DatabaseConfig.getConnexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql.toString())) {
                while (rs.next()) {
                    String soc = rs.getString("nom_societe");
                    String client = (soc != null && !soc.isBlank()) ? soc : rs.getString("nom");
                    String statut = switch (rs.getString("statut")) {
                        case "GENERE"  -> "Généré";
                        case "ENVOYE"  -> "Envoyé";
                        case "REDIME"  -> "Rédimé ✅";
                        case "EXPIRE"  -> "Expiré";
                        case "ANNULE"  -> "Annulé";
                        default        -> rs.getString("statut");
                    };
                    String exp = rs.getDate("date_expiration") != null
                            ? rs.getDate("date_expiration").toLocalDate()
                                   .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) : "—";
                    liste.add(new BonRow(rs.getString("code"), rs.getString("reference"),
                            client, String.format("Rs %,.0f", rs.getDouble("valeur")),
                            exp, statut));
                }
            } catch (SQLException e) { System.err.println("Erreur bons : " + e.getMessage()); }

            Platform.runLater(() -> {
                donnees.setAll(liste);
                lblTotal.setText(liste.size() + " bon(s)");
            });
        }).start();
    }

    @FXML private void onFiltreTous()    { setFiltre("TOUS"); }
    @FXML private void onFiltreGeneres() { setFiltre("GENERE"); }
    @FXML private void onFiltreRedimes() { setFiltre("REDIME"); }
    @FXML private void onFiltreExpires() { setFiltre("EXPIRE"); }

    private void setFiltre(String f) {
        this.filtreActif = f;
        String on  = "-fx-background-color:#1e40af;-fx-text-fill:white;-fx-background-radius:16;-fx-padding:4 14;-fx-font-size:11px;-fx-cursor:hand;";
        String off = "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;-fx-background-radius:16;-fx-padding:4 14;-fx-font-size:11px;-fx-cursor:hand;";
        btnTous.setStyle("TOUS".equals(f)   ? on : off);
        btnGeneres.setStyle("GENERE".equals(f) ? on : off);
        btnRedimes.setStyle("REDIME".equals(f) ? on : off);
        btnExpires.setStyle("EXPIRE".equals(f) ? on : off);
        charger();
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

    public static class BonRow {
        private final String code, reference, client, valeur, expiration, statut;
        public BonRow(String code, String ref, String client, String valeur, String exp, String statut) {
            this.code=code; this.reference=ref; this.client=client;
            this.valeur=valeur; this.expiration=exp; this.statut=statut;
        }
        public String getCode()       { return code; }
        public String getReference()  { return reference; }
        public String getClient()     { return client; }
        public String getValeur()     { return valeur; }
        public String getExpiration() { return expiration; }
        public String getStatut()     { return statut; }
    }
}
