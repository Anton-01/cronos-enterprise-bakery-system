package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;

class InMemoryAvatarStorageContractTest extends AvatarStorageContractTest {

    @Override
    protected AvatarStorage createStorage() {
        return new InMemoryAvatarStorage();
    }
}
