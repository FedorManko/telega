package com.manko.telega.service;

import com.manko.telega.config.BotConfig;
import com.manko.telega.models.Ads;
import com.manko.telega.models.User;
import com.manko.telega.repositories.AdsRepository;
import com.manko.telega.repositories.UsersRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final AdsRepository adsRepository;
    private final UsersRepository usersRepository;
    private static final String HELP_TEXT = "This bot is to created to demonstrate Spring capabilities.\n" +
            "You can execute from the main menu on the left or by typing a command.\n" +
            "Type /start to see a welcome message.\n" +
            "Type /mydata to see data stored about yourself.\n" +
            "Type /help to see this message again.\n";
    private static final String YES_BUTTON = "YES_BUTTON";
    private static final String NO_BUTTON = "NO_BUTTON";
    private final BotConfig botConfig;

    @Autowired
    public TelegramBot(AdsRepository adsRepository, UsersRepository usersRepository, BotConfig botConfig) {
        this.adsRepository = adsRepository;
        this.usersRepository = usersRepository;
        this.botConfig = botConfig;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start","Get a welcome message"));
        listOfCommands.add(new BotCommand("/mydata","Get your data stored"));
        listOfCommands.add(new BotCommand("/deletedata", "Delete my data"));
        listOfCommands.add(new BotCommand("/help","Info about how to use this bot"));
        listOfCommands.add(new BotCommand("/settings","Set new preferences"));

        try {
            this.execute(new SetMyCommands(listOfCommands,new BotCommandScopeDefault(),null));
        } catch (TelegramApiException e) {
            log.error("Error settings bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText()){
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            System.out.println(chatId);
            System.out.println(usersRepository.findById(update.getMessage().getChat().getId()).get().getChatId());
            if(messageText.contains("/send") && chatId == usersRepository.findById(update.getMessage().getChat().getId()).get().getChatId()){
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = usersRepository.findAll();
                for (User us: users) {
                    prepareAndSendMessage(us.getChatId(),textToSend);
                }
            } else {
                switch (messageText) {
                    case "/start" -> {
                        startCommandReceived(chatId, update.getMessage().getChat().getUserName());
                        registerUser(update.getMessage());
                    }
                    case "/help" -> prepareAndSendMessage(chatId, HELP_TEXT);
                    case "/mydata" -> prepareAndSendMessage(chatId, getData(update.getMessage()));
                    case "/deletedata" -> prepareAndSendMessage(chatId, deleteData(update.getMessage()));
                    case "/register" -> register(chatId);
                    default -> prepareAndSendMessage(chatId, "Sorry command was not recognize");
                }
            }
        } else if(update.hasCallbackQuery()){
            String callBackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            if(callBackData.equals(YES_BUTTON)){
                String text = "You pressed YES button";
                executeEditMessageTest(text,chatId,messageId);
            } else if(callBackData.equals(NO_BUTTON)) {
                String text = "You pressed NO button";
                executeEditMessageTest(text,chatId,messageId);
            }
        }
    }
    private void startCommandReceived(long chatId, String userName){
            String answer = EmojiParser.parseToUnicode("Hi," + userName + ", nice to meet you!" + ":blush:");
            log.info("Replied to user: " + userName);
            sendMessage(chatId,answer);
    }

    private void sendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        ReplyKeyboardMarkup keyboardMarkup  = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRowList = new ArrayList<>();
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardRow.add("weather");
        keyboardRow.add("get random joke");
        keyboardRowList.add(keyboardRow);
        keyboardRow = new KeyboardRow();
        keyboardRow.add("register");
        keyboardRow.add("check my data");
        keyboardRow.add("delete my data");
        keyboardRowList.add(keyboardRow);
        keyboardMarkup.setKeyboard(keyboardRowList);
        message.setReplyMarkup(keyboardMarkup);
        executeMessage(message);
    }
    private void registerUser(Message message){
        if(usersRepository.findById(message.getChatId()).isEmpty()){
            var chatId = message.getChatId();
            var chat = message.getChat();
            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setUserName(chat.getUserName());
            user.setLastName(chat.getLastName());
            user.setRegisteredAt(LocalDateTime.now());
            usersRepository.save(user);
            log.info("User saved" + user);
        }
    }
    private String getData(Message message){
        StringBuilder stringBuilder = new StringBuilder();
        Chat chat = message.getChat();
        if(usersRepository.findById(message.getChatId()).isPresent()){
           stringBuilder.append(chat.getUserName()).append(" , ").append(chat.getFirstName()).append(" , ").
                   append(chat.getPermissions());
           log.info("Information presented");
        } else {
            log.info("something Wrong");
        }
        return stringBuilder.toString();
    }
    private String deleteData(Message message){
        Chat chat = message.getChat();
        if(usersRepository.findById(message.getChatId()).isPresent()){
            usersRepository.deleteById(chat.getId());
            log.info("Delete successfully");
        } else {
            log.info("something Wrong");
        }
        return "Your data delete";
    }
    private void  register(long chatId){
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Do you really want to register");
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> buttonList = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();
        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);
        var noButton = new InlineKeyboardButton();
        noButton.setText("No");
        noButton.setCallbackData(NO_BUTTON);
        buttonList.add(yesButton);
        buttonList.add(noButton);
        rowsLine.add(buttonList);
        inlineKeyboardMarkup.setKeyboard(rowsLine);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        executeMessage(sendMessage);
    }
    private void executeEditMessageTest(String text, long chatId, long messageId){
        EditMessageText messageText = new EditMessageText();
        messageText.setChatId(chatId);
        messageText.setText(text);
        messageText.setMessageId((int) messageId);
        try {
            execute(messageText);
        } catch (TelegramApiException e){
            log.error("Error occurred " + e.getMessage());
        }
    }

    private void  executeMessage(SendMessage message){
        try {
            execute(message);
        } catch (TelegramApiException e){
            log.error("Error occurred " + e.getMessage());
        }
    }
    private void prepareAndSendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }
    @Scheduled(cron = "${cron.scheduler}")
    private void sendAds(){
        var ads = adsRepository.findAll();
        var users = usersRepository.findAll();
        for (Ads ad: ads){
            for (User us: users) {
                prepareAndSendMessage(us.getChatId(),ad.getAd());
            }
        }
    }
}
