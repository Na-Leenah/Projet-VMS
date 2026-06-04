package com.vms.controller;

import com.vms.config.DatabaseConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.sql.*;

public class RapportsController {

    @FXML private Label lblStatut;

    @FXML
    public void initialize() {
        lblStatut.setText("");
    }

    // ── Export : Toutes les demandes ────────────────────────
    @FXML
    private void onExportDemandes() {
        String sql = """
                SELECT d.reference, c.nom||' '||(COALESCE(c.nom_societe,'')) AS client,
                       d.nombre_bons, d.valeur_unitaire, d.montant_total,
                       d.statut, d.date_demande, d.date_expiration_bons
                FROM demandes d JOIN clients c ON c.id = d.client_id
                ORDER BY d.date_demande DESC
                """;
        String[] entetes = {"Reference", "Client", "Nb Bons", "Valeur Unitaire (Rs)",
                            "Montant Total (Rs)", "Statut", "Date Demande", "Date Expiration"};
        exporter("demandes_VMS", sql, entetes);
    }

    // ── Export : Demandes en attente paiement ───────────────
    @FXML
    private void onExportAttentePaiement() {
        String sql = """
                SELECT d.reference, c.nom||' '||(COALESCE(c.nom_societe,'')) AS client,
                       d.nombre_bons, d.montant_total, d.date_demande
                FROM demandes d JOIN clients c ON c.id = d.client_id
                WHERE d.statut IN ('EN_ATTENTE','FACTUREE')
                ORDER BY d.date_demande ASC
                """;
        String[] entetes = {"Reference", "Client", "Nb Bons", "Montant Total (Rs)", "Date Demande"};
        exporter("attente_paiement_VMS", sql, entetes);
    }

    // ── Export : Tous les bons ───────────────────────────────
    @FXML
    private void onExportBons() {
        String sql = """
                SELECT v.code, d.reference, c.nom||' '||(COALESCE(c.nom_societe,'')) AS client,
                       v.valeur, v.statut, v.date_emission, v.date_expiration
                FROM vouchers v
                JOIN demandes d ON d.id = v.demande_id
                JOIN clients  c ON c.id = v.client_id
                ORDER BY v.cree_le DESC
                """;
        String[] entetes = {"Code Bon", "Demande", "Client", "Valeur (Rs)",
                            "Statut", "Date Emission", "Date Expiration"};
        exporter("bons_VMS", sql, entetes);
    }

    // ── Export : Bons redimes ────────────────────────────────
    @FXML
    private void onExportRedimes() {
        String sql = """
                SELECT v.code, d.reference, c.nom||' '||(COALESCE(c.nom_societe,'')) AS client,
                       v.valeur, s.nom AS magasin,
                       u.prenom||' '||u.nom AS superviseur,
                       r.redime_le
                FROM redemptions r
                JOIN vouchers  v ON v.id = r.voucher_id
                JOIN demandes  d ON d.id = v.demande_id
                JOIN clients   c ON c.id = v.client_id
                JOIN societes  s ON s.id = r.societe_id
                JOIN utilisateurs u ON u.id = r.redime_par
                ORDER BY r.redime_le DESC
                """;
        String[] entetes = {"Code Bon", "Demande", "Client", "Valeur (Rs)",
                            "Magasin", "Superviseur", "Date Redemption"};
        exporter("bons_redimes_VMS", sql, entetes);
    }

    // ── Export : Bons expires ────────────────────────────────
    @FXML
    private void onExportExpires() {
        String sql = """
                SELECT v.code, d.reference, c.nom||' '||(COALESCE(c.nom_societe,'')) AS client,
                       v.valeur, v.date_expiration
                FROM vouchers v
                JOIN demandes d ON d.id = v.demande_id
                JOIN clients  c ON c.id = v.client_id
                WHERE v.statut = 'EXPIRE'
                   OR (v.statut NOT IN ('REDIME','ANNULE') AND v.date_expiration < CURRENT_DATE)
                ORDER BY v.date_expiration DESC
                """;
        String[] entetes = {"Code Bon", "Demande", "Client", "Valeur (Rs)", "Date Expiration"};
        exporter("bons_expires_VMS", sql, entetes);
    }

