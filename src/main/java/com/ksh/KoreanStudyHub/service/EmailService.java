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
        message.setSubject("[Korean Study Hub] Cap lai mat khau tai khoan");
        message.setText("Chào bạn,\n\n"
                + "Tài khoản của bạn đã được quản trị viên đặt lại mật khẩu mới.\n"
                + "Mật khẩu đăng nhập mới của bạn là: " + newPassword + "\n\n"
                + "Vui lòng đăng nhập và đổi lại mật khẩu của bạn để đảm bảo an toàn.\n\n"
                + "Trân trọng,\n"
                + "Korean Study Hub Team");
        mailSender.send(message);
    }
}
