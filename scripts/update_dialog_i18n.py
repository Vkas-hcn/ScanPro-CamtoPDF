# -*- coding: utf-8 -*-
from pathlib import Path
import re

root = Path(r"D:\android_work\pdf_converter\app\src\main\res")
updates = {
    "hi": {
        "file_rename_title": "कृपया नया नाम दर्ज करें",
        "file_rename_hint": "कृपया नया नाम दर्ज करें",
        "file_delete_title": "क्या आप सुनिश्चित हैं ?",
        "file_delete_message": "यह फ़ाइल स्थायी रूप से हटा दी जाएगी",
        "action_save": "सहेजें",
    },
    "fr": {
        "file_rename_title": "Veuillez saisir un nouveau nom",
        "file_rename_hint": "Veuillez saisir un nouveau nom",
        "file_delete_title": "Êtes-vous sûr ?",
        "file_delete_message": "Ce fichier sera définitivement supprimé",
        "action_save": "Enregistrer",
    },
    "de": {
        "file_rename_title": "Bitte neuen Namen eingeben",
        "file_rename_hint": "Bitte neuen Namen eingeben",
        "file_delete_title": "Sind Sie sicher ?",
        "file_delete_message": "Diese Datei wird dauerhaft gelöscht",
        "action_save": "Speichern",
    },
    "pt": {
        "file_rename_title": "Digite o novo nome",
        "file_rename_hint": "Digite o novo nome",
        "file_delete_title": "Tem certeza ?",
        "file_delete_message": "Este arquivo será excluído permanentemente",
        "action_save": "Salvar",
    },
    "es": {
        "file_rename_title": "Introduce el nuevo nombre",
        "file_rename_hint": "Introduce el nuevo nombre",
        "file_delete_title": "¿Estás seguro ?",
        "file_delete_message": "Este archivo se eliminará permanentemente",
        "action_save": "Guardar",
    },
    "in": {
        "file_rename_title": "Masukkan nama baru",
        "file_rename_hint": "Masukkan nama baru",
        "file_delete_title": "Apakah Anda yakin ?",
        "file_delete_message": "File ini akan dihapus secara permanen",
        "action_save": "Simpan",
    },
    "ru": {
        "file_rename_title": "Введите новое имя",
        "file_rename_hint": "Введите новое имя",
        "file_delete_title": "Вы уверены ?",
        "file_delete_message": "Файл будет удалён безвозвратно",
        "action_save": "Сохранить",
    },
}


def esc(s: str) -> str:
    return (
        s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


for lang, kv in updates.items():
    path = root / f"values-{lang}" / "strings.xml"
    text = path.read_text(encoding="utf-8")
    for key, val in kv.items():
        pattern = rf'<string name="{key}">.*?</string>'
        repl = f'<string name="{key}">{esc(val)}</string>'
        if re.search(pattern, text):
            text = re.sub(pattern, repl, text)
        else:
            text = text.replace("</resources>", f"    {repl}\n</resources>")
    path.write_text(text, encoding="utf-8")
    print("updated", lang)
