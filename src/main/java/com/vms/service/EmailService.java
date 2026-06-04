package com.vms.service;

import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.util.Properties;

/**
 * Service d'envoi d'emails via Gmail SMTP.
 *
 * IMPORTANT : mot de passe d'application SANS espaces
 * Ex: Google affiche "abcd efgh ijkl mnop" -> saisir "abcdefghijklmnop"
 */
public class EmailService {

    // ── CONFIGURATION ───────────────────────────────────────
    private static final String EMAIL_EXPEDITEUR = "nazrababdallah@gmail.com"; // <- VOTRE GMAIL
    private static final String MOT_DE_PASSE_APP = "kolbvkgkglqlduao";        // <- 16 CHARS SANS ESPACES
    private static final String NOM_EXPEDITEUR   = "VMS VoucherManager";
    // ────────────────────────────────────────────────────────

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int    SMTP_PORT = 587;

    /**
     * Envoie un email HTML simple (sans pièce jointe).
     */
    public static void envoyer(String destinataire, String cc,
                                String sujet, String corpsHtml) throws MessagingException {

        System.out.println("[EMAIL] Envoi vers : " + destinataire + " | Sujet : " + sujet);

        Session session = creerSession();

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(EMAIL_EXPEDITEUR));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinataire));

        if (cc != null && !cc.isBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
        }

        message.setSubject(sujet);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(corpsHtml, "text/html; charset=UTF-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);
        message.setContent(multipart);

        Transport.send(message);
        System.out.println("[EMAIL] Envoye avec succes vers : " + destinataire);
    }

    /**
     * Notifie l'approbateur (ou l'admin) qu'un paiement a ete valide.
     */
    public static void notifierApprobateur(String emailApprobateur,
                                            String reference,
                                            String client,
                                            String montant) {
        String sujet = "[VMS] " + reference + " - Paiement valide - A approuver";
        String corps =
            "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;\">" +
            "<div style=\"background:#1e3a8a;padding:24px;border-radius:8px 8px 0 0;\">" +
            "<h2 style=\"color:white;margin:0;\">VoucherManager - Action requise</h2></div>" +
            "<div style=\"background:#f8fafc;padding:24px;border-radius:0 0 8px 8px;" +
            "border:1px solid #e2e8f0;\">" +
            "<p>Bonjour,</p>" +
            "<p>Le paiement de la demande suivante a ete valide et necessite votre approbation :</p>" +
            "<table style=\"width:100%;border-collapse:collapse;margin:16px 0;\">" +
            "<tr><td style=\"padding:8px;color:#6b7280;border-bottom:1px solid #e2e8f0;\">Reference</td>" +
            "<td style=\"padding:8px;font-weight:600;color:#1e40af;font-family:monospace;" +
            "border-bottom:1px solid #e2e8f0;\">" + reference + "</td></tr>" +
            "<tr><td style=\"padding:8px;color:#6b7280;border-bottom:1px solid #e2e8f0;\">Client</td>" +
            "<td style=\"padding:8px;border-bottom:1px solid #e2e8f0;\">" + client + "</td></tr>" +
            "<tr><td style=\"padding:8px;color:#6b7280;\">Montant</td>" +
            "<td style=\"padding:8px;font-weight:700;font-size:18px;color:#1e40af;\">" + montant + "</td></tr>" +
            "</table>" +
            "<p>Connectez-vous a VMS pour approuver ou rejeter cette demande.</p>" +
            "<p style=\"color:#9ca3af;font-size:12px;margin-top:24px;\">" +
            "VoucherManager - MCCI Business School 2026</p></div></div>";

        try {
            envoyer(emailApprobateur, null, sujet, corps);
        } catch (MessagingException e) {
            System.err.println("[EMAIL] Erreur notification approbateur : " + e.getMessage());
        }
    }

    /**
     * Envoie les bons au client avec les PDFs en pièces jointes.
     */
    public static void envoyerBonsClientAvecPdf(String emailClient, String nomClient,
                                                  String reference, int nbBons,
                                                  double valeurUnitaire, String dateExpiration,
                                                  java.util.List<byte[]> pdfs) {
        String sujet = "[VMS] Vos " + nbBons + " bons cadeaux - " + reference;

        String corps =
            "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;\">" +
            "  <div style=\"background:#1e3a8a;padding:24px;border-radius:8px 8px 0 0;\">" +
            "    <h2 style=\"color:white;margin:0;\">Vos bons cadeaux sont disponibles !</h2>" +
            "  </div>" +
            "  <div style=\"background:#f8fafc;padding:24px;border-radius:0 0 8px 8px;border:1px solid #e2e8f0;\">" +
            "    <p style=\"color:#374151;\">Bonjour " + nomClient + ",</p>" +
            "    <p style=\"color:#374151;\">Votre commande de bons cadeaux a été approuvée.</p>" +
            "    <div style=\"background:#d1fae5;border:1px solid #a7f3d0;border-radius:8px;padding:16px;margin:16px 0;\">" +
            "      <p style=\"color:#065f46;margin:0;font-weight:600;\">" +
            "        " + nbBons + " bons de Rs " + (int) valeurUnitaire + " chacun<br>" +
            "        Valides jusqu'au " + dateExpiration +
            "      </p>" +
            "    </div>" +
            "    <p style=\"color:#374151;\">Vos <strong>" + nbBons + " bons PDF</strong> sont joints à cet email.</p>" +
            "    <p style=\"color:#6b7280;font-size:12px;\">Chaque bon est à usage unique et non fractionnable.<br>" +
            "    Présentez le QR code en magasin pour la rédemption.</p>" +
            "    <p style=\"color:#9ca3af;font-size:12px;margin-top:24px;\">" +
            "      Référence commande : " + reference + "<br>" +
            "      VoucherManager - MCCI Business School 2026" +
            "    </p>" +
            "  </div>" +
            "</div>";

        try {
            Session session = creerSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_EXPEDITEUR, NOM_EXPEDITEUR));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailClient));
            message.setSubject(sujet);

            MimeBodyPart corpsHtml = new MimeBodyPart();
            corpsHtml.setContent(corps, "text/html; charset=UTF-8");

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(corpsHtml);

            int i = 1;
            for (byte[] pdf : pdfs) {
                MimeBodyPart attachment = new MimeBodyPart();
                // CORRECTION : jakarta.activation à la place de javax.activation
                attachment.setDataHandler(new jakarta.activation.DataHandler(
                        new jakarta.mail.util.ByteArrayDataSource(pdf, "application/pdf")));
                attachment.setFileName("bon_" + reference + "_" + String.format("%03d", i) + ".pdf");
                multipart.addBodyPart(attachment);
                i++;
            }

            message.setContent(multipart);
            Transport.send(message);
            System.out.println("[EMAIL] Envoye avec " + pdfs.size() + " PDF(s) vers : " + emailClient);

        } catch (Exception e) {
            System.err.println("[EMAIL] Erreur envoi email client avec PDFs : " + e.getMessage());
        }
    }

    /**
     * Recapitulatif admin apres generation des bons.
     */
    public static void envoyerRecapAdmin(String emailAdmin,
                                          String reference,
                                          String client,
                                          int nbBons,
                                          double montantTotal) {
        String sujet = "[VMS] Recap - " + nbBons + " bons emis - " + reference;
        String corps =
            "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;\">" +
            "<div style=\"background:#1e3a8a;padding:24px;border-radius:8px 8px 0 0;\">" +
            "<h2 style=\"color:white;margin:0;\">VMS - Bons emis avec succes</h2></div>" +
            "<div style=\"background:#f8fafc;padding:24px;border-radius:0 0 8px 8px;" +
            "border:1px solid #e2e8f0;\">" +
            "<p>Bonjour,</p>" +
            "<p>Les bons suivants ont ete generes et envoyes au client :</p>" +
            "<table style=\"width:100%;border-collapse:collapse;margin:16px 0;\">" +
            "<tr><td style=\"padding:8px;color:#6b7280;border-bottom:1px solid #e2e8f0;\">Reference</td>" +
            "<td style=\"padding:8px;font-family:monospace;color:#1e40af;" +
            "border-bottom:1px solid #e2e8f0;\">" + reference + "</td></tr>" +
            "<tr><td style=\"padding:8px;color:#6b7280;border-bottom:1px solid #e2e8f0;\">Client</td>" +
            "<td style=\"padding:8px;border-bottom:1px solid #e2e8f0;\">" + client + "</td></tr>" +
            "<tr><td style=\"padding:8px;color:#6b7280;border-bottom:1px solid #e2e8f0;\">Nb bons</td>" +
            "<td style=\"padding:8px;font-weight:600;border-bottom:1px solid #e2e8f0;\">" + nbBons + "</td></tr>" +
            "<tr><td style=\"padding:8px;color:#6b7280;\">Montant total</td>" +
            "<td style=\"padding:8px;font-weight:700;color:#1e40af;\">" +
            String.format("Rs %,.0f", montantTotal) + "</td></tr></table>" +
            "<p style=\"color:#9ca3af;font-size:12px;margin-top:24px;\">" +
            "VoucherManager - MCCI Business School 2026</p></div></div>";

        try {
            envoyer(emailAdmin, null, sujet, corps);
        } catch (MessagingException e) {
            System.err.println("[EMAIL] Erreur recap admin : " + e.getMessage());
        }
    }

    private static Session creerSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth",              "true");
        props.put("mail.smtp.starttls.enable",   "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host",              SMTP_HOST);
        props.put("mail.smtp.port",              String.valueOf(SMTP_PORT));
        props.put("mail.smtp.ssl.trust",         SMTP_HOST);
        props.put("mail.debug",                  "false");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_EXPEDITEUR, MOT_DE_PASSE_APP);
            }
        });
    }

    /**
     * Test de connexion SMTP - appeler depuis GenererHash.java pour verifier.
     */
    public static boolean testerConnexion() {
        System.out.println("[EMAIL] Test connexion SMTP Gmail...");
        try {
            Session session = creerSession();
            Transport transport = session.getTransport("smtp");
            transport.connect(SMTP_HOST, SMTP_PORT, EMAIL_EXPEDITEUR, MOT_DE_PASSE_APP);
            transport.close();
            System.out.println("[EMAIL] Connexion SMTP OK !");
            return true;
        } catch (Exception e) {
            System.err.println("[EMAIL] Connexion SMTP ECHEC : " + e.getMessage());
            return false;
        }
    }
}