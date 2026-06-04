package com.vms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Demande {

    private UUID          id;
    private String        reference;
    private String        clientNom;
    private String        clientSociete;
    private int           nombreBons;
    private double        valeurUnitaire;
    private double        montantTotal;
    private String        statut;
    private LocalDate     dateDemande;
    private LocalDate     dateExpiration;
    private String        remarques;
    private LocalDateTime creeLe;

    public Demande() {}

    // Getters / Setters
    public UUID   getId()                        { return id; }
    public void   setId(UUID id)                 { this.id = id; }

    public String getReference()                 { return reference; }
    public void   setReference(String r)         { this.reference = r; }

    public String getClientNom()                 { return clientNom; }
    public void   setClientNom(String n)         { this.clientNom = n; }

    public String getClientSociete()             { return clientSociete; }
    public void   setClientSociete(String s)     { this.clientSociete = s; }

    public int    getNombreBons()                { return nombreBons; }
    public void   setNombreBons(int n)           { this.nombreBons = n; }

    public double getValeurUnitaire()            { return valeurUnitaire; }
    public void   setValeurUnitaire(double v)    { this.valeurUnitaire = v; }

    public double getMontantTotal()              { return montantTotal; }
    public void   setMontantTotal(double m)      { this.montantTotal = m; }

    public String getStatut()                    { return statut; }
    public void   setStatut(String s)            { this.statut = s; }

    public LocalDate getDateDemande()            { return dateDemande; }
    public void   setDateDemande(LocalDate d)    { this.dateDemande = d; }

    public LocalDate getDateExpiration()         { return dateExpiration; }
    public void   setDateExpiration(LocalDate d) { this.dateExpiration = d; }

    public String getRemarques()                 { return remarques; }
    public void   setRemarques(String r)         { this.remarques = r; }

    public LocalDateTime getCreeLe()             { return creeLe; }
    public void   setCreeLe(LocalDateTime d)     { this.creeLe = d; }

    /** Libellé lisible du statut */
    public String getStatutLibelle() {
        if (statut == null) return "";
        return switch (statut) {
            case "EN_ATTENTE"      -> "En attente";
            case "FACTUREE"        -> "Facturée";
            case "PAIEMENT_VALIDE" -> "Att. approbation";
            case "APPROUVEE"       -> "Approuvée";
            case "EMISE"           -> "Émise";
            case "ANNULEE"         -> "Annulée";
            default                -> statut;
        };
    }

    /** Classe CSS badge selon statut */
    public String getStatutBadgeStyle() {
        if (statut == null) return "";
        return switch (statut) {
            case "EN_ATTENTE", "FACTUREE" ->
                    "-fx-background-color:#fef3c7;-fx-text-fill:#92400e;";
            case "PAIEMENT_VALIDE" ->
                    "-fx-background-color:#f3e8ff;-fx-text-fill:#6b21a8;";
            case "APPROUVEE" ->
                    "-fx-background-color:#dbeafe;-fx-text-fill:#1e40af;";
            case "EMISE" ->
                    "-fx-background-color:#d1fae5;-fx-text-fill:#065f46;";
            case "ANNULEE" ->
                    "-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;";
            default ->
                    "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
        };
    }

    /** Affichage client dans le tableau */
    public String getClientAffichage() {
        if (clientSociete != null && !clientSociete.isBlank())
            return clientSociete;
        return clientNom != null ? clientNom : "";
    }

    /** Montant formaté */
    public String getMontantFormate() {
        return String.format("Rs %,.0f", montantTotal);
    }
}
