package com.vms.controller;

import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;
import com.vms.service.EmailService;

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
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PaiementController {

    @FXML private TableView<DemandeRow>            tableView;
    @FXML private TableColumn<DemandeRow, String>  colReference;
    @FXML private TableColumn<DemandeRow, String>  colClient;
    @FXML private TableColumn<DemandeRow, String>  colMontant;
    @FXML private TableColumn<DemandeRow, String>  colDate;
    @FXML private TableColumn<DemandeRow, String>  colStatut;
    @FXML private TableColumn<DemandeRow, Void>    colActions;
    @FXML private Label lblTotal;

    private final ObservableList<DemandeRow> donnees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurerColonnes();
        tableView.setItems(donnees);
        chargerDemandes();
    }

    private void configurerColonnes() {
        colReference.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colReference.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
                if (!empty) setStyle("-fx-font-family:monospace;-fx-text-fill:#1e40af;-fx-font-weight:700;");
            }
        });

        colClient.setCellValueFactory(new PropertyValueFactory<>("client"));
        colMontant.setCellValueFactory(new PropertyValueFactory<>("montant"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("dateDemande"));

        // Statut avec badge coloré
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v);
                String style = switch (v) {
                    case "Att. paiement"  -> "-fx-background-color:#fef3c7;-fx-text-fill:#92400e;";
                    case "Validé ✅"       -> "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
                    case "Rejeté ✕"       -> "-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;";
                    default               -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                badge.setStyle(style + "-fx-background-radius:10;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });

        // Actions : boutons visibles selon statut
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnValider = creerBtn("✔", "#d1fae5", "#065f46", "Valider le paiement");
            private final Button btnRejeter = creerBtn("✕", "#fee2e2", "#991b1b", "Rejeter le paiement");
            private final HBox   box        = new HBox(6, btnValider, btnRejeter);
            {
                btnValider.setOnAction(e -> onValider(getTableView().getItems().get(getIndex())));
                btnRejeter.setOnAction(e -> onRejeter(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                DemandeRow row = getTableView().getItems().get(getIndex());
                // Boutons actifs seulement si encore en attente
                boolean enAttente = "Att. paiement".equals(row.getStatut());
                btnValider.setDisable(!enAttente);
                btnValider.setOpacity(enAttente ? 1.0 : 0.3);
                btnRejeter.setDisable(!enAttente);
                btnRejeter.setOpacity(enAttente ? 1.0 : 0.3);
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

    // ── Valider — confirmation simple ───────────────────────
    private void onValider(DemandeRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Valider le paiement");
        confirm.setHeaderText("Confirmer la validation — " + row.getReference());
        confirm.setContentText("Client : " + row.getClient() + "\nMontant : " + row.getMontant() +
                "\n\nÊtes-vous sûr de valider ce paiement ?\nL'approbateur sera notifié.");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("✔ Oui, valider");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Non");

        if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion()) {
                    String userId = SessionManager.getInstance().getUtilisateurConnecte().getId().toString();
                    // Facture
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO factures(demande_id,numero_facture,montant_ht,statut_paiement,
                                date_paiement,valide_par,valide_le,cree_par)
                            VALUES(?::uuid,'FAC-'||TO_CHAR(NOW(),'YYYY-')||LPAD(NEXTVAL('seq_demande_num')::TEXT,4,'0'),
                                (SELECT montant_total FROM demandes WHERE id=?::uuid),
                                'VALIDE',CURRENT_DATE,?::uuid,NOW(),?::uuid)
                            ON CONFLICT(demande_id) DO UPDATE SET statut_paiement='VALIDE',
                                date_paiement=CURRENT_DATE,valide_par=EXCLUDED.valide_par,valide_le=NOW()
                            """)) {
                        ps.setString(1,row.getId()); ps.setString(2,row.getId());
                        ps.setString(3,userId);      ps.setString(4,userId);
                        ps.executeUpdate();
                    }
                    // Statut
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE demandes SET statut='PAIEMENT_VALIDE',modifie_le=NOW() WHERE id=?::uuid")) {
                        ps.setString(1,row.getId()); ps.executeUpdate();
                    }
                    // Audit
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO audit_trail(type_action,entite,entite_id,utilisateur_id,description) " +
                            "VALUES('PAIEMENT'::type_audit,'demandes',?::uuid,?::uuid,?)")) {
                        ps.setString(1,row.getId()); ps.setString(2,userId);
                        ps.setString(3,"Paiement validé — " + row.getReference() + " — " + row.getClient());
                        ps.executeUpdate();
                    }

                    System.out.println(">>> DEBUG : tentative envoi email approbateur");
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT email FROM utilisateurs WHERE role = 'APPROBATEUR' LIMIT 1")) {
                        ResultSet rsEmail = ps.executeQuery();
                        if (rsEmail.next()) {
                            String emailApprobateur = rsEmail.getString("email");
                            System.out.println(">>> DEBUG : email trouvé = " + emailApprobateur);
                            EmailService.notifierApprobateur(
                                emailApprobateur,
                                row.getReference(),
                                row.getClient(),
                                row.getMontant()
                            );
                        } else {
                            System.out.println(">>> DEBUG : aucun approbateur trouvé en base !");
                        }
                    }

                    Platform.runLater(this::chargerDemandes);
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    // ── Rejeter — confirmation simple ───────────────────────
    private void onRejeter(DemandeRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Rejeter le paiement");
        confirm.setHeaderText("Rejeter — " + row.getReference());
        confirm.setContentText("Client : " + row.getClient() + "\n\nÊtes-vous sûr de rejeter ce paiement ?\nLa demande repassera en EN_ATTENTE.");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("✕ Oui, rejeter");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Non");

        if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion()) {
                    String userId = SessionManager.getInstance().getUtilisateurConnecte().getId().toString();
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE demandes SET statut='EN_ATTENTE',modifie_le=NOW() WHERE id=?::uuid")) {
                        ps.setString(1,row.getId()); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO audit_trail(type_action,entite,entite_id,utilisateur_id,description) " +
                            "VALUES('PAIEMENT'::type_audit,'demandes',?::uuid,?::uuid,?)")) {
                        ps.setString(1,row.getId()); ps.setString(2,userId);
                        ps.setString(3,"Paiement rejeté — " + row.getReference());
                        ps.executeUpdate();
                    }
                    Platform.runLater(this::chargerDemandes);
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    // ── Chargement — TOUT visible avec statuts ───────────────
    private void chargerDemandes() {
        new Thread(() -> {
            List<DemandeRow> liste = new ArrayList<>();
            String sql = """
                    SELECT d.id, d.reference, d.montant_total, d.statut, d.date_demande,
                           c.nom, c.nom_societe,
                           f.statut_paiement
                    FROM demandes d
                    JOIN clients c ON c.id = d.client_id
                    LEFT JOIN factures f ON f.demande_id = d.id
                    WHERE d.statut NOT IN ('ANNULEE')
                    ORDER BY d.date_demande DESC
                    """;
            try (Connection conn = DatabaseConfig.getConnexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String soc = rs.getString("nom_societe");
                    String client = (soc != null && !soc.isBlank()) ? soc : rs.getString("nom");
                    String statut = rs.getString("statut");
                    String statutAffiche = switch (statut) {
                        case "EN_ATTENTE","FACTUREE" -> "Att. paiement";
                        case "PAIEMENT_VALIDE","APPROUVEE","EMISE" -> "Validé ✅";
                        default -> statut;
                    };
                    String date = rs.getDate("date_demande") != null
                            ? rs.getDate("date_demande").toLocalDate()
                                   .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) : "—";
                    liste.add(new DemandeRow(rs.getString("id"), rs.getString("reference"),
                            client, String.format("Rs %,.0f", rs.getDouble("montant_total")),
                            date, statutAffiche));
                }
            } catch (SQLException e) { System.err.println("Erreur paiements : " + e.getMessage()); }
            Platform.runLater(() -> {
                donnees.setAll(liste);
                long enAttente = liste.stream().filter(r -> "Att. paiement".equals(r.getStatut())).count();
                lblTotal.setText(liste.size() + " demande(s) · " + enAttente + " en attente");
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

    private void afficherErreur(String t, String m) {
        Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }

    public static class DemandeRow {
        private final String id, reference, client, montant, dateDemande, statut;
        public DemandeRow(String id, String ref, String client, String montant, String date, String statut) {
            this.id=id; this.reference=ref; this.client=client; this.montant=montant; this.dateDemande=date; this.statut=statut;
        }
        public String getId()          { return id; }
        public String getReference()   { return reference; }
        public String getClient()      { return client; }
        public String getMontant()     { return montant; }
        public String getDateDemande() { return dateDemande; }
        public String getStatut()      { return statut; }
    }
}
