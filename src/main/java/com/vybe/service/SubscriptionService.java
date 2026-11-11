package com.vybe.service;

import com.vybe.entity.Subscription;
import com.vybe.entity.User;
import com.vybe.exception.BadRequestException;
import com.vybe.repository.SubscriptionRepository;
import com.vybe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;

    public SubscriptionService(SubscriptionRepository subscriptions, UserRepository users) {
        this.subscriptions = subscriptions;
        this.users = users;
    }

    public boolean isSubscribed(User user, User channel) {
        return user != null && !user.getId().equals(channel.getId())
                && subscriptions.existsBySubscriberAndChannel(user, channel);
    }

    public List<User> channelsOf(User user) {
        return subscriptions.channelsOf(user);
    }

    /** @return true if subscribed after the call. */
    @Transactional
    public boolean toggle(User user, Long channelId) {
        User channel = users.findById(channelId).orElseThrow(() -> new BadRequestException("Канал не найден"));
        if (channel.getId().equals(user.getId())) throw new BadRequestException("Нельзя подписаться на себя");
        Optional<Subscription> existing = subscriptions.findBySubscriberAndChannel(user, channel);
        if (existing.isPresent()) {
            subscriptions.delete(existing.get());
            subscriptions.flush();
            channel.setSubscriberCount(subscriptions.countByChannel(channel));
            return false;
        }
        Subscription s = new Subscription();
        s.setSubscriber(user);
        s.setChannel(channel);
        subscriptions.saveAndFlush(s);
        channel.setSubscriberCount(subscriptions.countByChannel(channel));
        return true;
    }

    public long count(User channel) {
        return users.findById(channel.getId()).map(User::getSubscriberCount).orElse(0L);
    }
}
