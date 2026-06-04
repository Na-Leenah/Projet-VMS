package com.vms.controller;

import com.vms.config.DatabaseConfig;
import com.vms.config.SessionManager;
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
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UtilisateursController {

    @FXML private TableView<UtilisateurRow>            tableView;
    @FXML private TableColumn<UtilisateurRow, String>  colNom;
    @FXML private TableColumn<UtilisateurRow, String>  colEmail;
    @FXML private TableColumn<UtilisateurRow, String>  colRole;
    @FXML private TableColumn<UtilisateurRow, String>  colSite;
    @FXML private TableColumn<UtilisateurRow, String>  colStatut;
    @FXML private TableColumn<UtilisateurRow, Void>    colActions;

    @FXML private Label     lblTotal;
    @FXML private TextField txtRecherche;

    private final ObservableList<UtilisateurRow> donnees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurerColonnes();
        tableView.setItems(donnees);
        txtRecherche.textProperty().addListener((o, a, b) -> charger());
        charger();
    }

    private void configurerColonnes() {
        colNom.setCellValueFactory(new PropertyValueFactory<>("nomComplet"));
        colNom.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
                if (!empty) setStyle("-fx-font-weight:600;");
            }
        });

        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));

        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colRole.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v);
                String style = switch (v) {
                    case "ADMIN"               -> "-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;";
                    case "COMPTABLE"           -> "-fx-background-color:#fef3c7;-fx-text-fill:#92400e;";
                    case "APPROBATEUR"         -> "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
                    case "SUPERVISEUR_MAGASIN" -> "-fx-background-color:#f3e8ff;-fx-text-fill:#6b21a8;";
                    default                    -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                badge.setStyle(style + "-fx-background-radius:10;-fx-padding:2 8;-fx-font-size:10px;-fx-font-weight:600;");
                setGraphic(badge); setText(null);
            }
        });

        colSite.setCellValueFactory(new PropertyValueFactory<>("site"));

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
                btnToggle.setOnAction(e -> onToggleActif(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                UtilisateurRow row = getTableView().getItems().get(getIndex());
                // Ne pas pouvoir desactiver son propre compte
                String moi = SessionManager.getInstance().getUtilisateurConnecte().getId().toString();
                btnToggle.setDisable(row.getId().equals(moi));
                btnToggle.setOpacity(row.getId().equals(moi) ? 0.3 : 1.0);
                btnToggle.setText("Actif".equals(row.getStatut()) ? "D" : "A");
                btnToggle.setTooltip(new Tooltip("Actif".equals(row.getStatut()) ? "Desactiver" : "Activer"));
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

    @FXML
    private void onNouvelUtilisateur() {
        afficherDialogue(null);
    }

    private void onModifier(UtilisateurRow row) {
        afficherDialogue(row);
    }

    private void onToggleActif(UtilisateurRow row) {
        boolean estActif = "Actif".equals(row.getStatut());
        String action = estActif ? "desactiver" : "activer";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmer");
        confirm.setHeaderText(action.substring(0,1).toUpperCase() + action.substring(1) + " " + row.getNomComplet());
        confirm.setContentText("Etes-vous sur de vouloir " + action + " ce compte ?");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Oui");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Non");

        if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE utilisateurs SET est_actif=?, modifie_le=NOW() WHERE id=?::uuid")) {
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

    private void afficherDialogue(UtilisateurRow existant) {
        boolean isNouvel = (existant == null);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isNouvel ? "Nouvel utilisateur" : "Modifier - " + existant.getNomComplet());
        dialog.setHeaderText(isNouvel ? "Creer un nouveau compte" : "Modification du compte");

        TextField txtNom    = new TextField(isNouvel ? "" : nvl(existant.getNom()));
        TextField txtPrenom = new TextField(isNouvel ? "" : nvl(existant.getPrenom()));
        TextField txtEmail  = new TextField(isNouvel ? "" : nvl(existant.getEmail()));
        PasswordField txtMdp = new PasswordField();
        txtMdp.setPromptText(isNouvel ? "Mot de passe *" : "Laisser vide = pas de changement");

        ComboBox<String> cbRole = new ComboBox<>(FXCollections.observableArrayList(
                "ADMIN", "COMPTABLE", "APPROBATEUR", "SUPERVISEUR_MAGASIN", "LECTEUR"));
        cbRole.setValue(isNouvel ? "LECTEUR" : existant.getRole());
        cbRole.setMaxWidth(Double.MAX_VALUE);

        // Liste des societes pour associer l'utilisateur
        ComboBox<String[]> cbSociete = new ComboBox<>();
        cbSociete.setMaxWidth(Double.MAX_VALUE);
        List<String[]> societes = chargerSocietes();
        cbSociete.setItems(FXCollections.observableArrayList(societes));
        cbSociete.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item[1]);
            }
        });
        cbSociete.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item[1]);
            }
        });
        // Preselectioner la societe si modification
        if (!isNouvel) {
            societes.stream().filter(s -> s[0].equals(existant.getSocieteId()))
                    .findFirst().ifPresent(cbSociete::setValue);
        }

        VBox contenu = new VBox(10,
                ligne("Prenom *", txtPrenom, true, true),
                ligne("Nom *",    txtNom,    true, true),
                ligne("Email *",  txtEmail,  true, true),
                ligne("Mot de passe" + (isNouvel ? " *" : ""), txtMdp, true, true),
                champ("Role *",    cbRole),
                champ("Site / Societe *", cbSociete)
        );
        contenu.setPrefWidth(420);
        contenu.setStyle("-fx-padding:10 0 0 0;");
        dialog.getDialogPane().setContent(contenu);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK))
                .setText(isNouvel ? "Creer" : "Enregistrer");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Annuler");

        if (dialog.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
            if (txtNom.getText().isBlank() || txtEmail.getText().isBlank()) {
                afficherErreur("Champ manquant", "Nom et email sont obligatoires."); return;
            }
            if (isNouvel && txtMdp.getText().isBlank()) {
                afficherErreur("Champ manquant", "Le mot de passe est obligatoire pour un nouvel utilisateur."); return;
            }
            if (cbSociete.getValue() == null) {
                afficherErreur("Champ manquant", "Veuillez selectionner un site."); return;
            }

            String societeId = cbSociete.getValue()[0];
            String mdpHash   = txtMdp.getText().isBlank() ? null
                    : BCrypt.hashpw(txtMdp.getText(), BCrypt.gensalt(12));

            new Thread(() -> {
                try (Connection conn = DatabaseConfig.getConnexion()) {
                    if (isNouvel) {
                        try (PreparedStatement ps = conn.prepareStatement("""
                                INSERT INTO utilisateurs(societe_id,nom,prenom,email,mot_de_passe,role)
                                VALUES(?::uuid,?,?,?,?,?::role_utilisateur)""")) {
                            ps.setString(1, societeId);
                            ps.setString(2, txtNom.getText().trim());
                            ps.setString(3, txtPrenom.getText().trim());
                            ps.setString(4, txtEmail.getText().trim());
                            ps.setString(5, mdpHash);
                            ps.setString(6, cbRole.getValue());
                            ps.executeUpdate();
                        }
                    } else {
                        String sqlUpd = mdpHash != null
                                ? "UPDATE utilisateurs SET nom=?,prenom=?,email=?,role=?::role_utilisateur,societe_id=?::uuid,mot_de_passe=?,modifie_le=NOW() WHERE id=?::uuid"
                                : "UPDATE utilisateurs SET nom=?,prenom=?,email=?,role=?::role_utilisateur,societe_id=?::uuid,modifie_le=NOW() WHERE id=?::uuid";
                        try (PreparedStatement ps = conn.prepareStatement(sqlUpd)) {
                            ps.setString(1, txtNom.getText().trim());
                            ps.setString(2, txtPrenom.getText().trim());
                            ps.setString(3, txtEmail.getText().trim());
                            ps.setString(4, cbRole.getValue());
                            ps.setString(5, societeId);
                            if (mdpHash != null) {
                                ps.setString(6, mdpHash);
                                ps.setString(7, existant.getId());
                            } else {
                                ps.setString(6, existant.getId());
                            }
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
            List<UtilisateurRow> liste = new ArrayList<>();
            String r = txtRecherche.getText();
            StringBuilder sql = new StringBuilder("""
                    SELECT u.id, u.societe_id, u.nom, u.prenom, u.email,
                           u.role, u.est_actif, s.nom AS site
                    FROM utilisateurs u
                    JOIN societes s ON s.id = u.societe_id
                    WHERE 1=1
                    """);
            if (r != null && !r.isBlank())
                sql.append(" AND (u.nom ILIKE '%").append(r).append("%' OR u.prenom ILIKE '%")
                   .append(r).append("%' OR u.email ILIKE '%").append(r).append("%')");
            sql.append(" ORDER BY u.nom, u.prenom");

            try (Connection conn = DatabaseConfig.getConnexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql.toString())) {
                while (rs.next()) {
                    liste.add(new UtilisateurRow(
                            rs.getString("id"), rs.getString("societe_id"),
                            rs.getString("nom"), rs.getString("prenom"),
                            rs.getString("email"), rs.getString("role"),
                            rs.getBoolean("est_actif") ? "Actif" : "Inactif",
                            rs.getString("site")));
                }
            } catch (SQLException e) { System.err.println("Erreur users : " + e.getMessage()); }
            Platform.runLater(() -> {
                donnees.setAll(liste);
                lblTotal.setText(liste.size() + " utilisateur(s)");
            });
        }).start();
    }

    private List<String[]> chargerSocietes() {
        List<String[]> liste = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnexion();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, nom FROM societes WHERE est_actif=TRUE ORDER BY est_siege DESC, nom")) {
            while (rs.next()) liste.add(new String[]{rs.getString("id"), rs.getString("nom")});
        } catch (SQLException e) { System.err.println("Erreur societes : " + e.getMessage()); }
        return liste;
    }

    private VBox ligne(String label, javafx.scene.control.Control field, boolean full, boolean hgrow) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#374151;");
        if (full) field.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, lbl, field);
        if (hgrow) HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private VBox champ(String label, javafx.scene.control.Control field) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#374151;");
        field.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, lbl, field);
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

    public static class UtilisateurRow {
        private final String id, societeId, nom, prenom, email, role, statut, site;
        public UtilisateurRow(String id, String societeId, String nom, String prenom,
                String email, String role, String statut, String site) {
            this.id=id; this.societeId=societeId; this.nom=nom; this.prenom=prenom;
            this.email=email; this.role=role; this.statut=statut; this.site=site;
        }
        public String getId()         { return id; }
        public String getSocieteId()  { return societeId; }
        public String getNom()        { return nom; }
        public String getPrenom()     { return prenom; }
        public String getNomComplet() { return prenom + " " + nom; }
        public String getEmail()      { return email; }
        public String getRole()       { return role; }
        public String getStatut()     { return statut; }
        public String getSite()       { return site; }
    }
}
