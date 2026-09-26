package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

class LocalFilesystemAvatarStorageContractTest extends AvatarStorageContractTest {

    @TempDir
    Path root;

    @Override
    protected AvatarStorage createStorage() {
        AvatarProperties properties = new AvatarProperties("local", URI.create("http://localhost:9191/api/v1/public/"), root.toString(), null);
        return new LocalFilesystemAvatarStorage(root, key -> properties.urlFor(key.value()));
    }
}