    // ── Export : Proches expiration (30 jours) ───────────────
    @FXML
    private void onExportProcheExpiration() {
        String sql = """
                SELECT v.code, d.reference, c.nom||' '||(COALESCE(c.nom_societe,'')) AS client,
                       v.valeur, v.date_expiration,
                       (v.date_expiration - CURRENT_DATE) AS jours_restants
                FROM vouchers v
                JOIN demandes d ON d.id = v.demande_id
                JOIN clients  c ON c.id = v.client_id
                WHERE v.statut NOT IN ('REDIME','EXPIRE','ANNULE')
                  AND v.date_expiration BETWEEN CURRENT_DATE AND CURRENT_DATE + 30
                ORDER BY v.date_expiration ASC
                """;
        String[] entetes = {"Code Bon", "Demande", "Client", "Valeur (Rs)",
                            "Date Expiration", "Jours Restants"};
        exporter("proches_expiration_VMS", sql, entetes);
    }

    // ── Moteur d'export Excel ────────────────────────────────
    private void exporter(String nomFichier, String sql, String[] entetes) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Enregistrer le rapport Excel");
        fc.setInitialFileName(nomFichier + "_" +
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ".xlsx");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers Excel (*.xlsx)", "*.xlsx"));
        File fichier = fc.showSaveDialog(lblStatut.getScene().getWindow());
        if (fichier == null) return;

        lblStatut.setText("Generation en cours...");
        lblStatut.setStyle("-fx-text-fill:#1e40af;");

        new Thread(() -> {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Rapport VMS");

                // Style en-tete
                CellStyle styleEntete = wb.createCellStyle();
                Font fontEntete = wb.createFont();
                fontEntete.setBold(true);
                fontEntete.setColor(IndexedColors.WHITE.getIndex());
                styleEntete.setFont(fontEntete);
                styleEntete.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                styleEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                styleEntete.setBorderBottom(BorderStyle.THIN);

                // Style alternatif lignes
                CellStyle styleImpair = wb.createCellStyle();
                styleImpair.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
                styleImpair.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                // Ligne en-tete
                Row rowEntete = sheet.createRow(0);
                for (int i = 0; i < entetes.length; i++) {
                    Cell cell = rowEntete.createCell(i);
                    cell.setCellValue(entetes[i]);
                    cell.setCellStyle(styleEntete);
                    sheet.setColumnWidth(i, 5000);
                }

                // Donnees
                int numLigne = 1;
                try (Connection conn = DatabaseConfig.getConnexion();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {
                    int nbColonnes = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        Row row = sheet.createRow(numLigne);
                        if (numLigne % 2 == 0) {
                            for (int i = 0; i < nbColonnes; i++) {
                                row.createCell(i).setCellStyle(styleImpair);
                            }
                        }
                        for (int i = 0; i < nbColonnes; i++) {
                            String val = rs.getString(i + 1);
                            row.createCell(i).setCellValue(val != null ? val : "");
                        }
                        numLigne++;
                    }
                }

                // Ecrire le fichier
                try (FileOutputStream fos = new FileOutputStream(fichier)) {
                    wb.write(fos);
                }

                final int total = numLigne - 1;
                Platform.runLater(() -> {
                    lblStatut.setText(total + " lignes exportees : " + fichier.getName());
                    lblStatut.setStyle("-fx-text-fill:#065f46;");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblStatut.setText("Erreur : " + e.getMessage());
                    lblStatut.setStyle("-fx-text-fill:#dc2626;");
                });
            }
        }).start();
    }

    @FXML private void onRetourDashboard() { naviguer("/fxml/dashboard.fxml"); }
    private void naviguer(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) lblStatut.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/global.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) { e.printStackTrace(); }
    }
}
