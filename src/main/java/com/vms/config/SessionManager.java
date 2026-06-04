package com.vms.config;

import com.vms.model.Utilisateur;

/**
 * Singleton – conserve l'utilisateur connecté pendant toute la session.
 */
public class SessionManager {

    private static SessionManager instance;
    private Utilisateur utilisateurConnecte;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    public Utilisateur getUtilisateurConnecte()       { return utilisateurConnecte; }
    public void setUtilisateurConnecte(Utilisateur u) { this.utilisateurConnecte = u; }

    public boolean estConnecte() { return utilisateurConnecte != null; }

    public void deconnecter() { utilisateurConnecte = null; }

    public String getRoleCourant() {
        return estConnecte() ? utilisateurConnecte.getRole() : "";
    }

    public boolean aLeRole(String role) {
        return getRoleCourant().equals(role);
    }
}
