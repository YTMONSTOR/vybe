package com.vybe.repository;

import com.vybe.entity.Playlist;
import com.vybe.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByOwnerOrderByUpdatedAtDesc(User owner);

    List<Playlist> findByOwnerAndCollectionOrderByUpdatedAtDesc(User owner, boolean collection);

    List<Playlist> findByOwnerAndPublicVisibleTrueOrderByUpdatedAtDesc(User owner);
}
