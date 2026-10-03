package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;

/** Dev/test adapter: {@code {root}/avatars/{userId}/{hash}.jpg}, written atomically (temp file + move). */
public class LocalFilesystemAvatarStorage implements AvatarStorage {

    private final Path root;
    private final Function<AvatarKey, URI> publicUrls;

    public LocalFilesystemAvatarStorage(Path root, Function<AvatarKey, URI> publicUrls) {
        this.root = root.toAbsolutePath().normalize();
        this.publicUrls = publicUrls;
    }

    @Override
    public void put(AvatarKey key, byte[] jpeg) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, jpeg);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store avatar " + key.fileName(), e);
        }
    }

    @Override
    public Optional<byte[]> read(AvatarKey key) {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read avatar " + key.fileName(), e);
        }
    }

    @Override
    public boolean exists(AvatarKey key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(AvatarKey key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete avatar " + key.fileName(), e);
        }
    }

    @Override
    public URI publicUrl(AvatarKey key) {
        return publicUrls.apply(key);
    }

    private Path resolve(AvatarKey key) {
        Path path = root.resolve(key.value()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Avatar key escapes the storage root");
        }
        return path;
    }
}
