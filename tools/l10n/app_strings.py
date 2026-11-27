# Android-specific translations for rows marked 조정 (adjusted) and 별도 (Android-only) in the
# Figma handoff "Android 화면별 다국어 인계표" (node 311-3097). Rows marked 그대로 come straight
# from the web dictionaries (tools/l10n/generate.py). Order of values per entry:
LANGS = ["en", "ja", "de", "fr", "id", "vi", "tr", "ru", "hi", "th", "pt-BR", "es-ES", "es-419", "zh-Hans", "zh-Hant-TW", "zh-Hant-HK"]

# Plural entries are dicts {quantity: text}; plain entries are strings. %1$s / %1$d keep positions.
P = "plural"

APP = {
    # TP-0220 · Android reads records stored on the device
    "load_failed_title": [
        "Couldn’t load your notes", "記録を読み込めませんでした", "Notizen konnten nicht geladen werden",
        "Impossible de charger vos notes", "Catatan tidak dapat dimuat", "Không tải được ghi chú",
        "Notlar yüklenemedi", "Не удалось загрузить записи", "नोट लोड नहीं हो सके",
        "โหลดบันทึกไม่ได้", "Não foi possível carregar as anotações", "No se han podido cargar las notas",
        "No se pudieron cargar las notas", "无法载入记录", "無法載入紀錄", "無法載入記錄",
    ],
    # TP-0217
    "load_failed_body": [
        "The notes saved on this device can’t be read.\nPlease try again in a moment.",
        "この端末に保存された記録を読み込めません。\nしばらくしてからもう一度お試しください。",
        "Die auf diesem Gerät gespeicherten Notizen können nicht gelesen werden.\nBitte versuche es gleich noch einmal.",
        "Les notes enregistrées sur cet appareil ne peuvent pas être lues.\nRéessayez dans un instant.",
        "Catatan yang tersimpan di perangkat ini tidak dapat dibaca.\nCoba lagi sebentar lagi.",
        "Không đọc được ghi chú đã lưu trên thiết bị này.\nVui lòng thử lại sau giây lát.",
        "Bu cihazda kayıtlı notlar okunamıyor.\nBiraz sonra tekrar dene.",
        "Не удаётся прочитать записи, сохранённые на этом устройстве.\nПопробуйте ещё раз чуть позже.",
        "इस डिवाइस पर सहेजे गए नोट पढ़े नहीं जा सके।\nथोड़ी देर बाद फिर से कोशिश करें।",
        "อ่านบันทึกที่เก็บไว้ในเครื่องนี้ไม่ได้\nโปรดลองอีกครั้งในอีกสักครู่",
        "Não é possível ler as anotações salvas neste aparelho.\nTente novamente daqui a pouco.",
        "No se pueden leer las notas guardadas en este dispositivo.\nVuelve a intentarlo en un momento.",
        "No se pueden leer las notas guardadas en este dispositivo.\nVuelve a intentarlo en un momento.",
        "无法读取保存在此设备上的记录。\n请稍后再试。",
        "無法讀取儲存在這部裝置上的紀錄。\n請稍後再試一次。",
        "無法讀取儲存在此裝置上的記錄。\n請稍後再試。",
    ],
    # TP-0309 · adjusted from demo/TP-0052; %1$s is a view name (일간, 기록한 날, 월간 달력, 연간)
    "cd_back_to": [
        "Back to %1$s", "%1$sに戻る", "Zurück zur Ansicht „%1$s“", "Revenir à la vue « %1$s »",
        "Kembali ke tampilan %1$s", "Quay lại chế độ %1$s", "“%1$s” görünümüne dön", "Назад к разделу «%1$s»",
        "इस दृश्य पर लौटें: %1$s", "กลับไปที่มุมมอง%1$s", "Voltar para a visualização “%1$s”",
        "Volver a la vista «%1$s»", "Volver a la vista “%1$s”", "返回%1$s", "返回%1$s", "返回%1$s",
    ],
    # TP-0218 · device storage, no web refresh notice
    "settings_storage_body": [
        "Notes and drafts are saved on this device and are deleted if you clear the app’s data. Tokipin doesn’t access your location or calendar.",
        "記録と下書きはこの端末に保存されます。アプリのデータを消去すると一緒に削除されます。位置情報やカレンダーにはアクセスしません。",
        "Notizen und Entwürfe werden auf diesem Gerät gespeichert und beim Löschen der App-Daten mit entfernt. Tokipin greift nicht auf deinen Standort oder Kalender zu.",
        "Les notes et brouillons sont enregistrés sur cet appareil et supprimés si vous effacez les données de l’app. Tokipin n’accède ni à votre position ni à votre agenda.",
        "Catatan dan draf disimpan di perangkat ini dan ikut terhapus jika data aplikasi dihapus. Tokipin tidak mengakses lokasi atau kalendermu.",
        "Ghi chú và bản nháp được lưu trên thiết bị này và sẽ bị xóa khi bạn xóa dữ liệu ứng dụng. Tokipin không truy cập vị trí hay lịch của bạn.",
        "Notlar ve taslaklar bu cihazda saklanır; uygulama verilerini silersen onlar da silinir. Tokipin konumuna veya takvimine erişmez.",
        "Записи и черновики хранятся на этом устройстве и удаляются вместе с данными приложения. Tokipin не обращается к вашему местоположению и календарю.",
        "नोट और ड्राफ़्ट इसी डिवाइस पर सहेजे जाते हैं और ऐप का डेटा मिटाने पर साथ में मिट जाते हैं। Tokipin आपकी जगह या कैलेंडर तक नहीं पहुँचता।",
        "บันทึกและฉบับร่างจะเก็บไว้ในเครื่องนี้ และจะถูกลบไปด้วยเมื่อล้างข้อมูลแอป Tokipin ไม่เข้าถึงตำแหน่งหรือปฏิทินของคุณ",
        "Anotações e rascunhos ficam salvos neste aparelho e são apagados se você limpar os dados do app. O Tokipin não acessa sua localização nem sua agenda.",
        "Las notas y los borradores se guardan en este dispositivo y se eliminan si borras los datos de la app. Tokipin no accede a tu ubicación ni a tu calendario.",
        "Las notas y los borradores se guardan en este dispositivo y se eliminan si borras los datos de la app. Tokipin no accede a tu ubicación ni a tu calendario.",
        "记录和草稿保存在这台设备上，清除应用数据时会一并删除。Tokipin 不会访问你的位置或日历。",
        "紀錄和草稿會儲存在這部裝置上，清除 App 資料時會一併刪除。Tokipin 不會存取你的位置或日曆。",
        "記錄和草稿會儲存在此裝置上，清除應用程式資料時會一併刪除。Tokipin 不會存取你的位置或日曆。",
    ],
    # TP-0219 · after the document is actually written
    "settings_export_done": [
        "Notes exported.", "記録を書き出しました。", "Notizen exportiert.", "Notes exportées.",
        "Catatan sudah diekspor.", "Đã xuất ghi chú.", "Notlar dışa aktarıldı.", "Записи экспортированы.",
        "नोट एक्सपोर्ट हो गए।", "ส่งออกบันทึกแล้ว", "Anotações exportadas.", "Notas exportadas.",
        "Notas exportadas.", "记录已导出。", "紀錄已匯出。", "記錄已匯出。",
    ],
    # TP-0221
    "settings_export_failed": [
        "Couldn’t export. Please try again.", "書き出せませんでした。もう一度お試しください。",
        "Export fehlgeschlagen. Bitte versuche es noch einmal.", "Échec de l’export. Réessayez.",
        "Gagal mengekspor. Coba lagi.", "Không xuất được. Vui lòng thử lại.", "Dışa aktarılamadı. Tekrar dene.",
        "Не удалось экспортировать. Попробуйте ещё раз.", "एक्सपोर्ट नहीं हो सका। फिर से कोशिश करें।",
        "ส่งออกไม่สำเร็จ โปรดลองอีกครั้ง", "Não foi possível exportar. Tente novamente.",
        "No se ha podido exportar. Vuelve a intentarlo.", "No se pudo exportar. Vuelve a intentarlo.",
        "导出失败，请重试。", "無法匯出，請再試一次。", "無法匯出，請再試一次。",
    ],
    # TP-0239 · adjusted from demo/TP-0007: Android picks a place and saves a file (CreateDocument)
    "settings_export_body": [
        "Save your notes as a JSON file wherever you choose.", "保存した記録をJSONファイルとして好きな場所に保存します。",
        "Speichere deine Notizen als JSON-Datei an einem Ort deiner Wahl.", "Enregistrez vos notes dans un fichier JSON, à l’emplacement de votre choix.",
        "Simpan catatan sebagai file JSON di lokasi pilihanmu.", "Lưu các ghi chú dưới dạng tệp JSON vào vị trí bạn chọn.",
        "Kaydettiğin notları seçtiğin konuma JSON dosyası olarak kaydet.", "Сохраните записи в файл JSON в выбранном месте.",
        "सहेजे हुए नोट अपनी चुनी जगह पर JSON फ़ाइल के रूप में सेव करें।", "บันทึกข้อมูลเป็นไฟล์ JSON ไว้ในตำแหน่งที่คุณเลือก",
        "Salve suas anotações em um arquivo JSON no local que escolher.", "Guarda tus notas en un archivo JSON donde prefieras.",
        "Guarda tus notas en un archivo JSON donde prefieras.", "将已保存的记录存为 JSON 文件，位置由你选择。",
        "將儲存的紀錄存成 JSON 檔案，位置由你選擇。", "將已儲存的記錄存為 JSON 檔案，位置由你選擇。",
    ],
    # TP-0323 · nothing to export yet
    "settings_export_empty": [
        "You can export once you’ve written a note.", "記録を残すと書き出せます。", "Sobald du eine Notiz hast, kannst du exportieren.",
        "Vous pourrez exporter après avoir écrit une note.", "Kamu bisa mengekspor setelah menulis catatan.",
        "Bạn có thể xuất sau khi viết ghi chú.", "Not ekledikten sonra dışa aktarabilirsin.", "Экспорт станет доступен после первой записи.",
        "नोट लिखने के बाद एक्सपोर्ट कर सकते हैं।", "เมื่อมีบันทึกแล้วจึงจะส่งออกได้", "Você poderá exportar depois de escrever uma anotação.",
        "Podrás exportar cuando escribas una nota.", "Podrás exportar cuando escribas una nota.", "写下记录后即可导出。",
        "留下紀錄後就能匯出。", "留下記錄後便可匯出。",
    ],
    # TP-0259 · feedback (메모 1: Android mail app and copy flow)
    "settings_feedback": [
        "Send us a note", "Tokipinにひとこと", "Schreib uns", "Nous écrire", "Kirim pesan ke Tokipin",
        "Gửi lời nhắn cho Tokipin", "Tokipin’e bir not yaz", "Написать в Tokipin", "Tokipin को संदेश भेजें",
        "ส่งข้อความถึง Tokipin", "Fale com a Tokipin", "Escríbenos", "Escríbenos", "给 Tokipin 留言",
        "給 Tokipin 留言", "給 Tokipin 留言",
    ],
    # TP-0275
    "feedback_fallback": [
        "Your mail app couldn’t be opened. Please write to the address below.",
        "メールアプリを開けません。下のアドレスに送ってください。",
        "Die Mail-App lässt sich nicht öffnen. Schreib uns bitte an die Adresse unten.",
        "Impossible d’ouvrir l’app de messagerie. Écrivez-nous à l’adresse ci-dessous.",
        "Aplikasi email tidak dapat dibuka. Kirim ke alamat di bawah ini.",
        "Không mở được ứng dụng email. Vui lòng gửi đến địa chỉ bên dưới.",
        "E-posta uygulaması açılamadı. Lütfen aşağıdaki adrese yaz.",
        "Не удалось открыть почтовое приложение. Напишите на адрес ниже.",
        "ईमेल ऐप नहीं खुल सका। कृपया नीचे दिए पते पर भेजें।",
        "เปิดแอปอีเมลไม่ได้ โปรดส่งไปยังที่อยู่ด้านล่าง",
        "Não foi possível abrir o app de e-mail. Envie para o endereço abaixo.",
        "No se ha podido abrir la app de correo. Escríbenos a la dirección de abajo.",
        "No se pudo abrir la app de correo. Escríbenos a la dirección de abajo.",
        "无法打开邮件应用。请发送到下方地址。", "無法開啟郵件 App，請寄到下方的地址。", "無法開啟電郵應用程式，請寄到下方地址。",
    ],
    # TP-0276
    "feedback_mail": [
        "Send an email", "メールで送る", "E-Mail schreiben", "Envoyer un e-mail", "Kirim email", "Gửi email",
        "E-posta gönder", "Написать письмо", "ईमेल भेजें", "ส่งอีเมล", "Enviar e-mail", "Enviar un correo",
        "Enviar un correo", "发送邮件", "寄送電子郵件", "發送電郵",
    ],
    # TP-0280 · adjusted from demo/TP-0001 (short form on the back button)
    "cd_back_to_settings": [
        "Back to settings", "設定に戻る", "Zurück zu den Einstellungen", "Retour aux réglages", "Kembali ke pengaturan",
        "Quay lại cài đặt", "Ayarlara dön", "Вернуться в настройки", "सेटिंग्स पर लौटें", "กลับไปที่การตั้งค่า",
        "Voltar às configurações", "Volver a los ajustes", "Volver a la configuración", "返回设置", "返回設定", "返回設定",
    ],
    # TP-0281
    "feedback_lead": [
        "Did something come to mind while writing?\nWe’d love to hear anything.",
        "書いていて思いついたことはありますか？\nどんなお話でも歓迎です。",
        "Ist dir beim Schreiben etwas eingefallen?\nWir freuen uns über jede Nachricht.",
        "Une idée vous est venue en écrivant ?\nNous serons ravis de vous lire.",
        "Ada yang terpikir saat menulis?\nKami senang mendengar apa pun darimu.",
        "Bạn có suy nghĩ gì khi viết không?\nChúng tôi luôn vui được lắng nghe.",
        "Yazarken aklına bir şey geldi mi?\nHer türlü fikrini duymaktan memnuniyet duyarız.",
        "Появилась мысль, пока писали?\nБудем рады любому сообщению.",
        "लिखते हुए कोई बात मन में आई?\nआपकी हर बात सुनकर हमें खुशी होगी।",
        "ระหว่างเขียนมีอะไรผุดขึ้นมาไหม\nเรายินดีรับฟังทุกเรื่อง",
        "Pensou em algo enquanto escrevia?\nAdoraríamos saber.",
        "¿Se te ha ocurrido algo mientras escribías?\nNos encantará leerte.",
        "¿Se te ocurrió algo mientras escribías?\nNos encantará leerte.",
        "写的时候有什么想法吗？\n什么都欢迎告诉我们。",
        "寫的時候有想到什麼嗎？\n什麼都歡迎跟我們說。",
        "寫的時候有甚麼想法嗎？\n甚麼都歡迎告訴我們。",
    ],
    # TP-0294
    "feedback_copy": [
        "Copy address", "アドレスをコピー", "Adresse kopieren", "Copier l’adresse", "Salin alamat", "Sao chép địa chỉ",
        "Adresi kopyala", "Скопировать адрес", "पता कॉपी करें", "คัดลอกที่อยู่", "Copiar endereço", "Copiar dirección",
        "Copiar dirección", "复制地址", "複製地址", "複製地址",
    ],
    # TP-0295
    "feedback_copied": [
        "Address copied", "アドレスをコピーしました", "Adresse kopiert", "Adresse copiée", "Alamat disalin", "Đã sao chép địa chỉ",
        "Adres kopyalandı", "Адрес скопирован", "पता कॉपी हो गया", "คัดลอกที่อยู่แล้ว", "Endereço copiado", "Dirección copiada",
        "Dirección copiada", "地址已复制", "已複製地址", "已複製地址",
    ],
    # TP-0204 · adjusted from demo/TP-0116, plurals
    "editor_selected_count": [
        {"one": "%1$d selected", "other": "%1$d selected"}, {"other": "%1$d件選択中"},
        {"one": "%1$d ausgewählt", "other": "%1$d ausgewählt"},
        {"one": "Sélection : %1$d", "many": "Sélection : %1$d", "other": "Sélection : %1$d"},
        {"other": "%1$d item dipilih"}, {"other": "Đã chọn %1$d mục"},
        {"one": "%1$d öğe seçili", "other": "%1$d öğe seçili"},
        {"one": "Выбрано: %1$d", "few": "Выбрано: %1$d", "many": "Выбрано: %1$d", "other": "Выбрано: %1$d"},
        {"one": "चुने गए आइटम: %1$d", "other": "चुने गए आइटम: %1$d"}, {"other": "เลือกแล้ว %1$d รายการ"},
        {"one": "%1$d item selecionado", "many": "%1$d itens selecionados", "other": "%1$d itens selecionados"},
        {"one": "%1$d elemento seleccionado", "many": "%1$d elementos seleccionados", "other": "%1$d elementos seleccionados"},
        {"one": "%1$d elemento seleccionado", "many": "%1$d elementos seleccionados", "other": "%1$d elementos seleccionados"},
        {"other": "已选 %1$d 项"}, {"other": "已選取 %1$d 個項目"}, {"other": "已選取 %1$d 個項目"},
    ],
    # TP-0238
    "editor_save_failed": [
        "Couldn’t save.\nWhat you entered is still on this screen.\nPlease check your storage space.",
        "保存できませんでした。\n入力内容はこの画面に残っています。\n空き容量を確認してください。",
        "Speichern fehlgeschlagen.\nDeine Eingaben sind noch auf diesem Bildschirm.\nBitte prüfe den Speicherplatz.",
        "Impossible d’enregistrer.\nVotre saisie reste sur cet écran.\nVérifiez l’espace de stockage.",
        "Gagal menyimpan.\nIsianmu masih ada di layar ini.\nPeriksa ruang penyimpanan.",
        "Không lưu được.\nNội dung bạn nhập vẫn còn trên màn hình này.\nVui lòng kiểm tra dung lượng lưu trữ.",
        "Kaydedilemedi.\nGirdiklerin bu ekranda duruyor.\nDepolama alanını kontrol et.",
        "Не удалось сохранить.\nВведённое осталось на этом экране.\nПроверьте свободное место.",
        "सेव नहीं हो सका।\nआपने जो लिखा वह इसी स्क्रीन पर है।\nस्टोरेज की जगह देखें।",
        "บันทึกไม่สำเร็จ\nสิ่งที่พิมพ์ยังอยู่ในหน้านี้\nโปรดตรวจสอบพื้นที่เก็บข้อมูล",
        "Não foi possível salvar.\nO que você digitou continua nesta tela.\nVerifique o espaço de armazenamento.",
        "No se ha podido guardar.\nLo que has escrito sigue en esta pantalla.\nRevisa el espacio de almacenamiento.",
        "No se pudo guardar.\nLo que escribiste sigue en esta pantalla.\nRevisa el espacio de almacenamiento.",
        "未能保存。\n输入的内容仍保留在此页面。\n请检查存储空间。",
        "無法儲存。\n輸入的內容仍保留在這個畫面。\n請檢查儲存空間。",
        "未能儲存。\n輸入的內容仍保留在此畫面。\n請檢查儲存空間。",
    ],
    # TP-0299 · TalkBack selected state
    "cd_selected": [
        "Selected", "選択中", "Ausgewählt", "Sélectionné", "Dipilih", "Đã chọn", "Seçili", "Выбрано", "चुना गया",
        "เลือกอยู่", "Selecionado", "Seleccionado", "Seleccionado", "已选择", "已選取", "已選取",
    ],
    # TP-0300 · adjusted from demo/TP-0117: the previous screen depends on where the editor was opened
    "cd_back_keep_draft": [
        "Back, keep the draft", "前の画面に戻る、下書きを保存", "Zurück, Entwurf behalten", "Retour, garder le brouillon",
        "Kembali, simpan draf", "Quay lại, giữ bản nháp", "Geri dön, taslağı sakla", "Назад, сохранить черновик",
        "पीछे जाएँ, ड्राफ़्ट रखें", "ย้อนกลับ เก็บฉบับร่างไว้", "Voltar e manter o rascunho", "Volver y conservar el borrador",
        "Volver y conservar el borrador", "返回，保留草稿", "返回，保留草稿", "返回，保留草稿",
    ],
    # TP-0301
    "editor_empty_edit": [
        "Enter a value or delete the item.", "値を入力するか、項目を削除してください。", "Gib einen Wert ein oder lösche das Element.",
        "Saisissez une valeur ou supprimez l’élément.", "Isi nilainya atau hapus item ini.", "Hãy nhập giá trị hoặc xóa mục này.",
        "Bir değer gir ya da öğeyi sil.", "Введите значение или удалите элемент.", "कोई मान लिखें या आइटम मिटाएँ।",
        "ป้อนค่า หรือลบรายการนี้", "Digite um valor ou exclua o item.", "Escribe un valor o elimina el elemento.",
        "Escribe un valor o elimina el elemento.", "请输入内容或删除此项。", "請輸入內容或刪除這個項目。", "請輸入內容或刪除此項目。",
    ],
    # TP-0302
    "editor_need_one": [
        "Add at least a word or an emoji.", "ことばか絵文字をひとつ残してみましょう。", "Füge mindestens ein Wort oder Emoji hinzu.",
        "Ajoutez au moins un mot ou un émoji.", "Tambahkan setidaknya satu kata atau emoji.", "Hãy thêm ít nhất một từ hoặc một emoji.",
        "En az bir kelime ya da emoji ekle.", "Добавьте хотя бы одно слово или эмодзи.", "कम से कम एक शब्द या इमोजी जोड़ें।",
        "ลองเพิ่มคำหรืออีโมจิสักหนึ่งอย่าง", "Adicione pelo menos uma palavra ou um emoji.", "Añade al menos una palabra o un emoji.",
        "Agrega al menos una palabra o un emoji.", "至少留下一个词语或表情符号。", "至少留下一個詞語或表情符號。", "至少留下一個字詞或表情符號。",
    ],
    # TP-0303 · adjusted from demo/TP-0106: only the device keyboard's emoji key
    "editor_emoji_prompt": [
        "Tap the emoji key on your keyboard.", "キーボードの絵文字キーをタップ。", "Tippe auf die Emoji-Taste deiner Tastatur.",
        "Touchez la touche émoji du clavier.", "Ketuk tombol emoji di papan ketik.", "Chạm vào phím emoji trên bàn phím.",
        "Klavyendeki emoji tuşuna dokun.", "Нажмите клавишу эмодзи на клавиатуре.", "अपने कीबोर्ड पर इमोजी का बटन टैप करें।",
        "แตะปุ่มอีโมจิบนแป้นพิมพ์", "Use a tecla de emoji do seu teclado.", "Usa la tecla de emojis de tu teclado.",
        "Usa la tecla de emojis de tu teclado.", "点键盘上的表情键。", "點鍵盤上的表情符號鍵。", "點選鍵盤上的表情符號鍵。",
    ],
    # TP-0304 · adjusted from demo/TP-0136, plurals
    "editor_items_deleted": [
        {"one": "%1$d item deleted.", "other": "%1$d items deleted."}, {"other": "%1$d件の項目を削除しました。"},
        {"one": "%1$d Element gelöscht.", "other": "%1$d Elemente gelöscht."},
        {"one": "%1$d élément supprimé.", "many": "%1$d éléments supprimés.", "other": "%1$d éléments supprimés."},
        {"other": "%1$d item dihapus."}, {"other": "Đã xóa %1$d mục."},
        {"one": "%1$d öğe silindi.", "other": "%1$d öğe silindi."},
        {"one": "Удалён %1$d элемент.", "few": "Удалено %1$d элемента.", "many": "Удалено %1$d элементов.", "other": "Удалено %1$d элемента."},
        {"one": "%1$d आइटम मिटाया गया।", "other": "%1$d आइटम मिटाए गए।"}, {"other": "ลบ %1$d รายการแล้ว"},
        {"one": "%1$d item excluído.", "many": "%1$d itens excluídos.", "other": "%1$d itens excluídos."},
        {"one": "%1$d elemento eliminado.", "many": "%1$d elementos eliminados.", "other": "%1$d elementos eliminados."},
        {"one": "%1$d elemento eliminado.", "many": "%1$d elementos eliminados.", "other": "%1$d elementos eliminados."},
        {"other": "已删除 %1$d 项。"}, {"other": "已刪除 %1$d 個項目。"}, {"other": "已刪除 %1$d 個項目。"},
    ],
    # TP-0313 · adjusted from demo/TP-0123 (the value is already read by the row)
    "item_edit": [
        "Edit", "編集", "Bearbeiten", "Modifier", "Edit", "Sửa", "Düzenle", "Изменить", "बदलें", "แก้ไข", "Editar",
        "Editar", "Editar", "编辑", "編輯", "編輯",
    ],
    # TP-0305 · adjusted from demo/TP-0038: OK button of the Material date and time pickers
    "time_picker_ok": [
        "OK", "OK", "OK", "OK", "OK", "OK", "Tamam", "ОК", "ठीक है", "ตกลง", "OK", "Aceptar", "Aceptar", "确定", "確定", "確定",
    ],
    # TP-0306 · adjusted from demo/TP-0118: click label only (the date is read separately)
    "cd_change_date": [
        "Change note date", "記録の日付を変更", "Datum der Notiz ändern", "Changer la date de la note", "Ubah tanggal catatan",
        "Đổi ngày ghi chú", "Notun tarihini değiştir", "Изменить дату записи", "नोट की तारीख बदलें", "เปลี่ยนวันที่บันทึก",
        "Mudar a data da anotação", "Cambiar la fecha de la nota", "Cambiar la fecha de la nota", "更改记录日期", "更改紀錄日期", "更改記錄日期",
    ],
    # TP-0229 · the whole journal is empty
    "home_empty": [
        "No notes yet.", "まだ記録がありません。", "Noch keine Notizen.", "Aucune note pour l’instant.", "Belum ada catatan.",
        "Chưa có ghi chú nào.", "Henüz not yok.", "Записей пока нет.", "अभी तक कोई नोट नहीं है।", "ยังไม่มีบันทึก",
        "Ainda não há anotações.", "Aún no hay notas.", "Aún no hay notas.", "还没有记录。", "還沒有紀錄。", "未有記錄。",
    ],
    # TP-0231 · adjusted from demo/TP-0076: deletes every item (fragment) of that day now; the count is items, plurals
    "delete_day": [
        {"one": "Delete %1$d item from this day", "other": "Delete all %1$d items from this day"},
        {"other": "この日の項目%1$d件を削除"},
        {"one": "%1$d Element dieses Tages löschen", "other": "Alle %1$d Elemente dieses Tages löschen"},
        {"one": "Supprimer %1$d élément de ce jour", "many": "Supprimer les %1$d éléments de ce jour", "other": "Supprimer les %1$d éléments de ce jour"},
        {"other": "Hapus %1$d item dari tanggal ini"}, {"other": "Xóa %1$d mục của ngày này"},
        {"one": "Bu günün %1$d öğesini sil", "other": "Bu günün %1$d öğesini sil"},
        {"one": "Удалить %1$d элемент за этот день", "few": "Удалить %1$d элемента за этот день", "many": "Удалить %1$d элементов за этот день", "other": "Удалить %1$d элемента за этот день"},
        {"one": "इस दिन का %1$d आइटम मिटाएँ", "other": "इस दिन के %1$d आइटम मिटाएँ"},
        {"other": "ลบ %1$d รายการของวันที่เลือก"},
        {"one": "Excluir %1$d item deste dia", "many": "Excluir os %1$d itens deste dia", "other": "Excluir os %1$d itens deste dia"},
        {"one": "Eliminar %1$d elemento de este día", "many": "Eliminar los %1$d elementos de este día", "other": "Eliminar los %1$d elementos de este día"},
        {"one": "Eliminar %1$d elemento de este día", "many": "Eliminar los %1$d elementos de este día", "other": "Eliminar los %1$d elementos de este día"},
        {"other": "删除当天的 %1$d 项"}, {"other": "刪除這天的 %1$d 個項目"}, {"other": "刪除當天的 %1$d 個項目"},
    ],
    # TP-0232 · adjusted from demo/TP-0082: the count is items, plurals
    "day_deleted": [
        {"one": "Deleted %1$d item from this day.", "other": "Deleted %1$d items from this day."},
        {"other": "この日の項目%1$d件を削除しました。"},
        {"one": "%1$d Element dieses Tages gelöscht.", "other": "%1$d Elemente dieses Tages gelöscht."},
        {"one": "%1$d élément de ce jour supprimé.", "many": "%1$d éléments de ce jour supprimés.", "other": "%1$d éléments de ce jour supprimés."},
        {"other": "%1$d item dari tanggal ini sudah dihapus."}, {"other": "Đã xóa %1$d mục của ngày này."},
        {"one": "Bu günün %1$d öğesi silindi.", "other": "Bu günün %1$d öğesi silindi."},
        {"one": "Удалён %1$d элемент за этот день.", "few": "Удалено %1$d элемента за этот день.", "many": "Удалено %1$d элементов за этот день.", "other": "Удалено %1$d элемента за этот день."},
        {"one": "इस दिन का %1$d आइटम मिटा दिया गया।", "other": "इस दिन के %1$d आइटम मिटा दिए गए।"},
        {"other": "ลบ %1$d รายการของวันที่เลือกแล้ว"},
        {"one": "%1$d item deste dia foi excluído.", "many": "%1$d itens deste dia foram excluídos.", "other": "%1$d itens deste dia foram excluídos."},
        {"one": "Se ha eliminado %1$d elemento de este día.", "many": "Se han eliminado %1$d elementos de este día.", "other": "Se han eliminado %1$d elementos de este día."},
        {"one": "Se eliminó %1$d elemento de este día.", "many": "Se eliminaron %1$d elementos de este día.", "other": "Se eliminaron %1$d elementos de este día."},
        {"other": "已删除当天的 %1$d 项。"}, {"other": "已刪除這天的 %1$d 個項目。"}, {"other": "已刪除當天的 %1$d 個項目。"},
    ],
    # TP-0240
    "home_first_record": [
        "Write your first note", "最初の記録を残す", "Erste Notiz schreiben", "Écrire votre première note", "Tulis catatan pertama",
        "Viết ghi chú đầu tiên", "İlk notunu yaz", "Сделать первую запись", "पहला नोट लिखें", "เขียนบันทึกแรก",
        "Escrever a primeira anotação", "Escribir tu primera nota", "Escribir tu primera nota", "写下第一条记录", "留下第一則紀錄", "寫下第一項記錄",
    ],
    # TP-0290 · adjusted from demo/TP-0073
    "cd_edit_day": [
        "Edit this day’s notes", "この日の記録を編集", "Notizen dieses Tages bearbeiten", "Modifier les notes du jour",
        "Edit catatan untuk tanggal ini", "Sửa ghi chú của ngày này", "Günün notlarını düzenle", "Изменить записи за день",
        "इस दिन के नोट में बदलाव करें", "แก้ไขบันทึกของวันที่เลือก", "Editar as anotações deste dia", "Editar las notas del día",
        "Editar las notas del día", "编辑当天记录", "編輯這天的紀錄", "編輯當天記錄",
    ],
    # TP-0307 · adjusted from demo/TP-0053: opens the day menu (does not delete)
    "cd_day_menu": [
        "More options for this day", "この日のメニュー", "Weitere Optionen für diesen Tag", "Plus d’options pour ce jour",
        "Opsi lain untuk tanggal ini", "Thêm tùy chọn cho ngày này", "Günün diğer seçenekleri", "Другие действия для этого дня",
        "इस दिन के और विकल्प", "ตัวเลือกเพิ่มเติมของวันที่เลือก", "Mais opções para este dia", "Más opciones del día", "Más opciones del día",
        "这一天的更多选项", "這天的更多選項", "這一天的更多選項",
    ],
    # TP-0308 · adjusted from demo/TP-0088; %1$s is the item
    "cd_item_menu": [
        "More options for %1$s", "%1$sのメニュー", "Weitere Optionen für %1$s", "Plus d’options pour %1$s", "Opsi lain untuk %1$s",
        "Thêm tùy chọn cho %1$s", "%1$s için diğer seçenekler", "Другие действия: %1$s", "%1$s के और विकल्प", "ตัวเลือกเพิ่มเติมของ %1$s",
        "Mais opções para %1$s", "Más opciones de %1$s", "Más opciones de %1$s", "%1$s的更多选项", "%1$s的更多選項", "%1$s的更多選項",
    ],
    # TP-0311 · adjusted from demo/TP-0070 (generic "this month")
    "month_empty": [
        "No notes this month.", "今月の記録はありません。", "Keine Notizen in diesem Monat.", "Aucune note ce mois-ci.",
        "Belum ada catatan bulan ini.", "Chưa có ghi chú trong tháng này.", "Bu ay not yok.", "В этом месяце записей нет.",
        "इस महीने कोई नोट नहीं है।", "ยังไม่มีบันทึกในเดือนนี้", "Nenhuma anotação neste mês.", "No hay notas este mes.",
        "No hay notas este mes.", "本月还没有记录。", "這個月還沒有紀錄。", "本月未有記錄。",
    ],
    # TP-0312 · adjusted from demo/TP-0070; %1$s is the localized month name
    "month_empty_cd": [
        "No notes for %1$s.", "%1$sの記録はありません。", "Keine Notizen für %1$s.", "%1$s : aucune note.", "Belum ada catatan pada %1$s.",
        "Chưa có ghi chú trong %1$s.", "%1$s için not yok.", "%1$s: записей нет.", "%1$s में कोई नोट नहीं है।", "ยังไม่มีบันทึกใน%1$s",
        "Nenhuma anotação em %1$s.", "No hay notas de %1$s.", "No hay notas de %1$s.", "%1$s还没有记录。", "%1$s還沒有紀錄。", "%1$s未有記錄。",
    ],
    # TP-0222
    "show_calendar": [
        "Show calendar", "カレンダーを表示", "Kalender anzeigen", "Afficher le calendrier", "Tampilkan kalender", "Hiện lịch",
        "Takvimi göster", "Показать календарь", "कैलेंडर दिखाएँ", "แสดงปฏิทิน", "Mostrar calendário", "Mostrar calendario",
        "Mostrar calendario", "显示日历", "顯示日曆", "顯示日曆",
    ],
    # TP-0249 · %1$s localized year
    "cd_gap_year": [
        "%1$s, no notes this year", "%1$s、記録のない年", "%1$s, ein Jahr ohne Notizen", "%1$s, année sans notes",
        "%1$s, tahun tanpa catatan", "%1$s, năm không có ghi chú", "%1$s, not olmayan yıl", "%1$s, год без записей",
        "%1$s, बिना नोट वाला साल", "%1$s ปีที่ไม่มีบันทึก", "%1$s, ano sem anotações", "%1$s, año sin notas", "%1$s, año sin notas",
        "%1$s，没有记录的年份", "%1$s，沒有紀錄的年份", "%1$s，沒有記錄的年份",
    ],
    # TP-0251 · %1$s first year, %2$s last year, %3$d number of years, plurals
    "cd_gap_years": [
        {"one": "%1$s to %2$s, %3$d year without notes", "other": "%1$s to %2$s, %3$d years without notes"},
        {"other": "%1$sから%2$sまで、記録のない%3$d年間"},
        {"one": "%1$s bis %2$s, %3$d Jahr ohne Notizen", "other": "%1$s bis %2$s, %3$d Jahre ohne Notizen"},
        {"one": "De %1$s à %2$s, %3$d an sans notes", "many": "De %1$s à %2$s, %3$d ans sans notes", "other": "De %1$s à %2$s, %3$d ans sans notes"},
        {"other": "%1$s sampai %2$s, %3$d tahun tanpa catatan"}, {"other": "Từ %1$s đến %2$s, %3$d năm không có ghi chú"},
        {"one": "%1$s – %2$s, not olmayan %3$d yıl", "other": "%1$s – %2$s, not olmayan %3$d yıl"},
        {"one": "С %1$s по %2$s, %3$d год без записей", "few": "С %1$s по %2$s, %3$d года без записей", "many": "С %1$s по %2$s, %3$d лет без записей", "other": "С %1$s по %2$s, %3$d года без записей"},
        {"one": "%1$s से %2$s तक, बिना नोट वाला %3$d साल", "other": "%1$s से %2$s तक, बिना नोट वाले %3$d साल"},
        {"other": "%1$s ถึง %2$s ไม่มีบันทึก %3$d ปี"},
        {"one": "De %1$s a %2$s, %3$d ano sem anotações", "many": "De %1$s a %2$s, %3$d anos sem anotações", "other": "De %1$s a %2$s, %3$d anos sem anotações"},
        {"one": "De %1$s a %2$s, %3$d año sin notas", "many": "De %1$s a %2$s, %3$d años sin notas", "other": "De %1$s a %2$s, %3$d años sin notas"},
        {"one": "De %1$s a %2$s, %3$d año sin notas", "many": "De %1$s a %2$s, %3$d años sin notas", "other": "De %1$s a %2$s, %3$d años sin notas"},
        {"other": "%1$s至%2$s，%3$d 年没有记录"}, {"other": "%1$s至%2$s，%3$d 年沒有紀錄"}, {"other": "%1$s至%2$s，%3$d 年沒有記錄"},
    ],
    # TP-0257 · adjusted from demo/TP-0043; %1$s localized month
    "cd_out_of_range": [
        "%1$s, outside the available range", "%1$s、範囲外", "%1$s, außerhalb des verfügbaren Zeitraums", "%1$s, hors de la période disponible",
        "%1$s, di luar rentang yang tersedia", "%1$s, ngoài khoảng có thể xem", "%1$s, kullanılabilir tarih aralığının dışında",
        "%1$s, вне доступного периода", "%1$s, उपलब्ध अवधि से बाहर", "%1$s อยู่นอกช่วงที่เลือกได้", "%1$s, fora do período disponível",
        "%1$s, fuera del periodo disponible", "%1$s, fuera del período disponible", "%1$s，超出可选范围", "%1$s，超出可選範圍", "%1$s，超出可選範圍",
    ],
    # TP-0293 / TP-0297 · TalkBack state
    "cd_collapsed": [
        "Collapsed", "折りたたみ中", "Eingeklappt", "Replié", "Diciutkan", "Đã thu gọn", "Daraltılmış", "Свёрнуто", "समेटा हुआ",
        "ย่ออยู่", "Recolhido", "Contraído", "Contraído", "已收起", "已收合", "已收起",
    ],
    "cd_expanded": [
        "Expanded", "展開中", "Ausgeklappt", "Déplié", "Diperluas", "Đã mở rộng", "Genişletilmiş", "Развёрнуто", "फैला हुआ",
        "ขยายอยู่", "Expandido", "Expandido", "Expandido", "已展开", "已展開", "已展開",
    ],
    # TP-0310 · adjusted from demo/TP-0050: short view name in the menu
    "view_year": [
        "Year", "年間", "Jahr", "Année", "Tahunan", "Năm", "Yıl", "Год", "साल", "รายปี", "Ano", "Año", "Año", "年历", "年曆", "年曆",
    ],
    # TP-0317
    "cd_has_records": [
        "Has notes", "記録あり", "Mit Notizen", "Contient des notes", "Ada catatan", "Có ghi chú", "Not var", "Есть записи",
        "नोट हैं", "มีบันทึก", "Com anotações", "Con notas", "Con notas", "有记录", "有紀錄", "有記錄",
    ],
    # TP-0318 · adjusted from demo/TP-0044 (count clause only; the month is read by the parent), plurals
    "cd_recorded_days": [
        {"one": "%1$d day with a note", "other": "%1$d days with notes"}, {"other": "記録のある日は%1$d日"},
        {"one": "%1$d Tag mit einer Notiz", "other": "%1$d Tage mit Notizen"},
        {"one": "%1$d jour avec une note", "many": "%1$d jours avec des notes", "other": "%1$d jours avec des notes"},
        {"other": "ada catatan pada %1$d hari"}, {"other": "có ghi chú trong %1$d ngày"},
        {"one": "not aldığın gün sayısı: %1$d", "other": "not aldığın gün sayısı: %1$d"},
        {"one": "%1$d день с записью", "few": "%1$d дня с записями", "many": "%1$d дней с записями", "other": "%1$d дня с записями"},
        {"one": "नोट वाले दिन: %1$d", "other": "नोट वाले दिन: %1$d"}, {"other": "มีบันทึก %1$d วัน"},
        {"one": "%1$d dia com anotações", "many": "%1$d dias com anotações", "other": "%1$d dias com anotações"},
        {"one": "%1$d día con notas", "many": "%1$d días con notas", "other": "%1$d días con notas"},
        {"one": "%1$d día con notas", "many": "%1$d días con notas", "other": "%1$d días con notas"},
        {"other": "其中 %1$d 天有记录"}, {"other": "其中有 %1$d 天有紀錄"}, {"other": "其中有 %1$d 日有記錄"},
    ],
    # TP-0319 · %1$s first month, %2$s last month (localized year-month), %3$d number of months, plurals
    "cd_gap_months": [
        {"one": "%1$s to %2$s, %3$d month without notes", "other": "%1$s to %2$s, %3$d months without notes"},
        {"other": "%1$sから%2$sまで、記録のない%3$dか月"},
        {"one": "%1$s bis %2$s, %3$d Monat ohne Notizen", "other": "%1$s bis %2$s, %3$d Monate ohne Notizen"},
        {"one": "De %1$s à %2$s, %3$d mois sans notes", "many": "De %1$s à %2$s, %3$d mois sans notes", "other": "De %1$s à %2$s, %3$d mois sans notes"},
        {"other": "%1$s sampai %2$s, %3$d bulan tanpa catatan"}, {"other": "Từ %1$s đến %2$s, %3$d tháng không có ghi chú"},
        {"one": "%1$s – %2$s, not olmayan %3$d ay", "other": "%1$s – %2$s, not olmayan %3$d ay"},
        {"one": "С %1$s по %2$s, %3$d месяц без записей", "few": "С %1$s по %2$s, %3$d месяца без записей", "many": "С %1$s по %2$s, %3$d месяцев без записей", "other": "С %1$s по %2$s, %3$d месяца без записей"},
        {"one": "%1$s से %2$s तक, बिना नोट वाला %3$d महीना", "other": "%1$s से %2$s तक, बिना नोट वाले %3$d महीने"},
        {"other": "%1$s ถึง %2$s ไม่มีบันทึก %3$d เดือน"},
        {"one": "De %1$s a %2$s, %3$d mês sem anotações", "many": "De %1$s a %2$s, %3$d meses sem anotações", "other": "De %1$s a %2$s, %3$d meses sem anotações"},
        {"one": "De %1$s a %2$s, %3$d mes sin notas", "many": "De %1$s a %2$s, %3$d meses sin notas", "other": "De %1$s a %2$s, %3$d meses sin notas"},
        {"one": "De %1$s a %2$s, %3$d mes sin notas", "many": "De %1$s a %2$s, %3$d meses sin notas", "other": "De %1$s a %2$s, %3$d meses sin notas"},
        {"other": "%1$s至%2$s，%3$d 个月没有记录"}, {"other": "%1$s至%2$s，%3$d 個月沒有紀錄"}, {"other": "%1$s至%2$s，%3$d 個月沒有記錄"},
    ],
    # TP-0320 / TP-0321 · click action names (the range is read separately)
    "cd_expand": [
        "Expand", "展開", "Ausklappen", "Déplier", "Perluas", "Mở rộng", "Genişlet", "Развернуть", "फैलाएँ", "ขยาย",
        "Expandir", "Expandir", "Expandir", "展开", "展開", "展開",
    ],
    "cd_collapse": [
        "Collapse", "折りたたむ", "Einklappen", "Replier", "Ciutkan", "Thu gọn", "Daralt", "Свернуть", "समेटें", "ย่อ",
        "Recolher", "Contraer", "Contraer", "收起", "收合", "收起",
    ],
    # TP-0216 · clears the whole query
    "cd_clear_search": [
        "Clear search", "検索語を消去", "Suchbegriff löschen", "Effacer la recherche", "Hapus kata pencarian", "Xóa từ khóa tìm kiếm",
        "Aramayı temizle", "Очистить поиск", "खोज मिटाएँ", "ล้างคำค้นหา", "Limpar busca", "Borrar búsqueda", "Borrar búsqueda",
        "清除搜索词", "清除搜尋字詞", "清除搜尋字詞",
    ],
    # TP-0322 · adjusted from demo/TP-0054: short noun on the button, tapping returns to the results
    "search_results": [
        "Search results", "検索結果", "Suchergebnisse", "Résultats", "Hasil pencarian", "Kết quả tìm kiếm", "Arama sonuçları",
        "Результаты поиска", "खोज के नतीजे", "ผลการค้นหา", "Resultados da busca", "Resultados", "Resultados", "搜索结果", "搜尋結果", "搜尋結果",
    ],
    # TP-0348 (new) · revealed swipe delete button: the action and its target, e.g. "단어 산책"
    "cd_delete_item": [
        "Delete %1$s", "%1$sを削除", "%1$s löschen", "Supprimer %1$s", "Hapus %1$s", "Xóa %1$s", "%1$s öğesini sil",
        "Удалить: %1$s", "%1$s मिटाएँ", "ลบ %1$s", "Excluir %1$s", "Eliminar %1$s", "Eliminar %1$s", "删除%1$s", "刪除%1$s", "刪除%1$s",
    ],
    # TP-0362
    "onboarding_start": [
        "Start", "始める", "Starten", "Commencer",
        "Mulai", "Bắt đầu", "Başla", "Начать",
        "शुरू करें", "เริ่ม", "Começar", "Empezar",
        "Empezar", "开始", "開始", "開始",
    ],
    # TP-0363
    "onboarding_invitation": [
        "Ready to start a journal of your own?", "そろそろ、自分だけの記録を重ねてみませんか？", "Möchtest du jetzt mit deinem eigenen Tagebuch beginnen?", "Et si vous commenciez votre propre journal ?",
        "Siap mulai mengisi jurnalmu sendiri?", "Bạn đã sẵn sàng viết nhật ký của riêng mình chưa?", "Şimdi kendi günlüğünü tutmaya başlamak ister misin?", "Готовы начать вести свой дневник?",
        "अब अपनी डायरी लिखना शुरू करें?", "พร้อมเริ่มเก็บบันทึกของตัวเองหรือยัง", "Que tal começar seu próprio diário agora?", "¿Te animas a empezar tu propio diario?",
        "¿Te animas a empezar tu propio diario?", "现在开始一点点写下自己的记录，好吗？", "現在開始慢慢寫下自己的紀錄，好嗎？", "現在開始慢慢寫下自己的記錄，好嗎？",
    ],
    # TP-0364
    "onboarding_next": [
        "Next", "次へ", "Weiter", "Suivant",
        "Berikutnya", "Tiếp", "İleri", "Далее",
        "आगे", "ถัดไป", "Avançar", "Siguiente",
        "Siguiente", "下一步", "下一步", "下一步",
    ],
    # TP-0365
    "onboarding_keep_exploring": [
        "Keep exploring", "もう少し見てみる", "Weiter erkunden", "Continuer à explorer",
        "Terus menjelajah", "Tiếp tục khám phá", "Keşfetmeye devam et", "Продолжить знакомство",
        "और देखें", "สำรวจต่อ", "Continuar explorando", "Seguir explorando",
        "Seguir explorando", "继续浏览", "繼續瀏覽", "繼續瀏覽",
    ],
    # TP-0366
    "debug_replay_onboarding": [
        "Replay onboarding", "オンボーディングをもう一度体験", "Einführung erneut durchlaufen", "Revoir le parcours de découverte",
        "Coba lagi panduan awal", "Trải nghiệm lại phần hướng dẫn ban đầu", "Tanıtımı yeniden dene", "Пройти знакомство с приложением ещё раз",
        "शुरुआती गाइड फिर से देखें", "ลองขั้นตอนแนะนำแอปอีกครั้ง", "Refazer a introdução", "Repetir el recorrido inicial",
        "Repetir el recorrido inicial", "重新体验新手引导", "重新體驗新手導覽", "重新體驗新手導覽",
    ],
}
