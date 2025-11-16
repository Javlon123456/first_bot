package com.uniteen;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

// Kod satrlari soni 900 dan oshishi kutilmoqda.
public class Main extends TelegramLongPollingBot {

    // ------------------ Konfiguratsiya ------------------
    // Iltimos, bu o'zgaruvchilarni o'zingizning ma'lumotlaringiz bilan yangilang!
    private static final long ADMIN_CHAT_ID = 1943895887L; 
    private static final String ADMIN_USERNAME = "@mirobidov_26";
    private static final String SUPPORT_TEACHER_USERNAME = "@Ibrohimjonugli";
    private static final String SUPPORT_TEACHER_NAME = "Muhammadaziz";
    private static final String BOT_TOKEN = "8042469241:AAEgIUE0YTpbEECh7KOK7U-7Is-K-oAaNSI";
    
    // Foydalanuvchi Chat ID larini saqlash fayli
    private static final String USERS_FILE = "users.txt";

    // ------------------ Kurs Ma'lumotlari ------------------
    private static final Map<String, String> COURSE_PRICES = new HashMap<>();
    private static final Map<String, String> COURSE_EMOJIS = new HashMap<>();

    static {
        COURSE_PRICES.put("IT", "1.100.000 UZS");
        COURSE_PRICES.put("Tarix", "750.000 UZS");
        COURSE_PRICES.put("Matematika", "650.000 UZS");
        COURSE_PRICES.put("General English", "650.000 UZS");
        COURSE_PRICES.put("CEFR/IELTS", "750.000 UZS");
        COURSE_PRICES.put("SAT(English,Math)", "750.000 UZS");
        COURSE_PRICES.put("Prezdent Maktab", "750.000 UZS");

        COURSE_EMOJIS.put("IT", "💻");
        COURSE_EMOJIS.put("Tarix", "📜");
        COURSE_EMOJIS.put("Matematika", "🧮");
        COURSE_EMOJIS.put("General English", "🇬🇧");
        COURSE_EMOJIS.put("CEFR/IELTS", "🎓");
        COURSE_EMOJIS.put("SAT(English,Math)", "📝");
        COURSE_EMOJIS.put("Prezdent Maktab", "🏫");
    }

    // ------------------ Callback Stringlari ------------------
    private static final String CALLBACK_SHOW_PRICES = "show_prices";
    private static final String CALLBACK_ENROLL_COURSE = "enroll_course_";
    private static final String CALLBACK_CONTACT_ADMIN = "contact_admin";
    private static final String CALLBACK_SUGGESTION = "suggestion";
    private static final String CALLBACK_INFORMATION = "information";
    private static final String CALLBACK_BACK_TO_MAIN = "back_to_main";
    private static final String CALLBACK_SUPPORT_MENU = "support_menu";
    private static final String CALLBACK_SUPPORT_INFO = "support_info";
    private static final String CALLBACK_SUPPORT_REGISTER = "support_register";
    private static final String CALLBACK_UNREGISTER_AND_HOURS = "unregister_and_hours"; 

    // ------------------ State Management ------------------
    private final Map<Long, Boolean> waitingForSuggestion = new HashMap<>();
    private final Map<Long, String> userPhones = new HashMap<>();
    private final Map<Long, Integer> lastSentMessageId = new HashMap<>();
    private final Map<Long, Boolean> waitingForUnregisterTime = new HashMap<>();
    private final Map<Long, Boolean> waitingForSupportRegistrationInfo = new HashMap<>(); 
    private static final Set<Long> allUsers = Collections.synchronizedSet(new HashSet<>());

    static {
        loadUsersFromFile();
    }

    // ------------------ Bot API Methods ------------------

