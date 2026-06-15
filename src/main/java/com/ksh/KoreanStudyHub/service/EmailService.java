package com.ksh.KoreanStudyHub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendNewPassword(String toEmail, String newPassword) {


        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(toEmail);
        message.setSubject("[Korean Study Hub] Test");

        message.setText(
                "Password moi: " + newPassword
        );

        mailSender.send(message);

    }
}
