package com.vybe.repository;

import com.vybe.entity.Subscription;
import com.vybe.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsBySubscriberAndChannel(User subscriber, User channel);

    Optional<Subscription> findBySubscriberAndChannel(User subscriber, User channel);

    @Query("select s.channel from Subscription s where s.subscriber = :user order by s.createdAt desc")
    List<User> channelsOf(@Param("user") User user);

    @Query("select s.channel.id from Subscription s where s.subscriber = :user")
    List<Long> channelIdsOf(@Param("user") User user);

    List<Subscription> findByChannelAndCreatedAtAfter(User channel, LocalDateTime since);

    long countByChannel(User channel);
}
