package com.vms.model;

import java.util.UUID;

public class Client {
    private UUID   id;
    private String codeClient;
    private String nom;
    private String prenom;
    private String nomSociete;
    private String email;
    private String telephone;
    private String adresse;
    private String ville;

    public Client() {}

    public UUID   getId()          { return id; }
    public void   setId(UUID id)   { this.id = id; }

    public String getCodeClient()               { return codeClient; }
    public void   setCodeClient(String c)       { this.codeClient = c; }

    public String getNom()                      { return nom; }
    public void   setNom(String nom)            { this.nom = nom; }

    public String getPrenom()                   { return prenom; }
    public void   setPrenom(String prenom)      { this.prenom = prenom; }

    public String getNomSociete()               { return nomSociete; }
    public void   setNomSociete(String s)       { this.nomSociete = s; }

    public String getEmail()                    { return email; }
    public void   setEmail(String email)        { this.email = email; }

    public String getTelephone()                { return telephone; }
    public void   setTelephone(String t)        { this.telephone = t; }

    public String getAdresse()                  { return adresse; }
    public void   setAdresse(String a)          { this.adresse = a; }

    public String getVille()                    { return ville; }
    public void   setVille(String v)            { this.ville = v; }

    /** Affichage dans la liste déroulante */
    public String getAffichage() {
        String base = (prenom != null ? prenom + " " : "") + nom;
        return nomSociete != null && !nomSociete.isBlank()
                ? base + " — " + nomSociete
                : base;
    }

    @Override
    public String toString() { return getAffichage(); }
}
