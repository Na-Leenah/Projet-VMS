package com.vms.controller;

import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Écran de rédemption — Superviseur Magasin.
 * Le superviseur saisit ou scanne le code du bon.
 * Le système vérifie en base : valide / expiré / déjà utilisé.
 * Si OK → rédemption enregistrée.
 */
public class ScanRedemptionController {

    @FXML private TextField txtCodeBon;
    @FXML private Button    btnValider;
    @FXML private VBox      panneauResultat;

    // Résultat OK
    @FXML private VBox  panneauOk;
    @FXML private Label lblCodeOk;
    @FXML private Label lblClientOk;
    @FXML private Label lblValeurOk;
    @FXML private Label lblExpirationOk;
    @FXML private Label lblDemandOk;

    // Résultat erreur
    @FXML private VBox  panneauErreur;
    @FXML private Label lblMessageErreur;

    // Historique du jour
    @FXML private ListView<String> listeHistorique;

    @FXML
    public void initialize() {
        masquerResultat();
        // Permettre la validation avec Entrée
        txtCodeBon.setOnAction(e -> onValider());
        chargerHistorique();
    }

    @FXML
    private void onValider() {
        String code = txtCodeBon.getText().trim().toUpperCase();
        if (code.isBlank()) {
            afficherErreur("Veuillez saisir ou scanner un code de bon.");
            return;
        }
        btnValider.setDisable(true);
        btnValider.setText("Vérification...");

        new Thread(() -> {
            try (Connection conn = DatabaseConfig.getConnexion()) {
                // Chercher le bon
                String sql = """
                        SELECT v.id, v.code, v.valeur, v.statut,
                               v.date_expiration, d.reference,
                               c.nom, c.nom_societe
                        FROM vouchers v
                        JOIN demandes d ON d.id = v.demande_id
                        JOIN clients  c ON c.id = v.client_id
                        WHERE v.code = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, code);
                    ResultSet rs = ps.executeQuery();

                    if (!rs.next()) {
                        Platform.runLater(() -> {
                            afficherErreur("❌  Code inconnu — ce bon n'existe pas dans le système.");
                            reinitialiserBouton();
                        });
                        return;
                    }

                    String id       = rs.getString("id");
                    String statut   = rs.getString("statut");
                    double valeur   = rs.getDouble("valeur");
                    String ref      = rs.getString("reference");
                    String soc      = rs.getString("nom_societe");
                    String client   = (soc != null && !soc.isBlank()) ? soc : rs.getString("nom");
                    Date   dateExp  = rs.getDate("date_expiration");
                    String expStr   = dateExp != null
                            ? dateExp.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";
                    boolean expire  = dateExp != null && dateExp.toLocalDate().isBefore(java.time.LocalDate.now());

                    // Vérifications
                    if ("REDIME".equals(statut)) {
                        Platform.runLater(() -> { afficherErreur("❌  Bon déjà utilisé — rédemption refusée."); reinitialiserBouton(); });
                        return;
                    }
                    if ("ANNULE".equals(statut)) {
                        Platform.runLater(() -> { afficherErreur("❌  Bon annulé — rédemption refusée."); reinitialiserBouton(); });
                        return;
                    }
                    if (expire) {
                        Platform.runLater(() -> { afficherErreur("❌  Bon expiré le " + expStr + " — rédemption refusée."); reinitialiserBouton(); });
                        return;
                    }

                    // ✅ Bon valide — enregistrer la rédemption
                    String userId   = SessionManager.getInstance().getUtilisateurConnecte().getId().toString();
                    String societeId = getSocieteId(conn, userId);

                    // Marquer comme rédimé
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE vouchers SET statut='REDIME', modifie_le=NOW() WHERE id=?::uuid")) {
                        upd.setString(1, id); upd.executeUpdate();
                    }
                    // Enregistrer la rédemption
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO redemptions(voucher_id,societe_id,redime_par) VALUES(?::uuid,?::uuid,?::uuid)")) {
                        ins.setString(1, id); ins.setString(2, societeId); ins.setString(3, userId);
                        ins.executeUpdate();
                    }
                    // Audit
                    try (PreparedStatement aud = conn.prepareStatement(
                            "INSERT INTO audit_trail(type_action,entite,entite_id,utilisateur_id,description) " +
                            "VALUES('REDEMPTION'::type_audit,'vouchers',?::uuid,?::uuid,?)")) {
                        aud.setString(1, id); aud.setString(2, userId);
                        aud.setString(3, "Rédemption bon " + code + " — " + client + " — Rs " + (int)valeur);
                        aud.executeUpdate();
                    }

                    final String clientF = client, expFinal = expStr, refFinal = ref;
                    final double valFinal = valeur;
                    Platform.runLater(() -> {
                        afficherOk(code, clientF, String.format("Rs %,.0f", valFinal), expFinal, refFinal);
                        txtCodeBon.clear();
                        reinitialiserBouton();
                        chargerHistorique();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> { afficherErreur("Erreur système : " + e.getMessage()); reinitialiserBouton(); });
            }
        }).start();
    }

    @FXML
    private void onEffacer() {
        txtCodeBon.clear();
        masquerResultat();
        txtCodeBon.requestFocus();
    }

    private void afficherOk(String code, String client, String valeur, String exp, String ref) {
        panneauResultat.setVisible(true); panneauResultat.setManaged(true);
        panneauOk.setVisible(true);      panneauOk.setManaged(true);
        panneauErreur.setVisible(false); panneauErreur.setManaged(false);
        lblCodeOk.setText(code);
        lblClientOk.setText(client);
        lblValeurOk.setText(valeur);
        lblExpirationOk.setText(exp);
        lblDemandOk.setText(ref);
    }

    private void afficherErreur(String msg) {
        panneauResultat.setVisible(true); panneauResultat.setManaged(true);
        panneauErreur.setVisible(true);   panneauErreur.setManaged(true);
        panneauOk.setVisible(false);      panneauOk.setManaged(false);
        lblMessageErreur.setText(msg);
    }

    private void masquerResultat() {
        panneauResultat.setVisible(false); panneauResultat.setManaged(false);
    }

    private void reinitialiserBouton() {
        btnValider.setDisable(false);
        btnValider.setText("✔ Valider");
    }

    private void chargerHistorique() {
        new Thread(() -> {
            var items = new java.util.ArrayList<String>();
            String sql = """
                    SELECT v.code, c.nom, c.nom_societe, r.redime_le, v.valeur
                    FROM redemptions r
                    JOIN vouchers v ON v.id = r.voucher_id
                    JOIN demandes d ON d.id = v.demande_id
                    JOIN clients c ON c.id = v.client_id
                    WHERE r.redime_par = ?::uuid
                      AND r.redime_le >= CURRENT_DATE
                    ORDER BY r.redime_le DESC
                    LIMIT 20
                    """;
            try (Connection conn = DatabaseConfig.getConnexion();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, SessionManager.getInstance().getUtilisateurConnecte().getId().toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String soc = rs.getString("nom_societe");
                    String client = (soc != null && !soc.isBlank()) ? soc : rs.getString("nom");
                    Timestamp ts = rs.getTimestamp("redime_le");
                    String heure = ts != null ? ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "—";
                    items.add("✅  " + heure + "  |  " + rs.getString("code")
                            + "  |  " + client
                            + "  |  Rs " + (int)rs.getDouble("valeur"));
                }
            } catch (Exception e) { System.err.println("Erreur historique : " + e.getMessage()); }
            Platform.runLater(() -> listeHistorique.setItems(
                    javafx.collections.FXCollections.observableArrayList(items)));
        }).start();
    }

    private String getSocieteId(Connection conn, String userId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT societe_id FROM utilisateurs WHERE id=?::uuid")) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) { return null; }
    }

    @FXML private void onRetourDashboard() { naviguer("/fxml/dashboard.fxml"); }
    private void naviguer(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) txtCodeBon.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) { e.printStackTrace(); }
    }
}
