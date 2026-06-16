package com.ksh.KoreanStudyHub.service;

import com.ksh.KoreanStudyHub.entity.Notification;
import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.repository.NotificationRepository;
import com.ksh.KoreanStudyHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public List<Notification> getAll() {
        return notificationRepository.findAll(Sort.by("createdAt").descending());
    }

    public void send(Long userId, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message);
        n.setStatus("UNREAD");
        notificationRepository.save(n);
    }

    public void sendToAll(String message) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            Notification n = new Notification();
            n.setUser(user);
            n.setMessage(message);
            n.setStatus("UNREAD");
            notificationRepository.save(n);
        }
    }

    public void delete(Long id) { notificationRepository.deleteById(id); }

    public long countUnread() { return notificationRepository.countByStatus("UNREAD"); }
    public long countTotal() { return notificationRepository.count(); }
}
