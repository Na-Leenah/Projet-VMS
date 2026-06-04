package com.vms.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modèle correspondant à la table "utilisateurs".
 */
public class Utilisateur {

    private UUID          id;
    private UUID          societeId;
    private String        nom;
    private String        prenom;
    private String        email;
    private String        role;
    private boolean       estActif;
    private LocalDateTime derniereConnexion;

    public Utilisateur() {}

    public UUID getId()                              { return id; }
    public void setId(UUID id)                       { this.id = id; }

    public UUID getSocieteId()                       { return societeId; }
    public void setSocieteId(UUID societeId)         { this.societeId = societeId; }

    public String getNom()                           { return nom; }
    public void setNom(String nom)                   { this.nom = nom; }

    public String getPrenom()                        { return prenom; }
    public void setPrenom(String prenom)             { this.prenom = prenom; }

    public String getEmail()                         { return email; }
    public void setEmail(String email)               { this.email = email; }

    public String getRole()                          { return role; }
    public void setRole(String role)                 { this.role = role; }

    public boolean isEstActif()                      { return estActif; }
    public void setEstActif(boolean estActif)        { this.estActif = estActif; }

    public LocalDateTime getDerniereConnexion()      { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime d){ this.derniereConnexion = d; }

    public String getNomComplet() { return prenom + " " + nom; }

    public String getRoleLibelle() {
        return switch (role) {
            case "ADMIN"               -> "Administrateur";
            case "COMPTABLE"           -> "Comptable";
            case "APPROBATEUR"         -> "Approbateur";
            case "SUPERVISEUR_MAGASIN" -> "Superviseur Magasin";
            case "LECTEUR"             -> "Lecteur";
            default                    -> role;
        };
    }
}
