package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test double honouring the full {@link AvatarStorage} contract (verified by {@link AvatarStorageContractTest}). */
public class InMemoryAvatarStorage implements AvatarStorage {

    private final Map<AvatarKey, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(AvatarKey key, byte[] jpeg) {
        objects.put(key, jpeg.clone());
    }

    @Override
    public Optional<byte[]> read(AvatarKey key) {
        return Optional.ofNullable(objects.get(key)).map(byte[]::clone);
    }

    @Override
    public boolean exists(AvatarKey key) {
        return objects.containsKey(key);
    }

    @Override
    public void delete(AvatarKey key) {
        objects.remove(key);
    }

    @Override
    public URI publicUrl(AvatarKey key) {
        return URI.create("https://cdn.test/" + key.value());
    }

    public int size() {
        return objects.size();
    }
}