    @Override
    public String getBotUsername() {
        return "UniteenAcademyBot";
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                long chatId = update.getMessage().getChatId();
                User user = update.getMessage().getFrom();
                String messageText = update.getMessage().hasText() ? update.getMessage().getText() : "";

                // --- Admin Commands and Broadcast Logic ---
                if (chatId == ADMIN_CHAT_ID && handleAdminCommands(update)) {
                    return;
                }
                
                // --- Telefon Raqami So'rovi ---
                if (!userPhones.containsKey(chatId)) {
                    if (update.getMessage().hasContact()) {
                        handlePhoneNumberReceived(update.getMessage().getContact(), chatId, user);
                    } else if (messageText.equals("/start")) {
                        sendPhoneNumberRequest(chatId);
                    } else {
                        sendPhoneNumberRequest(chatId);
                    }
                    return;
                }

                // --- Asosiy Bot Oqimi ---
                if (update.getMessage().hasText()) {
                    if (messageText.equals("/start")) {
                        addUser(chatId);
                        resetAllWaitingStates(chatId);
                        sendMainMenu(chatId, user);
                    } else if (waitingForSuggestion.getOrDefault(chatId, false)) {
                        handleSuggestion(update.getMessage()); 
                    } else if (waitingForUnregisterTime.getOrDefault(chatId, false)) { 
                        handleUnregisterTimeInput(update.getMessage()); 
                    } else if (waitingForSupportRegistrationInfo.getOrDefault(chatId, false)) { 
                        handleSupportRegistrationInput(update.getMessage());
                    } else {
                        SendMessage reply = new SendMessage();
                        reply.setChatId(chatId);
                        reply.setText("❗ Iltimos, pastdagi menyudan foydalaning yoki /start buyrug‘ini bosing.");
                        execute(reply);
                    }
                }
            }

            // --- Callback Querylarni Bajarish ---
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    
    // ------------------ Helper Methods & Broadcast Logic ------------------

    private void resetAllWaitingStates(long chatId) {
        waitingForSuggestion.put(chatId, false);
        waitingForUnregisterTime.put(chatId, false);
        waitingForSupportRegistrationInfo.put(chatId, false); 
    }
    
    private boolean handleAdminCommands(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().hasText() ? update.getMessage().getText() : "";

        if (messageText != null && messageText.startsWith("/broadcast_photo")) {
            String rest = messageText.replaceFirst("/broadcast_photo", "").trim();
            if (rest.isEmpty() || !rest.contains("|")) {
                SendMessage reply = new SendMessage();
                reply.setChatId(chatId);
                reply.setText("Iltimos, /broadcast_photo <fileId>|<caption> formatida yuboring.");
                execute(reply);
            } else {
                String[] parts = rest.split("\\|", 2);
                String fileId = parts[0].trim();
                String caption = parts.length > 1 ? parts[1].trim() : "";
                SendMessage ack = new SendMessage();
                ack.setChatId(chatId);
                ack.setText("Rasm barcha foydalanuvchilarga yuborilmoqda (" + allUsers.size() + " ta).");
                execute(ack);
                broadcastPhoto(fileId, caption);
            }
            return true;
        }
        
        if (messageText != null && messageText.startsWith("/broadcast")) {
            String payload = messageText.replaceFirst("/broadcast", "").trim();
            if (payload.isEmpty()) {
                SendMessage reply = new SendMessage();
                reply.setChatId(chatId);
                reply.setText("Iltimos, /broadcast <xabar> formatida yuboring.");
                execute(reply);
            } else {
                SendMessage ack = new SendMessage();
                ack.setChatId(chatId);
                ack.setText("Xabar barcha foydalanuvchilarga yuborilmoqda (" + allUsers.size() + " ta).");
                execute(ack);
                broadcastMessage(payload);
            }
            return true;
        }
        
        if (messageText != null && messageText.equals("/users")) {
            SendMessage rep = new SendMessage();
            rep.setChatId(chatId);
            rep.setText("Hozirgi foydalanuvchilar soni: " + allUsers.size());
            execute(rep);
            return true;
        }
        
        if (update.getMessage().hasPhoto()) {
            String fileId = update.getMessage().getPhoto().get(0).getFileId();
            SendMessage fileIdMsg = new SendMessage();
            fileIdMsg.setChatId(chatId);
            
            // HTML formatlash
            fileIdMsg.setText("Qabul qilingan rasm File ID si: \n<code>" + fileId + "</code>\n\nUni /broadcast_photo <fileId> | <caption> formatida ishlatishingiz mumkin.");
            fileIdMsg.setParseMode("Html");
            
            execute(fileIdMsg);
            return true;
        }
        
        return false;
    }

