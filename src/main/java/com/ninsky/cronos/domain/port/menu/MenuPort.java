package com.ninsky.cronos.domain.port.menu;

import com.ninsky.cronos.domain.model.menu.MenuNode;

import java.util.List;
import java.util.Set;

public interface MenuPort {

    /** Full active menu tree, pruned to only nodes the caller's granted permissions unlock. */
    List<MenuNode> buildTree(Set<String> grantedPermissionNames);
}
