# -*- coding: utf-8 -*-
from pathlib import Path

root = Path(r"D:/android_work/pdf_converter/app/src/main/res")
translations = {
    "hi": {
        "guide_get_start": "शुरू करें",
        "guide_agree_full": "मैं %1$s और %2$s से सहमत हूँ",
        "guide_terms_of_service": "सेवा की शर्तें",
        "guide_agree_required": "कृपया गोपनीयता नीति से सहमत हों",
        "guide_skip": "छोड़ें",
        "guide_allow": "अनुमति दें",
        "guide_overlay_headline": "महत्वपूर्ण सूचनाएँ न चूकें",
        "guide_overlay_subtitle": "फ़्लोटिंग विंडो अनुमतियाँ",
        "guide_overlay_manage": "प्रबंधन अनुमतियाँ",
        "guide_notif_title": "सूचनाएँ सक्षम करें",
        "guide_notif_message": "दस्तावेज़ अनुस्मारक और अपडेट न चूकने के लिए सूचनाएँ अनुमति दें।",
        "guide_notif_enable": "सक्षम करें",
        "guide_notif_later": "अभी नहीं",
    },
    "fr": {
        "guide_get_start": "Commencer",
        "guide_agree_full": "J\'accepte la %1$s et les %2$s",
        "guide_terms_of_service": "Conditions d\'utilisation",
        "guide_agree_required": "Veuillez accepter la politique de confidentialité",
        "guide_skip": "Passer",
        "guide_allow": "Autoriser",
        "guide_overlay_headline": "Ne manquez pas les notifications importantes",
        "guide_overlay_subtitle": "Autorisations de fenêtre flottante",
        "guide_overlay_manage": "Autorisations de gestion",
        "guide_notif_title": "Activer les notifications",
        "guide_notif_message": "Autorisez les notifications pour ne manquer aucun rappel de document.",
        "guide_notif_enable": "Activer",
        "guide_notif_later": "Pas maintenant",
    },
    "de": {
        "guide_get_start": "Starten",
        "guide_agree_full": "Ich stimme der %1$s und den %2$s zu",
        "guide_terms_of_service": "Nutzungsbedingungen",
        "guide_agree_required": "Bitte stimmen Sie der Datenschutzrichtlinie zu",
        "guide_skip": "Überspringen",
        "guide_allow": "Erlauben",
        "guide_overlay_headline": "Verpassen Sie keine wichtigen Benachrichtigungen",
        "guide_overlay_subtitle": "Berechtigungen für schwebende Fenster",
        "guide_overlay_manage": "Verwaltungsberechtigungen",
        "guide_notif_title": "Benachrichtigungen aktivieren",
        "guide_notif_message": "Erlauben Sie Benachrichtigungen, um Dokumenterinnerungen nicht zu verpassen.",
        "guide_notif_enable": "Aktivieren",
        "guide_notif_later": "Nicht jetzt",
    },
    "pt": {
        "guide_get_start": "Começar",
        "guide_agree_full": "Concordo com a %1$s e os %2$s",
        "guide_terms_of_service": "Termos de Serviço",
        "guide_agree_required": "Aceite a política de privacidade",
        "guide_skip": "Pular",
        "guide_allow": "Permitir",
        "guide_overlay_headline": "Não perca notificações importantes",
        "guide_overlay_subtitle": "Permissões de janela flutuante",
        "guide_overlay_manage": "Permissões de gerenciamento",
        "guide_notif_title": "Ativar notificações",
        "guide_notif_message": "Permita notificações para não perder lembretes de documentos.",
        "guide_notif_enable": "Ativar",
        "guide_notif_later": "Agora não",
    },
    "es": {
        "guide_get_start": "Empezar",
        "guide_agree_full": "Acepto la %1$s y los %2$s",
        "guide_terms_of_service": "Términos de servicio",
        "guide_agree_required": "Acepta la política de privacidad",
        "guide_skip": "Omitir",
        "guide_allow": "Permitir",
        "guide_overlay_headline": "No te pierdas notificaciones importantes",
        "guide_overlay_subtitle": "Permisos de ventana flotante",
        "guide_overlay_manage": "Permisos de administración",
        "guide_notif_title": "Activar notificaciones",
        "guide_notif_message": "Permite notificaciones para no perder recordatorios de documentos.",
        "guide_notif_enable": "Activar",
        "guide_notif_later": "Ahora no",
    },
    "in": {
        "guide_get_start": "Mulai",
        "guide_agree_full": "Saya setuju dengan %1$s dan %2$s",
        "guide_terms_of_service": "Ketentuan Layanan",
        "guide_agree_required": "Harap setujui kebijakan privasi",
        "guide_skip": "Lewati",
        "guide_allow": "Izinkan",
        "guide_overlay_headline": "Jangan lewatkan notifikasi penting",
        "guide_overlay_subtitle": "Izin jendela mengambang",
        "guide_overlay_manage": "Izin pengelolaan",
        "guide_notif_title": "Aktifkan notifikasi",
        "guide_notif_message": "Izinkan notifikasi agar tidak melewatkan pengingat dokumen.",
        "guide_notif_enable": "Aktifkan",
        "guide_notif_later": "Nanti saja",
    },
    "ru": {
        "guide_get_start": "Начать",
        "guide_agree_full": "Я согласен с %1$s и %2$s",
        "guide_terms_of_service": "Условиями использования",
        "guide_agree_required": "Примите политику конфиденциальности",
        "guide_skip": "Пропустить",
        "guide_allow": "Разрешить",
        "guide_overlay_headline": "Не пропускайте важные уведомления",
        "guide_overlay_subtitle": "Разрешения плавающего окна",
        "guide_overlay_manage": "Разрешения управления",
        "guide_notif_title": "Включить уведомления",
        "guide_notif_message": "Разрешите уведомления, чтобы не пропускать напоминания о документах.",
        "guide_notif_enable": "Включить",
        "guide_notif_later": "Не сейчас",
    },
}


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


for loc, pairs in translations.items():
    path = root / f"values-{loc}" / "strings.xml"
    text = path.read_text(encoding="utf-8")
    if "guide_notif_later" in text:
        print(loc, "skip")
        continue
    block = "\n".join(f'    <string name="{k}">{esc(v)}</string>' for k, v in pairs.items())
    path.write_text(text.replace("</resources>", block + "\n</resources>"), encoding="utf-8")
    print(loc, "ok")