    private static void loadUsersFromFile() {
        Path p = Paths.get(USERS_FILE);
        if (Files.exists(p)) {
            try {
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        try {
                            long id = Long.parseLong(line);
                            allUsers.add(id);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to load users file: " + e.getMessage());
            }
        }
    }

    private static void saveUsersToFile() {
        Path p = Paths.get(USERS_FILE);
        try {
            List<String> lines = new ArrayList<>();
            synchronized (allUsers) {
                for (Long id : allUsers) lines.add(String.valueOf(id));
            }
            Files.write(p, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save users file: " + e.getMessage());
        }
    }

    private void addUser(long chatId) {
        if (chatId <= 0) return;
        boolean added = allUsers.add(chatId);
        if (added) {
            saveUsersToFile();
        }
    }

    private void broadcastMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String payload = "📢 <b>Xabar from Admin:</b>\n\n" + text;
        synchronized (allUsers) {
            for (Long userId : new HashSet<>(allUsers)) {
                try {
                    SendMessage m = new SendMessage();
                    m.setChatId(userId);
                    m.setText(payload);
                    m.setParseMode("Html"); 
                    execute(m);
                } catch (TelegramApiException e) {
                    // Xato bo'lsa, uni logga yozish
                }
            }
        }
    }

    private void broadcastPhoto(String fileId, String caption) {
        if (fileId == null || fileId.isEmpty()) return;
        synchronized (allUsers) {
            for (Long userId : new HashSet<>(allUsers)) {
                try {
                    SendPhoto sp = new SendPhoto();
                    sp.setChatId(userId);
                    sp.setPhoto(new InputFile(fileId));
                    if (caption != null && !caption.isEmpty()) {
                        sp.setCaption(caption);
                        sp.setParseMode("Html"); 
                    }
                    execute(sp);
                } catch (TelegramApiException e) {
                    // Xato bo'lsa, uni logga yozish
                }
            }
        }
    }

    // ------------------ Asosiy Bot Logika Metodlari ------------------

    private void sendPhoneNumberRequest(long chatId) throws TelegramApiException {
        SendMessage requestPhone = new SendMessage();
        requestPhone.setChatId(chatId);
        requestPhone.setText("Iltimos, botdan foydalanish uchun <b>telefon raqamingizni yuboring</b>.");
        requestPhone.setParseMode("Html");

        KeyboardButton phoneButton = new KeyboardButton("📞 Telefon raqamni yuborish");
        phoneButton.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(phoneButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true); 

        requestPhone.setReplyMarkup(markup);
        execute(requestPhone);
    }

    private void handlePhoneNumberReceived(Contact contact, long chatId, User user) throws TelegramApiException {
        String phoneNumber = contact.getPhoneNumber();
        userPhones.put(chatId, phoneNumber);

        addUser(chatId);

        // Notify Admin
        SendMessage adminMsg = new SendMessage();
        adminMsg.setChatId(ADMIN_CHAT_ID);
        adminMsg.setText(
            "🆕 <b>Yangi foydalanuvchi!</b>\n\n" +
            "👤 Ismi: <b>" + (user.getFirstName() != null ? user.getFirstName() : "Noma'lum") + "</b>\n" +
            "🔗 Username: " + (user.getUserName() != null ? "@" + user.getUserName() : "Noma'lum") + "\n" +
            "📞 Telefon: <code>+" + phoneNumber + "</code>\n" +
            "🆔 Chat ID: <code>" + chatId + "</code>"
        );
        adminMsg.setParseMode("Html");
        execute(adminMsg);

        sendMainMenu(chatId, user);
    }

    private void sendMainMenu(long chatId, User user) throws TelegramApiException {
        // 1. Clear Reply Keyboard
        SendMessage clearKeyboard = new SendMessage();
        clearKeyboard.setChatId(chatId);
        clearKeyboard.setText("Uniteen botiga xush kelibsiz! Tanlovingizni kiriting:"); 
        
        ReplyKeyboardMarkup markupHide = new ReplyKeyboardMarkup();
        markupHide.setKeyboard(Collections.emptyList());
        markupHide.setResizeKeyboard(true);
        markupHide.setOneTimeKeyboard(false);
        clearKeyboard.setReplyMarkup(markupHide);
        execute(clearKeyboard); 
        
        // 2. Send Main Menu with Inline Keyboard
        SendMessage welcome = new SendMessage();
        welcome.setChatId(chatId);
        welcome.setText("Assalomu aleykum, Uniteen botga xush kelibsiz! 🎉\n\nQuyidagi tugmalardan birini tanlang:");
        
        InlineKeyboardMarkup markup = getMainMenuKeyboard();

        welcome.setReplyMarkup(markup);
        Message sentMessage = execute(welcome);
        lastSentMessageId.put(chatId, sentMessage.getMessageId());
    }

    private void handleCallbackQuery(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) throws TelegramApiException {
        String callbackData = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();

        // Callback Query natijasini yuborish (muhim)
        execute(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text("Yuklanmoqda...")
                .showAlert(false)
                .build());

        if (callbackData.equals(CALLBACK_BACK_TO_MAIN)) {
              resetAllWaitingStates(chatId);
              EditMessageText editMessage = new EditMessageText();
              editMessage.setChatId(chatId);
              editMessage.setMessageId(messageId);
              editMessage.setText("Assalomu aleykum, Uniteen botga xush kelibsiz! 🎉\n\nQuyidagi tugmalardan birini tanlang:");
              editMessage.setReplyMarkup(getMainMenuKeyboard());
              execute(editMessage);
              return; 
        }

        if (callbackData.equals(CALLBACK_SHOW_PRICES)) {
            sendCourseButtons(chatId, messageId);
        } else if (callbackData.equals(CALLBACK_INFORMATION)) {
            sendInformation(chatId, messageId); 
        } else if (callbackData.equals(CALLBACK_SUPPORT_MENU)) { 
            sendSupportMenu(chatId, messageId);
        } else if (callbackData.equals(CALLBACK_SUPPORT_INFO)) { 
            sendSupportInfo(chatId, messageId); 
        } else if (callbackData.equals(CALLBACK_SUPPORT_REGISTER)) { 
            handleSupportRegistration(chatId, messageId);
        } else if (callbackData.equals(CALLBACK_UNREGISTER_AND_HOURS)) { 
            handleUnregisterAndHoursRequest(chatId, messageId);
        } else if (callbackData.startsWith(CALLBACK_ENROLL_COURSE)) {
            handleEnrollment(chatId, username, firstName, callbackData);
        } else if (callbackData.equals(CALLBACK_CONTACT_ADMIN)) {
             SendMessage reply = new SendMessage();
             reply.setChatId(chatId);
             reply.setText("Admin bilan bog‘lanish: " + ADMIN_USERNAME);
             execute(reply);
        } else if (callbackData.equals(CALLBACK_SUGGESTION)) {
             SendMessage reply = new SendMessage();
             reply.setChatId(chatId);
             reply.setText("O‘z taklifingiz yoki murojaatingizni yuborishingiz mumkin bitta xabarning o'zida:");
             execute(reply);
             waitingForSuggestion.put(chatId, true);
        } else if (COURSE_PRICES.containsKey(callbackData)) { // Kurs narxi tugmasi bosildi
            handleCourseSelection(chatId, messageId, callbackData);
        }
        
        lastSentMessageId.put(chatId, messageId);
    }
    
    private InlineKeyboardMarkup getMainMenuKeyboard() {
        InlineKeyboardButton kursNarxiBtn = new InlineKeyboardButton("💰 Kurs narxi");
        kursNarxiBtn.setCallbackData(CALLBACK_SHOW_PRICES);

        InlineKeyboardButton infoBtn = new InlineKeyboardButton("ℹ️ Ma'lumot");
        infoBtn.setCallbackData(CALLBACK_INFORMATION);

        InlineKeyboardButton supportBtn = new InlineKeyboardButton("👨‍🏫 Support Teacher");
        supportBtn.setCallbackData(CALLBACK_SUPPORT_MENU);
        
        InlineKeyboardButton adminAloqaBtn = new InlineKeyboardButton("👨‍💼 Admin bilan aloqa");
        adminAloqaBtn.setCallbackData(CALLBACK_CONTACT_ADMIN);

        InlineKeyboardButton murojatTaklifBtn = new InlineKeyboardButton("📥 Murojaat va takliflar");
        murojatTaklifBtn.setCallbackData(CALLBACK_SUGGESTION);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(kursNarxiBtn, infoBtn));
        rows.add(Collections.singletonList(supportBtn));
        rows.add(Arrays.asList(adminAloqaBtn, murojatTaklifBtn));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }


