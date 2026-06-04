package com.vms.controller;

import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;
import com.vms.service.EmailService;
import com.vms.service.PdfBonService;

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
import java.util.ArrayList;
import java.util.List;



public class ApprobationController {

    @FXML private TableView<DemandeAppro>            tableView;
    @FXML private TableColumn<DemandeAppro, String>  colReference;
    @FXML private TableColumn<DemandeAppro, String>  colClient;
    @FXML private TableColumn<DemandeAppro, String>  colBons;
    @FXML private TableColumn<DemandeAppro, String>  colMontant;
    @FXML private TableColumn<DemandeAppro, String>  colPaiement;
    @FXML private TableColumn<DemandeAppro, String>  colStatut;
    @FXML private TableColumn<DemandeAppro, Void>    colActions;
    @FXML private Label lblTotal;

    private final ObservableList<DemandeAppro> donnees = FXCollections.observableArrayList();
    private boolean estApprobateur = false;

    @FXML
    public void initialize() {
        String role = SessionManager.getInstance().getRoleCourant();
        // Seuls APPROBATEUR et ADMIN peuvent approuver
        estApprobateur = "APPROBATEUR".equals(role) || "ADMIN".equals(role);

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
        colBons.setCellValueFactory(new PropertyValueFactory<>("bons"));
        colMontant.setCellValueFactory(new PropertyValueFactory<>("montant"));
        colPaiement.setCellValueFactory(new PropertyValueFactory<>("infoPaiement"));

        // Statut avec badge
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statutAffiche"));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v);
                String style = switch (v) {
                    case "Att. approbation" -> "-fx-background-color:#f3e8ff;-fx-text-fill:#6b21a8;";
                    case "Approuvé ✅"       -> "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
                    case "Rejeté ✕"         -> "-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;";
                    case "Émis ✅"           -> "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
                    default                 -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                badge.setStyle(style + "-fx-background-radius:10;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });

        // Actions — boutons grisés si déjà traité OU si pas le bon rôle
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnAppr = creerBtn("✔", "#d1fae5", "#065f46", "Approuver et générer les bons");
            private final Button btnRej  = creerBtn("✕", "#fee2e2", "#991b1b", "Rejeter la demande");
            private final HBox   box     = new HBox(6, btnAppr, btnRej);
            {
                btnAppr.setOnAction(e -> onApprouver(getTableView().getItems().get(getIndex())));
                btnRej.setOnAction(e  -> onRejeter(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                DemandeAppro row = getTableView().getItems().get(getIndex());
                boolean peutAgir = estApprobateur && "Att. approbation".equals(row.getStatutAffiche());
                btnAppr.setDisable(!peutAgir);
                btnAppr.setOpacity(peutAgir ? 1.0 : 0.3);
                btnRej.setDisable(!peutAgir);
                btnRej.setOpacity(peutAgir ? 1.0 : 0.3);
                // Si comptable : masquer complètement les boutons
                if (!estApprobateur) {
                    setGraphic(new Label("Vue seule") {{
                        setStyle("-fx-font-size:10px;-fx-text-fill:#94a3b8;");
                    }});
                } else {
                    setGraphic(box);
                }
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

    // ── Approuver — confirmation simple ─────────────────────
    private void onApprouver(DemandeAppro row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Approuver la demande");
        confirm.setHeaderText("Approuver — " + row.getReference());
        confirm.setContentText(
                "Client  : " + row.getClient() + "\n" +
                "Bons    : " + row.getBons() + "\n" +
                "Montant : " + row.getMontant() + "\n\n" +
                "Êtes-vous sûr ?\n" +
                "→ " + row.getNombreBons() + " bons seront générés automatiquement.\n" +
                "→ Email au client (phase 2).");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("✔ Oui, approuver");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Non");

        if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion()) {
                    String userId = SessionManager.getInstance().getUtilisateurConnecte().getId().toString();
                    // 1. APPROUVEE
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE demandes SET statut='APPROUVEE',modifie_le=NOW() WHERE id=?::uuid")) {
                        ps.setString(1,row.getId()); ps.executeUpdate();
                    }
                    // 2. Enregistrer approbation
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO approbations(demande_id,approuve_par,est_approuve)
                            VALUES(?::uuid,?::uuid,TRUE)
                            ON CONFLICT(demande_id) DO UPDATE SET approuve_par=EXCLUDED.approuve_par,
                                approuve_le=NOW(),est_approuve=TRUE""")) {
                        ps.setString(1,row.getId()); ps.setString(2,userId); ps.executeUpdate();
                    }
                    // 3. Générer les bons via procédure stockée
                    try (CallableStatement cs = conn.prepareCall("CALL sp_generer_vouchers(?::uuid,?::uuid)")) {
                        cs.setString(1,row.getId()); cs.setString(2,userId); cs.execute();
                    }
                    // 4. Audit
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO audit_trail(type_action,entite,entite_id,utilisateur_id,description) " +
                            "VALUES('APPROBATION'::type_audit,'demandes',?::uuid,?::uuid,?)")) {
                        ps.setString(1,row.getId()); ps.setString(2,userId);
                        ps.setString(3,"Approbation + génération " + row.getNombreBons() + " bons — " + row.getReference());
                        ps.executeUpdate();
                    }

                    // 5. Récupérer les bons générés depuis la BDD
                    List<PdfBonService.VoucherInfo> vouchers = new java.util.ArrayList<>();
                    String dateExpStr = "—";
                    try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT code, qr_data, valeur, date_expiration " +
                        "FROM vouchers WHERE demande_id = ?::uuid ORDER BY cree_le")) {
                            ps.setString(1, row.getId());
                            ResultSet rsV = ps.executeQuery();
                            while (rsV.next()) {
                                String dateExp = rsV.getDate("date_expiration") != null
                                ? rsV.getDate("date_expiration").toLocalDate()
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                : "—";
                                dateExpStr = dateExp;
                                vouchers.add(new PdfBonService.VoucherInfo(
                                    rsV.getString("code"),
                                    rsV.getString("qr_data"),
                                    rsV.getDouble("valeur"),
                                    dateExp
                                ));
                            }
                        }
                        System.out.println("[PDF] " + vouchers.size() + " bons à générer pour " + row.getReference());
                    // 6. Générer les PDFs en mémoire
                    List<byte[]> pdfs = PdfBonService.genererTousLesBons(
                        vouchers, row.getClient(), row.getReference());
 
                    // 7. Email au client avec PDFs en pièces jointes
                    System.out.println(">>> DEBUG : envoi email client → " + row.getEmailDispatch());
                    EmailService.envoyerBonsClientAvecPdf(
                        row.getEmailDispatch(),
                        row.getClient(),
                        row.getReference(),
                        Integer.parseInt(row.getNombreBons()),
                        Double.parseDouble(row.getValeurUnitaire()),
                        dateExpStr,
                        pdfs);
 
                    // 8. Email récap à l'admin
                    try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT email FROM utilisateurs WHERE role = 'ADMIN' LIMIT 1")) {
                            ResultSet rsAdmin = ps.executeQuery();
                            if (rsAdmin.next()) {
                                String emailAdmin = rsAdmin.getString("email");
                                System.out.println(">>> DEBUG : envoi email admin → " + emailAdmin);
                                EmailService.envoyerRecapAdmin(
                                    emailAdmin,
                                    row.getReference(),
                                    row.getClient(),
                                    Integer.parseInt(row.getNombreBons()),
                                    Double.parseDouble(row.getMontant().replaceAll("[^0-9.]", ""))
                                );
                            }
                        }

                    Platform.runLater(() -> {
                        afficherInfo("✅ Approuvée", row.getNombreBons() + " bons générés pour " + row.getReference() + " !");
                        chargerDemandes();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    // ── Rejeter — confirmation simple ────────────────────────
    private void onRejeter(DemandeAppro row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Rejeter la demande");
        confirm.setHeaderText("Rejeter — " + row.getReference());
        confirm.setContentText("Client : " + row.getClient() + "\n\nÊtes-vous sûr de rejeter cette demande ?\nElle repassera en EN_ATTENTE.");
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
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO approbations(demande_id,approuve_par,est_approuve,commentaire)
                            VALUES(?::uuid,?::uuid,FALSE,'Rejeté')
                            ON CONFLICT(demande_id) DO UPDATE SET approuve_par=EXCLUDED.approuve_par,
                                approuve_le=NOW(),est_approuve=FALSE""")) {
                        ps.setString(1,row.getId()); ps.setString(2,userId); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO audit_trail(type_action,entite,entite_id,utilisateur_id,description) " +
                            "VALUES('APPROBATION'::type_audit,'demandes',?::uuid,?::uuid,?)")) {
                        ps.setString(1,row.getId()); ps.setString(2,userId);
                        ps.setString(3,"Rejet approbation — " + row.getReference());
                        ps.executeUpdate();
                    }
                    Platform.runLater(() -> { afficherInfo("Rejeté", row.getReference() + " repassée en attente."); chargerDemandes(); });
                } catch (Exception e) {
                    Platform.runLater(() -> afficherErreur("Erreur", e.getMessage()));
                }
            }).start();
        }
    }

    // ── Chargement — TOUT visible avec statuts ───────────────
    private void chargerDemandes() {
        new Thread(() -> {
            List<DemandeAppro> liste = new ArrayList<>();
            String sql = """
                    SELECT d.id, d.reference, d.nombre_bons, d.valeur_unitaire,
                           d.montant_total, d.statut,
                           c.nom, c.nom_societe, c.email,
                           f.reference_paiement, f.date_paiement,
                           u.prenom||' '||u.nom AS valide_par
                    FROM demandes d
                    JOIN clients c ON c.id = d.client_id
                    LEFT JOIN factures f ON f.demande_id = d.id AND f.statut_paiement='VALIDE'
                    LEFT JOIN utilisateurs u ON u.id = f.valide_par
                    WHERE d.statut IN ('PAIEMENT_VALIDE','APPROUVEE','EMISE')
                    ORDER BY f.date_paiement ASC NULLS LAST
                    """;
            try (Connection conn = DatabaseConfig.getConnexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String soc = rs.getString("nom_societe");
                    String client = (soc != null && !soc.isBlank()) ? soc : rs.getString("nom");
                    int    nb  = rs.getInt("nombre_bons");
                    double val = rs.getDouble("valeur_unitaire");
                    String statut = rs.getString("statut");
                    String statutAffiche = switch (statut) {
                        case "PAIEMENT_VALIDE" -> "Att. approbation";
                        case "APPROUVEE"       -> "Approuvé ✅";
                        case "EMISE"           -> "Émis ✅";
                        default                -> statut;
                    };
                    String datePaie  = rs.getDate("date_paiement") != null
                            ? rs.getDate("date_paiement").toLocalDate()
                                   .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) : "—";
                    String validePar = rs.getString("valide_par");
                    String refPaie   = rs.getString("reference_paiement");
                    String infoPaie  = datePaie
                            + (validePar != null ? " · " + validePar : "")
                            + (refPaie   != null ? " · " + refPaie   : "");

                    liste.add(new DemandeAppro(rs.getString("id"), rs.getString("reference"),
                            client, nb + " × Rs " + (int)val, String.valueOf(nb),
                            String.valueOf((int)val),
                            String.format("Rs %,.0f", rs.getDouble("montant_total")),
                            infoPaie, rs.getString("email"), statutAffiche));
                }
            } catch (SQLException e) { System.err.println("Erreur approbations : " + e.getMessage()); }
            Platform.runLater(() -> {
                donnees.setAll(liste);
                long enAttente = liste.stream().filter(r -> "Att. approbation".equals(r.getStatutAffiche())).count();
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

    private void afficherInfo(String t, String m)   { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait(); }
    private void afficherErreur(String t, String m) { Alert a = new Alert(Alert.AlertType.ERROR);       a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait(); }

    public static class DemandeAppro {
        private final String id, reference, client, bons, nombreBons, valeurUnitaire,
                             montant, infoPaiement, emailDispatch, statutAffiche;
        public DemandeAppro(String id, String ref, String client, String bons,
                            String nb, String val, String montant, String infoPaie,
                            String email, String statutAffiche) {
            this.id=id; this.reference=ref; this.client=client; this.bons=bons;
            this.nombreBons=nb; this.valeurUnitaire=val; this.montant=montant;
            this.infoPaiement=infoPaie; this.emailDispatch=email; this.statutAffiche=statutAffiche;
        }
        public String getId()             { return id; }
        public String getReference()      { return reference; }
        public String getClient()         { return client; }
        public String getBons()           { return bons; }
        public String getNombreBons()     { return nombreBons; }
        public String getValeurUnitaire() { return valeurUnitaire; }
        public String getMontant()        { return montant; }
        public String getInfoPaiement()   { return infoPaiement; }
        public String getEmailDispatch()  { return emailDispatch != null ? emailDispatch : "—"; }
        public String getStatutAffiche()  { return statutAffiche; }
    }
}