    // ------------------ Ma'lumotlar va Kurs Funksiyalari ------------------

    private void sendInformation(long chatId, int messageId) throws TelegramApiException {
        
        // Uniteen Academy haqidagi asosiy ma'lumot matni (TALABGA MOS)
        String infoText = "ℹ️ <b>Uniteen Academy haqida ma'lumot</b>\n\n" +
                          "Uniteen Academy – <b>sifatli ta’lim va tajribali ustozlar</b> bilan bilim olishingizga yordam beradigan o‘quv markazi.\n\n" +
                          "<b>Qanday imkoniyatlar sizni kutadi:</b>\n" +
                          "✅ Yosh balansi saqlanadi: Guruhdagi yosh farqi 2–3 yoshdan oshmaydi.\n" +
                          "✅ Kichik guruhlar: 8–10 nafar o‘quvchi, <b>individual e’tibor kafolatlanadi</b>.\n" +
                          "✅ 1 haftalik bepul sinov darslari: Ustoz, dars va guruhni to‘liq sinab ko‘ring.\n" +
                          "✅ Haftalik testlar: Bilimni nazorat qilish va ota-onani xabardor qilish.\n" +
                          "✅ Qiziqarli eventlar: Yuqori natija ko‘rsatganlarga sovg‘alar va sayohatlar.\n\n" +
                          "✨ <b>Batafsil ma'lumot:</b> <a href=\"https://t.me/uniteenuz\">https://t.me/uniteenuz</a>"; 

        InlineKeyboardButton channelBtn = new InlineKeyboardButton("🔗 Kanalimizga o'tish");
        channelBtn.setUrl("https://t.me/uniteenuz"); 
        
        InlineKeyboardButton backBtn = new InlineKeyboardButton("🔙 Bosh menyu");
        backBtn.setCallbackData(CALLBACK_BACK_TO_MAIN);
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(channelBtn));
        rows.add(Collections.singletonList(backBtn));
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setText(infoText);
            editMessage.setReplyMarkup(markup);
            editMessage.setParseMode("Html"); 
            execute(editMessage);
        } catch (TelegramApiException e) {
             SendMessage message = new SendMessage();
             message.setChatId(chatId);
             message.setParseMode("Html"); 
             message.setText(infoText);
             message.setReplyMarkup(markup);
             execute(message);
        }
    }

    private void sendSupportInfo(long chatId, int messageId) throws TelegramApiException {
        
        // Support Teacher haqidagi maxsus matn (TALABGA MOS)
        String text = "ℹ️ <b>Support Teacher Ma'lumoti</b>\n\n" +
                      "<b>Support Teacher:</b> " + SUPPORT_TEACHER_NAME + "\n" +
                      "<b>Username:</b> <a href=\"https://t.me/" + SUPPORT_TEACHER_USERNAME.substring(1) + "\">" + SUPPORT_TEACHER_USERNAME + "</a>\n\n" +
                      "Support Teacher o'quvchilarning o'quv jarayonidagi savollariga javob berish, dars jadvaliga oid muammolarni hal qilish va kurslarga yozilishda yordam berish uchun mas'uldir.";

        InlineKeyboardButton registerBtn = new InlineKeyboardButton("📝 Uchrashuvga ro'yxatdan o'tish");
        registerBtn.setCallbackData(CALLBACK_SUPPORT_REGISTER);

        InlineKeyboardButton backToSupportBtn = new InlineKeyboardButton("◀️ Support menyusiga qaytish");
        backToSupportBtn.setCallbackData(CALLBACK_SUPPORT_MENU);
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(registerBtn));
        rows.add(Collections.singletonList(backToSupportBtn));
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setText(text);
            editMessage.setReplyMarkup(markup);
            editMessage.setParseMode("Html"); 
            execute(editMessage);
        } catch (TelegramApiException e) {
             SendMessage message = new SendMessage();
             message.setChatId(chatId);
             message.setParseMode("Html"); 
             message.setText(text);
             message.setReplyMarkup(markup);
             execute(message);
        }
    }

    private void sendCourseButtons(long chatId, int messageId) throws TelegramApiException {
        // Kurs narxlari tugmalarini yaratish logikasi (Yo'qolgan qism tiklandi)
        String text = "💰 <b>Kurs Narxlari</b>\n\nQuyidagi tugmalardan o'zingizni qiziqtirgan kursni tanlang va narxini ko'ring:";
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        int count = 0;

        for (Map.Entry<String, String> entry : COURSE_PRICES.entrySet()) {
            InlineKeyboardButton btn = new InlineKeyboardButton(COURSE_EMOJIS.getOrDefault(entry.getKey(), "") + " " + entry.getKey());
            btn.setCallbackData(entry.getKey()); 
            currentRow.add(btn);
            count++;
            
            if (count % 2 == 0) { 
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        
        if (!currentRow.isEmpty()) { 
            rows.add(currentRow);
        }

        InlineKeyboardButton backBtn = new InlineKeyboardButton("🔙 Bosh menyu");
        backBtn.setCallbackData(CALLBACK_BACK_TO_MAIN);
        rows.add(Collections.singletonList(backBtn));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setText(text);
            editMessage.setReplyMarkup(markup);
            editMessage.setParseMode("Html"); 
            execute(editMessage);
        } catch (TelegramApiException e) {
             SendMessage message = new SendMessage();
             message.setChatId(chatId);
             message.setParseMode("Html"); 
             message.setText(text);
             message.setReplyMarkup(markup);
             execute(message);
        }
    }

    private void handleCourseSelection(long chatId, int messageId, String courseName) throws TelegramApiException {
        // Kurs tanlovi ma'lumotlarini ko'rsatish logikasi (Yo'qolgan qism tiklandi)
        String price = COURSE_PRICES.get(courseName);
        String emoji = COURSE_EMOJIS.getOrDefault(courseName, "");
        
        String text = emoji + " <b>" + courseName + "</b> kurs narxi:\n\n" +
                      "✅ Bir oylik to'lov: <b>" + price + "</b>\n\n" +
                      "Ro'yxatdan o'tish yoki to'liq ma'lumot olish uchun quyidagi tugmani bosing:";

        InlineKeyboardButton enrollBtn = new InlineKeyboardButton("📝 Ro'yxatdan o'tish");
        enrollBtn.setCallbackData(CALLBACK_ENROLL_COURSE + courseName);

        InlineKeyboardButton backBtn = new InlineKeyboardButton("◀️ Kurslar menyusiga qaytish");
        backBtn.setCallbackData(CALLBACK_SHOW_PRICES);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(enrollBtn));
        rows.add(Collections.singletonList(backBtn));
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setText(text);
            editMessage.setReplyMarkup(markup);
            editMessage.setParseMode("Html"); 
            execute(editMessage);
        } catch (TelegramApiException e) {
             SendMessage message = new SendMessage();
             message.setChatId(chatId);
             message.setParseMode("Html"); 
             message.setText(text);
             message.setReplyMarkup(markup);
             execute(message);
        }
    }
    
    private void handleEnrollment(long chatId, String username, String firstName, String callbackData) throws TelegramApiException {
        // Kursga ro'yxatdan o'tish logikasi (Yo'qolgan qism tiklandi)
        String courseName = callbackData.replace(CALLBACK_ENROLL_COURSE, "");
        String phoneNumber = userPhones.getOrDefault(chatId, "Noma'lum");

        // 1. Notify Admin 
        SendMessage adminMsg = new SendMessage();
        adminMsg.setChatId(ADMIN_CHAT_ID);
        adminMsg.setText(
            "🔔 <b>Yangi kursga ro'yxatdan o'tish so'rovi!</b>\n\n" +
            "📚 Kurs: <b>" + courseName + "</b>\n" +
            "👤 Foydalanuvchi: <b>" + (firstName != null ? firstName : "Noma'lum") + "</b>\n" +
            "🔗 Username: " + (username != null ? "@" + username : "Noma'lum") + "\n" +
            "📞 Telefon: <code>+" + phoneNumber + "</code>\n\n" +
            "Admin, iltimos, foydalanuvchi bilan tezda bog'laning."
        );
        adminMsg.setParseMode("Html");
        execute(adminMsg);

        // 2. Send confirmation to user 
        SendMessage userReply = new SendMessage();
        userReply.setChatId(chatId);
        userReply.setText("✅ Ro'yxatdan o'tish so'rovingiz <b>" + courseName + "</b> kursi uchun qabul qilindi!\n\n" +
                          "Adminimiz (" + ADMIN_USERNAME + ") siz bilan tez orada bog'lanib, barcha ma'lumotlarni taqdim etadi. Rahmat!");
        userReply.setParseMode("Html");
        execute(userReply);
    }

    private void sendSupportMenu(long chatId, int messageId) throws TelegramApiException {
        // Support menu logikasi
        String text = "👨‍🏫 <b>Support Teacher Bo'limi</b>\n\n" +
                      "Bu bo'limda siz <b>" + SUPPORT_TEACHER_NAME + "</b> Support Teacher haqida ma'lumot olishingiz, ular bilan uchrashuvga ro'yxatdan o'tishingiz yoki ro'yxatdan bekor qilish/qabul soatlari haqida bilishingiz mumkin.";
        
        InlineKeyboardButton supportInfoBtn = new InlineKeyboardButton("ℹ️ Support haqida ma'lumot");
        supportInfoBtn.setCallbackData(CALLBACK_SUPPORT_INFO); 

        InlineKeyboardButton supportRegisterBtn = new InlineKeyboardButton("📝 Uchrashuvga ro'yxatdan o'tish");
        supportRegisterBtn.setCallbackData(CALLBACK_SUPPORT_REGISTER);

        InlineKeyboardButton unregisterAndHoursBtn = new InlineKeyboardButton("❌ Ro'yxatdan bekor qilish/Qabul soatlari");
        unregisterAndHoursBtn.setCallbackData(CALLBACK_UNREGISTER_AND_HOURS);
        
        InlineKeyboardButton backBtn = new InlineKeyboardButton("🔙 Bosh menyu");
        backBtn.setCallbackData(CALLBACK_BACK_TO_MAIN);
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(supportInfoBtn));
        rows.add(Arrays.asList(supportRegisterBtn, unregisterAndHoursBtn));
        rows.add(Collections.singletonList(backBtn));
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setText(text);
            editMessage.setReplyMarkup(markup);
            editMessage.setParseMode("Html"); 
            execute(editMessage);
        } catch (TelegramApiException e) {
             SendMessage message = new SendMessage();
             message.setChatId(chatId);
             message.setParseMode("Html"); 
             message.setText(text);
             message.setReplyMarkup(markup);
             execute(message);
        }
    }
    
    // ... (Qolgan yordamchi metodlar avvalgi kod bilan bir xil) ...
    
    private void handleSuggestion(Message message) throws TelegramApiException {
        long chatId = message.getChatId();
        String suggestionText = message.getText();
        User user = message.getFrom();
        
        SendMessage adminMsg = new SendMessage();
        adminMsg.setChatId(ADMIN_CHAT_ID);
        adminMsg.setText(
            "📝 <b>Yangi Murojaat/Taklif!</b>\n\n" +
            "👤 Foydalanuvchi: <b>" + (user.getFirstName() != null ? user.getFirstName() : "Noma'lum") + "</b>\n" +
            "🔗 Username: " + (user.getUserName() != null ? "@" + user.getUserName() : "Noma'lum") + "\n" +
            "📞 Telefon: <code>+" + userPhones.getOrDefault(chatId, "Yo'q") + "</code>\n" +
            "💬 Murojaat:\n" + suggestionText
        );
        adminMsg.setParseMode("Html");
        execute(adminMsg);
        
        SendMessage userReply = new SendMessage();
        userReply.setChatId(chatId);
        userReply.setText("✅ Murojaat/Taklifingiz muvaffaqiyatli yuborildi. Tez orada ko‘rib chiqiladi. Rahmat!");
        execute(userReply);
        
        waitingForSuggestion.put(chatId, false); 
    }
    
    private void handleUnregisterTimeInput(Message message) throws TelegramApiException {
        long chatId = message.getChatId();
        String timeRequest = message.getText();
        User user = message.getFrom();

        SendMessage adminMsg = new SendMessage();
        adminMsg.setChatId(ADMIN_CHAT_ID);
        adminMsg.setText(
            "❌ <b>Yangi Ro'yxatdan O'tishni Bekor qilish So'rovi / Qabulga yozilish!</b>\n\n" +
            "👤 Foydalanuvchi: <b>" + (user.getFirstName() != null ? user.getFirstName() : "Noma'lum") + "</b>\n" +
            "🔗 Username: " + (user.getUserName() != null ? "@" + user.getUserName() : "Noma'lum") + "\n" +
            "📞 Telefon: <code>+" + userPhones.getOrDefault(chatId, "Yo'q") + "</code>\n" +
            "🕰️ So'rov/Izoh:\n" + timeRequest + "\n\n" +
            "Admin, iltimos, ushbu so'rov bo'yicha foydalanuvchi bilan bog'laning."
        );
        adminMsg.setParseMode("Html");
        execute(adminMsg);

        SendMessage userReply = new SendMessage();
        userReply.setChatId(chatId);
        userReply.setText("✅ Ro'yxatdan bekor qilish so'rovingiz yoki qabulga yozilish murojaatingiz qabul qilindi. Adminimiz (" + ADMIN_USERNAME + ") siz kiritgan ma'lumotlar bo'yicha tez orada bog'lanadi.");
        userReply.setParseMode("Html");
        execute(userReply);

        waitingForUnregisterTime.put(chatId, false);
    }
    
    private void handleSupportRegistration(long chatId, int messageId) throws TelegramApiException {
        String text = "📝 <b>Support Teacher bilan uchrashuvga ro'yxatdan o'tish</b>\n\n" +
                      "⏰ <b>Support Teacher ish jadvali</b>:\n\n" +
                      "Dushanba - Juma: 10:00 dan 20:00 gacha\n" +
                      "Shanba: 10:00 dan 15:00 gacha\n" +
                      "Yakshanba: Dam olish kuni\n\n" +
                      "Iltimos, sizga qulay bo'lgan vaqt oralig'ini, yoki murojaat sababini yuboring (Masalan: 'Ertaga 10:00 da dars jadvali bo'yicha' yoki 'Bugun 15:00 da IELTS bo'yicha maslahat'):";
        
        InlineKeyboardButton backToSupportBtn = new InlineKeyboardButton("◀️ Support menyusiga qaytish");
        backToSupportBtn.setCallbackData(CALLBACK_SUPPORT_MENU);
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(Collections.singletonList(Collections.singletonList(backToSupportBtn)));

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setText(text);
            editMessage.setReplyMarkup(markup);
            editMessage.setParseMode("Html"); 
            execute(editMessage);
        } catch (TelegramApiException e) {
             SendMessage message = new SendMessage();
             message.setChatId(chatId);
             message.setText(text);
             message.setReplyMarkup(markup);
             message.setParseMode("Html"); 
             execute(message);
        }
        
        waitingForSupportRegistrationInfo.put(chatId, true);
        waitingForUnregisterTime.put(chatId, false); 
        waitingForSuggestion.put(chatId, false); 
    }
    
    private void handleSupportRegistrationInput(Message message) throws TelegramApiException {
        long chatId = message.getChatId();
        String requestInfo = message.getText();
        User user = message.getFrom();
        String phoneNumber = userPhones.getOrDefault(chatId, "Noma'lum");
        String username = user.getUserName();
        String firstName = user.getFirstName();

        SendMessage adminMsg = new SendMessage();
        adminMsg.setChatId(ADMIN_CHAT_ID);
        adminMsg.setText(
            "🔔 <b>Yangi Support Teacher bilan uchrashuv so'rovi!</b>\n\n" +
            "👤 Foydalanuvchi: <b>" + (firstName != null ? firstName : "Noma'lum") + "</b>\n" +
            "🔗 Username: " + (username != null ? "@" + username : "Noma'lum") + "\n" +
            "📞 Telefon: <code>+" + phoneNumber + "</code>\n" +
            "📝 Murojaat vaqti/Izoh:\n" + requestInfo + "\n\n" +
            "Admin, iltimos, ushbu ma'lumotlar bilan foydalanuvchi bilan bog'laning."
        );
        adminMsg.setParseMode("Html");
        execute(adminMsg);

        SendMessage userReply = new SendMessage();
        userReply.setChatId(chatId);
        userReply.setText("✅ Support Teacher bilan uchrashuvga ro'yxatdan o'tish so'rovingiz qabul qilindi!\n\n" +
                          "Admin (" + ADMIN_USERNAME + ") siz kiritgan ma'lumotlar bo'yicha tez orada bog'lanadi.");
        userReply.setParseMode("Html");
        execute(userReply);
        
        waitingForSupportRegistrationInfo.put(chatId, false);
    }

    private void handleUnregisterAndHoursRequest(long chatId, int messageId) throws TelegramApiException {
        String text = "❌ <b>Ro'yxatdan bekor qilish so'rovi / Qabul soatlari</b>\n\n" +
                      "⏰ Support Teacher ish jadvali:\n" +
                      "Dushanba - Juma: 10:00 dan 20:00 gacha\n" +
                      "Shanba: 10:00 dan 15:00 gacha\n" +
                      "Yakshanba: Dam olish kuni\n\n" +
                      "Agar siz <b>ro'yxatdan bekor qilmoqchi</b> bo'lsangiz yoki <b>Support Teacher bilan uchrashmoqchi</b> bo'lsangiz, iltimos, sizga qulay bo'lgan vaqt oralig'ini, yoki boshqa izohlaringizni yuboring (Masalan: 'Ertaga 14:00 dan 16:00 gacha' yoki 'Darsga yozilgan edim, bekor qilmoqchiman'):";
        
        InlineKeyboardButton backToSupportBtn = new InlineKeyboardButton("◀️ Support menyusiga qaytish");
        backToSupportBtn.setCallbackData(CALLBACK_SUPPORT_MENU);
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(Collections.singletonList(Collections.singletonList(backToSupportBtn)));

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setText(text);
            editMessage.setReplyMarkup(markup);
            editMessage.setParseMode("Html"); 
            execute(editMessage);
        } catch (TelegramApiException e) {
             SendMessage message = new SendMessage();
             message.setChatId(chatId);
             message.setText(text);
             message.setReplyMarkup(markup);
             message.setParseMode("Html"); 
             execute(message);
        }
        
        waitingForUnregisterTime.put(chatId, true);
        waitingForSupportRegistrationInfo.put(chatId, false); 
        waitingForSuggestion.put(chatId, false); 
    }

    // ------------------ Main Method ------------------
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new Main());
            System.out.println("Uniteen Academy Bot is running...");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}